package com.direkjames.dkchat;

import com.direkjames.dkchat.command.DChatCommand;
import com.direkjames.dkchat.config.ConfigManager;
import com.direkjames.dkchat.config.Messages;
import com.direkjames.dkchat.format.FormatManager;
import com.direkjames.dkchat.format.FormatRenderer;
import com.direkjames.dkchat.hook.PlaceholderHook;
import com.direkjames.dkchat.listener.ChatListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

/**
 * dkChat - global chat for our PurpurMC server.
 *
 * @author direk james
 */
public final class DkChat extends JavaPlugin {

    private ConfigManager configs;
    private Messages messages;
    private FormatManager formats;
    private FormatRenderer renderer;
    private PlaceholderHook placeholders;

    @Override
    public void onEnable() {
        configs = new ConfigManager(this);
        try {
            configs.loadAll();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not load dkChat's config files. Fix the YAML error and restart.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new Messages(configs);
        placeholders = new PlaceholderHook();
        formats = new FormatManager();
        int loaded = formats.load(configs.formats(), getLogger());
        renderer = new FormatRenderer(formats, placeholders,
                () -> configs.config().getBoolean("name-hover.enabled", true));

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        registerCommand("dchat", "dkChat main command", List.of("dkchat"), new DChatCommand(this));

        logHooks();
        getLogger().info("dkChat enabled with " + loaded + " chat format(s). Made by direk james.");
    }

    /** Reloads every config file. Returns the number of formats loaded, or -1 if a file is broken. */
    public int reload() {
        try {
            configs.loadAll();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Reload failed, keeping the previous settings.", e);
            return -1;
        }
        return formats.load(configs.formats(), getLogger());
    }

    private void logHooks() {
        var pm = getServer().getPluginManager();
        if (placeholders.isEnabled()) {
            getLogger().info("Hooked into PlaceholderAPI.");
        } else {
            getLogger().warning("PlaceholderAPI not found. %placeholders% in formats will show as plain text.");
        }
        if (pm.isPluginEnabled("Essentials")) {
            getLogger().info("EssentialsX found. Nicknames come from display names "
                    + "(keep 'change-displayname: true' in the Essentials config).");
        }
        if (pm.isPluginEnabled("DiscordSRV")) {
            getLogger().info("DiscordSRV found. Public chat is passed through Paper's chat event for relaying.");
        }
    }

    public ConfigManager configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public FormatManager formats() {
        return formats;
    }

    public FormatRenderer renderer() {
        return renderer;
    }

    public PlaceholderHook placeholders() {
        return placeholders;
    }
}
