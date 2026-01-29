package com.nhulston.essentials.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nhulston.essentials.util.Log;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks items obtained while in Creative mode to prevent abuse.
 * Players with Creative items in their inventory cannot switch to Adventure mode
 * or use certain teleportation commands until the items are removed.
 */
public class CreativeItemTracker {
    // Maps player UUID to snapshot of item counts they had BEFORE entering Creative mode
    // Format: itemId -> total quantity
    private final Map<UUID, Map<String, Integer>> preCreativeSnapshot = new ConcurrentHashMap<>();

    /**
     * Takes a snapshot of items when entering Creative mode.
     * Tracks the quantity of each item type the player currently has.
     */
    public void trackCreativeItems(@Nonnull UUID playerUuid, @Nonnull Store<EntityStore> store,
                                   @Nonnull Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // Count total quantity of each item type
        Map<String, Integer> itemCounts = new HashMap<>();
        countItemsFromContainer(inventory.getHotbar(), itemCounts);
        countItemsFromContainer(inventory.getStorage(), itemCounts);
        countItemsFromContainer(inventory.getArmor(), itemCounts);
        countItemsFromContainer(inventory.getUtility(), itemCounts);
        countItemsFromContainer(inventory.getTools(), itemCounts);

        preCreativeSnapshot.put(playerUuid, itemCounts);

        Log.info("Taking snapshot of " + itemCounts.size() + " item types for player " + playerUuid + " entering Creative mode");
    }

    /**
     * Helper method to count items from a specific inventory container.
     */
    private void countItemsFromContainer(@Nonnull ItemContainer container, @Nonnull Map<String, Integer> itemCounts) {
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty()) {
                String itemId = item.getItemId();
                int quantity = item.getQuantity();
                itemCounts.put(itemId, itemCounts.getOrDefault(itemId, 0) + quantity);
            }
        }
    }

    /**
     * Checks if a player has any NEW items or increased quantities obtained in Creative mode.
     * Returns true if the player has more items than they had when entering Creative.
     */
    public boolean hasCreativeItems(@Nonnull UUID playerUuid, @Nonnull Store<EntityStore> store,
                                   @Nonnull Ref<EntityStore> ref) {
        Map<String, Integer> snapshot = preCreativeSnapshot.get(playerUuid);
        if (snapshot == null) {
            // No snapshot means they never entered Creative mode with tracking enabled
            return false;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }

        // Count current items
        Map<String, Integer> currentCounts = new HashMap<>();
        countItemsFromContainer(inventory.getHotbar(), currentCounts);
        countItemsFromContainer(inventory.getStorage(), currentCounts);
        countItemsFromContainer(inventory.getArmor(), currentCounts);
        countItemsFromContainer(inventory.getUtility(), currentCounts);
        countItemsFromContainer(inventory.getTools(), currentCounts);

        // Check if any item has increased in quantity or is completely new
        for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
            String itemId = entry.getKey();
            int currentQuantity = entry.getValue();
            int snapshotQuantity = snapshot.getOrDefault(itemId, 0);

            if (currentQuantity > snapshotQuantity) {
                int difference = currentQuantity - snapshotQuantity;
                Log.info("Player " + playerUuid + " has " + difference + " NEW " + itemId + " (had: " + snapshotQuantity + ", now: " + currentQuantity + ")");
                return true;
            }
        }

        // If no new items, clear the tracking
        preCreativeSnapshot.remove(playerUuid);
        return false;
    }

    /**
     * Clears all tracked items for a player.
     * Called when items are confirmed removed or player is exempt from tracking.
     */
    public void clearTrackedItems(@Nonnull UUID playerUuid) {
        preCreativeSnapshot.remove(playerUuid);
        Log.info("Cleared Creative item tracking for player " + playerUuid);
    }

    /**
     * Clears tracking for a player when they disconnect.
     */
    public void onPlayerQuit(@Nonnull UUID playerUuid) {
        preCreativeSnapshot.remove(playerUuid);
    }
}
