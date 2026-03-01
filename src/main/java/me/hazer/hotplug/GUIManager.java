package me.hazer.hotplug;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Управляет жизненным циклом GUI и его listener.
 */
public final class GUIManager {

    private final Main plugin;
    private final ConfigManager config;
    private final PluginInventoryGUI inventoryGui;

    public GUIManager(Main plugin,
                      ConfigManager config,
                      PluginRegistry registry,
                      PluginLoader loader,
                      PluginUnloader unloader,
                      LoggerUtil logger) {
        this.plugin = plugin;
        this.config = config;
        this.inventoryGui = new PluginInventoryGUI(plugin, registry, loader, unloader, logger);
    }

    public void register() {
        if (!config.isEnableGui()) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(inventoryGui, plugin);
    }

    public void unregister() {
        HandlerList.unregisterAll(inventoryGui);
    }

    public boolean open(Player player) {
        if (!config.isEnableGui()) {
            return false;
        }
        inventoryGui.open(player);
        return true;
    }
}
