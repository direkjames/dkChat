package com.direkjames.dkchat.ad;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.hook.LiteBansHook;
import com.direkjames.dkchat.hook.VaultHook;
import com.direkjames.dkchat.pm.PrivateMessageService;
import com.direkjames.dkchat.text.Colors;
import com.direkjames.dkchat.text.Lines;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ad - rank-based advertisements.
 *
 * <p>Order of checks: tier permission, length, cooldown, balance, LiteBans mute (async), then
 * money is taken and the ad is sent. Money is only taken when the ad is actually sent.
 * The last ad time is saved on the player, so cooldowns survive relogs and restarts.</p>
 */
public final class AdManager {

    public static final String BYPASS_PERMISSION = "dkchat.ad.bypass";

    public record Tier(String id, int priority, String permission, long cooldownSeconds, double cost) {
    }

    private final DkChat plugin;
    private final LiteBansHook liteBans;
    private final VaultHook vault;
    private final NamespacedKey lastAdKey;
    /** Players whose ad is waiting on the mute check, so a double /ad can't sneak through twice. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public AdManager(DkChat plugin, LiteBansHook liteBans, VaultHook vault) {
        this.plugin = plugin;
        this.liteBans = liteBans;
        this.vault = vault;
        this.lastAdKey = new NamespacedKey(plugin, "last_ad");
    }

    /** The highest priority tier the player has permission for, or null. */
    public Tier tierOf(Player player) {
        // Older config.yml files don't have tiers yet, so fall back to the defaults in the jar.
        String path = "advertisement.tiers";
        ConfigurationSection section = config().contains(path, true)
                ? config().getConfigurationSection(path)
                : config().getDefaults() == null ? null : config().getDefaults().getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        List<Tier> tiers = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) {
                continue;
            }
            tiers.add(new Tier(id, t.getInt("priority", 0), t.getString("permission", "dkchat.ad"),
                    Math.max(0, t.getLong("cooldown", 3600)), Math.max(0, t.getDouble("cost", 0))));
        }
        tiers.sort(Comparator.comparingInt(Tier::priority).reversed());
        for (Tier tier : tiers) {
            if (tier.permission().isEmpty() || player.hasPermission(tier.permission())) {
                return tier;
            }
        }
        return null;
    }

    /** Runs all checks and, if they pass, charges the player and sends the ad. Main thread only. */
    public void submit(Player player, String text) {
        if (!config().getBoolean("advertisement.enabled")) {
            plugin.messages().send(player, "ad-disabled");
            return;
        }
        Tier tier = tierOf(player);
        if (tier == null) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (text.isBlank()) {
            plugin.messages().send(player, "ad-usage");
            return;
        }

        Component message = Colors.player(text, Colors.Allow.of(player));
        int length = PlainTextComponentSerializer.plainText().serialize(message).trim().length();
        int min = config().getInt("advertisement.min-length");
        int max = config().getInt("advertisement.max-length");
        if (length < min) {
            plugin.messages().send(player, "ad-too-short", "min", String.valueOf(min));
            return;
        }
        if (max > 0 && length > max) {
            plugin.messages().send(player, "ad-too-long", "max", String.valueOf(max));
            return;
        }

        boolean bypass = player.hasPermission(BYPASS_PERMISSION);
        if (!bypass) {
            long remaining = remainingCooldown(player, tier);
            if (remaining > 0) {
                plugin.messages().send(player, "ad-cooldown", "time", Lines.duration(remaining));
                return;
            }
        }
        double cost = bypass ? 0 : tier.cost();
        if (cost > 0) {
            if (!vault.isAvailable()) {
                plugin.getLogger().warning("An ad costs money but no Vault economy is available. Ad blocked.");
                plugin.messages().send(player, "ad-unavailable");
                return;
            }
            if (!vault.has(player, cost)) {
                plugin.messages().send(player, "ad-no-money", "cost", vault.format(cost));
                return;
            }
        }

        if (!pending.add(player.getUniqueId())) {
            return; // An ad from this player is already being processed.
        }
        if (liteBans == null) {
            finish(player, tier, message, cost, bypass);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean muted = liteBans.isMuted(player);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (muted) {
                    pending.remove(player.getUniqueId());
                    plugin.messages().send(player, "ad-muted");
                    return;
                }
                finish(player, tier, message, cost, bypass);
            });
        });
    }

    private void finish(Player player, Tier tier, Component message, double cost, boolean bypass) {
        try {
            if (!player.isOnline()) {
                return;
            }
            // Re-check the cooldown in case anything changed while we waited.
            if (!bypass && remainingCooldown(player, tier) > 0) {
                return;
            }
            if (cost > 0 && !vault.withdraw(player, cost)) {
                plugin.messages().send(player, "ad-no-money", "cost", vault.format(cost));
                return;
            }
            player.getPersistentDataContainer().set(lastAdKey, PersistentDataType.LONG, System.currentTimeMillis());
            broadcast(player, message);
            if (cost > 0) {
                plugin.messages().send(player, "ad-paid", "cost", vault.format(cost));
            }
        } finally {
            pending.remove(player.getUniqueId());
        }
    }

    /** Seconds left before the player can advertise again with their current tier. */
    public long remainingCooldown(Player player, Tier tier) {
        long last = player.getPersistentDataContainer().getOrDefault(lastAdKey, PersistentDataType.LONG, 0L);
        long readyAt = last + tier.cooldownSeconds() * 1000L;
        long now = System.currentTimeMillis();
        return readyAt > now ? (readyAt - now + 999) / 1000 : 0;
    }

    private void broadcast(Player sender, Component message) {
        Component name = plugin.renderer().nameWithHover(sender, plugin.formats().select(sender));
        TagResolver[] resolvers = {
                Placeholder.component("dk_name", name),
                Placeholder.unparsed("dk_player", sender.getName()),
                Placeholder.component("dk_message", message)
        };

        List<Component> lines = new ArrayList<>();
        for (String raw : config().getStringList("advertisement.format")) {
            lines.add(Lines.render(tokens(raw), sender, plugin.placeholders(), resolvers));
        }

        Title title = null;
        if (config().getBoolean("advertisement.title.enabled")) {
            title = Title.title(
                    Lines.render(tokens(str("advertisement.title.title")), sender, plugin.placeholders(), resolvers),
                    Lines.render(tokens(str("advertisement.title.subtitle")), sender, plugin.placeholders(), resolvers));
        }

        boolean exempt = sender.hasPermission(PrivateMessageService.IGNORE_EXEMPT_PERMISSION);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!exempt && plugin.users().isIgnoring(viewer.getUniqueId(), sender.getUniqueId())) {
                continue;
            }
            lines.forEach(viewer::sendMessage);
            if (title != null) {
                viewer.showTitle(title);
            }
            Lines.playSound(viewer, config(), "advertisement.sound", plugin.getLogger());
        }

        if (config().getBoolean("advertisement.log-to-console")) {
            plugin.getLogger().info("[AD] " + sender.getName() + ": "
                    + PlainTextComponentSerializer.plainText().serialize(message));
        }
    }

    private static String tokens(String template) {
        return template
                .replace("{name}", "<dk_name>")
                .replace("{player}", "<dk_player>")
                .replace("{message}", "<dk_message>");
    }

    private String str(String path) {
        String value = config().getString(path);
        return value == null ? "" : value;
    }

    private YamlConfiguration config() {
        return plugin.configs().config();
    }
}
