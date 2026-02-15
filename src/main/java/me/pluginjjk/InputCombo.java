package me.pluginjjk;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum InputCombo {
    LEFT_CLICK("left_click", "ЛКМ"),
    SHIFT_LEFT_CLICK("shift_left_click", "Shift + ЛКМ"),
    RIGHT_CLICK("right_click", "ПКМ"),
    SHIFT_RIGHT_CLICK("shift_right_click", "Shift + ПКМ"),
    SWAP_HANDS("swap_hands", "F (смена руки)"),
    DROP_KEY("drop_key", "Q (выброс)" );

    private final String id;
    private final String display;

    InputCombo(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public static Optional<InputCombo> fromInput(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        return switch (normalized) {
            case "lmb", "left", "left_click" -> Optional.of(LEFT_CLICK);
            case "shift_lmb", "shift_left", "shift_left_click" -> Optional.of(SHIFT_LEFT_CLICK);
            case "rmb", "right", "right_click" -> Optional.of(RIGHT_CLICK);
            case "shift_rmb", "shift_right", "shift_right_click" -> Optional.of(SHIFT_RIGHT_CLICK);
            case "f", "swap", "swap_hands" -> Optional.of(SWAP_HANDS);
            case "q", "drop", "drop_key" -> Optional.of(DROP_KEY);
            default -> Arrays.stream(values()).filter(v -> v.id.equals(normalized)).findFirst();
        };
    }
}
