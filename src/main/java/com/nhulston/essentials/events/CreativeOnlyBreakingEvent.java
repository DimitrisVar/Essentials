package com.nhulston.essentials.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nhulston.essentials.util.ConfigManager;
import com.nhulston.essentials.util.Log;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Requires players to be in Creative mode to break blocks in specific worlds.
 * Players with essentials.build.bypass permission can bypass this restriction.
 */
public class CreativeOnlyBreakingEvent {
    private static final String BYPASS_PERMISSION = "essentials.build.bypass";
    private static final String PROTECTED_MESSAGE = "You must be in Creative mode to break blocks in this world!";
    private static final String PROTECTED_COLOR = "#FF5555";

    private final ConfigManager configManager;

    public CreativeOnlyBreakingEvent(@Nonnull ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new CreativeOnlyBreakSystem(configManager));
        Log.info("Creative-only breaking protection registered.");
    }

    private static boolean canBypass(@Nonnull UUID playerUuid) {
        return PermissionsModule.get().hasPermission(playerUuid, BYPASS_PERMISSION);
    }

    private static void sendProtectedMessage(PlayerRef playerRef) {
        if (playerRef != null) {
            playerRef.sendMessage(Message.raw(PROTECTED_MESSAGE).color(PROTECTED_COLOR));
        }
    }

    /**
     * Prevents block breaking unless player is in Creative mode.
     */
    private static class CreativeOnlyBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final ConfigManager configManager;

        CreativeOnlyBreakSystem(ConfigManager configManager) {
            super(BreakBlockEvent.class);
            this.configManager = configManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void handle(int index, @NotNull ArchetypeChunk<EntityStore> chunk,
                           @NotNull Store<EntityStore> store,
                           @NotNull CommandBuffer<EntityStore> buffer,
                           @NotNull BreakBlockEvent event) {
            // Skip if already cancelled
            if (event.isCancelled()) {
                return;
            }

            List<String> creativeOnlyWorlds = configManager.getCreativeOnlyWorlds();

            // If no worlds are configured, allow breaking everywhere
            if (creativeOnlyWorlds.isEmpty()) {
                return;
            }

            // Get the world name from store
            String worldName = store.getExternalData().getWorld().getName();

            // Check if this world requires Creative mode for breaking
            if (!creativeOnlyWorlds.contains(worldName)) {
                return;
            }

            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }

            // Check if player has bypass permission
            if (canBypass(playerRef.getUuid())) {
                return;
            }

            // Get player's game mode
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            Player player = store.getComponent(ref, Player.getComponentType());

            if (player == null) {
                return;
            }

            GameMode gameMode = player.getGameMode();

            // Block breaking if not in Creative mode
            if (gameMode != GameMode.Creative) {
                event.setCancelled(true);
                sendProtectedMessage(playerRef);
            }
        }
    }
}
