package com.direkjames.dkchat.listener;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.format.ChatFormat;
import com.direkjames.dkchat.text.Colors;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Formats public chat.
 *
 * <p>We only set the event's message and renderer and never cancel-and-rebroadcast, so plugins
 * that read Paper's chat event (DiscordSRV, chat loggers) keep working, and plugins that cancel
 * chat earlier (LiteBans mutes) are respected through {@code ignoreCancelled}.</p>
 */
public final class ChatListener implements Listener {

    private final DkChat plugin;

    public ChatListener(DkChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.configs().config().getBoolean("chat.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
        Component message = Colors.player(typed, Colors.Allow.of(player));

        // A message made only of color codes ("&c&l") would show up empty.
        if (PlainTextComponentSerializer.plainText().serialize(message).isBlank()) {
            event.setCancelled(true);
            return;
        }

        // Setting the message (not just the renderer) is what DiscordSRV relays to Discord.
        event.message(message);

        ChatFormat format = plugin.formats().select(player);
        event.renderer(ChatRenderer.viewerUnaware(
                (source, sourceDisplayName, finalMessage) -> plugin.renderer().render(source, format, finalMessage)));
    }
}
