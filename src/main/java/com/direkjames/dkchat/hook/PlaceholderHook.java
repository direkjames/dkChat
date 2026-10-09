package com.direkjames.dkchat.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional PlaceholderAPI support. PlaceholderAPI classes are only touched when the plugin is
 * installed, so dkChat still runs without it.
 *
 * <p>Each %placeholder% is resolved on its own and its value is cleaned: click, hover,
 * insertion and similar MiniMessage tags are removed. Values like custom tags or nicknames can
 * come from players through other plugins, and must never be able to add a run_command click
 * to chat.</p>
 */
public final class PlaceholderHook {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[^%\\s]+%");
    private static final Pattern UNSAFE_TAG = Pattern.compile(
            "</?(?:click|hover|insert|insertion|nbt|data|selector|sel|score|keybind|key|lang|translate|tr)(?::[^>]*)?>",
            Pattern.CASE_INSENSITIVE);

    private final boolean enabled;

    public PlaceholderHook() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Replaces %placeholders% for the given player. Only call this on config text, never on player input. */
    public String apply(Player player, String text) {
        if (!enabled || text.indexOf('%') < 0) {
            return text;
        }
        return PLACEHOLDER.matcher(text).replaceAll(match -> {
            String value = Bridge.set(player, match.group());
            return Matcher.quoteReplacement(value.equals(match.group()) ? value : clean(value));
        });
    }

    /** Removes interactive tags. Repeats until stable so nested tricks like "<cl<click:x>ick:...>" fail. */
    static String clean(String value) {
        String current = value;
        for (int i = 0; i < 10; i++) {
            String next = UNSAFE_TAG.matcher(current).replaceAll("");
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
        // Still changing after 10 passes: something is trying hard, show no tags at all.
        return current.replace("<", "");
    }

    /** Separate class so PlaceholderAPI is only loaded when it is present. */
    private static final class Bridge {
        static String set(Player player, String text) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
