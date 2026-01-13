package com.nhulston.essentials.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nhulston.essentials.managers.HomeManager;
import com.nhulston.essentials.models.Home;
import com.nhulston.essentials.util.Msg;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public class HomeCommand extends AbstractPlayerCommand {
    private final HomeManager homeManager;

    public HomeCommand(@Nonnull HomeManager homeManager) {
        super("home", "Teleport to your home");
        this.homeManager = homeManager;

        // Add variant with name argument: /home <name>
        addUsageVariant(new HomeNamedCommand(homeManager));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World currentWorld) {
        UUID playerUuid = playerRef.getUuid();
        Map<String, Home> homes = homeManager.getHomes(playerUuid);

        if (homes.isEmpty()) {
            Msg.fail(context, "You don't have any homes set. Use /sethome to set one.");
            return;
        }

        // No argument provided
        if (homes.size() == 1) {
            // Only one home - teleport to it
            String homeName = homes.keySet().iterator().next();
            doTeleportToHome(context, store, ref, playerUuid, homeName, homeManager);
        } else {
            // Multiple homes - list them
            Msg.prefix(context, "Homes", String.join(", ", homes.keySet()));
        }
    }

    static void doTeleportToHome(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                                 @Nonnull Ref<EntityStore> ref, @Nonnull UUID playerUuid,
                                 @Nonnull String homeName, @Nonnull HomeManager homeManager) {
        Home home = homeManager.getHome(playerUuid, homeName);
        if (home == null) {
            Msg.fail(context, "Home '" + homeName + "' not found.");
            return;
        }

        World targetWorld = Universe.get().getWorld(home.getWorld());
        if (targetWorld == null) {
            Msg.fail(context, "World '" + home.getWorld() + "' is not loaded.");
            return;
        }

        Vector3d position = new Vector3d(home.getX(), home.getY(), home.getZ());
        Vector3f rotation = new Vector3f(home.getPitch(), home.getYaw(), 0.0F);

        Teleport teleport = new Teleport(targetWorld, position, rotation);
        store.putComponent(ref, Teleport.getComponentType(), teleport);

        Msg.success(context, String.format("Teleported to home '%s'.", homeName));
    }

    // Inner class for /home <name> variant
    private static class HomeNamedCommand extends AbstractPlayerCommand {
        private final HomeManager homeManager;
        private final RequiredArg<String> nameArg;

        HomeNamedCommand(@Nonnull HomeManager homeManager) {
            super("Teleport to a specific home");
            this.homeManager = homeManager;
            this.nameArg = withRequiredArg("name", "Home name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String homeName = context.get(nameArg);
            doTeleportToHome(context, store, ref, playerRef.getUuid(), homeName, homeManager);
        }
    }
}
