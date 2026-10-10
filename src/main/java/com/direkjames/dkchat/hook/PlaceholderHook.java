package com.direkjames.dkchat.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
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
 *
 * <p>Nested placeholders: a value like a LuckPerms prefix can itself contain placeholders
 * (for example {@code %nexo_dev%} typed into the prefix). PlaceholderAPI does not expand those,
 * so after a value is cleaned we expand placeholders inside it again, but only for the
 * identifiers listed in {@code placeholders.nested-identifiers} (default: nexo). A fixed
 * allowlist means a nickname or tag can never smuggle in something like %player_ip%.</p>
 */
public final class PlaceholderHook {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[^%\\s]+%");
    private static final Pattern UNSAFE_TAG = Pattern.compile(
            "</?(?:click|hover|insert|insertion|nbt|data|selector|sel|score|keybind|key|lang|translate|tr)(?::[^>]*)?>",
            Pattern.CASE_INSENSITIVE);

    /** How many levels deep nested placeholders are expanded. Keeps loops like %a% -> %a% harmless. */
    private static final int MAX_NESTING = 3;

    private final boolean enabled;
    private final Supplier<List<String>> nestedIdentifiers;

    public PlaceholderHook() {
        this(() -> List.of("nexo"));
    }

    public PlaceholderHook(Supplier<List<String>> nestedIdentifiers) {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.nestedIdentifiers = nestedIdentifiers;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Replaces %placeholders% for the given player. Only call this on config text, never on player input. */
    public String apply(Player player, String text) {
        if (!enabled || text.indexOf('%') < 0) {
            return text;
        }
        return resolve(player, text, MAX_NESTING);
    }

    private String resolve(Player player, String text, int depth) {
        return PLACEHOLDER.matcher(text).replaceAll(match -> {
            String value = Bridge.set(player, match.group());
            if (value.equals(match.group())) {
                return Matcher.quoteReplacement(value);
            }
            value = clean(value);
            if (depth > 1 && value.indexOf('%') >= 0) {
                value = resolveNested(player, value, depth - 1);
            }
            return Matcher.quoteReplacement(value);
        });
    }

    /** Expands only the allowlisted identifiers inside an already-resolved value. */
    private String resolveNested(Player player, String value, int depth) {
        List<String> allowed = nestedIdentifiers.get();
        if (allowed == null || allowed.isEmpty()) {
            return value;
        }
        return PLACEHOLDER.matcher(value).replaceAll(match -> {
            String inner = match.group();
            if (!isAllowedNested(inner, allowed)) {
                return Matcher.quoteReplacement(inner);
            }
            String resolved = Bridge.set(player, inner);
            if (resolved.equals(inner)) {
                return Matcher.quoteReplacement(inner);
            }
            resolved = clean(resolved);
            if (depth > 1 && resolved.indexOf('%') >= 0) {
                resolved = resolveNested(player, resolved, depth - 1);
            }
            return Matcher.quoteReplacement(resolved);
        });
    }

    /** "%nexo_dev%" has the identifier "nexo". */
    private static boolean isAllowedNested(String placeholder, List<String> allowed) {
        String body = placeholder.substring(1, placeholder.length() - 1);
        int underscore = body.indexOf('_');
        String identifier = (underscore < 0 ? body : body.substring(0, underscore)).toLowerCase(Locale.ROOT);
        for (String entry : allowed) {
            if (entry != null && entry.trim().toLowerCase(Locale.ROOT).equals(identifier)) {
                return true;
            }
        }
        return false;
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
