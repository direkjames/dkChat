package com.direkjames.dkchat.text;

import com.direkjames.dkchat.hook.PlaceholderHook;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.logging.Logger;

/** Shared helpers for multi-line messages: {center}, sounds and durations. */
public final class Lines {

    private Lines() {
    }

    private static final String CENTER = "{center}";
    /** Half the width of the default chat box, in pixels. */
    private static final int CENTER_PX = 154;
    /** A space is 3 pixels wide plus 1 pixel of spacing. */
    private static final int SPACE_PX = 4;

    /**
     * Renders one config line. A line starting with {center} is centered in the chat box.
     * {name} and {player} become the player's display name and username, and PlaceholderAPI
     * placeholders are filled in for {@code player} (if not null).
     */
    public static Component render(String line, Player player, PlaceholderHook placeholders, TagResolver... extra) {
        boolean center = line.startsWith(CENTER);
        String text = center ? line.substring(CENTER.length()) : line;
        if (player != null) {
            text = placeholders.apply(player, text);
        }
        Component component = Colors.trusted(text, extra);
        return center ? center(component) : component;
    }

    /** Pads a line with spaces so it sits in the middle of the default chat width. */
    public static Component center(Component line) {
        int width = width(line, false);
        int pad = (CENTER_PX - width / 2) / SPACE_PX;
        if (pad <= 0) {
            return line;
        }
        return Component.text(" ".repeat(pad)).append(line);
    }

    private static int width(Component component, boolean parentBold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.TRUE || (state == TextDecoration.State.NOT_SET && parentBold);
        int width = 0;
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int i = 0; i < content.length(); i++) {
                width += charWidth(content.charAt(i)) + (bold ? 1 : 0);
            }
        }
        for (Component child : component.children()) {
            width += width(child, bold);
        }
        return width;
    }

    /** Pixel width of a character in Minecraft's default font, including 1px spacing. */
    private static int charWidth(char c) {
        return switch (c) {
            case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', 't', '[', ']', ' ' -> 4;
            case 'f', 'k', '"', '(', ')', '<', '>', '*', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    /** Plays the sound at {@code path} (enabled, name, volume, pitch) if it is enabled. */
    public static void playSound(Player player, YamlConfiguration config, String path, Logger logger) {
        if (!config.getBoolean(path + ".enabled")) {
            return;
        }
        String name = config.getString(path + ".name");
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(name.toLowerCase(Locale.ROOT)), Sound.Source.MASTER,
                    (float) config.getDouble(path + ".volume"), (float) config.getDouble(path + ".pitch")));
        } catch (RuntimeException e) {
            logger.warning("Invalid sound '" + name + "' at " + path + ".");
        }
    }

    /** 3725 -> "1h 2m 5s". */
    public static String duration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder out = new StringBuilder();
        if (h > 0) {
            out.append(h).append("h ");
        }
        if (m > 0) {
            out.append(m).append("m ");
        }
        if (s > 0 || out.isEmpty()) {
            out.append(s).append("s");
        }
        return out.toString().trim();
    }
}
