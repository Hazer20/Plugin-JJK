package me.pluginjjk;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class JjkCommand implements CommandExecutor, TabCompleter, Listener {
    private final ProgressManager progressManager;
    private final Random random = new Random();
    private final Map<UUID, Long> inputCooldown = new HashMap<>();

    public JjkCommand(ProgressManager progressManager) {
        this.progressManager = progressManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько игрок может использовать эту команду.");
            return true;
        }

        PlayerProgress progress = progressManager.get(player);

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(player);
            case "stats" -> showStats(player, progress);
            case "list" -> listTechniques(player, progress);
            case "train" -> handleTrain(player, progress, args);
            case "use" -> handleUse(player, progress, args);
            case "bind" -> handleBind(player, progress, args);
            case "unbind" -> handleUnbind(player, progress, args);
            case "binds" -> showBinds(player, progress);
            case "gojo", "sukuna", "fushiguro" -> handleLegacyUse(player, progress, args);
            default -> sendHelp(player);
        }

        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        InputCombo combo = switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> event.getPlayer().isSneaking() ? InputCombo.SHIFT_LEFT_CLICK : InputCombo.LEFT_CLICK;
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> event.getPlayer().isSneaking() ? InputCombo.SHIFT_RIGHT_CLICK : InputCombo.RIGHT_CLICK;
            default -> null;
        };

        if (combo == null) {
            return;
        }

        if (triggerBoundTechnique(event.getPlayer(), combo)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (triggerBoundTechnique(event.getPlayer(), InputCombo.SWAP_HANDS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (triggerBoundTechnique(event.getPlayer(), InputCombo.DROP_KEY)) {
            event.setCancelled(true);
        }
    }

    private boolean triggerBoundTechnique(Player player, InputCombo combo) {
        PlayerProgress progress = progressManager.get(player);
        Technique technique = progress.binding(combo);
        if (technique == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long prev = inputCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - prev < 300) {
            return true;
        }
        inputCooldown.put(player.getUniqueId(), now);

        useTechnique(player, progress, technique);
        return true;
    }

    private void handleBind(Player player, PlayerProgress progress, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§eИспользование: /jjk bind <combo> <technique>");
            player.sendMessage("§7Комбо: left_click, shift_left_click, right_click, shift_right_click, swap_hands, drop_key");
            return;
        }

        InputCombo combo = InputCombo.fromInput(args[1]).orElse(null);
        if (combo == null) {
            player.sendMessage("§cНеизвестное комбо.");
            return;
        }

        Technique technique = Technique.fromInput(args[2]).orElse(null);
        if (technique == null) {
            player.sendMessage("§cНеизвестная техника.");
            return;
        }

        if (!progress.unlocked(technique)) {
            player.sendMessage("§cНельзя привязать закрытую технику. Сначала открой её.");
            return;
        }

        progress.setBinding(combo, technique);
        player.sendMessage("§aПривязано: §f" + combo.display() + " §7-> §d" + technique.display());
    }

    private void handleUnbind(Player player, PlayerProgress progress, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eИспользование: /jjk unbind <combo>");
            return;
        }

        InputCombo combo = InputCombo.fromInput(args[1]).orElse(null);
        if (combo == null) {
            player.sendMessage("§cНеизвестное комбо.");
            return;
        }

        progress.setBinding(combo, null);
        player.sendMessage("§aБинд удален для: §f" + combo.display());
    }

    private void showBinds(Player player, PlayerProgress progress) {
        player.sendMessage("§6=== Бинды техник ===");
        for (InputCombo combo : InputCombo.values()) {
            Technique technique = progress.binding(combo);
            if (technique == null) {
                player.sendMessage("§7- " + combo.display() + " §8[пусто]");
            } else {
                player.sendMessage("§7- " + combo.display() + " §f-> §d" + technique.display());
            }
        }
    }

    private void handleTrain(Player player, PlayerProgress progress, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eИспользование: /jjk train <technique>");
            return;
        }

        Technique technique = Technique.fromInput(args[1]).orElse(null);
        if (technique == null) {
            player.sendMessage("§cНеизвестная техника.");
            return;
        }

        double trainingCost = Math.max(10, technique.baseEnergyCost() * 0.45);
        if (!progress.consumeEnergy(trainingCost)) {
            player.sendMessage("§cНедостаточно проклятой энергии для тренировки. Нужно: " + (int) trainingCost);
            return;
        }

        if (!progress.unlocked(technique)) {
            int rank = progressManager.sorcererRank(progress);
            int gain = Math.max(4, 12 + rank - (technique.unlockDifficulty() / 10) + random.nextInt(10));
            progress.addUnlockProgress(technique, gain);

            int cur = progress.unlockProgress(technique);
            int need = technique.unlockDifficulty();

            player.sendMessage("§7Тренировка §f" + technique.display() + "§7: +" + gain + " прогресса (" + cur + "/" + need + ")");

            if (cur >= need) {
                progress.unlock(technique);
                progress.setXp(technique, 0);
                player.sendMessage("§aТы открыл технику: " + technique.display());
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
            return;
        }

        int level = progress.level(technique);
        if (level >= technique.maxLevel()) {
            player.sendMessage("§aТехника уже максимального уровня.");
            return;
        }

        int xpGain = 16 + random.nextInt(14);
        progress.addXp(technique, xpGain);
        int haveXp = progress.xp(technique);
        int need = progress.xpNeedForNext(level);

        player.sendMessage("§bПрокачка " + technique.display() + "§7: +" + xpGain + " XP (" + haveXp + "/" + need + ")");

        if (progress.tryLevelUp(technique)) {
            int newLevel = progress.level(technique);
            player.sendMessage("§dУровень техники повышен: " + technique.display() + " §f-> §6" + newLevel);
            progress.setMaxCursedEnergy(100 + progressManager.sorcererRank(progress) * 6);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private void handleUse(Player player, PlayerProgress progress, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eИспользование: /jjk use <technique>");
            return;
        }

        Technique technique = Technique.fromInput(args[1]).orElse(null);
        if (technique == null) {
            player.sendMessage("§cНеизвестная техника.");
            return;
        }

        useTechnique(player, progress, technique);
    }

    private void handleLegacyUse(Player player, PlayerProgress progress, String[] args) {
        if (args.length < 2) {
            sendHelp(player);
            return;
        }

        Technique technique = Technique.fromInput(args[1]).orElse(null);
        if (technique == null) {
            player.sendMessage("§cНеизвестная техника.");
            return;
        }

        useTechnique(player, progress, technique);
    }

    private void useTechnique(Player player, PlayerProgress progress, Technique technique) {
        if (!progress.unlocked(technique)) {
            player.sendMessage("§cТехника еще не открыта. Тренируйся: /jjk train " + technique.id());
            return;
        }

        int level = progress.level(technique);
        double actualCost = Math.max(8.0, technique.baseEnergyCost() - ((level - 1) * 1.3));

        if (!progress.consumeEnergy(actualCost)) {
            player.sendMessage("§cНедостаточно проклятой энергии. Нужно: " + (int) actualCost);
            return;
        }

        switch (technique) {
            case GOJO_BLUE -> castBlue(player, level);
            case GOJO_RED -> castRed(player, level);
            case GOJO_PURPLE -> castPurple(player, level);
            case SUKUNA_SLASH -> castSlash(player, level);
            case FUSHIGURO_TEN_SHADOWS -> castTenShadows(player, level);
        }
    }

    private void castBlue(Player player, int level) {
        World world = player.getWorld();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(4));
        double radius = 5.5 + level * 0.45;

        world.spawnParticle(Particle.PORTAL, center, 120 + level * 15, 0.9, 0.9, 0.9, 0.3);
        world.spawnParticle(Particle.DUST, center, 80 + level * 8, 0.8, 0.8, 0.8, 0.01,
                new Particle.DustOptions(Color.fromRGB(80, 140, 255), 2.2f));

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                Vector pull = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.8 + level * 0.08);
                target.setVelocity(target.getVelocity().add(pull));
                target.damage(3.5 + level * 0.8, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + level * 8, 0));
            }
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40 + level * 12, 0));
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.1f, 0.65f);
        player.sendMessage("§9Годжо: Синий §7[Ур." + level + "]");
    }

    private void castRed(Player player, int level) {
        World world = player.getWorld();
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();

        for (int i = 1; i <= 10 + level; i++) {
            Location point = origin.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.FLAME, point, 8 + level, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.DUST, point, 5 + level, 0.15, 0.15, 0.15, 0.01,
                    new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.6f));
        }

        Location blast = origin.clone().add(direction.multiply(9 + level));
        world.createExplosion(blast, 1.8f + (level * 0.2f), false, false, player);

        for (Entity entity : world.getNearbyEntities(blast, 4 + level * 0.4, 4 + level * 0.4, 4 + level * 0.4)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                Vector knockback = target.getLocation().toVector().subtract(blast.toVector()).normalize().multiply(0.8 + level * 0.12);
                target.setVelocity(target.getVelocity().add(knockback));
                target.damage(7.0 + level * 1.1, player);
                target.setFireTicks(40 + level * 12);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
            }
        }

        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.15f);
        player.sendMessage("§cГоджо: Красный §7[Ур." + level + "]");
    }

    private void castPurple(Player player, int level) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        for (int i = 1; i <= 16 + level * 2; i++) {
            Location point = eye.clone().add(direction.clone().multiply(i));

            world.spawnParticle(Particle.DUST, point, 12 + level, 0.25, 0.25, 0.25, 0.01,
                    new Particle.DustOptions(Color.fromRGB(160, 70, 255), 1.8f));
            world.spawnParticle(Particle.END_ROD, point, 2, 0.05, 0.05, 0.05, 0.01);

            for (Entity entity : world.getNearbyEntities(point, 1.4, 1.4, 1.4)) {
                if (entity instanceof LivingEntity target && !target.equals(player)) {
                    target.damage(11.0 + level * 1.5, player);
                    Vector kb = direction.clone().multiply(1.0 + level * 0.07);
                    target.setVelocity(target.getVelocity().add(kb));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30 + level * 5, 0));
                }
            }
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40 + level * 10, 0));
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.6f);
        player.sendMessage("§5Годжо: Фиолетовый §7[Ур." + level + "]");
    }

    private void castSlash(Player player, int level) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        int length = 12 + level;

        for (int i = 1; i <= length; i++) {
            Location point = eye.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.CRIT, point, 3 + level / 2, 0.05, 0.05, 0.05, 0.02);
        }

        damageInFront(player, length, 1.4 + level * 0.05, 8.0 + level * 1.2);
        for (Entity entity : world.getNearbyEntities(player.getLocation(), length, 4, length)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40 + level * 8, 0));
            }
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40 + level * 6, 0));
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        player.sendMessage("§4Сукуна: Разрез §7[Ур." + level + "]");
    }

    private void castTenShadows(Player player, int level) {
        World world = player.getWorld();
        Location center = player.getLocation();

        int summons = 8 + Math.min(level, 2);
        for (int i = 0; i < summons; i++) {
            double angle = (Math.PI * 2 / summons) * i;
            double x = Math.cos(angle) * (2.2 + level * 0.08);
            double z = Math.sin(angle) * (2.2 + level * 0.08);
            Location shadow = center.clone().add(x, 0.1, z);

            world.spawnParticle(Particle.SMOKE, shadow, 16 + level * 3, 0.25, 0.4, 0.25, 0.01);
            world.spawnParticle(Particle.SOUL, shadow, 6 + level, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.DUST, shadow, 8 + level, 0.25, 0.1, 0.25, 0.01,
                    new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.4f));
        }

        for (Entity entity : world.getNearbyEntities(center, 5 + level * 0.3, 3, 5 + level * 0.3)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                target.damage(5.0 + level, player);
                Vector pull = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.35 + level * 0.04);
                target.setVelocity(target.getVelocity().add(pull));
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60 + level * 8, 0));
            }
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50 + level * 12, 0));
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.8f);
        player.sendMessage("§8Фушигуро: Техника 10 Теней §7[Ур." + level + "]");
    }

    private void damageInFront(Player player, double length, double width, double damage) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();

        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), length, 4, length)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) {
                continue;
            }

            Vector toTarget = target.getEyeLocation().toVector().subtract(origin.toVector());
            double forwardDistance = toTarget.dot(direction);
            if (forwardDistance < 0 || forwardDistance > length) {
                continue;
            }

            Vector closest = origin.toVector().add(direction.clone().multiply(forwardDistance));
            double sideDistance = target.getEyeLocation().toVector().distance(closest);
            if (sideDistance <= width) {
                target.damage(damage, player);
            }
        }
    }

    private void showStats(Player player, PlayerProgress progress) {
        int rank = progressManager.sorcererRank(progress);
        player.sendMessage("§6=== Проклятая энергия ===");
        player.sendMessage("§fЭнергия: §b" + (int) progress.cursedEnergy() + "§7/§b" + (int) progress.maxCursedEnergy());
        player.sendMessage("§fРанг колдуна: §d" + rank);
        for (Technique technique : Technique.values()) {
            int level = progress.level(technique);
            if (level > 0) {
                int need = progress.xpNeedForNext(level);
                player.sendMessage("§a- " + technique.display() + " §7ур." + level + " §8XP: " + progress.xp(technique) + "/" + need);
            } else {
                player.sendMessage("§7- " + technique.display() + " §8(закрыта, прогресс: "
                        + progress.unlockProgress(technique) + "/" + technique.unlockDifficulty() + ")");
            }
        }
    }

    private void listTechniques(Player player, PlayerProgress progress) {
        player.sendMessage("§6=== Техники ===");
        for (Technique technique : Technique.values()) {
            String state = progress.unlocked(technique)
                    ? "§aОткрыта (ур." + progress.level(technique) + ")"
                    : "§cЗакрыта";
            player.sendMessage("§f" + technique.id() + " §7- " + technique.display() + " §8[" + state + "§8]");
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6/JJK команды:");
        player.sendMessage("§e/jjk help §7- помощь");
        player.sendMessage("§e/jjk list §7- список техник");
        player.sendMessage("§e/jjk stats §7- энергия и уровни");
        player.sendMessage("§e/jjk train <technique> §7- тренировать/открывать/качать технику");
        player.sendMessage("§e/jjk use <technique> §7- использовать технику");
        player.sendMessage("§e/jjk bind <combo> <technique> §7- привязать технику к вводу");
        player.sendMessage("§e/jjk unbind <combo> §7- убрать привязку");
        player.sendMessage("§e/jjk binds §7- показать бинды");
        player.sendMessage("§7Комбо: left_click, shift_left_click, right_click, shift_right_click, swap_hands(F), drop_key(Q)");
        player.sendMessage("§7Совместимость: /jjk <gojo|sukuna|fushiguro> <technique>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("help", "list", "stats", "train", "use", "bind", "unbind", "binds", "gojo", "sukuna", "fushiguro"), args[0]);
        }
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if ("train".equals(first) || "use".equals(first)) {
                return filter(Arrays.asList("gojo_blue", "gojo_red", "gojo_purple", "sukuna_slash", "fushiguro_ten_shadows"), args[1]);
            }
            if ("bind".equals(first) || "unbind".equals(first)) {
                return filter(Arrays.asList("left_click", "shift_left_click", "right_click", "shift_right_click", "swap_hands", "drop_key"), args[1]);
            }
            if ("gojo".equals(first)) {
                return filter(Arrays.asList("blue", "red", "purple"), args[1]);
            }
            if ("sukuna".equals(first)) {
                return filter(List.of("slash"), args[1]);
            }
            if ("fushiguro".equals(first)) {
                return filter(Arrays.asList("tenshadows", "10shadows"), args[1]);
            }
        }
        if (args.length == 3 && "bind".equals(args[0].toLowerCase(Locale.ROOT))) {
            return filter(Arrays.asList("gojo_blue", "gojo_red", "gojo_purple", "sukuna_slash", "fushiguro_ten_shadows"), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String lc = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lc)) {
                result.add(option);
            }
        }
        return result;
    }
}
