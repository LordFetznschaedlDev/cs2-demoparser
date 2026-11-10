package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.entities.MatchHalf;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Round;
import dev.lordfetznschaedl.entities.RoundEndReason;
import dev.lordfetznschaedl.entities.Team;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundModuleHalvesTest {

    private static final Player A = new Player(1, "a", false);
    private static final Player B = new Player(2, "b", false);
    private static final Player C = new Player(3, "c", false);
    private static final Player D = new Player(4, "d", false);

    private static Round round(int number, List<Player> t, List<Player> ct, Team winner) {
        return new Round(number, 0, 0, 0, winner, RoundEndReason.UNKNOWN, 0, 0, List.of(), t, ct);
    }

    @Test
    void splitsAtSideSwapsIncludingOvertime() {
        List<Round> rounds = new ArrayList<>();
        int n = 1;
        for (int i = 0; i < 12; i++) {
            rounds.add(round(n++, List.of(A, B), List.of(C, D), Team.TERRORIST));
        }
        for (int i = 0; i < 12; i++) {
            rounds.add(round(n++, List.of(C, D), List.of(A, B), Team.COUNTER_TERRORIST));
        }
        for (int i = 0; i < 3; i++) {
            rounds.add(round(n++, List.of(A, B), List.of(C, D), Team.TERRORIST));
        }

        List<MatchHalf> halves = RoundModule.halves(rounds);

        assertEquals(3, halves.size());
        assertEquals(List.of(A, B), halves.get(0).terrorists());
        assertEquals(List.of(A, B), halves.get(1).counterTerrorists());
        assertEquals(12, halves.get(1).counterTerroristRoundsWon());
        assertFalse(halves.get(1).isOvertime());
        MatchHalf overtime = halves.get(2);
        assertTrue(overtime.isOvertime());
        assertEquals(25, overtime.firstRound());
        assertEquals(27, overtime.lastRound());
    }

    @Test
    void singleTeamChangeDoesNotStartNewHalf() {
        List<Round> rounds = List.of(
                round(1, List.of(A, B), List.of(C, D), Team.TERRORIST),
                round(2, List.of(A), List.of(B, C, D), Team.TERRORIST),
                round(3, List.of(A), List.of(B, C, D), Team.TERRORIST));

        List<MatchHalf> halves = RoundModule.halves(rounds);

        assertEquals(1, halves.size());
        assertEquals(List.of(A), halves.getFirst().terrorists());
        assertEquals(List.of(B, C, D), halves.getFirst().counterTerrorists());
    }
}
