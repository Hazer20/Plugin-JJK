package me.hazer.hotplug;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр состояний плагинов для hot-load логики.
 *
 * <p>Содержит независимое представление статусов, чтобы GUI, команды
 * и watcher работали через один источник правды.</p>
 */
public final class PluginRegistry {

    /**
     * Внутренний статус файла плагина.
     */
    public enum PluginState {
        LOADED,
        PENDING,
        DISABLED,
        MISSING,
        ERROR
    }

    /**
     * Снимок состояния конкретного плагина.
     */
    public static final class PluginRecord {
        private final String pluginName;
        private final Path jarPath;
        private PluginState state;
        private long lastModified;
        private String reason;

        public PluginRecord(String pluginName, Path jarPath, PluginState state, long lastModified, String reason) {
            this.pluginName = pluginName;
            this.jarPath = jarPath;
            this.state = state;
            this.lastModified = lastModified;
            this.reason = reason;
        }

        public String getPluginName() {
            return pluginName;
        }

        public Path getJarPath() {
            return jarPath;
        }

        public PluginState getState() {
            return state;
        }

        public void setState(PluginState state) {
            this.state = state;
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    private final Map<String, PluginRecord> records = new ConcurrentHashMap<>();

    /**
     * Нормализует имя для использования в Map.
     */
    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Возвращает lastModified для файла или 0 при ошибке.
     */
    private long safeLastModified(Path path) {
        File file = path.toFile();
        return file.exists() ? file.lastModified() : 0L;
    }

    /**
     * Регистрирует найденный jar как pending.
     */
    public synchronized void markPending(String pluginName, Path jarPath, String reason) {
        String key = normalize(pluginName);
        PluginRecord record = records.get(key);
        long modified = safeLastModified(jarPath);

        if (record == null) {
            record = new PluginRecord(pluginName, jarPath, PluginState.PENDING, modified, reason);
            records.put(key, record);
            return;
        }

        record.setState(PluginState.PENDING);
        record.setLastModified(modified);
        record.setReason(reason);
    }

    /**
     * Помечает плагин как загруженный.
     */
    public synchronized void markLoaded(String pluginName, Path jarPath) {
        String key = normalize(pluginName);
        PluginRecord record = records.get(key);
        long modified = safeLastModified(jarPath);

        if (record == null) {
            record = new PluginRecord(pluginName, jarPath, PluginState.LOADED, modified, "Loaded successfully");
            records.put(key, record);
            return;
        }

        record.setState(PluginState.LOADED);
        record.setLastModified(modified);
        record.setReason("Loaded successfully");
    }

    /**
     * Помечает плагин как выгруженный.
     */
    public synchronized void markDisabled(String pluginName, String reason) {
        String key = normalize(pluginName);
        PluginRecord record = records.get(key);

        if (record == null) {
            record = new PluginRecord(pluginName, Path.of(pluginName + ".jar"), PluginState.DISABLED, 0L, reason);
            records.put(key, record);
            return;
        }

        record.setState(PluginState.DISABLED);
        record.setReason(reason);
    }

    /**
     * Помечает запись как ошибочную.
     */
    public synchronized void markError(String pluginName, Path jarPath, String reason) {
        String key = normalize(pluginName);
        PluginRecord record = records.get(key);
        long modified = safeLastModified(jarPath);

        if (record == null) {
            record = new PluginRecord(pluginName, jarPath, PluginState.ERROR, modified, reason);
            records.put(key, record);
            return;
        }

        record.setState(PluginState.ERROR);
        record.setLastModified(modified);
        record.setReason(reason);
    }

    /**
     * Помечает удаленный файл как missing.
     */
    public synchronized void markMissing(String pluginName) {
        String key = normalize(pluginName);
        PluginRecord record = records.get(key);

        if (record == null) {
            record = new PluginRecord(pluginName, Path.of(pluginName + ".jar"), PluginState.MISSING, 0L, "File removed");
            records.put(key, record);
            return;
        }

        record.setState(PluginState.MISSING);
        record.setReason("File removed");
    }

    /**
     * Удаляет запись полностью.
     */
    public synchronized void remove(String pluginName) {
        records.remove(normalize(pluginName));
    }

    /**
     * Получение записи по имени.
     */
    public synchronized Optional<PluginRecord> find(String pluginName) {
        return Optional.ofNullable(records.get(normalize(pluginName)));
    }

    /**
     * Возвращает снимок всех записей.
     */
    public synchronized List<PluginRecord> snapshot() {
        List<PluginRecord> list = new ArrayList<>(records.values());
        list.sort(Comparator.comparing(PluginRecord::getPluginName, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(list);
    }

    /**
     * Возвращает список pending-плагинов.
     */
    public synchronized List<PluginRecord> pending() {
        List<PluginRecord> list = new ArrayList<>();
        for (PluginRecord record : records.values()) {
            if (record.getState() == PluginState.PENDING) {
                list.add(record);
            }
        }
        list.sort(Comparator.comparing(PluginRecord::getPluginName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /**
     * Обновляет статусы для уже загруженных Bukkit-плагинов.
     */
    public synchronized void syncLoadedFromServer() {
        Collection<Plugin> loaded = List.of(Bukkit.getPluginManager().getPlugins());
        Set<String> loadedKeys = new HashSet<>();
        Map<String, Plugin> loadedByName = new HashMap<>();

        for (Plugin plugin : loaded) {
            String key = normalize(plugin.getName());
            loadedKeys.add(key);
            loadedByName.put(key, plugin);
        }

        for (Map.Entry<String, PluginRecord> entry : records.entrySet()) {
            PluginRecord rec = entry.getValue();
            if (loadedKeys.contains(entry.getKey())) {
                rec.setState(PluginState.LOADED);
                rec.setReason("Present in PluginManager");
            } else if (rec.getState() == PluginState.LOADED) {
                rec.setState(PluginState.DISABLED);
                rec.setReason("Not present in PluginManager");
            }
        }

        for (Map.Entry<String, Plugin> entry : loadedByName.entrySet()) {
            String normalized = entry.getKey();
            if (!records.containsKey(normalized)) {
                Plugin plugin = entry.getValue();
                Path fallback = Path.of("plugins", plugin.getName() + ".jar");
                PluginRecord record = new PluginRecord(
                        plugin.getName(),
                        fallback,
                        PluginState.LOADED,
                        0L,
                        "Detected from running server"
                );
                records.put(normalized, record);
            }
        }
    }

    /**
     * Возвращает список имен плагинов с указанным состоянием.
     */
    public synchronized List<String> namesByState(PluginState state) {
        List<String> names = new ArrayList<>();
        for (PluginRecord record : records.values()) {
            if (record.getState() == state) {
                names.add(record.getPluginName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
