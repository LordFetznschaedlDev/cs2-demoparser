package dev.lordfetznschaedl.entities;

import java.util.List;

/**
 * Final result of one of the two teams.
 *
 * @param side            side the team played on at the end of the match
 * @param name            clan name, or a default name for the side
 * @param score           total rounds won
 * @param scoreFirstHalf  rounds won in the first half
 * @param scoreSecondHalf rounds won in the second half
 * @param scoreOvertime   rounds won in overtime
 * @param players         players that were on the team at the end of the match
 */
public record TeamResult(
        Team side,
        String name,
        int score,
        int scoreFirstHalf,
        int scoreSecondHalf,
        int scoreOvertime,
        List<Player> players) {
}
