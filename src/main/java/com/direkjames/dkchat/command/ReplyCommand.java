package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.pm.PrivateMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /r, /reply &lt;message&gt; - answers the last person you messaged or who messaged you.
 * A Bukkit command for the same reason as {@link MsgCommand}.
 */
public final class ReplyCommand extends Command {

    private final DkChat plugin;

    public ReplyCommand(DkChat plugin) {
        super("r", "Reply to your last private message", "/r <message>", List.of("reply"));
        this.plugin = plugin;
        setPermission(PrivateMessageService.MSG_PERMISSION);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PrivateMessageService.MSG_PERMISSION)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(sender, "reply-usage");
            return true;
        }

        PrivateMessageService pm = plugin.privateMessages();
        if (!pm.hasReplyTarget(sender)) {
            plugin.messages().send(sender, "reply-none");
            return true;
        }
        CommandSender target = pm.replyTarget(sender);
        if (target == null) {
            plugin.messages().send(sender, "reply-offline");
            return true;
        }

        pm.send(sender, target, String.join(" ", args));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return List.of();
    }
}
