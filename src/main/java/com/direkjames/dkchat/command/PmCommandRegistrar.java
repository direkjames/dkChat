package com.direkjames.dkchat.command;

import com.direkjames.dkchat.DkChat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Makes sure /msg, /tell, /w (and the rest) belong to dkChat.
 *
 * <p>When msg/tell/w are disabled in EssentialsX, Essentials puts the vanilla versions back as
 * Bukkit commands, and Paper won't let a plugin replace another Bukkit command. So once the
 * server has finished loading (after Essentials), we remove whatever owns our labels and register
 * ours. As a safety net, if something takes a label back later (e.g. /essentials reload), the
 * typed command is redirected to dkChat's namespaced version (/dkchat:msg).</p>
 */
public final class PmCommandRegistrar implements Listener {

    private static final String NAMESPACE = "dkchat";

    private final DkChat plugin;
    private final List<Command> commands;
    private final Map<String, Command> byLabel = new HashMap<>();
    private final Map<Command, List<String>> labelsOf = new HashMap<>();

    public PmCommandRegistrar(DkChat plugin) {
        this.plugin = plugin;
        this.commands = List.of(new MsgCommand(plugin), new ReplyCommand(plugin));
        for (Command command : commands) {
            // Remember every label now: Bukkit drops aliases from a command if they fail to register.
            List<String> labels = labels(command);
            labelsOf.put(command, labels);
            for (String label : labels) {
                byLabel.put(label, command);
            }
        }
    }

    /** Removes anything using our labels and registers dkChat's commands. Safe to call again. */
    public void takeOver() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        List<String> replaced = new ArrayList<>();

        for (Command command : commands) {
            command.unregister(map); // also restores any aliases dropped on an earlier attempt
            for (String label : labelsOf.get(command)) {
                Command existing = known.get(label);
                if (existing != null && existing != command) {
                    replaced.add(label);
                }
                known.remove(label);
                known.remove(NAMESPACE + ":" + label);
            }
            map.register(NAMESPACE, command);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
        if (!replaced.isEmpty()) {
            plugin.getLogger().info("Took over /" + String.join(", /", replaced)
                    + " for dkChat private messages.");
        }
    }

    /** Removes dkChat's /msg and /r so nothing points at a disabled plugin (e.g. plugin reloaders). */
    public void release() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        for (Command command : commands) {
            for (String label : labelsOf.get(command)) {
                known.remove(label, command);
                known.remove(NAMESPACE + ":" + label, command);
            }
            command.unregister(map);
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        // One tick later, so plugins that adjust commands at load (EssentialsX) have finished.
        Bukkit.getScheduler().runTask(plugin, this::takeOver);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String rewritten = redirect(event.getMessage().substring(1));
        if (rewritten != null) {
            event.setMessage("/" + rewritten);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        String command = event.getCommand().startsWith("/") ? event.getCommand().substring(1) : event.getCommand();
        String rewritten = redirect(command);
        if (rewritten != null) {
            event.setCommand(rewritten);
        }
    }

    /** Returns the command pointed at dkChat if another plugin currently owns its label, else null. */
    private String redirect(String commandLine) {
        int space = commandLine.indexOf(' ');
        String label = (space < 0 ? commandLine : commandLine.substring(0, space)).toLowerCase(Locale.ROOT);
        Command ours = byLabel.get(label);
        if (ours == null || Bukkit.getCommandMap().getCommand(label) == ours) {
            return null;
        }
        return NAMESPACE + ":" + ours.getName() + (space < 0 ? "" : commandLine.substring(space));
    }

    private static List<String> labels(Command command) {
        List<String> labels = new ArrayList<>();
        labels.add(command.getName().toLowerCase(Locale.ROOT));
        for (String alias : command.getAliases()) {
            labels.add(alias.toLowerCase(Locale.ROOT));
        }
        return labels;
    }
}
