package com.direkjames.dkchat.format;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/** Holds the chat formats and picks the right one for each player. */
public final class FormatManager {

    /** Used only if formats.yml has no format the player can use. */
    private static final ChatFormat FALLBACK = new ChatFormat(
            "fallback", Integer.MIN_VALUE, "",
            "<gray>{name}</gray> <dark_gray>»</dark_gray> <white>{message}", List.of());

    private volatile List<ChatFormat> formats = List.of();
    private volatile List<String> defaultHover = List.of();

    /** Loads formats from formats.yml and returns how many were loaded. */
    public int load(YamlConfiguration yaml, Logger logger) {
        List<String> newDefaultHover = List.copyOf(yaml.getStringList("default-hover"));
        List<ChatFormat> list = new ArrayList<>();

        ConfigurationSection section = yaml.getConfigurationSection("formats");
        if (section == null) {
            logger.warning("formats.yml has no 'formats' section. Everyone will use the fallback format.");
        } else {
            for (String id : section.getKeys(false)) {
                ConfigurationSection f = section.getConfigurationSection(id);
                if (f == null) {
                    continue;
                }
                String format = f.getString("format");
                if (format == null || format.isBlank()) {
                    logger.warning("Chat format '" + id + "' has no 'format' line and was skipped.");
                    continue;
                }
                List<String> hover = f.isList("hover") ? List.copyOf(f.getStringList("hover")) : null;
                list.add(new ChatFormat(
                        id,
                        f.getInt("priority", 0),
                        f.getString("permission", "").trim(),
                        format,
                        hover));
            }
        }

        list.sort(Comparator.comparingInt(ChatFormat::priority).reversed());
        this.defaultHover = newDefaultHover;
        this.formats = List.copyOf(list);
        return list.size();
    }

    /** The highest priority format the player has permission for. */
    public ChatFormat select(Player player) {
        for (ChatFormat format : formats) {
            if (format.permission().isEmpty() || player.hasPermission(format.permission())) {
                return format;
            }
        }
        return FALLBACK;
    }

    public List<String> hoverFor(ChatFormat format) {
        return format.hover() != null ? format.hover() : defaultHover;
    }
}
