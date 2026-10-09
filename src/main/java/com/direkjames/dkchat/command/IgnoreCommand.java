package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.pm.PrivateMessageService;
import com.direkjames.dkchat.user.ChatUser;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /ignore &lt;player&gt; toggles ignoring a player. /ignore list shows who you ignore. */
public final class IgnoreCommand implements BasicCommand {

    public static final String PERMISSION = "dkchat.ignore";

    private final DkChat plugin;

    public IgnoreCommand(DkChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            plugin.messages().send(source.getSender(), "players-only");
            return;
        }
        if (args.length != 1) {
            plugin.messages().send(player, "ignore-usage");
            return;
        }
        if (args[0].equalsIgnoreCase("list")) {
            showList(player);
            return;
        }

        // Online players first (respecting vanish), then players who have joined before.
        OfflinePlayer target = plugin.privateMessages().findVisible(player, args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null) {
            plugin.messages().send(player, "player-not-found", "player", args[0]);
            return;
        }
        String name = target.getName() == null ? args[0] : target.getName();

        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "ignore-self");
            return;
        }

        ChatUser user = plugin.users().get(player);
        boolean alreadyIgnored = user.ignored().contains(target.getUniqueId());
        if (!alreadyIgnored) {
            Player online = target.getPlayer();
            if (online != null && online.hasPermission(PrivateMessageService.IGNORE_EXEMPT_PERMISSION)) {
                plugin.messages().send(player, "ignore-exempt", "player", name);
                return;
            }
            int max = plugin.configs().config().getInt("ignore.max-ignored", 50);
            if (user.ignored().size() >= max) {
                plugin.messages().send(player, "ignore-full", "max", String.valueOf(max));
                return;
            }
        }

        boolean nowIgnored = plugin.users().toggleIgnore(player, target.getUniqueId());
        plugin.messages().send(player, nowIgnored ? "ignore-added" : "ignore-removed", "player", name);
    }

    private void showList(Player player) {
        ChatUser user = plugin.users().get(player);
        if (user.ignored().isEmpty()) {
            plugin.messages().send(player, "ignore-list-empty");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID id : user.ignored()) {
            String name = Bukkit.getOfflinePlayer(id).getName();
            names.add(name == null ? id.toString() : name);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        plugin.messages().send(player, "ignore-list",
                "count", String.valueOf(names.size()),
                "players", String.join(", ", names));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        String typed = args.length == 0 ? "" : args[0];
        List<String> options = new ArrayList<>(MsgCommand.visibleNames(plugin, source.getSender(), typed));
        if ("list".startsWith(typed.toLowerCase(Locale.ROOT))) {
            options.addFirst("list");
        }
        return options;
    }

    @Override
    public String permission() {
        return PERMISSION;
    }
}
