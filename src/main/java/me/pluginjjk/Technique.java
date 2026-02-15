package me.pluginjjk;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Technique {
    GOJO_BLUE("gojo_blue", "Годжо: Синий", "gojo", 28, 140, 5),
    GOJO_RED("gojo_red", "Годжо: Красный", "gojo", 36, 180, 6),
    GOJO_PURPLE("gojo_purple", "Годжо: Фиолетовый", "gojo", 55, 260, 9),
    SUKUNA_SLASH("sukuna_slash", "Сукуна: Разрез", "sukuna", 30, 150, 6),
    FUSHIGURO_TEN_SHADOWS("fushiguro_ten_shadows", "Фушигуро: 10 Теней", "fushiguro", 42, 220, 8);

    private final String id;
    private final String display;
    private final String owner;
    private final int baseEnergyCost;
    private final int unlockDifficulty;
    private final int maxLevel;

    Technique(String id, String display, String owner, int baseEnergyCost, int unlockDifficulty, int maxLevel) {
        this.id = id;
        this.display = display;
        this.owner = owner;
        this.baseEnergyCost = baseEnergyCost;
        this.unlockDifficulty = unlockDifficulty;
        this.maxLevel = maxLevel;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String owner() {
        return owner;
    }

    public int baseEnergyCost() {
        return baseEnergyCost;
    }

    public int unlockDifficulty() {
        return unlockDifficulty;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public static Optional<Technique> fromInput(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalized = input.toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if ("blue".equals(normalized) || "gojo_blue".equals(normalized)) {
            return Optional.of(GOJO_BLUE);
        }
        if ("red".equals(normalized) || "gojo_red".equals(normalized)) {
            return Optional.of(GOJO_RED);
        }
        if ("purple".equals(normalized) || "gojo_purple".equals(normalized)) {
            return Optional.of(GOJO_PURPLE);
        }
        if ("slash".equals(normalized) || "sukuna_slash".equals(normalized) || "cleave".equals(normalized)) {
            return Optional.of(SUKUNA_SLASH);
        }
        if ("tenshadows".equals(normalized) || "10shadows".equals(normalized)
                || "fushiguro_ten_shadows".equals(normalized) || "ten_shadows".equals(normalized)) {
            return Optional.of(FUSHIGURO_TEN_SHADOWS);
        }

        return Arrays.stream(values()).filter(v -> v.id.equals(normalized)).findFirst();
    }
}
