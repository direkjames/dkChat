package com.direkjames.dkchat.showcase;

import com.direkjames.dkchat.DkChat;
import com.direkjames.dkchat.text.Colors;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * [item], [inv] and [echest] in chat.
 *
 * <p>Anti-dupe: inventories are copied the moment the message is sent, shown in a GUI where every
 * click is cancelled, and every copied item is marked so it is deleted if it ever leaves the GUI.
 * Anti-spam: per-type cooldowns, and each tag is only shown once per message.</p>
 */
public final class ShowcaseManager {

    public static final String BYPASS_COOLDOWN_PERMISSION = "dkchat.showcase.bypasscooldown";
    public static final String VIEW_PERMISSION = "dkchat.showcase.view";

    private final DkChat plugin;
    private final NamespacedKey previewKey;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Map<ShowcaseType, Long>> cooldowns = new ConcurrentHashMap<>();
    private volatile Map<ShowcaseType, Pattern> patterns = Map.of();
    private volatile Map<ShowcaseType, String> firstTag = Map.of();

    private record Snapshot(Component title, ItemStack[] contents, int size, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /** What was copied from the player on the main thread. Fields are null when not requested. */
    private record Captured(ItemStack hand, HoverEventSource<?> handHover, ItemStack[] inventory, ItemStack[] enderChest) {
    }

    public ShowcaseManager(DkChat plugin) {
        this.plugin = plugin;
        this.previewKey = new NamespacedKey(plugin, "preview_item");
    }

    /** Reads tags from config.yml. Called on enable and on /dchat reload. */
    public void reload() {
        Map<ShowcaseType, Pattern> newPatterns = new EnumMap<>(ShowcaseType.class);
        Map<ShowcaseType, String> newFirstTag = new EnumMap<>(ShowcaseType.class);
        for (ShowcaseType type : ShowcaseType.values()) {
            if (!config().getBoolean(path(type, "enabled"))) {
                continue;
            }
            List<String> tags = config().getStringList(path(type, "tags")).stream()
                    .filter(tag -> !tag.isBlank())
                    .toList();
            if (tags.isEmpty()) {
                continue;
            }
            newPatterns.put(type, Pattern.compile(
                    tags.stream().map(Pattern::quote).collect(Collectors.joining("|")),
                    Pattern.CASE_INSENSITIVE));
            newFirstTag.put(type, tags.getFirst());
        }
        this.patterns = newPatterns;
        this.firstTag = newFirstTag;
    }

    /** Removes expired previews every minute so they don't pile up in memory. */
    public void startCleanup() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            snapshots.values().removeIf(Snapshot::expired);
            long now = System.currentTimeMillis();
            cooldowns.values().forEach(map -> map.values().removeIf(until -> until <= now));
            cooldowns.values().removeIf(Map::isEmpty);
        }, 1200L, 1200L);
    }

    // ------------------------------------------------------------------ chat

    /**
     * Replaces showcase tags in a chat message.
     *
     * @return the new message, or {@code null} if the chat message should be cancelled
     *         (cooldown, empty hand). The player has already been told why.
     */
    public Component apply(Player player, String typed, Component message) {
        Map<ShowcaseType, Pattern> active = patterns;
        Set<ShowcaseType> used = EnumSet.noneOf(ShowcaseType.class);
        for (Map.Entry<ShowcaseType, Pattern> entry : active.entrySet()) {
            // Without permission the tag simply stays as plain text.
            if (entry.getValue().matcher(typed).find() && player.hasPermission(entry.getKey().permission())) {
                used.add(entry.getKey());
            }
        }
        if (used.isEmpty()) {
            return message;
        }

        long now = System.currentTimeMillis();
        boolean bypass = player.hasPermission(BYPASS_COOLDOWN_PERMISSION);
        if (!bypass) {
            Map<ShowcaseType, Long> mine = cooldowns.getOrDefault(player.getUniqueId(), Map.of());
            for (ShowcaseType type : used) {
                long until = mine.getOrDefault(type, 0L);
                if (until > now) {
                    long seconds = (until - now + 999) / 1000;
                    plugin.messages().send(player, "showcase-cooldown",
                            "tag", firstTag.getOrDefault(type, ""), "time", String.valueOf(seconds));
                    return null;
                }
            }
        }

        Captured captured;
        try {
            captured = onMainThread(() -> capture(player, used));
        } catch (Exception e) {
            plugin.messages().send(player, "showcase-failed");
            return null;
        }
        if (used.contains(ShowcaseType.ITEM) && captured.hand() == null) {
            plugin.messages().send(player, "showcase-empty-hand", "tag", firstTag.getOrDefault(ShowcaseType.ITEM, ""));
            return null;
        }

        Component result = message;
        for (ShowcaseType type : used) {
            Component display = switch (type) {
                case ITEM -> itemDisplay(player, captured.hand(), captured.handHover());
                case INVENTORY -> snapshotDisplay(player, type, captured.inventory());
                case ENDERCHEST -> snapshotDisplay(player, type, captured.enderChest());
            };
            Pattern pattern = active.get(type);
            // Show the first tag, delete any repeats ("[i][i][i]" shows one item).
            result = result.replaceText(TextReplacementConfig.builder()
                    .match(pattern).once().replacement(display).build());
            result = result.replaceText(TextReplacementConfig.builder()
                    .match(pattern).replacement(Component.empty()).build());
        }

        if (!bypass) {
            Map<ShowcaseType, Long> mine = cooldowns.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
            for (ShowcaseType type : used) {
                mine.put(type, now + Math.max(0, config().getInt(path(type, "cooldown"))) * 1000L);
            }
        }
        return result;
    }

    private Component itemDisplay(Player player, ItemStack hand, HoverEventSource<?> hover) {
        int amount = hand.getAmount();
        Component amountText = amount > 1
                ? Colors.trusted(str(path(ShowcaseType.ITEM, "amount-format"))
                        .replace("{count}", String.valueOf(amount)))
                : Component.empty();
        String format = tokens(str(path(ShowcaseType.ITEM, "format")))
                .replace("{item}", "<dk_item>")
                .replace("{amount}", "<dk_amount>");
        return Colors.trusted(format, nameResolvers(player,
                        Placeholder.component("dk_item", hand.effectiveName()),
                        Placeholder.component("dk_amount", amountText)))
                .hoverEvent(hover);
    }

    private Component snapshotDisplay(Player player, ShowcaseType type, ItemStack[] contents) {
        UUID id = UUID.randomUUID();
        long expireMinutes = Math.max(1, config().getInt("showcase.snapshot-expire-minutes"));
        Component title = Colors.trusted(tokens(str(path(type, "title"))), nameResolvers(player));
        snapshots.put(id, new Snapshot(title, contents, type.guiSize(),
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireMinutes)));

        Component hover = Colors.trusted(tokens(str(path(type, "hover"))), nameResolvers(player));
        return Colors.trusted(tokens(str(path(type, "format"))), nameResolvers(player))
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.runCommand("/dchat view " + id));
    }

    // --------------------------------------------------------------- capture

    /** Runs on the main thread: copies only what the message uses. */
    private Captured capture(Player player, Set<ShowcaseType> used) {
        ItemStack hand = null;
        HoverEventSource<?> hover = null;
        ItemStack[] inventory = null;
        ItemStack[] ender = null;

        if (used.contains(ShowcaseType.ITEM)) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!held.getType().isAir()) {
                hand = held.clone();
                hover = safeHover(hand);
            }
        }
        if (used.contains(ShowcaseType.INVENTORY)) {
            inventory = inventoryLayout(player.getInventory());
        }
        if (used.contains(ShowcaseType.ENDERCHEST)) {
            ItemStack[] source = player.getEnderChest().getContents();
            ender = new ItemStack[ShowcaseType.ENDERCHEST.guiSize()];
            for (int i = 0; i < source.length && i < ender.length; i++) {
                ender[i] = marked(source[i]);
            }
        }
        return new Captured(hand, hover, inventory, ender);
    }

    /**
     * 5-row layout: rows 1-3 main inventory, row 4 hotbar, row 5 helmet, chestplate,
     * leggings, boots, (gap), offhand, with glass filling the empty spots.
     */
    private ItemStack[] inventoryLayout(PlayerInventory inv) {
        ItemStack[] out = new ItemStack[ShowcaseType.INVENTORY.guiSize()];
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 9; i < 36 && i < storage.length; i++) {
            out[i - 9] = marked(storage[i]);
        }
        for (int i = 0; i < 9 && i < storage.length; i++) {
            out[27 + i] = marked(storage[i]);
        }
        out[36] = marked(inv.getHelmet());
        out[37] = marked(inv.getChestplate());
        out[38] = marked(inv.getLeggings());
        out[39] = marked(inv.getBoots());
        out[41] = marked(inv.getItemInOffHand());
        ItemStack filler = filler();
        for (int slot : new int[]{40, 42, 43, 44}) {
            out[slot] = filler.clone();
        }
        return out;
    }

    /**
     * The chat hover sends the whole item to every player. A shulker full of written books
     * can be big enough to kick people, so heavy data is removed from the hover when the
     * item is over the configured size. The item itself is not changed.
     */
    private HoverEventSource<?> safeHover(ItemStack item) {
        int max = Math.max(1024, config().getInt("showcase.max-hover-bytes"));
        ItemStack copy = item.clone();
        if (sizeOf(copy) <= max) {
            return copy;
        }
        copy.unsetData(DataComponentTypes.CONTAINER);
        copy.unsetData(DataComponentTypes.BUNDLE_CONTENTS);
        copy.unsetData(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        copy.unsetData(DataComponentTypes.WRITABLE_BOOK_CONTENT);
        if (sizeOf(copy) <= max) {
            return copy;
        }
        return HoverEvent.showText(item.effectiveName());
    }

    private static int sizeOf(ItemStack item) {
        try {
            return item.serializeAsBytes().length;
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------- GUI

    /** Opens a preview. Returns false if it expired or never existed. */
    public boolean open(Player viewer, UUID id) {
        Snapshot snapshot = snapshots.get(id);
        if (snapshot == null || snapshot.expired()) {
            return false;
        }
        SnapshotHolder holder = new SnapshotHolder();
        Inventory inventory = Bukkit.createInventory(holder, snapshot.size(), snapshot.title());
        holder.inventory(inventory);
        ItemStack[] copy = new ItemStack[snapshot.size()];
        for (int i = 0; i < copy.length && i < snapshot.contents().length; i++) {
            ItemStack item = snapshot.contents()[i];
            copy[i] = item == null ? null : item.clone();
        }
        inventory.setContents(copy);
        viewer.openInventory(inventory);
        return true;
    }

    /** Closes every open preview (used when the plugin shuts down). */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view.getTopInventory().getHolder() instanceof SnapshotHolder) {
                player.closeInventory();
            }
        }
    }

    /** True if the item is a copy made for a preview GUI (it should never exist outside one). */
    public boolean isPreviewItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(previewKey, PersistentDataType.BYTE);
    }

    private ItemStack marked(ItemStack original) {
        if (original == null || original.getType().isAir()) {
            return null;
        }
        ItemStack copy = original.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return marked(pane);
    }

    // --------------------------------------------------------------- helpers

    private YamlConfiguration config() {
        return plugin.configs().config();
    }

    /** Single-argument getter so keys missing from older config.yml files use the jar default. */
    private String str(String path) {
        String value = config().getString(path);
        return value == null ? "" : value;
    }

    private static String path(ShowcaseType type, String key) {
        return "showcase." + type.key() + "." + key;
    }

    private static String tokens(String template) {
        return template.replace("{name}", "<dk_name>").replace("{player}", "<dk_player>");
    }

    private static TagResolver[] nameResolvers(Player player, TagResolver... extra) {
        List<TagResolver> list = new ArrayList<>(List.of(extra));
        list.add(Placeholder.component("dk_name", player.displayName()));
        list.add(Placeholder.unparsed("dk_player", player.getName()));
        return list.toArray(TagResolver[]::new);
    }

    private <T> T onMainThread(Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return task.call();
        }
        return Bukkit.getScheduler().callSyncMethod(plugin, task).get(2, TimeUnit.SECONDS);
    }
}
