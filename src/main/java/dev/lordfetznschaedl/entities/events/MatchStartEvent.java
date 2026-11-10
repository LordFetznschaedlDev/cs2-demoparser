package dev.lordfetznschaedl.entities.events;

/**
 * The match went live (warmup is over).
 */
public record MatchStartEvent(
        int tick,
        int round) implements GameEvent {
}
