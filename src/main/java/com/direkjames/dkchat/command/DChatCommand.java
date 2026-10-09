package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.format.ChatFormat;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

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
            default -> plugin.messages().send(sender, "unknown-subcommand");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(List.of("help", "format"));
        if (source.getSender().hasPermission(ADMIN)) {
            options.add("reload");
        }
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }
}
