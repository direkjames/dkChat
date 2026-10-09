package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.pm.PrivateMessageService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /msg, /m, /tell, /whisper, /w &lt;player&gt; &lt;message&gt; */
public final class MsgCommand implements BasicCommand {

    private final DkChat plugin;

    public MsgCommand(DkChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length < 2) {
            plugin.messages().send(sender, "msg-usage");
            return;
        }

        PrivateMessageService pm = plugin.privateMessages();
        Player target = pm.findVisible(sender, args[0]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[0]);
            return;
        }
        if (target == sender) {
            plugin.messages().send(sender, "msg-self");
            return;
        }

        pm.send(sender, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        return visibleNames(plugin, source.getSender(), args.length == 0 ? "" : args[0]);
    }

    @Override
    public String permission() {
        return PrivateMessageService.MSG_PERMISSION;
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
