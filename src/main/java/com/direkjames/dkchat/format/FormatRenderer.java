package com.direkjames.dkchat.format;

import com.direkjames.dkchat.hook.PlaceholderHook;
import com.direkjames.dkchat.text.Colors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Builds the final chat line from a format. {name}, {player} and {message} are inserted as
 * components (not as text), so nothing a player types can break out of the format.
 */
public final class FormatRenderer {

    private static final String NAME = "dk_name";
    private static final String PLAYER = "dk_player";
    private static final String MESSAGE = "dk_message";

    private final FormatManager formats;
    private final PlaceholderHook placeholders;
    private final BooleanSupplier hoverEnabled;

    public FormatRenderer(FormatManager formats, PlaceholderHook placeholders, BooleanSupplier hoverEnabled) {
        this.formats = formats;
        this.placeholders = placeholders;
        this.hoverEnabled = hoverEnabled;
    }

    public Component render(Player player, ChatFormat format, Component message) {
        Component name = nameWithHover(player, format);

        String template = format.format()
                .replace("{name}", "<" + NAME + ">")
                .replace("{player}", "<" + PLAYER + ">")
                .replace("{message}", "<" + MESSAGE + ">");
        template = placeholders.apply(player, template);

        return Colors.trusted(template,
                Placeholder.component(NAME, name),
                Placeholder.unparsed(PLAYER, player.getName()),
                Placeholder.component(MESSAGE, message));
    }

    /** The player's display name (EssentialsX nickname if set) with the configured tooltip. */
    public Component nameWithHover(Player player, ChatFormat format) {
        Component displayName = Colors.displayName(player);
        if (!hoverEnabled.getAsBoolean()) {
            return displayName;
        }
        List<String> lines = formats.hoverFor(format);
        if (lines.isEmpty()) {
            return displayName;
        }

        List<Component> parts = new ArrayList<>(lines.size());
        for (String line : lines) {
            String text = line
                    .replace("{name}", "<" + NAME + ">")
                    .replace("{player}", "<" + PLAYER + ">");
            text = placeholders.apply(player, text);
            parts.add(Colors.trusted(text,
                    Placeholder.component(NAME, displayName),
                    Placeholder.unparsed(PLAYER, player.getName())));
        }
        Component tooltip = Component.join(JoinConfiguration.newlines(), parts);
        return displayName.hoverEvent(HoverEvent.showText(tooltip));
    }
}
