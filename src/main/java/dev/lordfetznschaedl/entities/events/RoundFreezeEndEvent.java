package dev.lordfetznschaedl.entities.events;

/**
 * Freeze time ended, players can move.
 */
public record RoundFreezeEndEvent(
        int tick,
        int round) implements GameEvent {
}
