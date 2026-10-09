package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.format.ChatFormat;
import com.direkjames.dkchat.showcase.ShowcaseManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /dchat [help | format | reload] */
public final class DChatCommand implements BasicCommand {

    private static final String ADMIN = "dkchat.admin";

    private final DkChat plugin;

    public DChatCommand(DkChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> {
                plugin.messages().sendList(sender, "help", "version", plugin.getPluginMeta().getVersion());
                if (sender.hasPermission(ADMIN)) {
                    plugin.messages().sendList(sender, "help-admin");
                }
            }
            case "format" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "players-only");
                    return;
                }
                ChatFormat format = plugin.formats().select(player);
                plugin.messages().send(sender, "your-format",
                        "format", format.id(),
                        "priority", String.valueOf(format.priority()));
            }
            case "reload" -> {
                if (!sender.hasPermission(ADMIN)) {
                    plugin.messages().send(sender, "no-permission");
                    return;
                }
                long start = System.nanoTime();
                int loaded = plugin.reload();
                if (loaded < 0) {
                    plugin.messages().send(sender, "reload-failed");
                    return;
                }
                long ms = (System.nanoTime() - start) / 1_000_000L;
                plugin.messages().send(sender, "reload-success",
                        "time", String.valueOf(ms),
                        "formats", String.valueOf(loaded));
            }
            case "announce" -> {
                if (!sender.hasPermission(ADMIN)) {
                    plugin.messages().send(sender, "no-permission");
                    return;
                }
                if (args.length < 2) {
                    plugin.messages().send(sender, "announce-usage",
                            "list", String.join(", ", plugin.announcements().ids()));
                    return;
                }
                if (plugin.announcements().sendNow(args[1])) {
                    plugin.messages().send(sender, "announce-sent", "id", args[1]);
                } else {
                    plugin.messages().send(sender, "announce-unknown", "id", args[1],
                            "list", String.join(", ", plugin.announcements().ids()));
                }
            }
            case "view" -> {
                // Used by the click on [inv] / [echest] in chat. Not shown in tab completion.
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "players-only");
                    return;
                }
                if (!player.hasPermission(ShowcaseManager.VIEW_PERMISSION)) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                UUID id;
                try {
                    id = args.length < 2 ? null : UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    id = null;
                }
                if (id == null || !plugin.showcase().open(player, id)) {
                    plugin.messages().send(player, "showcase-expired");
                }
            }
            default -> plugin.messages().send(sender, "unknown-subcommand");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("announce") && source.getSender().hasPermission(ADMIN)) {
            String typedId = args[1].toLowerCase(Locale.ROOT);
            return plugin.announcements().ids().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(typedId))
                    .toList();
        }
        if (args.length > 1) {
            return List.of();
        }
        String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(List.of("help", "format"));
        if (source.getSender().hasPermission(ADMIN)) {
            options.add("reload");
            options.add("announce");
        }
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }
}
