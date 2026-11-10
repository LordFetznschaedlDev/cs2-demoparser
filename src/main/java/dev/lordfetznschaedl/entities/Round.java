package dev.lordfetznschaedl.entities;

import dev.lordfetznschaedl.entities.events.KillEvent;

import java.util.List;

/**
 * A round of the match.
 *
 * @param number                1-based round number
 * @param startTick             tick the round started (freeze time begins)
 * @param freezeEndTick         tick the freeze time ended, {@code -1} if unknown
 * @param endTick               tick the round was decided, {@code -1} if the demo ended before
 * @param winner                winning side, {@link Team#UNASSIGNED} if unfinished or a draw
 * @param reason                why the round ended
 * @param terroristScore        score of the team on the T side after this round
 * @param counterTerroristScore score of the team on the CT side after this round
 * @param kills                 all kills that happened in this round
 */
public record Round(
        int number,
        int startTick,
        int freezeEndTick,
        int endTick,
        Team winner,
        RoundEndReason reason,
        int terroristScore,
        int counterTerroristScore,
        List<KillEvent> kills) {

    public boolean isFinished() {
        return endTick >= 0;
    }
}
