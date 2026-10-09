package com.direkjames.dkchat.config;

import com.direkjames.dkchat.text.Colors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Sends messages from messages.yml. Replacements are passed as key/value pairs: "time", "12". */
public final class Messages {

    private final ConfigManager configs;

    public Messages(ConfigManager configs) {
        this.configs = configs;
    }

    public void send(CommandSender to, String key, String... replacements) {
        String raw = configs.messages().getString(key);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        to.sendMessage(Colors.trusted(fill(raw, replacements)));
    }

    public void sendList(CommandSender to, String key, String... replacements) {
        List<String> lines = configs.messages().getStringList(key);
        for (String line : lines) {
            to.sendMessage(Colors.trusted(fill(line, replacements)));
        }
    }

    private String fill(String raw, String... replacements) {
        String prefix = configs.messages().getString("prefix");
        String out = raw.replace("{prefix}", prefix == null ? "" : prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            // Values can be typed by players (e.g. a name in /msg), so they are never parsed as tags.
            out = out.replace("{" + replacements[i] + "}", MiniMessage.miniMessage().escapeTags(replacements[i + 1]));
        }
        return out;
    }
}
