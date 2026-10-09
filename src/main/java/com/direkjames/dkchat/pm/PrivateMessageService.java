package com.direkjames.dkchat.pm;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.hook.LiteBansHook;
import com.direkjames.dkchat.text.Colors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.Locale;
import java.util.UUID;

/** Sends private messages, sets reply targets and shows them to spies. */
public final class PrivateMessageService {

    /** Stands in for the console in reply targets. */
    public static final UUID CONSOLE_ID = new UUID(0L, 0L);

    public static final String MSG_PERMISSION = "dkchat.msg";
    public static final String SEE_VANISHED_PERMISSION = "dkchat.msg.seevanished";
    public static final String SPY_EXEMPT_PERMISSION = "dkchat.spy.exempt";
    public static final String IGNORE_EXEMPT_PERMISSION = "dkchat.ignore.exempt";

    private final DkChat plugin;
    private final LiteBansHook liteBans;
    private volatile UUID consoleReplyTarget;

    public PrivateMessageService(DkChat plugin, LiteBansHook liteBans) {
        this.plugin = plugin;
        this.liteBans = liteBans;
    }

    // ------------------------------------------------------------ lookups

    /**
     * Finds an online player by exact name (any case) that the sender is allowed to see.
     * Vanished players count as offline unless the sender has dkchat.msg.seevanished.
     */
    public Player findVisible(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        return target != null && canSee(sender, target) ? target : null;
    }

    public boolean canSee(CommandSender sender, Player target) {
        if (!(sender instanceof Player viewer) || viewer.hasPermission(SEE_VANISHED_PERMISSION)) {
            return true;
        }
        if (!viewer.canSee(target)) {
            return false;
        }
        for (MetadataValue value : target.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return false;
            }
        }
        return true;
    }

    /** Who the sender would reply to, or null if nobody (or that player went offline/vanished). */
    public CommandSender replyTarget(CommandSender sender) {
        UUID id = sender instanceof Player player ? plugin.users().get(player).replyTarget() : consoleReplyTarget;
        return id == null ? null : resolve(sender, id);
    }

    public boolean hasReplyTarget(CommandSender sender) {
        return sender instanceof Player player ? plugin.users().get(player).replyTarget() != null : consoleReplyTarget != null;
    }

    private CommandSender resolve(CommandSender viewer, UUID id) {
        if (CONSOLE_ID.equals(id)) {
            return Bukkit.getConsoleSender();
        }
        Player player = Bukkit.getPlayer(id);
        return player != null && canSee(viewer, player) ? player : null;
    }

    private static UUID idOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }

    // ------------------------------------------------------------ sending

    /** Sends a private message. Checks ignore lists and (if LiteBans is installed) mutes. */
    public void send(CommandSender sender, CommandSender target, String text) {
        if (target instanceof Player receiver && sender instanceof Player from
                && !from.hasPermission(IGNORE_EXEMPT_PERMISSION)
                && plugin.users().isIgnoring(receiver.getUniqueId(), from.getUniqueId())) {
            plugin.messages().send(sender, "msg-ignored", "player", receiver.getName());
            return;
        }

        Component message = Colors.player(text,
                sender instanceof Player p ? Colors.Allow.of(p) : Colors.Allow.ALL);
        if (PlainTextComponentSerializer.plainText().serialize(message).isBlank()) {
            plugin.messages().send(sender, "msg-empty");
            return;
        }

        if (liteBans == null || !(sender instanceof Player from)) {
            deliver(sender, target, message);
            return;
        }

        // LiteBans checks its database, so ask off the main thread and come back to deliver.
        UUID targetId = idOf(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean muted = liteBans.isMuted(from);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!from.isOnline()) {
                    return;
                }
                if (muted) {
                    plugin.messages().send(from, "msg-muted");
                    return;
                }
                CommandSender stillThere = resolve(from, targetId);
                if (stillThere == null) {
                    plugin.messages().send(from, "player-not-found", "player", target.getName());
                    return;
                }
                deliver(from, stillThere, message);
            });
        });
    }

    private void deliver(CommandSender sender, CommandSender target, Component message) {
        Settings cfg = new Settings(plugin.configs().config());
        Component senderName = nameOf(sender, cfg);
        Component receiverName = nameOf(target, cfg);

        send(sender, render(cfg.string("sender-format"), sender, senderName, receiverName, message));
        send(target, render(cfg.string("receiver-format"), sender, senderName, receiverName, message));

        // Both sides can /r each other from now on.
        setReplyTarget(sender, idOf(target));
        setReplyTarget(target, idOf(sender));

        if (target instanceof Player receiver) {
            playSound(receiver, cfg);
        }

        if (!isSpyExempt(sender) && !isSpyExempt(target)) {
            String spyFormat = cfg.string("spy-format");
            if (!spyFormat.isEmpty()) {
                Component spyLine = null;
                for (Player spy : Bukkit.getOnlinePlayers()) {
                    if (spy == sender || spy == target || !plugin.users().isSpying(spy)) {
                        continue;
                    }
                    if (spyLine == null) {
                        spyLine = render(spyFormat, sender, senderName, receiverName, message);
                    }
                    spy.sendMessage(spyLine);
                }
            }
        }

        if (cfg.bool("log-to-console")
                && !(sender instanceof ConsoleCommandSender) && !(target instanceof ConsoleCommandSender)) {
            String consoleFormat = cfg.string("console-format");
            if (!consoleFormat.isEmpty()) {
                plugin.getComponentLogger().info(render(consoleFormat, sender, senderName, receiverName, message));
            }
        }
    }

    private void setReplyTarget(CommandSender who, UUID target) {
        if (who instanceof Player player) {
            plugin.users().get(player).replyTarget(target);
        } else if (who instanceof ConsoleCommandSender) {
            consoleReplyTarget = target;
        }
    }

    private static boolean isSpyExempt(CommandSender sender) {
        return sender instanceof Player player && player.hasPermission(SPY_EXEMPT_PERMISSION);
    }

    private Component nameOf(CommandSender who, Settings cfg) {
        if (who instanceof Player player) {
            return plugin.renderer().nameWithHover(player, plugin.formats().select(player));
        }
        return Colors.trusted(cfg.string("console-name"));
    }

    /** PlaceholderAPI placeholders in PM formats are filled in for the sender. */
    private Component render(String template, CommandSender sender, Component senderName,
                             Component receiverName, Component message) {
        String t = template
                .replace("{sender}", "<dk_sender>")
                .replace("{receiver}", "<dk_receiver>")
                .replace("{message}", "<dk_message>");
        if (sender instanceof Player player) {
            t = plugin.placeholders().apply(player, t);
        }
        return Colors.trusted(t,
                Placeholder.component("dk_sender", senderName),
                Placeholder.component("dk_receiver", receiverName),
                Placeholder.component("dk_message", message));
    }

    private static void send(CommandSender to, Component line) {
        if (!PlainTextComponentSerializer.plainText().serialize(line).isEmpty()) {
            to.sendMessage(line);
        }
    }

    private void playSound(Player player, Settings cfg) {
        if (!cfg.bool("sound.enabled")) {
            return;
        }
        String name = cfg.string("sound.name");
        if (name.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(name.toLowerCase(Locale.ROOT)), Sound.Source.MASTER,
                    (float) cfg.number("sound.volume"), (float) cfg.number("sound.pitch")));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Invalid private message sound '" + name + "' in config.yml.");
        }
    }

    /**
     * Reads "private-messages.*" with the single-argument getters, so a key missing from the
     * server's config.yml falls back to the default shipped in the jar.
     */
    private record Settings(YamlConfiguration config) {
        String string(String key) {
            String value = config.getString("private-messages." + key);
            return value == null ? "" : value;
        }

        boolean bool(String key) {
            return config.getBoolean("private-messages." + key);
        }

        double number(String key) {
            return config.getDouble("private-messages." + key);
        }
    }
}
