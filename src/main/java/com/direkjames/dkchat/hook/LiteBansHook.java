package com.direkjames.dkchat.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional LiteBans support, used to block private messages from muted players.
 *
 * <p>Uses reflection so dkChat doesn't need the LiteBans API jar to build. LiteBans looks up
 * mutes in its database, so {@link #isMuted(Player)} must only be called off the main thread.</p>
 */
public final class LiteBansHook {

    private final Logger logger;
    private final Method getDatabase;
    private final Method isPlayerMuted;

    private LiteBansHook(Logger logger, Method getDatabase, Method isPlayerMuted) {
        this.logger = logger;
        this.getDatabase = getDatabase;
        this.isPlayerMuted = isPlayerMuted;
    }

    /** Returns a working hook, or {@code null} if LiteBans isn't installed or its API changed. */
    public static LiteBansHook create(Logger logger) {
        Plugin liteBans = Bukkit.getPluginManager().getPlugin("LiteBans");
        if (liteBans == null || !liteBans.isEnabled()) {
            return null;
        }
        try {
            Class<?> database = Class.forName("litebans.api.Database", true, liteBans.getClass().getClassLoader());
            Method get = database.getMethod("get");
            Method muted = database.getMethod("isPlayerMuted", UUID.class, String.class);
            return new LiteBansHook(logger, get, muted);
        } catch (ReflectiveOperationException e) {
            logger.warning("LiteBans was found but its API couldn't be loaded. Muted players can still use /msg. ("
                    + e.getMessage() + ")");
            return null;
        }
    }

    /** Asks LiteBans if the player is muted. Call this off the main thread. */
    public boolean isMuted(Player player) {
        try {
            Object database = getDatabase.invoke(null);
            InetSocketAddress address = player.getAddress();
            String ip = address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
            return (boolean) isPlayerMuted.invoke(database, player.getUniqueId(), ip);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.WARNING, "LiteBans mute check failed for " + player.getName()
                    + ". Blocking the message to be safe.", e);
            return true;
        }
    }
}
