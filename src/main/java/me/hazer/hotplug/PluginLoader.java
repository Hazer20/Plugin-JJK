package me.hazer.hotplug;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.SimplePluginManager;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Класс безопасной загрузки плагинов из jar-файла.
 *
 * <p>Использует стандартный Bukkit PluginManager#loadPlugin,
 * затем вызывает enablePlugin. Ошибки перехватываются и фиксируются
 * в реестре без падения сервера.</p>
 */
public final class PluginLoader {

    private final Main plugin;
    private final LoggerUtil logger;
    private final PluginRegistry registry;

    public PluginLoader(Main plugin, LoggerUtil logger, PluginRegistry registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.registry = registry;
    }

    /**
     * Пытается загрузить плагин по пути к jar.
     *
     * @param jarPath путь к jar
     * @return true если успех
     */
    public boolean load(Path jarPath) {
        File jarFile = jarPath.toFile();
        if (!jarFile.exists() || !jarFile.getName().endsWith(".jar")) {
            logger.warn("Cannot load non-existing or non-jar file: " + jarFile.getAbsolutePath());
            return false;
        }

        try {
            PluginDescriptionFile description = plugin.getPluginLoader().getPluginDescription(jarFile);
            String pluginName = description.getName();

            if (Bukkit.getPluginManager().getPlugin(pluginName) != null) {
                logger.warn("Plugin already loaded: " + pluginName);
                registry.markLoaded(pluginName, jarPath);
                return true;
            }

            org.bukkit.plugin.Plugin loaded = Bukkit.getPluginManager().loadPlugin(jarFile);
            if (loaded == null) {
                registry.markError(pluginName, jarPath, "PluginManager returned null");
                logger.error("Failed to load plugin: " + pluginName + " (null plugin)");
                return false;
            }

            Bukkit.getPluginManager().enablePlugin(loaded);
            forceCommandSync();

            registry.markLoaded(pluginName, jarPath);
            logger.info("Plugin loaded: " + pluginName);
            logger.info("Plugin loaded");
            return true;
        } catch (Throwable throwable) {
            String name = jarFile.getName().replace(".jar", "");
            registry.markError(name, jarPath, throwable.getMessage());
            logger.error("Failed to load plugin from " + jarFile.getName(), throwable);
            return false;
        }
    }

    /**
     * Загружает по имени плагина через поиск jar в папке plugins.
     */
    public boolean loadByName(String name) {
        Path path = plugin.resolvePluginJar(name);
        if (path == null) {
            logger.warn("Plugin jar not found for name: " + name);
            return false;
        }
        return load(path);
    }

    /**
     * Загружает все pending плагины.
     */
    public int loadPendingPlugins() {
        List<PluginRegistry.PluginRecord> pending = new ArrayList<>(registry.pending());
        int loaded = 0;
        for (PluginRegistry.PluginRecord record : pending) {
            if (load(record.getJarPath())) {
                loaded++;
            }
        }
        return loaded;
    }

    /**
     * Рефлексия для синхронизации команд после hot-load.
     */
    @SuppressWarnings("unchecked")
    private void forceCommandSync() {
        try {
            Method sync = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
            sync.setAccessible(true);
            sync.invoke(Bukkit.getServer());
        } catch (Throwable ignored) {
            // fallback below
        }

        try {
            if (!(Bukkit.getPluginManager() instanceof SimplePluginManager simplePluginManager)) {
                return;
            }
            Field commandMapField = SimplePluginManager.class.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            Object commandMap = commandMapField.get(simplePluginManager);
            if (commandMap == null) {
                return;
            }

            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            if (knownCommands == null) {
                return;
            }

            List<String> aliasesToFix = new ArrayList<>();
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                if (entry.getValue() instanceof PluginCommand pluginCommand) {
                    Plugin owner = pluginCommand.getPlugin();
                    if (owner != null && owner.isEnabled()) {
                        aliasesToFix.add(entry.getKey());
                    }
                }
            }

            for (String alias : aliasesToFix) {
                Command command = knownCommands.get(alias);
                if (command == null) {
                    continue;
                }
                knownCommands.put(alias.toLowerCase(Locale.ROOT), command);
            }
        } catch (Throwable throwable) {
            logger.warn("Could not fully sync commands after load: " + throwable.getMessage());
        }
    }
}
