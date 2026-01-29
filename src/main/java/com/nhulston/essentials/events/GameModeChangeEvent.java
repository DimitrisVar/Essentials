package com.nhulston.essentials.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nhulston.essentials.Essentials;
import com.nhulston.essentials.managers.CreativeItemTracker;
import com.nhulston.essentials.util.Log;
import com.nhulston.essentials.util.MessageManager;
import com.nhulston.essentials.util.Msg;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles game mode changes to prevent abuse of Creative mode items.
 * Prevents players from switching to Adventure mode if they have items
 * obtained while in Creative mode still in their inventory.
 * Also tracks when players enter Creative mode to mark their items.
 */
public class GameModeChangeEvent {
    private final CreativeItemTracker creativeItemTracker;
    private final MessageManager messages;

    public GameModeChangeEvent(@Nonnull CreativeItemTracker creativeItemTracker) {
        this.creativeItemTracker = creativeItemTracker;
        this.messages = Essentials.getInstance().getMessageManager();
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new GameModeTrackingSystem(creativeItemTracker, messages));
        Log.info("Game mode change event registered.");
    }

    /**
     * System that tracks player game mode changes.
     */
    private static class GameModeTrackingSystem extends EntityTickingSystem<EntityStore> {
        private final CreativeItemTracker creativeItemTracker;
        private final MessageManager messages;
        private final Map<UUID, GameMode> lastKnownGameMode = new ConcurrentHashMap<>();

        GameModeTrackingSystem(@Nonnull CreativeItemTracker creativeItemTracker,
                              @Nonnull MessageManager messages) {
            this.creativeItemTracker = creativeItemTracker;
            this.messages = messages;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void tick(float deltaTime, int index, ArchetypeChunk<EntityStore> chunk,
                        @NotNull Store<EntityStore> store, @NotNull CommandBuffer<EntityStore> buffer) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }

            GameMode currentMode = player.getGameMode();
            UUID playerUuid = playerRef.getUuid();
            GameMode previousMode = lastKnownGameMode.get(playerUuid);

            // Update tracked gamemode
            lastKnownGameMode.put(playerUuid, currentMode);

            // Check if gamemode changed
            if (previousMode == null || currentMode == previousMode) {
                return;
            }

            handleGameModeChange(store, ref, buffer, playerRef, previousMode, currentMode);
        }

        private void handleGameModeChange(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                                        @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull PlayerRef playerRef,
                                        @Nonnull GameMode previousMode, @Nonnull GameMode currentMode) {
            // Check if player has bypass permission
            boolean hasBypass = com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
                    .hasPermission(playerRef.getUuid(), "essentials.creative.items.bypass");

            if (hasBypass) {
                return;
            }

            // Get Player component for gamemode change
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }

            // If switching FROM Creative mode
            if (previousMode == GameMode.Creative && currentMode != GameMode.Creative) {
                // Check if player has Creative items in inventory
                boolean hasCreativeItems = creativeItemTracker.hasCreativeItems(
                    playerRef.getUuid(), store, ref
                );

                if (hasCreativeItems) {
                    // Block the gamemode change by switching back to Creative
                    player.setGameMode(ref, GameMode.Creative, buffer);
                    lastKnownGameMode.put(playerRef.getUuid(), GameMode.Creative);

                    // Send message to player
                    Msg.send(playerRef, messages.get("commands.gamemode.blocked-creative-items"));

                    Log.info("Blocked gamemode change for player " + playerRef.getUsername() +
                            " - has Creative mode items in inventory");
                    return;
                }
            }

            // If switching TO Creative mode, start tracking items
            if (currentMode == GameMode.Creative && previousMode != GameMode.Creative) {
                creativeItemTracker.trackCreativeItems(playerRef.getUuid(), store, ref);
                Log.info("Started tracking Creative mode items for player " + playerRef.getUsername());
            }
        }

        /**
         * Clean up tracking when player disconnects.
         */
        public void onPlayerQuit(@Nonnull UUID playerUuid) {
            lastKnownGameMode.remove(playerUuid);
        }
    }
}
