package dev.lordfetznschaedl.entities;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitiesTest {

    private static final Player ALICE = new Player(76561198000000001L, "alice", false);
    private static final Player BOT = new Player(0, "Bob", true);

    @Test
    void scoreboardEntryDerivedStats() {
        ScoreboardEntry e = new ScoreboardEntry(ALICE, Team.TERRORIST, 20, 10, 5, 10, 2000, 100, 3, 4, 50, 1, 0, 0, 20);
        assertEquals(100.0, e.adr());
        assertEquals(2.0, e.killDeathRatio());
        assertEquals(50.0, e.headshotPercentage());
        assertEquals(10, e.killDeathDifference());
    }

    @Test
    void scoreboardEntryWithoutDeathsOrRounds() {
        ScoreboardEntry e = new ScoreboardEntry(ALICE, Team.TERRORIST, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(3.0, e.killDeathRatio());
        assertEquals(0.0, e.adr());
        assertEquals(0.0, new ScoreboardEntry(ALICE, Team.TERRORIST, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                .headshotPercentage());
    }

    @Test
    void playerIdentity() {
        assertEquals("76561198000000001", ALICE.id());
        assertEquals("BOT:Bob", BOT.id());
    }

    @Test
    void teamNumbers() {
        assertEquals(Team.TERRORIST, Team.fromNumber(2));
        assertEquals(Team.COUNTER_TERRORIST, Team.fromNumber(3));
        assertEquals(Team.UNASSIGNED, Team.fromNumber(42));
        assertEquals(Team.COUNTER_TERRORIST, Team.TERRORIST.opponent());
        assertTrue(Team.TERRORIST.isPlaying());
    }

    @Test
    void roundEndReasonCodes() {
        assertEquals(RoundEndReason.BOMB_DEFUSED, RoundEndReason.fromCode(7));
        assertEquals(RoundEndReason.TARGET_BOMBED, RoundEndReason.fromCode(1));
        assertEquals(RoundEndReason.UNKNOWN, RoundEndReason.fromCode(999));
    }

    @Test
    void matchWinnerAndScore() {
        TeamResult t = new TeamResult(Team.TERRORIST, "Terrorists", 13, 8, 5, 0, List.of(ALICE));
        TeamResult ct = new TeamResult(Team.COUNTER_TERRORIST, "Counter-Terrorists", 9, 4, 5, 0, List.of(BOT));
        DemoInfo info = new DemoInfo("de_test", "", "", "", 0, 0, 64, 6400, Duration.ofSeconds(100));
        Match match = new Match(info, List.of(ALICE, BOT), List.of(t, ct), List.of(), List.of(),
                new Scoreboard(List.of()), List.of(), List.of(), Duration.ofMillis(1500));

        assertEquals(Duration.ofMillis(1500), match.parseDuration());
        assertEquals("13:9", match.finalScore());
        assertEquals(t, match.winner().orElseThrow());
        assertEquals(ALICE, match.playerByName("ALICE").orElseThrow());
        assertEquals(Duration.ofSeconds(1), info.tickToTime(64));
    }

    @Test
    void halfAndRoundSides() {
        MatchHalf half = new MatchHalf(3, 25, 27, List.of(ALICE), List.of(BOT), 2, 1);
        assertTrue(half.isOvertime());
        assertEquals(3, half.roundCount());
        assertTrue(half.containsRound(27));
        assertEquals(Team.TERRORIST, half.sideOf(ALICE));
        assertEquals(Team.COUNTER_TERRORIST, half.sideOf(BOT));
        assertEquals(Team.UNASSIGNED, half.sideOf(new Player(1, "other", false)));
        assertEquals(2, half.roundsWon(Team.TERRORIST));

        Round round = new Round(1, 0, 10, 20, Team.TERRORIST, RoundEndReason.TERRORISTS_WIN, 1, 0,
                List.of(), List.of(BOT), List.of(ALICE));
        assertEquals(Team.COUNTER_TERRORIST, round.sideOf(ALICE));
        assertEquals(List.of(BOT), round.players(Team.TERRORIST));
    }

    @Test
    void drawHasNoWinner() {
        TeamResult t = new TeamResult(Team.TERRORIST, "T", 15, 0, 0, 0, List.of());
        TeamResult ct = new TeamResult(Team.COUNTER_TERRORIST, "CT", 15, 0, 0, 0, List.of());
        Match match = new Match(null, List.of(), List.of(t, ct), List.of(), List.of(), new Scoreboard(List.of()), List.of(), List.of(), Duration.ZERO);
        assertTrue(match.winner().isEmpty());
    }
}
