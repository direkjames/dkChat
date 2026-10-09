package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.ad.AdManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** /ad, /advertise &lt;message&gt; */
public final class AdCommand implements BasicCommand {

    private final DkChat plugin;

    public AdCommand(DkChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            plugin.messages().send(source.getSender(), "players-only");
            return;
        }
        if (args.length == 0) {
            AdManager.Tier tier = plugin.ads().tierOf(player);
            plugin.messages().send(player, "ad-usage");
            if (tier != null) {
                long remaining = player.hasPermission(AdManager.BYPASS_PERMISSION)
                        ? 0 : plugin.ads().remainingCooldown(player, tier);
                plugin.messages().send(player, remaining > 0 ? "ad-status-cooldown" : "ad-status-ready",
                        "time", com.direkjames.dkchat.text.Lines.duration(remaining));
            }
            return;
        }
        plugin.ads().submit(player, String.join(" ", args));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return List.of();
    }
}
