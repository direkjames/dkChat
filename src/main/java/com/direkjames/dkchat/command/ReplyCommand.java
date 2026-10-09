package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.pm.PrivateMessageService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** /r, /reply &lt;message&gt; - answers the last person you messaged or who messaged you. */
public final class ReplyCommand implements BasicCommand {

    private final DkChat plugin;

    public ReplyCommand(DkChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            plugin.messages().send(sender, "reply-usage");
            return;
        }

        PrivateMessageService pm = plugin.privateMessages();
        if (!pm.hasReplyTarget(sender)) {
            plugin.messages().send(sender, "reply-none");
            return;
        }
        CommandSender target = pm.replyTarget(sender);
        if (target == null) {
            plugin.messages().send(sender, "reply-offline");
            return;
        }

        pm.send(sender, target, String.join(" ", args));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return List.of();
    }

    @Override
    public String permission() {
        return PrivateMessageService.MSG_PERMISSION;
    }
}
