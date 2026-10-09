package com.direkjames.dkchat.announce;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.text.Colors;
import com.direkjames.dkchat.text.Lines;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Timed chat announcements from announcements.yml. */
public final class AnnouncementManager {

    private record Announcement(String id, String permission, String hidePermission, List<String> lines) {
    }

    private final DkChat plugin;
    private List<Announcement> announcements = List.of();
    private BukkitTask task;
    private int nextIndex;
    /** Random mode: a shuffled "bag" so nothing repeats until every announcement has been shown. */
    private final List<Announcement> bag = new ArrayList<>();

    public AnnouncementManager(DkChat plugin) {
        this.plugin = plugin;
    }

    /** (Re)loads announcements.yml and restarts the timer. Returns how many were loaded. */
    public int reload() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        YamlConfiguration yaml = plugin.configs().announcements();
        List<Announcement> list = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("announcements");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection a = section.getConfigurationSection(id);
                if (a == null || !a.getBoolean("enabled", true)) {
                    continue;
                }
                List<String> lines = a.getStringList("lines");
                if (lines.isEmpty()) {
                    plugin.getLogger().warning("Announcement '" + id + "' has no lines and was skipped.");
                    continue;
                }
                list.add(new Announcement(id, a.getString("permission", "").trim(),
                        a.getString("hide-permission", "").trim(), List.copyOf(lines)));
            }
        }
        announcements = List.copyOf(list);
        nextIndex = 0;
        bag.clear();

        long interval = Math.max(10, yaml.getLong("interval", 300)) * 20L;
        if (yaml.getBoolean("enabled", true) && !announcements.isEmpty()) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        }
        return announcements.size();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public List<String> ids() {
        return announcements.stream().map(Announcement::id).toList();
    }

    /** Sends one announcement right now (for /dchat announce). Returns false if the id is unknown. */
    public boolean sendNow(String id) {
        for (Announcement announcement : announcements) {
            if (announcement.id().equalsIgnoreCase(id)) {
                broadcast(announcement);
                return true;
            }
        }
        return false;
    }

    private void tick() {
        YamlConfiguration yaml = plugin.configs().announcements();
        if (Bukkit.getOnlinePlayers().size() < yaml.getInt("min-players", 1)) {
            return;
        }
        Announcement next = next(yaml.getString("mode", "random").toLowerCase(Locale.ROOT));
        if (next != null) {
            broadcast(next);
        }
    }

    private Announcement next(String mode) {
        if (announcements.isEmpty()) {
            return null;
        }
        if (mode.equals("sequential")) {
            Announcement a = announcements.get(nextIndex % announcements.size());
            nextIndex = (nextIndex + 1) % announcements.size();
            return a;
        }
        if (bag.isEmpty()) {
            bag.addAll(announcements);
            Collections.shuffle(bag);
        }
        return bag.removeFirst();
    }

    private void broadcast(Announcement announcement) {
        YamlConfiguration yaml = plugin.configs().announcements();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!announcement.permission().isEmpty() && !player.hasPermission(announcement.permission())) {
                continue;
            }
            if (!announcement.hidePermission().isEmpty() && player.hasPermission(announcement.hidePermission())) {
                continue;
            }
            for (String raw : announcement.lines()) {
                // Placeholders are filled in per player, so %player_name% etc. are personal.
                String line = raw.replace("{name}", "<dk_name>").replace("{player}", "<dk_player>");
                player.sendMessage(Lines.render(line, player, plugin.placeholders(),
                        Placeholder.component("dk_name", Colors.displayName(player)),
                        Placeholder.unparsed("dk_player", player.getName())));
            }
            Lines.playSound(player, yaml, "sound", plugin.getLogger());
        }
    }
}
