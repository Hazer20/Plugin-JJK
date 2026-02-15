package me.pluginjjk;

import java.util.EnumMap;
import java.util.Map;

public class PlayerProgress {
    private double cursedEnergy;
    private double maxCursedEnergy;
    private final Map<Technique, Integer> unlockProgress;
    private final Map<Technique, Integer> level;
    private final Map<Technique, Integer> xp;
    private final Map<InputCombo, Technique> bindings;

    public PlayerProgress() {
        this.cursedEnergy = 100.0;
        this.maxCursedEnergy = 100.0;
        this.unlockProgress = new EnumMap<>(Technique.class);
        this.level = new EnumMap<>(Technique.class);
        this.xp = new EnumMap<>(Technique.class);
        this.bindings = new EnumMap<>(InputCombo.class);
    }

    public double cursedEnergy() {
        return cursedEnergy;
    }

    public void setEnergy(double value) {
        cursedEnergy = Math.max(0.0, Math.min(maxCursedEnergy, value));
    }

    public double maxCursedEnergy() {
        return maxCursedEnergy;
    }

    public void setMaxCursedEnergy(double value) {
        this.maxCursedEnergy = Math.max(50.0, value);
        if (this.cursedEnergy > this.maxCursedEnergy) {
            this.cursedEnergy = this.maxCursedEnergy;
        }
    }

    public void addEnergy(double amount) {
        this.cursedEnergy = Math.min(maxCursedEnergy, cursedEnergy + Math.max(0, amount));
    }

    public boolean consumeEnergy(double amount) {
        if (cursedEnergy < amount) {
            return false;
        }
        cursedEnergy -= amount;
        return true;
    }

    public int unlockProgress(Technique technique) {
        return unlockProgress.getOrDefault(technique, 0);
    }

    public void setUnlockProgress(Technique technique, int amount) {
        unlockProgress.put(technique, Math.max(0, amount));
    }

    public void addUnlockProgress(Technique technique, int amount) {
        unlockProgress.put(technique, unlockProgress(technique) + Math.max(0, amount));
    }

    public boolean unlocked(Technique technique) {
        return level(technique) > 0;
    }

    public int level(Technique technique) {
        return level.getOrDefault(technique, 0);
    }

    public void setLevel(Technique technique, int value) {
        level.put(technique, Math.max(0, value));
    }

    public void unlock(Technique technique) {
        level.put(technique, Math.max(1, level(technique)));
        xp.putIfAbsent(technique, 0);
    }

    public int xp(Technique technique) {
        return xp.getOrDefault(technique, 0);
    }

    public void setXp(Technique technique, int value) {
        xp.put(technique, Math.max(0, value));
    }

    public void addXp(Technique technique, int amount) {
        xp.put(technique, xp(technique) + Math.max(0, amount));
    }

    public boolean tryLevelUp(Technique technique) {
        int current = level(technique);
        if (current <= 0 || current >= technique.maxLevel()) {
            return false;
        }

        int need = xpNeedForNext(current);
        if (xp(technique) < need) {
            return false;
        }

        xp.put(technique, xp(technique) - need);
        level.put(technique, current + 1);
        return true;
    }

    public int xpNeedForNext(int currentLevel) {
        return 40 + (currentLevel * 35);
    }


    public void setBinding(InputCombo combo, Technique technique) {
        if (technique == null) {
            bindings.remove(combo);
            return;
        }
        bindings.put(combo, technique);
    }

    public Technique binding(InputCombo combo) {
        return bindings.get(combo);
    }

    public Map<InputCombo, Technique> bindings() {
        return bindings;
    }
    public Map<Technique, Integer> levelMap() {
        return level;
    }
}
