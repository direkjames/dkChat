package com.direkjames.dkchat.user;

import com.direkjames.dkchat.DkChat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Keeps chat state for online players. Spy toggles and ignore lists are saved on the player
 * itself (persistent data), so they survive relogs and restarts without any extra files.
 */
public final class UserManager implements Listener {

    public static final String SPY_PERMISSION = "dkchat.spy";

    private final NamespacedKey spyKey;
    private final NamespacedKey ignoreKey;
    private final Map<UUID, ChatUser> users = new ConcurrentHashMap<>();

    public UserManager(DkChat plugin) {
        this.spyKey = new NamespacedKey(plugin, "spy_disabled");
        this.ignoreKey = new NamespacedKey(plugin, "ignored");
    }

    /** Loads players that are already online (for example after a plugin reload). */
    public void loadOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        users.remove(event.getPlayer().getUniqueId());
    }

    private void load(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        ChatUser user = new ChatUser();
        user.spyDisabled(pdc.getOrDefault(spyKey, PersistentDataType.BOOLEAN, false));

        String raw = pdc.get(ignoreKey, PersistentDataType.STRING);
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                try {
                    user.ignored().add(UUID.fromString(part.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Skip a damaged entry instead of losing the whole list.
                }
            }
        }
        users.put(player.getUniqueId(), user);
    }

    public ChatUser get(Player player) {
        return users.computeIfAbsent(player.getUniqueId(), id -> new ChatUser());
    }

    // ---------------------------------------------------------------- spy

    /** Spy is on automatically for anyone with dkchat.spy, unless they turned it off. */
    public boolean isSpying(Player player) {
        return player.hasPermission(SPY_PERMISSION) && !get(player).spyDisabled();
    }

    /** Flips the player's spy setting and returns true if spy is now on. */
    public boolean toggleSpy(Player player) {
        ChatUser user = get(player);
        boolean disabled = !user.spyDisabled();
        user.spyDisabled(disabled);
        player.getPersistentDataContainer().set(spyKey, PersistentDataType.BOOLEAN, disabled);
        return !disabled;
    }

    // ------------------------------------------------------------- ignore

    /** True if {@code viewer} is ignoring {@code sender}. Safe to call from any thread. */
    public boolean isIgnoring(UUID viewer, UUID sender) {
        ChatUser user = users.get(viewer);
        return user != null && user.ignored().contains(sender);
    }

    /** Adds or removes {@code target} from the player's ignore list. Returns true if now ignored. */
    public boolean toggleIgnore(Player player, UUID target) {
        ChatUser user = get(player);
        boolean nowIgnored;
        if (user.ignored().remove(target)) {
            nowIgnored = false;
        } else {
            user.ignored().add(target);
            nowIgnored = true;
        }
        saveIgnores(player, user);
        return nowIgnored;
    }

    private void saveIgnores(Player player, ChatUser user) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (user.ignored().isEmpty()) {
            pdc.remove(ignoreKey);
        } else {
            pdc.set(ignoreKey, PersistentDataType.STRING,
                    user.ignored().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
    }
}
