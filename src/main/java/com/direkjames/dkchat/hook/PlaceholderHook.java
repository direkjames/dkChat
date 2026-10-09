package com.direkjames.dkchat.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI support. PlaceholderAPI classes are only touched when the plugin is
 * installed, so dkChat still runs without it.
 */
public final class PlaceholderHook {

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
        return Bridge.set(player, text);
    }

    /** Separate class so PlaceholderAPI is only loaded when it is present. */
    private static final class Bridge {
        static String set(Player player, String text) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
