package com.direkjames.dkchat.config;

import com.direkjames.dkchat.DkChat;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads config.yml, formats.yml and messages.yml. A reload only replaces the active
 * settings when every file parses, so a typo never leaves the server with a broken chat.
 */
public final class ConfigManager {

    private final DkChat plugin;

    private volatile YamlConfiguration config = new YamlConfiguration();
    private volatile YamlConfiguration formats = new YamlConfiguration();
    private volatile YamlConfiguration messages = new YamlConfiguration();

    public ConfigManager(DkChat plugin) {
        this.plugin = plugin;
    }

    public void loadAll() throws IOException, InvalidConfigurationException {
        YamlConfiguration newConfig = load("config.yml", true);
        // No defaults for formats: a format removed by the owner should stay removed.
        YamlConfiguration newFormats = load("formats.yml", false);
        YamlConfiguration newMessages = load("messages.yml", true);

        this.config = newConfig;
        this.formats = newFormats;
        this.messages = newMessages;
    }

    private YamlConfiguration load(String name, boolean withDefaults) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new InvalidConfigurationException(name + ": " + e.getMessage(), e);
        }

        if (withDefaults) {
            // Missing keys fall back to the values shipped in the jar.
            try (InputStream in = plugin.getResource(name)) {
                if (in != null) {
                    yaml.setDefaults(YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8)));
                }
            }
        }
        return yaml;
    }

    public YamlConfiguration config() {
        return config;
    }

    public YamlConfiguration formats() {
        return formats;
    }

    public YamlConfiguration messages() {
        return messages;
    }
}
