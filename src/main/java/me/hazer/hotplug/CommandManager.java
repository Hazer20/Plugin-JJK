package me.hazer.hotplug;

import org.bukkit.command.PluginCommand;

/**
 * Регистрация команд и tab-complete.
 */
public final class CommandManager {

    private final Main plugin;
    private final LoggerUtil logger;

    public CommandManager(Main plugin, LoggerUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void register(HotPluginCommand commandExecutor) {
        PluginCommand command = plugin.getCommand("hotplugins");
        if (command == null) {
            logger.error("Command /hotplugins missing in plugin.yml");
            return;
        }

        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
    }
}
