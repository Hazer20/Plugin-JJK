package me.hazer.hotplug;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Главный класс плагина HazerHotPlug.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>инициализацию модулей;</li>
 *   <li>регистрацию команд и listener-ов;</li>
 *   <li>обработку datapack reload команд для авто-загрузки pending-плагинов.</li>
 * </ul>
 */
public final class Main extends JavaPlugin implements Listener {

    private LoggerUtil loggerUtil;
    private ConfigManager configManager;
    private PluginRegistry pluginRegistry;
    private PluginWatcher pluginWatcher;
    private PluginLoader pluginLoader;
    private PluginUnloader pluginUnloader;
    private GUIManager guiManager;
    private HotPluginCommand hotPluginCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        loggerUtil = new LoggerUtil(getLogger());
        configManager = new ConfigManager(this);
        pluginRegistry = new PluginRegistry();

        pluginLoader = new PluginLoader(this, loggerUtil, pluginRegistry);
        pluginUnloader = new PluginUnloader(this, loggerUtil, pluginRegistry);
        pluginWatcher = new PluginWatcher(this, loggerUtil, configManager, pluginRegistry);

        guiManager = new GUIManager(this, configManager, pluginRegistry, pluginLoader, pluginUnloader, loggerUtil);
        guiManager.register();

        hotPluginCommand = new HotPluginCommand(this, pluginWatcher, pluginLoader, pluginUnloader, pluginRegistry, guiManager);
        new CommandManager(this, loggerUtil).register(hotPluginCommand);

        Bukkit.getPluginManager().registerEvents(this, this);

        pluginRegistry.syncLoadedFromServer();
        if (configManager.isWatchPluginsFolder()) {
            pluginWatcher.start();
        }

        loggerUtil.info("HazerHotPlug enabled.");
    }

    @Override
    public void onDisable() {
        if (pluginWatcher != null) {
            pluginWatcher.stop();
        }
        if (guiManager != null) {
            guiManager.unregister();
        }
        loggerUtil.info("HazerHotPlug disabled.");
    }

    /**
     * Перехват команд игрока для datapack reload интеграции.
     */
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase(Locale.ROOT).trim();
        if (isDatapackReloadCommand(msg)) {
            runAutoLoadPipeline(event.getPlayer());
        }
    }

    /**
     * Перехват консольных команд для datapack reload интеграции.
     */
    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String cmd = event.getCommand().toLowerCase(Locale.ROOT).trim();
        if (isDatapackReloadCommand(cmd)) {
            runAutoLoadPipeline(event.getSender());
        }
    }

    /**
     * Проверка команд reload/datapack.
     */
    private boolean isDatapackReloadCommand(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        return normalized.equals("reload confirm")
                || normalized.equals("minecraft:reload")
                || normalized.equals("reload")
                || normalized.equals("minecraft:reload confirm");
    }

    /**
     * Pipeline автозагрузки при reload datapacks.
     */
    private void runAutoLoadPipeline(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                int found = pluginWatcher.scanNow();
                int loaded = 0;
                if (configManager.isAutoLoad()) {
                    loaded = pluginLoader.loadPendingPlugins();
                }
                loggerUtil.info("Datapack reload hook processed. Found=" + found + ", loaded=" + loaded);

                int finalLoaded = loaded;
                Bukkit.getScheduler().runTask(this, () ->
                        sender.sendMessage("§6[HazerHotPlug] §7Datapack reload scan: found=" + found + ", loaded=" + finalLoaded));
            } catch (Throwable throwable) {
                loggerUtil.error("Error in datapack reload hook", throwable);
            }
        });
    }

    /**
     * Папка plugins сервера.
     */
    public Path getPluginsDirectory() {
        File plugins = getDataFolder().getParentFile();
        if (plugins == null) {
            plugins = new File("plugins");
        }
        return plugins.toPath();
    }

    /**
     * Поиск jar по имени плагина.
     */
    public Path resolvePluginJar(String pluginName) {
        File[] jars = getPluginsDirectory().toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return null;
        }

        String lower = pluginName.toLowerCase(Locale.ROOT);

        for (File jar : jars) {
            String fileName = jar.getName().toLowerCase(Locale.ROOT);
            if (fileName.equals(lower + ".jar") || fileName.contains(lower)) {
                return jar.toPath();
            }

            String parsedName = parseNameFromJar(jar.toPath());
            if (parsedName != null && parsedName.equalsIgnoreCase(pluginName)) {
                return jar.toPath();
            }
        }

        return null;
    }

    /**
     * Возвращает путь к jar или fallback путь.
     */
    public Path resolvePluginJarOrFallback(String pluginName) {
        Path found = resolvePluginJar(pluginName);
        if (found != null) {
            return found;
        }
        return getPluginsDirectory().resolve(pluginName + ".jar");
    }

    /**
     * Парсер имени из plugin.yml в jar (делегат watcher-логики).
     */
    private String parseNameFromJar(Path jarPath) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
            java.util.zip.ZipEntry entry = jar.getEntry("plugin.yml");
            if (entry == null) {
                return null;
            }
            try (var in = jar.getInputStream(entry)) {
                for (String line : new java.io.BufferedReader(new java.io.InputStreamReader(in)).lines().toList()) {
                    String trimmed = line.trim();
                    if (trimmed.toLowerCase(Locale.ROOT).startsWith("name:")) {
                        return trimmed.substring("name:".length()).trim();
                    }
                }
            }
        } catch (Throwable ignored) {
            // silently ignore; loader will report explicit errors if needed
        }
        return null;
    }
}
