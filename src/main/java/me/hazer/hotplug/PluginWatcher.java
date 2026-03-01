package me.hazer.hotplug;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * WatchService-обертка для папки plugins.
 *
 * <p>Класс выполняет две роли:
 * <ul>
 *   <li>Реагирует на события create/modify/delete через Java NIO WatchService.</li>
 *   <li>Периодически сканирует директорию, чтобы не пропустить события,
 *       если ОС/FS не отправил notification.</li>
 * </ul>
 * Это повышает надежность hot plugin workflow.</p>
 */
public final class PluginWatcher {

    private final Main plugin;
    private final LoggerUtil logger;
    private final ConfigManager config;
    private final PluginRegistry registry;

    private WatchService watchService;
    private WatchKey watchKey;
    private BukkitTask pollingTask;

    private final Map<String, Long> knownJars = new HashMap<>();

    public PluginWatcher(Main plugin, LoggerUtil logger, ConfigManager config, PluginRegistry registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.registry = registry;
    }

    /**
     * Инициализация watcher и периодического опроса.
     */
    public void start() {
        if (!config.isWatchPluginsFolder()) {
            logger.info("Plugin folder watcher disabled by config");
            return;
        }

        Path pluginsDir = plugin.getPluginsDirectory();
        try {
            if (!Files.exists(pluginsDir)) {
                Files.createDirectories(pluginsDir);
            }
            watchService = FileSystems.getDefault().newWatchService();
            watchKey = pluginsDir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );
        } catch (IOException ioException) {
            logger.error("Unable to initialize plugin watcher", ioException);
            return;
        }

        initialSnapshot();

        pollingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::poll,
                config.getWatcherPollIntervalTicks(),
                config.getWatcherPollIntervalTicks()
        );

        logger.info("Plugin watcher started for folder: " + pluginsDir.toAbsolutePath());
    }

    /**
     * Остановка watcher.
     */
    public void stop() {
        if (pollingTask != null) {
            pollingTask.cancel();
            pollingTask = null;
        }

        if (watchKey != null) {
            watchKey.cancel();
            watchKey = null;
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // ignore on shutdown
            }
            watchService = null;
        }
    }

    /**
     * Активное сканирование директории по команде /hotplugins scan.
     */
    public synchronized int scanNow() {
        Path pluginsDir = plugin.getPluginsDirectory();
        File[] files = pluginsDir.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null) {
            return 0;
        }

        int pendingAdded = 0;
        for (File file : files) {
            String key = file.getName().toLowerCase(Locale.ROOT);
            long modified = file.lastModified();
            Long old = knownJars.get(key);

            if (old == null) {
                knownJars.put(key, modified);
                if (handleJarDetected(file.toPath(), "new file")) {
                    pendingAdded++;
                }
            } else if (modified > old) {
                knownJars.put(key, modified);
                if (handleJarUpdated(file.toPath())) {
                    pendingAdded++;
                }
            }
        }

        knownJars.entrySet().removeIf(entry -> {
            File file = pluginsDir.resolve(entry.getKey()).toFile();
            if (!file.exists()) {
                handleJarDeleted(file.toPath());
                return true;
            }
            return false;
        });

        return pendingAdded;
    }

    /**
     * Проверяет очередь событий WatchService.
     */
    private synchronized void poll() {
        if (watchService == null) {
            return;
        }

        WatchKey key;
        while ((key = watchService.poll()) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path relPath = (Path) event.context();
                if (relPath == null) {
                    continue;
                }

                String fileName = relPath.getFileName().toString();
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }

                Path absolute = plugin.getPluginsDirectory().resolve(relPath);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    knownJars.put(fileName.toLowerCase(Locale.ROOT), absolute.toFile().lastModified());
                    handleJarDetected(absolute, "watcher create");
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    knownJars.put(fileName.toLowerCase(Locale.ROOT), absolute.toFile().lastModified());
                    handleJarUpdated(absolute);
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    knownJars.remove(fileName.toLowerCase(Locale.ROOT));
                    handleJarDeleted(absolute);
                }
            }
            key.reset();
        }

        scanNow();
    }

    /**
     * Первичный снимок файлов при старте.
     */
    private void initialSnapshot() {
        Path pluginsDir = plugin.getPluginsDirectory();
        File[] files = pluginsDir.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            knownJars.put(file.getName().toLowerCase(Locale.ROOT), file.lastModified());
            if (config.isScanOnStartup()) {
                handleJarDetected(file.toPath(), "startup scan");
            }
        }
    }

    /**
     * Обработка нового jar.
     */
    private boolean handleJarDetected(Path jarPath, String source) {
        String pluginName = readPluginNameFromJar(jarPath);
        if (pluginName == null) {
            return false;
        }

        if (pluginName.equalsIgnoreCase(plugin.getName())) {
            return false;
        }

        registry.markPending(pluginName, jarPath, "Detected by " + source);
        logger.info("Plugin detected: " + pluginName + " [" + source + "]");
        logger.info("Plugin detected");

        notifyAdmins(pluginName);
        return true;
    }

    /**
     * Обработка обновления jar.
     */
    private boolean handleJarUpdated(Path jarPath) {
        String pluginName = readPluginNameFromJar(jarPath);
        if (pluginName == null) {
            return false;
        }

        if (pluginName.equalsIgnoreCase(plugin.getName())) {
            return false;
        }

        registry.markPending(pluginName, jarPath, "File modified");
        logger.info("Plugin jar updated and set pending: " + pluginName);
        notifyAdmins(pluginName);
        return true;
    }

    /**
     * Обработка удаления jar.
     */
    private void handleJarDeleted(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        String guessed = fileName.endsWith(".jar") ? fileName.substring(0, fileName.length() - 4) : fileName;
        registry.markMissing(guessed);
        logger.warn("Plugin jar deleted: " + fileName);
    }

    /**
     * Чтение имени плагина из plugin.yml внутри jar.
     */
    private String readPluginNameFromJar(Path jarPath) {
        File file = jarPath.toFile();
        if (!file.exists()) {
            return null;
        }

        try (JarFile jar = new JarFile(file)) {
            ZipEntry entry = jar.getEntry("plugin.yml");
            if (entry == null) {
                return null;
            }

            try (var input = jar.getInputStream(entry)) {
                List<String> lines = new java.io.BufferedReader(new java.io.InputStreamReader(input)).lines().toList();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.toLowerCase(Locale.ROOT).startsWith("name:")) {
                        String name = trimmed.substring("name:".length()).trim();
                        if (!name.isEmpty()) {
                            return name;
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            logger.warn("Failed to inspect jar " + file.getName() + ": " + throwable.getMessage());
        }

        return null;
    }

    /**
     * Отправка уведомления администраторам.
     */
    private void notifyAdmins(String pluginName) {
        if (!config.isNotifyAdmins()) {
            return;
        }

        String message = "§e⚡ Новый плагин обнаружен: §6" + pluginName + "§e. Используйте §a/hotplugins reload §eдля загрузки.";
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("hazer.hotplug.notify")) {
                    player.sendMessage(message);
                }
            }
        });
    }
}
