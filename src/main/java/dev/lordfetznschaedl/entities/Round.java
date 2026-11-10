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
 * @param terrorists            players on the T side in this round
 * @param counterTerrorists     players on the CT side in this round
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
        List<KillEvent> kills,
        List<Player> terrorists,
        List<Player> counterTerrorists) {

    public boolean isFinished() {
        return endTick >= 0;
    }

    public List<Player> players(Team side) {
        return switch (side) {
            case TERRORIST -> terrorists;
            case COUNTER_TERRORIST -> counterTerrorists;
            default -> List.of();
        };
    }

    /** The side the player was on in this round, or {@link Team#UNASSIGNED} if they did not play in it. */
    public Team sideOf(Player player) {
        if (terrorists.stream().anyMatch(p -> p.id().equals(player.id()))) {
            return Team.TERRORIST;
        }
        if (counterTerrorists.stream().anyMatch(p -> p.id().equals(player.id()))) {
            return Team.COUNTER_TERRORIST;
        }
        return Team.UNASSIGNED;
    }
}
