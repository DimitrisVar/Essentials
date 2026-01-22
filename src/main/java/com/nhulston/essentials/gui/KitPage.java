package com.nhulston.essentials.gui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nhulston.essentials.Essentials;
import com.nhulston.essentials.managers.KitManager;
import com.nhulston.essentials.models.Kit;
import com.nhulston.essentials.models.KitItem;
import com.nhulston.essentials.util.CooldownUtil;
import com.nhulston.essentials.util.MessageManager;
import com.nhulston.essentials.util.Msg;

import java.util.Map;

/**
 * A GUI page for selecting kits.
 */
public class KitPage extends InteractiveCustomUIPage<KitPage.KitPageData> {
    private static final String COOLDOWN_BYPASS_PERMISSION = "essentials.kit.cooldown.bypass";

    private final KitManager kitManager;
    private final MessageManager messages;

    public KitPage(@Nonnull PlayerRef playerRef, @Nonnull KitManager kitManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, KitPageData.CODEC);
        this.kitManager = kitManager;
        this.messages = Essentials.getInstance().getMessageManager();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Essentials_KitPage.ui");

        List<Kit> allKits = new ArrayList<>(kitManager.getKits());
        if (allKits.isEmpty()) {
            // No kits available - could add a "No kits available" message element
            return;
        }

        // Create a grid layout with 3 kits per row
        int kitsPerRow = 3;
        int totalRows = (int) Math.ceil((double) allKits.size() / kitsPerRow);

        for (int row = 0; row < totalRows; row++) {
            // Create a row group for this row
            commandBuilder.appendInline("#KitRows", 
                "Group { LayoutMode: Left; Anchor: (Height: 128); Padding: (Horizontal: 4); }");
            
            String rowSelector = "#KitRows[" + row + "]";
            
            // Calculate start and end index for this row
            int startIdx = row * kitsPerRow;
            int endIdx = Math.min(startIdx + kitsPerRow, allKits.size());
            
            for (int col = 0; col < (endIdx - startIdx); col++) {
                int kitIdx = startIdx + col;
                Kit kit = allKits.get(kitIdx);
                
                // Append kit entry to this row
                commandBuilder.append(rowSelector, "Pages/Essentials_KitEntry.ui");
                
                // Select the kit card within the row
                String cardSelector = rowSelector + "[" + col + "]";
                
                commandBuilder.set(cardSelector + " #Name.Text", kit.getDisplayName());

                // Check permission and cooldown status
                String permission = "essentials.kit." + kit.getId();
                boolean hasPermission = PermissionsModule.get().hasPermission(playerRef.getUuid(), permission);

                String status;
                if (!hasPermission) {
                    status = "No access";
                } else {
                    long remainingCooldown = kitManager.getRemainingCooldown(playerRef.getUuid(), kit.getId());
                    if (remainingCooldown > 0) {
                        status = CooldownUtil.formatCooldown(remainingCooldown);
                    } else {
                        status = "Ready";
                    }
                }
                commandBuilder.set(cardSelector + " #Status.Text", status);

                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        cardSelector,
                        EventData.of("Kit", kit.getId())
                );
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull KitPageData data) {
        if (data.kit == null || data.kit.isEmpty()) {
            return;
        }

        Kit kit = kitManager.getKit(data.kit);
        if (kit == null) {
            Msg.send(playerRef, messages.get("gui.kit.not-found"));
            this.close();
            return;
        }

        // Check permission
        String permission = "essentials.kit." + kit.getId();
        if (!PermissionsModule.get().hasPermission(playerRef.getUuid(), permission)) {
            Msg.send(playerRef, messages.get("gui.kit.no-permission"));
            this.close();
            return;
        }

        // Check cooldown (unless player has bypass permission)
        boolean canBypassCooldown = PermissionsModule.get().hasPermission(playerRef.getUuid(), COOLDOWN_BYPASS_PERMISSION);
        if (!canBypassCooldown) {
            long remainingCooldown = kitManager.getRemainingCooldown(playerRef.getUuid(), kit.getId());
            if (remainingCooldown > 0) {
                Msg.send(playerRef, messages.get("gui.kit.cooldown", Map.of("time", CooldownUtil.formatCooldown(remainingCooldown))));
                this.close();
                return;
            }
        }

        // Get player inventory
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Msg.send(playerRef, messages.get("gui.kit.inventory-error"));
            this.close();
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Msg.send(playerRef, messages.get("gui.kit.inventory-error"));
            this.close();
            return;
        }

        // Apply kit (overflow items will be dropped on the ground)
        applyKit(kit, inventory, ref, store);

        // Sync inventory changes to client
        player.sendInventory();

        // Set cooldown
        if (kit.getCooldown() > 0) {
            kitManager.setKitUsed(playerRef.getUuid(), kit.getId());
        }

        Msg.send(playerRef, messages.get("gui.kit.received", Map.of("kit", kit.getDisplayName())));
        this.close();
    }

    /**
     * Applies a kit to the player's inventory. Overflow items are dropped on the ground.
     */
    private void applyKit(@Nonnull Kit kit, @Nonnull Inventory inventory,
                          @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // Clear inventory if replace mode
        if (kit.isReplaceMode()) {
            inventory.clear();
        }

        // Add items to inventory
        for (KitItem kitItem : kit.getItems()) {
            ItemStack itemStack = new ItemStack(kitItem.itemId(), kitItem.quantity());
            ItemStack remainder = addItemWithOverflow(inventory, kitItem, itemStack);
            
            // Drop any overflow items on the ground
            if (remainder != null && !remainder.isEmpty()) {
                ItemUtils.dropItem(ref, remainder, store);
            }
        }
    }

    /**
     * Adds an item to inventory and returns any remainder that couldn't fit
     */
    @Nullable
    private ItemStack addItemWithOverflow(@Nonnull Inventory inventory, @Nonnull KitItem kitItem, @Nonnull ItemStack itemStack) {
        ItemContainer container = getContainerBySection(inventory, kitItem.section());
        
        if (container != null) {
            // Try to add to specific slot first
            short slot = (short) kitItem.slot();
            if (slot >= 0 && slot < container.getCapacity()) {
                ItemStack existing = container.getItemStack(slot);
                if (existing == null || existing.isEmpty()) {
                    container.setItemStackForSlot(slot, itemStack);
                    return null;
                }
            }
            // Slot occupied or invalid - for armor/utility/tools, fall back to hotbar/storage
            // (these containers don't support arbitrary item placement like hotbar/storage do)
            String section = kitItem.section().toLowerCase();
            if (section.equals("armor") || section.equals("utility") || section.equals("tools")) {
                ItemStackTransaction transaction = inventory.getCombinedHotbarFirst().addItemStack(itemStack);
                return transaction.getRemainder();
            }
            // For hotbar/storage, try adding anywhere in that container
            ItemStackTransaction transaction = container.addItemStack(itemStack);
            return transaction.getRemainder();
        } else {
            // Unknown section, try adding to combined hotbar/storage
            ItemStackTransaction transaction = inventory.getCombinedHotbarFirst().addItemStack(itemStack);
            return transaction.getRemainder();
        }
    }

    /**
     * Gets the appropriate item container by section name
     */
    private ItemContainer getContainerBySection(@Nonnull Inventory inventory, @Nonnull String section) {
        return switch (section.toLowerCase()) {
            case "hotbar" -> inventory.getHotbar();
            case "storage" -> inventory.getStorage();
            case "armor" -> inventory.getArmor();
            case "utility" -> inventory.getUtility();
            case "tools" -> inventory.getTools();
            default -> null;
        };
    }

    /**
     * Event data for kit selection.
     */
    public static class KitPageData {
        public static final BuilderCodec<KitPageData> CODEC = BuilderCodec.builder(KitPageData.class, KitPageData::new)
                .append(new KeyedCodec<>("Kit", Codec.STRING), (data, s) -> data.kit = s, data -> data.kit)
                .add()
                .build();

        private String kit;

        public String getKit() {
            return kit;
        }
    }
}
