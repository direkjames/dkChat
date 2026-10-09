package com.direkjames.dkchat;

import com.direkjames.dkchat.ad.AdManager;
import com.direkjames.dkchat.announce.AnnouncementManager;
import com.direkjames.dkchat.command.AdCommand;
import com.direkjames.dkchat.command.ClearChatCommand;
import com.direkjames.dkchat.command.DChatCommand;
import com.direkjames.dkchat.hook.VaultHook;
import com.direkjames.dkchat.command.IgnoreCommand;
import com.direkjames.dkchat.command.PmCommandRegistrar;
import com.direkjames.dkchat.command.SpyCommand;
import com.direkjames.dkchat.hook.LiteBansHook;
import com.direkjames.dkchat.pm.PrivateMessageService;
import com.direkjames.dkchat.showcase.ShowcaseListener;
import com.direkjames.dkchat.showcase.ShowcaseManager;
import com.direkjames.dkchat.user.UserManager;
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
    private UserManager users;
    private LiteBansHook liteBans;
    private PrivateMessageService privateMessages;
    private PmCommandRegistrar pmCommands;
    private ShowcaseManager showcase;
    private VaultHook vault;
    private AdManager ads;
    private AnnouncementManager announcements;

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

        users = new UserManager(this);
        users.loadOnline();
        liteBans = LiteBansHook.create(getLogger());
        privateMessages = new PrivateMessageService(this, liteBans);

        showcase = new ShowcaseManager(this);
        showcase.reload();
        showcase.startCleanup();

        vault = new VaultHook(getLogger());
        ads = new AdManager(this, liteBans, vault);
        announcements = new AnnouncementManager(this);
        int announcementCount = announcements.reload();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ShowcaseListener(this, showcase), this);
        getServer().getPluginManager().registerEvents(users, this);

        registerCommand("dchat", "dkChat main command", List.of("dkchat"), new DChatCommand(this));
        // /msg and /r are Bukkit commands taken over after startup, see PmCommandRegistrar.
        pmCommands = new PmCommandRegistrar(this);
        getServer().getPluginManager().registerEvents(pmCommands, this);
        pmCommands.takeOver();
        registerCommand("spy", "Toggle private message spy", List.of("socialspy"), new SpyCommand(this));
        registerCommand("ignore", "Ignore a player's chat and private messages", new IgnoreCommand(this));
        registerCommand("ad", "Advertise something to the whole server", List.of("advertise"), new AdCommand(this));
        registerCommand("clearchat", "Clear your chat", List.of("cc", "chatclear"), new ClearChatCommand(this));

        logHooks();
        getLogger().info("dkChat enabled with " + loaded + " chat format(s) and "
                + announcementCount + " announcement(s). Made by direk james.");
    }

    @Override
    public void onDisable() {
        // Never leave a preview GUI open without the listener that protects it.
        if (showcase != null) {
            showcase.closeAll();
        }
        if (announcements != null) {
            announcements.stop();
        }
        if (pmCommands != null) {
            pmCommands.release();
        }
    }

    /** Reloads every config file. Returns the number of formats loaded, or -1 if a file is broken. */
    public int reload() {
        try {
            configs.loadAll();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Reload failed, keeping the previous settings.", e);
            return -1;
        }
        int loaded = formats.load(configs.formats(), getLogger());
        showcase.reload();
        announcements.reload();
        // Also reclaims /msg, /tell, /w... if another plugin took them after startup.
        pmCommands.takeOver();
        return loaded;
    }

    private void logHooks() {
        var pm = getServer().getPluginManager();
        if (placeholders.isEnabled()) {
            getLogger().info("Hooked into PlaceholderAPI.");
        } else {
            getLogger().warning("PlaceholderAPI not found. %placeholders% in formats will show as plain text.");
        }
        if (liteBans != null) {
            getLogger().info("Hooked into LiteBans. Muted players can't send private messages.");
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

    public UserManager users() {
        return users;
    }

    public AdManager ads() {
        return ads;
    }

    public AnnouncementManager announcements() {
        return announcements;
    }

    public ShowcaseManager showcase() {
        return showcase;
    }

    public PrivateMessageService privateMessages() {
        return privateMessages;
    }
}
