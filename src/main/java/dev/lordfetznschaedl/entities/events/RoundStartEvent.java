package dev.lordfetznschaedl.entities.events;

/**
 * A new round started (start of freeze time).
 */
public record RoundStartEvent(
        int tick,
        int round) implements GameEvent {
}
