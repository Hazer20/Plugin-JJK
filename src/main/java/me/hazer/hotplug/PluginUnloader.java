package me.hazer.hotplug;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.SimplePluginManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Выгрузчик плагинов с использованием Reflection API.
 *
 * <p>Полной официальной поддержки unload в Bukkit API нет,
 * поэтому используется аккуратная очистка ссылок в менеджерах.
 * Этот подход широко применяется, но должен выполняться осторожно.</p>
 */
public final class PluginUnloader {

    private final Main plugin;
    private final LoggerUtil logger;
    private final PluginRegistry registry;

    public PluginUnloader(Main plugin, LoggerUtil logger, PluginRegistry registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.registry = registry;
    }

    /**
     * Выгружает плагин по имени.
     */
    @SuppressWarnings("unchecked")
    public boolean unload(String pluginName) {
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null) {
            logger.warn("Plugin not found for unload: " + pluginName);
            return false;
        }
        if (target.getName().equalsIgnoreCase(plugin.getName())) {
            logger.warn("Attempt to unload self plugin blocked");
            return false;
        }

        PluginManager pluginManager = Bukkit.getPluginManager();

        try {
            pluginManager.disablePlugin(target);

            if (!(pluginManager instanceof SimplePluginManager spm)) {
                registry.markDisabled(target.getName(), "Disabled only (unsupported manager)");
                return true;
            }

            Field pluginsField = SimplePluginManager.class.getDeclaredField("plugins");
            Field lookupNamesField = SimplePluginManager.class.getDeclaredField("lookupNames");
            Field listenersField = SimplePluginManager.class.getDeclaredField("listeners");
            Field commandMapField = SimplePluginManager.class.getDeclaredField("commandMap");

            pluginsField.setAccessible(true);
            lookupNamesField.setAccessible(true);
            listenersField.setAccessible(true);
            commandMapField.setAccessible(true);

            List<Plugin> plugins = (List<Plugin>) pluginsField.get(spm);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(spm);
            Map<Class<? extends Event>, List<RegisteredListener>> listeners =
                    (Map<Class<? extends Event>, List<RegisteredListener>>) listenersField.get(spm);
            Object commandMap = commandMapField.get(spm);

            if (plugins != null) {
                plugins.remove(target);
            }

            if (lookupNames != null) {
                lookupNames.remove(target.getName().toLowerCase(Locale.ROOT));
                lookupNames.remove(target.getName());
            }

            if (listeners != null) {
                for (List<RegisteredListener> list : listeners.values()) {
                    list.removeIf(registered -> registered.getPlugin().equals(target));
                }
            }

            if (commandMap != null) {
                Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

                if (knownCommands != null) {
                    List<String> removals = new ArrayList<>();
                    for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                        Command command = entry.getValue();
                        if (command instanceof PluginCommand pc && pc.getPlugin().equals(target)) {
                            command.unregister((org.bukkit.command.CommandMap) commandMap);
                            removals.add(entry.getKey());
                        }
                    }

                    for (String key : removals) {
                        knownCommands.remove(key);
                    }
                }
            }

            ClassLoader classLoader = target.getClass().getClassLoader();
            if (classLoader instanceof java.net.URLClassLoader urlClassLoader) {
                try {
                    urlClassLoader.close();
                } catch (Throwable closeError) {
                    logger.warn("Could not close classloader for " + target.getName() + ": " + closeError.getMessage());
                }
            }

            registry.markDisabled(target.getName(), "Manually unloaded");
            logger.info("Plugin unloaded: " + target.getName());
            logger.info("Plugin unloaded");
            return true;
        } catch (Throwable throwable) {
            registry.markError(target.getName(), plugin.resolvePluginJarOrFallback(target.getName()), throwable.getMessage());
            logger.error("Failed to unload plugin: " + target.getName(), throwable);
            return false;
        }
    }
}
