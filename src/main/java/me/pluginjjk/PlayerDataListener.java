package me.pluginjjk;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerDataListener implements Listener {
    private final ProgressManager progressManager;

    public PlayerDataListener(ProgressManager progressManager) {
        this.progressManager = progressManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        progressManager.get(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        progressManager.unload(event.getPlayer());
    }
}
