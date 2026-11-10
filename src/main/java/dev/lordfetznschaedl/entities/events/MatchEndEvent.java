package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Team;

/**
 * The match is over.
 */
public record MatchEndEvent(
        int tick,
        int round,
        Team winner,
        int terroristScore,
        int counterTerroristScore) implements GameEvent {
}
