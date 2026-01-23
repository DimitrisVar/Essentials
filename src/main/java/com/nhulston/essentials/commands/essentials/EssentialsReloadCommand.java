package com.nhulston.essentials.commands.essentials;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.nhulston.essentials.Essentials;
import com.nhulston.essentials.util.MessageManager;
import com.nhulston.essentials.util.Msg;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand to reload EssentialsCore configuration.
 * Usage: /essentials reload
 * Requires: essentials.reload permission
 * Can be executed by console or players.
 */
public class EssentialsReloadCommand extends AbstractCommand {
    private final MessageManager messages;

    public EssentialsReloadCommand() {
        super("reload", "Reload EssentialsCore configuration");
        this.messages = Essentials.getInstance().getMessageManager();

        requirePermission("essentials.reload");
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        Essentials.getInstance().reloadConfigs();
        Msg.send(context, messages.get("commands.essentials.reload.success"));
        return CompletableFuture.completedFuture(null);
    }
}
