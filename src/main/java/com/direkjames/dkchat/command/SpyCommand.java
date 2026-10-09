package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.user.UserManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** /spy, /socialspy - turns private message spying on or off for yourself. */
public final class SpyCommand implements BasicCommand {

    private final DkChat plugin;

    public SpyCommand(DkChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            plugin.messages().send(source.getSender(), "players-only");
            return;
        }
        boolean nowOn = plugin.users().toggleSpy(player);
        plugin.messages().send(player, nowOn ? "spy-enabled" : "spy-disabled");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return List.of();
    }

    @Override
    public String permission() {
        return UserManager.SPY_PERMISSION;
    }
}
