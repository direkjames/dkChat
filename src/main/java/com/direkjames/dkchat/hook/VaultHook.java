package com.direkjames.dkchat.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional Vault economy support (works with any Vault economy, including dkBank).
 *
 * <p>Uses reflection so dkChat doesn't need the Vault API jar to build. The economy is looked up
 * when first needed, so it doesn't matter whether the economy plugin loads before or after
 * dkChat. Call these methods on the main thread.</p>
 */
public final class VaultHook {

    private static final String ECONOMY = "net.milkbowl.vault.economy.Economy";
    private static final String RESPONSE = "net.milkbowl.vault.economy.EconomyResponse";

    private final Logger logger;
    private Object economy;
    private Method has;
    private Method withdraw;
    private Method format;
    private Method success;

    public VaultHook(Logger logger) {
        this.logger = logger;
    }

    /** True if a Vault economy is available right now. */
    public boolean isAvailable() {
        return resolve();
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!resolve()) {
            return false;
        }
        try {
            return (boolean) has.invoke(economy, player, amount);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.WARNING, "Vault balance check failed for " + player.getName(), e);
            return false;
        }
    }

    /** Takes money from the player. Returns true only if the economy confirms it. */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!resolve()) {
            return false;
        }
        try {
            Object response = withdraw.invoke(economy, player, amount);
            return (boolean) success.invoke(response);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.WARNING, "Vault withdraw failed for " + player.getName(), e);
            return false;
        }
    }

    /** Formats money the way the economy plugin does (e.g. "$1,000.00"). */
    public String format(double amount) {
        if (resolve()) {
            try {
                return (String) format.invoke(economy, amount);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall through to the plain number.
            }
        }
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    private synchronized boolean resolve() {
        if (economy != null) {
            return true;
        }
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return false;
        }
        try {
            ClassLoader loader = vault.getClass().getClassLoader();
            Class<?> economyClass = Class.forName(ECONOMY, true, loader);
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(economyClass);
            if (provider == null) {
                return false;
            }
            Class<?> responseClass = Class.forName(RESPONSE, true, loader);
            has = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            format = economyClass.getMethod("format", double.class);
            success = responseClass.getMethod("transactionSuccess");
            economy = provider.getProvider();
            logger.info("Hooked into Vault economy: " + provider.getPlugin().getName());
            return true;
        } catch (ReflectiveOperationException e) {
            logger.warning("Vault was found but its economy API couldn't be loaded: " + e.getMessage());
            return false;
        }
    }
}
