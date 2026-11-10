package dev.lordfetznschaedl.entities;

import java.util.List;

/**
 * One half of the match (regulation or overtime) with the players on each side.
 * <p>
 * Halves are derived from the rounds: a new half starts when the players swap sides, so this works
 * for any match format (MR12, MR15, overtime halves, wingman).
 *
 * @param number                1-based half number; halves after the second are overtime halves
 * @param firstRound            number of the first round of this half
 * @param lastRound             number of the last round of this half
 * @param terrorists            players that played on the T side in this half
 * @param counterTerrorists     players that played on the CT side in this half
 * @param terroristRoundsWon    rounds of this half won by the T side
 * @param counterTerroristRoundsWon rounds of this half won by the CT side
 */
public record MatchHalf(
        int number,
        int firstRound,
        int lastRound,
        List<Player> terrorists,
        List<Player> counterTerrorists,
        int terroristRoundsWon,
        int counterTerroristRoundsWon) {

    public boolean isOvertime() {
        return number > 2;
    }

    public int roundCount() {
        return lastRound - firstRound + 1;
    }

    public boolean containsRound(int round) {
        return round >= firstRound && round <= lastRound;
    }

    public List<Player> players(Team side) {
        return switch (side) {
            case TERRORIST -> terrorists;
            case COUNTER_TERRORIST -> counterTerrorists;
            default -> List.of();
        };
    }

    public int roundsWon(Team side) {
        return switch (side) {
            case TERRORIST -> terroristRoundsWon;
            case COUNTER_TERRORIST -> counterTerroristRoundsWon;
            default -> 0;
        };
    }

    /** The side the player was on in this half, or {@link Team#UNASSIGNED} if they did not play in it. */
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
