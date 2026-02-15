package me.pluginjjk;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class JjkTechniquesPlugin extends JavaPlugin {
    private ProgressManager progressManager;

    @Override
    public void onEnable() {
        progressManager = new ProgressManager(this);
        progressManager.load();
        progressManager.migrateOnline(getServer().getOnlinePlayers());

        JjkCommand command = new JjkCommand(progressManager);
        if (getCommand("jjk") != null) {
            getCommand("jjk").setExecutor(command);
            getCommand("jjk").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new PlayerDataListener(progressManager), this);
        getServer().getPluginManager().registerEvents(command, this);
        startEnergyRegenTask();

        getLogger().info("Plugin-JJK enabled.");
    }

    @Override
    public void onDisable() {
        if (progressManager != null) {
            progressManager.saveAll();
        }
        getLogger().info("Plugin-JJK disabled.");
    }

    private void startEnergyRegenTask() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                PlayerProgress progress = progressManager.get(player);
                int rank = progressManager.sorcererRank(progress);
                progress.setMaxCursedEnergy(100 + rank * 6);
                progress.addEnergy(2.8 + rank * 0.22);
            }
        }, 20L, 20L);

        getServer().getScheduler().runTaskTimer(this, progressManager::saveAll, 20L * 90, 20L * 90);
    }
}
