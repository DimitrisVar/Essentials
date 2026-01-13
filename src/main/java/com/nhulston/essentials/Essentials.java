package com.nhulston.essentials;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.nhulston.essentials.commands.home.DelHomeCommand;
import com.nhulston.essentials.commands.home.HomeCommand;
import com.nhulston.essentials.commands.home.SetHomeCommand;
import com.nhulston.essentials.managers.HomeManager;
import com.nhulston.essentials.util.ConfigManager;
import com.nhulston.essentials.util.StorageManager;
import com.nhulston.essentials.util.Log;

import javax.annotation.Nonnull;

public class Essentials extends JavaPlugin {
    private ConfigManager configManager;
    private StorageManager storageManager;
    private HomeManager homeManager;

    public Essentials(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        Log.init(getLogger());
        Log.info("Essentials is starting...");

        configManager = new ConfigManager(getDataDirectory());
        storageManager = new StorageManager(getDataDirectory());

        homeManager = new HomeManager(storageManager, configManager);
    }

    @Override
    protected void start() {
        registerCommands();
        Log.info("Essentials started successfully!");
    }

    @Override
    protected void shutdown() {
        Log.info("Essentials is shutting down...");

        if (storageManager != null) {
            storageManager.shutdown();
        }

        Log.info("Essentials shut down.");
    }

    private void registerCommands() {
        getCommandRegistry().registerCommand(new SetHomeCommand(homeManager));
        getCommandRegistry().registerCommand(new HomeCommand(homeManager));
        getCommandRegistry().registerCommand(new DelHomeCommand(homeManager));
    }
}
