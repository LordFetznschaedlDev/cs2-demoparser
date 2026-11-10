package dev.lordfetznschaedl.entities;

/**
 * The side a player or team is on.
 */
public enum Team {
    UNASSIGNED(0),
    SPECTATOR(1),
    TERRORIST(2),
    COUNTER_TERRORIST(3);

    private final int number;

    Team(int number) {
        this.number = number;
    }

    /** The engine's team number (2 = T, 3 = CT). */
    public int number() {
        return number;
    }

    public boolean isPlaying() {
        return this == TERRORIST || this == COUNTER_TERRORIST;
    }

    public Team opponent() {
        return switch (this) {
            case TERRORIST -> COUNTER_TERRORIST;
            case COUNTER_TERRORIST -> TERRORIST;
            default -> this;
        };
    }

    public static Team fromNumber(int number) {
        return switch (number) {
            case 1 -> SPECTATOR;
            case 2 -> TERRORIST;
            case 3 -> COUNTER_TERRORIST;
            default -> UNASSIGNED;
        };
    }
}
