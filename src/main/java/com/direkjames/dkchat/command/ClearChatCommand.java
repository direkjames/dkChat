package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * /clearchat - clears your own chat by pushing old messages off screen.
 * /clearchat all - staff: clears everyone's chat except other staff.
 */
public final class ClearChatCommand implements BasicCommand {

    public static final String PERMISSION = "dkchat.clearchat";
    public static final String ALL_PERMISSION = "dkchat.clearchat.all";
    public static final String EXEMPT_PERMISSION = "dkchat.clearchat.exempt";

    private final DkChat plugin;

    public ClearChatCommand(DkChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            if (!sender.hasPermission(ALL_PERMISSION)) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            Component blank = blankLines();
            String by = sender instanceof Player player ? player.getName() : "Console";
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(EXEMPT_PERMISSION)) {
                    player.sendMessage(blank);
                }
                plugin.messages().send(player, "clearchat-all", "player", by);
            }
            if (!(sender instanceof Player)) {
                plugin.messages().send(sender, "clearchat-all", "player", by);
            }
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        player.sendMessage(blankLines());
        plugin.messages().send(player, "clearchat-self");
    }

    /** One message made of many empty lines (one packet instead of a hundred). */
    private Component blankLines() {
        int lines = Math.max(1, Math.min(500, plugin.configs().config().getInt("clear-chat.lines")));
        return Component.text(" \n".repeat(lines - 1) + " ");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1 && source.getSender().hasPermission(ALL_PERMISSION)) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase();
            return "all".startsWith(typed) ? List.of("all") : List.of();
        }
        return List.of();
    }

    @Override
    public String permission() {
        return PERMISSION;
    }
}
