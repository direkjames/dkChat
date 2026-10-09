package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.pm.PrivateMessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /msg, /m, /tell, /whisper, /w &lt;player&gt; &lt;message&gt;
 *
 * <p>This is a Bukkit command (not a Brigadier one) on purpose: vanilla and EssentialsX also
 * use these labels, and {@link PmCommandRegistrar} has to be able to take them over at runtime.</p>
 */
public final class MsgCommand extends Command {

    private final DkChat plugin;

    public MsgCommand(DkChat plugin) {
        super("msg", "Send a private message", "/msg <player> <message>",
                List.of("m", "tell", "whisper", "w"));
        this.plugin = plugin;
        setPermission(PrivateMessageService.MSG_PERMISSION);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PrivateMessageService.MSG_PERMISSION)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "msg-usage");
            return true;
        }

        PrivateMessageService pm = plugin.privateMessages();
        Player target = pm.findVisible(sender, args[0]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        if (target == sender) {
            plugin.messages().send(sender, "msg-self");
            return true;
        }

        pm.send(sender, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PrivateMessageService.MSG_PERMISSION)) {
            return List.of();
        }
        return visibleNames(plugin, sender, args[0]);
    }

    /** Names of online players the sender can see, starting with {@code typed}. */
    static List<String> visibleNames(DkChat plugin, CommandSender sender, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> p != sender && plugin.privateMessages().canSee(sender, p))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
