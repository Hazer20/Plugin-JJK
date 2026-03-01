package com.de.squad.welcome;

import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getLogger().info("DE Squad Welcome plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DE Squad Welcome plugin disabled!");
    }
}
