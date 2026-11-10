package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Team;
import dev.lordfetznschaedl.entities.RoundEndReason;

/**
 * A round was decided. Scores are the totals after this round.
 */
public record RoundEndEvent(
        int tick,
        int round,
        Team winner,
        RoundEndReason reason,
        int terroristScore,
        int counterTerroristScore) implements GameEvent {
}
