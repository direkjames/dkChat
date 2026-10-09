package com.direkjames.dkchat.showcase;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as a read-only dkChat preview. Every click in it is cancelled. */
public final class SnapshotHolder implements InventoryHolder {

    private Inventory inventory;

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
