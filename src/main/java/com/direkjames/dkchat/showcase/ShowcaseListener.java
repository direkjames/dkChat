package com.direkjames.dkchat.showcase;

import com.direkjames.dkchat.DkChat;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps preview GUIs read-only.
 *
 * <p>Layer 1: every click and drag while a preview is open is cancelled, including shift-click,
 * number keys, offhand swap, double-click collect and creative middle-click.
 * Layer 2: preview items are marked; if one ever shows up anywhere else it is deleted and logged.</p>
 */
public final class ShowcaseListener implements Listener {

    private final DkChat plugin;
    private final ShowcaseManager showcase;

    public ShowcaseListener(DkChat plugin, ShowcaseManager showcase) {
        this.plugin = plugin;
        this.showcase = showcase;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SnapshotHolder) {
            event.setCancelled(true);
            return;
        }
        // Safety net outside preview GUIs.
        if (showcase.isPreviewItem(event.getCurrentItem())) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            warn(event.getWhoClicked(), "clicked");
        }
        if (showcase.isPreviewItem(event.getCursor())) {
            event.setCancelled(true);
            event.getView().setCursor(null);
            warn(event.getWhoClicked(), "held");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SnapshotHolder
                || showcase.isPreviewItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (showcase.isPreviewItem(dropped.getItemStack())) {
            dropped.remove();
            warn(event.getPlayer(), "dropped");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (showcase.isPreviewItem(stack)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    private void warn(HumanEntity who, String action) {
        plugin.getLogger().warning(who.getName() + " " + action
                + " a chat preview item outside the preview GUI. The item was deleted.");
    }
}
