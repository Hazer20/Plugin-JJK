package me.hazer.hotplug;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Менеджер конфигурации для простого доступа к настройкам.
 *
 * <p>Хранит ключевые флаги в типобезопасном виде и предоставляет
 * метод перезагрузки без пересоздания объекта.</p>
 */
public final class ConfigManager {

    private final Main plugin;

    private boolean autoLoad;
    private boolean watchPluginsFolder;
    private boolean notifyAdmins;
    private boolean enableGui;
    private boolean scanOnStartup;
    private long watcherPollIntervalTicks;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Повторно читает config.yml.
     */
    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        autoLoad = cfg.getBoolean("auto-load", true);
        watchPluginsFolder = cfg.getBoolean("watch-plugins-folder", true);
        notifyAdmins = cfg.getBoolean("notify-admins", true);
        enableGui = cfg.getBoolean("enable-gui", true);
        scanOnStartup = cfg.getBoolean("scan-on-startup", true);
        watcherPollIntervalTicks = Math.max(1L, cfg.getLong("watcher-poll-interval-ticks", 20L));
    }

    public boolean isAutoLoad() {
        return autoLoad;
    }

    public boolean isWatchPluginsFolder() {
        return watchPluginsFolder;
    }

    public boolean isNotifyAdmins() {
        return notifyAdmins;
    }

    public boolean isEnableGui() {
        return enableGui;
    }

    public boolean isScanOnStartup() {
        return scanOnStartup;
    }

    public long getWatcherPollIntervalTicks() {
        return watcherPollIntervalTicks;
    }
}
