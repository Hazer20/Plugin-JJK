package me.pluginjjk;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProgressManager {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerProgress> cache = new HashMap<>();
    private YamlConfiguration storage;

    public ProgressManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать playerdata.yml: " + e.getMessage());
            }
        }

        this.storage = YamlConfiguration.loadConfiguration(file);
    }

    public PlayerProgress get(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), this::loadProgress);
    }

    public void save(Player player) {
        PlayerProgress progress = cache.get(player.getUniqueId());
        if (progress == null) {
            return;
        }
        writeProgress(player.getUniqueId(), progress);
        persist();
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerProgress> entry : cache.entrySet()) {
            writeProgress(entry.getKey(), entry.getValue());
        }
        persist();
    }

    public void unload(Player player) {
        save(player);
        cache.remove(player.getUniqueId());
    }

    private PlayerProgress loadProgress(UUID uuid) {
        PlayerProgress progress = new PlayerProgress();
        String root = "players." + uuid;
        progress.setMaxCursedEnergy(storage.getDouble(root + ".maxEnergy", 100.0));
        progress.setEnergy(storage.getDouble(root + ".energy", 100.0));

        for (Technique technique : Technique.values()) {
            int unlock = storage.getInt(root + ".unlock." + technique.id(), 0);
            int level = storage.getInt(root + ".level." + technique.id(), 0);
            int xp = storage.getInt(root + ".xp." + technique.id(), 0);

            progress.setUnlockProgress(technique, unlock);
            progress.setLevel(technique, level);
            progress.setXp(technique, xp);
        }

        for (InputCombo combo : InputCombo.values()) {
            String techniqueId = storage.getString(root + ".bindings." + combo.id());
            Technique.fromInput(techniqueId).ifPresent(technique -> progress.setBinding(combo, technique));
        }

        return progress;
    }

    private void writeProgress(UUID uuid, PlayerProgress progress) {
        String root = "players." + uuid;
        storage.set(root + ".energy", progress.cursedEnergy());
        storage.set(root + ".maxEnergy", progress.maxCursedEnergy());

        for (Technique technique : Technique.values()) {
            storage.set(root + ".unlock." + technique.id(), progress.unlockProgress(technique));
            storage.set(root + ".level." + technique.id(), progress.level(technique));
            storage.set(root + ".xp." + technique.id(), progress.xp(technique));
        }

        for (InputCombo combo : InputCombo.values()) {
            Technique bound = progress.binding(combo);
            storage.set(root + ".bindings." + combo.id(), bound == null ? null : bound.id());
        }
    }

    private void persist() {
        try {
            storage.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить playerdata.yml: " + e.getMessage());
        }
    }

    public void migrateOnline(java.util.Collection<? extends Player> online) {
        for (Player player : online) {
            get(player);
        }
    }

    public int sorcererRank(PlayerProgress progress) {
        int totalLevel = 0;
        int unlocked = 0;
        for (Technique technique : Technique.values()) {
            int level = progress.level(technique);
            totalLevel += level;
            if (level > 0) {
                unlocked++;
            }
        }
        return unlocked * 2 + (totalLevel / 3);
    }
}
