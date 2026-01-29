package com.nhulston.essentials.events;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nhulston.essentials.util.ConfigManager;
import com.nhulston.essentials.util.Log;

import javax.annotation.Nonnull;

/**
 * Prevents players from picking up items in specific worlds.
 * Players with essentials.pickup.bypass permission can bypass this restriction.
 *
 * NOTE: This feature is currently disabled because the Hytale API does not yet expose
 * an item pickup event (PlayerCollectItemEvent or similar). This will be implemented
 * once the API provides the necessary event hooks.
 */
public class ItemPickupProtectionEvent {
    private static final String BYPASS_PERMISSION = "essentials.pickup.bypass";

    private final ConfigManager configManager;

    public ItemPickupProtectionEvent(@Nonnull ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        // TODO: Implement item pickup blocking when Hytale API provides item pickup events
        // Currently, there is no PlayerCollectItemEvent or similar event available in the API
        // The configuration is loaded and ready, but we cannot act on it yet

        Log.warning("Item pickup protection is configured but not active - waiting for Hytale API support");
        Log.warning("Configured blocked worlds: " + configManager.getItemPickupBlockedWorlds());
    }
}
