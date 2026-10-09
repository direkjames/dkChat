package com.direkjames.dkchat.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.permissions.Permissible;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The color pipeline. Everything is converted to MiniMessage first:
 * legacy &amp;a codes, &amp;#RRGGBB, &amp;x&amp;R&amp;R&amp;G&amp;G&amp;B&amp;B and MiniMessage itself.
 *
 * <p>Config text ("trusted") can use every MiniMessage tag. Player-typed text only gets the
 * tags their permissions allow, and never click/hover/insert tags, so players can't post
 * messages that run commands when clicked.</p>
 */
public final class Colors {

    private Colors() {
    }

    private static final Pattern BUKKIT_HEX = Pattern.compile("[&§][xX]((?:[&§][0-9a-fA-F]){6})");
    private static final Pattern AMP_HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fA-Fk-oK-OrR])");
    /** Matches the "<" of a MiniMessage hex color tag, e.g. <#FF00AA> or <color:#FF00AA>. */
    private static final Pattern MINI_HEX_OPEN = Pattern.compile(
            "<(?=/?(?:#|(?:c|color|colour):#)[0-9a-fA-F]{6}>)", Pattern.CASE_INSENSITIVE);

    private static final Map<Allow, MiniMessage> PLAYER_PARSERS = new ConcurrentHashMap<>();

    /** Which kinds of formatting a piece of text may use. */
    public record Allow(boolean colors, boolean hex, boolean decorations, boolean magic, boolean gradients) {

        public static final Allow ALL = new Allow(true, true, true, true, true);

        public static Allow of(Permissible p) {
            return new Allow(
                    p.hasPermission("dkchat.color.legacy"),
                    p.hasPermission("dkchat.color.hex"),
                    p.hasPermission("dkchat.color.format"),
                    p.hasPermission("dkchat.color.magic"),
                    p.hasPermission("dkchat.color.gradient"));
        }
    }

    /** Parses config text: legacy, HEX and every MiniMessage tag, plus the given placeholders. */
    public static Component trusted(String text, TagResolver... placeholders) {
        return MiniMessage.miniMessage().deserialize(legacyToMini(text, Allow.ALL), placeholders);
    }

    /** Parses text a player typed, limited to what {@code allow} permits. */
    public static Component player(String text, Allow allow) {
        String s = text;
        if (!allow.hex()) {
            // Escape MiniMessage hex tags so they show as plain text.
            s = MINI_HEX_OPEN.matcher(s).replaceAll(Matcher.quoteReplacement("\\<"));
        }
        s = legacyToMini(s, allow);
        return PLAYER_PARSERS.computeIfAbsent(allow, Colors::buildPlayerParser).deserialize(s);
    }

    /**
     * Converts legacy color codes to MiniMessage tags. Codes the {@code allow} doesn't permit
     * are left untouched, so they show up as plain text.
     */
    public static String legacyToMini(String input, Allow allow) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }
        String s = input;
        if (allow.hex()) {
            s = replace(BUKKIT_HEX, s, m -> "<#" + m.group(1).replaceAll("[&§]", "") + ">");
            s = replace(AMP_HEX, s, m -> "<#" + m.group(1) + ">");
        }
        s = replace(LEGACY_CODE, s, m -> {
            String tag = legacyTag(Character.toLowerCase(m.group(1).charAt(0)), allow);
            return tag == null ? m.group() : "<" + tag + ">";
        });
        return s;
    }

    private static String legacyTag(char code, Allow allow) {
        String color = switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            default -> null;
        };
        if (color != null) {
            return allow.colors() ? color : null;
        }
        return switch (code) {
            case 'k' -> allow.magic() ? "obfuscated" : null;
            case 'l' -> allow.decorations() ? "bold" : null;
            case 'm' -> allow.decorations() ? "strikethrough" : null;
            case 'n' -> allow.decorations() ? "underlined" : null;
            case 'o' -> allow.decorations() ? "italic" : null;
            case 'r' -> (allow.colors() || allow.decorations()) ? "reset" : null;
            default -> null;
        };
    }

    private static MiniMessage buildPlayerParser(Allow allow) {
        TagResolver.Builder tags = TagResolver.builder();
        if (allow.colors() || allow.hex()) {
            tags.resolver(StandardTags.color());
        }
        if (allow.colors() || allow.decorations()) {
            tags.resolver(StandardTags.reset());
        }
        if (allow.decorations()) {
            for (TextDecoration decoration : TextDecoration.values()) {
                if (decoration != TextDecoration.OBFUSCATED) {
                    tags.resolver(StandardTags.decorations(decoration));
                }
            }
        }
        if (allow.magic()) {
            tags.resolver(StandardTags.decorations(TextDecoration.OBFUSCATED));
        }
        if (allow.gradients()) {
            tags.resolver(StandardTags.gradient());
            tags.resolver(StandardTags.rainbow());
        }
        // Deliberately no click, hover, insertion, font, translatable, newline or selector tags.
        return MiniMessage.builder().tags(tags.build()).build();
    }

    private static String replace(Pattern pattern, String input, Function<MatchResult, String> replacer) {
        return pattern.matcher(input).replaceAll(m -> Matcher.quoteReplacement(replacer.apply(m)));
    }
}
