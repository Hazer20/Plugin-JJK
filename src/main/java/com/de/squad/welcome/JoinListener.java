package com.de.squad.welcome;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class JoinListener implements Listener {

    private final Main plugin;

    private final List<String> defaultSubtitles = Arrays.asList(
            "Добро пожаловать!",
            "Ты попал в лав-белый хаос!",
            "Готов к приключениям?"
    );

    private final List<String> defaultJoinMessages = Arrays.asList(
            "Игрок %player% только что телепортировался на DE Squad!",
            "Ого, %player% ворвался в игру как вихрь!",
            "BEHOLD %player% — легенда DE Squad появилась!",
            "%player% зашёл и поднял уровень стиля на максимум!"
    );

    private final List<String> defaultQuitMessages = Arrays.asList(
            "%player% ушёл в закат... но обещал вернуться!",
            "%player% вышел из игры. Сервер стал чуточку тише.",
            "%player% телепортировался в реальную жизнь!"
    );

    public JoinListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration config = plugin.getConfig();

        String title = colorize(config.getString("title", "§5DE §fSquad"));
        List<String> subtitles = config.getStringList("subtitles");
        if (subtitles.isEmpty()) {
            subtitles = defaultSubtitles;
        }

        String subtitle = colorize(pickRandom(subtitles));

        int fadeIn = config.getInt("title-settings.fade-in", 10);
        int stay = config.getInt("title-settings.stay", 60);
        int fadeOut = config.getInt("title-settings.fade-out", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        Sound sound = parseSound(config.getString("join-sound", "ENTITY_PLAYER_LEVELUP"));
        float volume = (float) config.getDouble("join-sound-volume", 1.0D);
        float pitch = (float) config.getDouble("join-sound-pitch", 1.0D);
        player.playSound(player.getLocation(), sound, volume, pitch);

        spawnWelcomeParticles(player, config);

        List<String> joinMessages = config.getStringList("join-messages");
        if (joinMessages.isEmpty()) {
            joinMessages = defaultJoinMessages;
        }

        event.setJoinMessage(colorize(pickRandom(joinMessages).replace("%player%", player.getName())));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        List<String> quitMessages = plugin.getConfig().getStringList("quit-messages");

        if (quitMessages.isEmpty()) {
            quitMessages = defaultQuitMessages;
        }

        String message = pickRandom(quitMessages).replace("%player%", player.getName());
        event.setQuitMessage(colorize(message));
    }

    private void spawnWelcomeParticles(Player player, FileConfiguration config) {
        String particleName = config.getString("join-particle", "HEART");
        Particle particle = parseParticle(particleName);

        int count = config.getInt("join-particle-count", 24);
        Location location = player.getLocation().add(0, 1, 0);

        player.getWorld().spawnParticle(
                particle,
                location,
                count,
                0.6,
                0.8,
                0.6,
                0.02
        );
    }

    private String pickRandom(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private Sound parseSound(String soundName) {
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown sound in config: " + soundName + ". Falling back to ENTITY_PLAYER_LEVELUP.");
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
    }

    private Particle parseParticle(String particleName) {
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown particle in config: " + particleName + ". Falling back to HEART.");
            return Particle.HEART;
        }
    }

    private String colorize(String text) {
        return text.replace('&', '§');
    }
}
