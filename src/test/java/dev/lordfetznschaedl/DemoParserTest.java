package dev.lordfetznschaedl;

import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.businesslogic.modules.GenericEventModule;
import dev.lordfetznschaedl.businesslogic.modules.ParserContext;
import dev.lordfetznschaedl.businesslogic.modules.ParserModule;
import dev.lordfetznschaedl.businesslogic.modules.WeaponFireModule;
import dev.lordfetznschaedl.entities.BombSite;
import dev.lordfetznschaedl.entities.Match;
import dev.lordfetznschaedl.entities.Round;
import dev.lordfetznschaedl.entities.ScoreboardEntry;
import dev.lordfetznschaedl.entities.Team;
import dev.lordfetznschaedl.entities.TeamResult;
import dev.lordfetznschaedl.entities.events.BombDefusedEvent;
import dev.lordfetznschaedl.entities.events.BombExplodedEvent;
import dev.lordfetznschaedl.entities.events.BombPlantedEvent;
import dev.lordfetznschaedl.entities.events.GameEvent;
import dev.lordfetznschaedl.entities.events.GenericGameEvent;
import dev.lordfetznschaedl.entities.events.GrenadeDetonateEvent;
import dev.lordfetznschaedl.entities.events.GrenadeType;
import dev.lordfetznschaedl.entities.events.KillEvent;
import dev.lordfetznschaedl.entities.events.MatchEndEvent;
import dev.lordfetznschaedl.entities.events.MatchStartEvent;
import dev.lordfetznschaedl.entities.events.RoundEndEvent;
import dev.lordfetznschaedl.entities.events.RoundStartEvent;
import dev.lordfetznschaedl.entities.events.WeaponFireEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Parses the demos in {@code src/test/resources/demos} and checks the result for consistency.
 * Each demo is parsed once and shared by the tests. The demos are not under version control, so
 * the tests only run locally ({@link LocalOnly}) and are skipped when the files are missing.
 */
@LocalOnly
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoParserTest {

    private static final Path DEMOS = Path.of("src", "test", "resources", "demos");

    private final Map<String, Match> matches = new HashMap<>();

    @BeforeAll
    void parseDemos() throws IOException {
        assumeTrue(demos().stream().allMatch(d -> Files.isRegularFile(DEMOS.resolve(d))),
                "test demos missing in " + DEMOS.toAbsolutePath());
        DemoParser parser = DemoParser.create();
        for (String demo : demos()) {
            matches.put(demo, parser.parse(DEMOS.resolve(demo)));
        }
    }

    List<String> demos() {
        return List.of("demo1.dem", "demo2.dem", "demo3.dem");
    }

    // ---------------------------------------------------------------- known results

    @Test
    void demo1Result() {
        Match m = matches.get("demo1.dem");
        assertEquals("cs_office", m.mapName());
        assertEquals("13:9", m.finalScore());
        assertEquals(22, m.rounds().size());
        assertEquals(Team.TERRORIST, m.winner().orElseThrow().side());
        ScoreboardEntry top = m.scoreboard().forPlayerName("mrJaNeeee").orElseThrow();
        assertEquals(25, top.kills());
        assertEquals(15, top.deaths());
    }

    @Test
    void demo2Result() {
        Match m = matches.get("demo2.dem");
        assertEquals("de_dust2", m.mapName());
        assertEquals("13:7", m.finalScore());
        assertEquals(20, m.rounds().size());
        assertEquals(14, m.events(BombPlantedEvent.class).size());
        assertEquals(3, m.events(BombDefusedEvent.class).size());
        assertEquals(2, m.events(BombExplodedEvent.class).size());
    }

    @Test
    void demo3Result() {
        Match m = matches.get("demo3.dem");
        assertEquals("de_dust2", m.mapName());
        assertEquals("9:13", m.finalScore());
        assertEquals(Team.COUNTER_TERRORIST, m.winner().orElseThrow().side());
    }

    // ---------------------------------------------------------------- consistency

    @ParameterizedTest
    @MethodSource("demos")
    void parsesWithoutWarnings(String demo) {
        Match m = matches.get(demo);
        assertTrue(m.warnings().isEmpty(), () -> String.join("\n", m.warnings()));
        assertEquals(64.0, m.info().tickRate());
        assertTrue(m.info().duration().toMinutes() > 20);
        assertTrue(m.info().totalTicks() > 0);
    }

    @ParameterizedTest
    @MethodSource("demos")
    void teamsAndPlayers(String demo) {
        Match m = matches.get(demo);
        assertEquals(2, m.teams().size());
        assertEquals(10, m.players().size());
        for (TeamResult team : m.teams()) {
            assertEquals(5, team.players().size(), team::toString);
            assertEquals(team.score(), team.scoreFirstHalf() + team.scoreSecondHalf() + team.scoreOvertime());
        }
        assertEquals(10, m.scoreboard().entries().size());
        assertEquals(5, m.scoreboard().forTeam(Team.TERRORIST).size());
    }

    @ParameterizedTest
    @MethodSource("demos")
    void roundScoresAreConsistent(String demo) {
        Match m = matches.get(demo);
        int totalScore = m.teams().stream().mapToInt(TeamResult::score).sum();
        assertEquals(totalScore, m.rounds().size());

        for (int i = 0; i < m.rounds().size(); i++) {
            Round round = m.rounds().get(i);
            assertEquals(i + 1, round.number());
            assertTrue(round.isFinished());
            assertTrue(round.winner().isPlaying(), () -> "no winner in " + round);
            assertTrue(round.startTick() < round.freezeEndTick() && round.freezeEndTick() < round.endTick());
            assertEquals(round.number(), round.terroristScore() + round.counterTerroristScore(), round::toString);
        }
        Round last = m.rounds().getLast();
        assertEquals(m.team(Team.TERRORIST).orElseThrow().score(), last.terroristScore());
        assertEquals(m.team(Team.COUNTER_TERRORIST).orElseThrow().score(), last.counterTerroristScore());
    }

    @ParameterizedTest
    @MethodSource("demos")
    void roundWinnerMatchesReason(String demo) {
        for (Round round : matches.get(demo).rounds()) {
            Team expected = switch (round.reason()) {
                case TARGET_BOMBED, TERRORISTS_WIN, HOSTAGES_NOT_RESCUED, CTS_SURRENDER -> Team.TERRORIST;
                case BOMB_DEFUSED, CTS_WIN, TARGET_SAVED, ALL_HOSTAGES_RESCUED, TERRORISTS_SURRENDER -> Team.COUNTER_TERRORIST;
                default -> null;
            };
            if (expected != null) {
                assertEquals(expected, round.winner(), round::toString);
            }
        }
    }

    /** The kill events must reproduce the kills and deaths the game itself counted. */
    @ParameterizedTest
    @MethodSource("demos")
    void killEventsMatchScoreboard(String demo) {
        Match m = matches.get(demo);
        List<KillEvent> live = m.kills().stream().filter(k -> k.round() > 0).toList();
        Map<String, Long> kills = live.stream()
                .filter(k -> k.attacker() != null && !k.isSuicide() && !k.isTeamKill())
                .collect(Collectors.groupingBy(k -> k.attacker().id(), Collectors.counting()));
        Map<String, Long> deaths = live.stream().filter(k -> k.victim() != null)
                .collect(Collectors.groupingBy(k -> k.victim().id(), Collectors.counting()));

        for (ScoreboardEntry entry : m.scoreboard().entries()) {
            assertEquals(entry.kills(), kills.getOrDefault(entry.player().id(), 0L), entry.player().name() + " kills");
            assertEquals(entry.deaths(), deaths.getOrDefault(entry.player().id(), 0L), entry.player().name() + " deaths");
        }
        int roundKills = m.rounds().stream().mapToInt(r -> r.kills().size()).sum();
        assertEquals(live.size(), roundKills);
    }

    @ParameterizedTest
    @MethodSource("demos")
    void killEventsResolvePlayersAndSides(String demo) {
        for (KillEvent kill : matches.get(demo).kills()) {
            if (kill.round() == 0) {
                continue;
            }
            assertNotNull(kill.victim(), kill::toString);
            assertTrue(kill.victimTeam().isPlaying(), kill::toString);
            assertNotNull(kill.weapon(), kill::toString);
            if (!kill.isSuicide()) {
                assertNotEquals(kill.attacker(), kill.victim());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("demos")
    void timelineIsOrderedAndComplete(String demo) {
        Match m = matches.get(demo);
        List<GameEvent> events = m.events();
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i - 1).tick() <= events.get(i).tick());
        }
        assertEquals(1, m.events(MatchStartEvent.class).size());
        assertEquals(1, m.events(MatchEndEvent.class).size());
        assertEquals(m.rounds().size(), m.events(RoundStartEvent.class).size());
        assertEquals(m.rounds().size(), m.events(RoundEndEvent.class).size());
        assertEquals(m.winner().orElseThrow().side(), m.events(MatchEndEvent.class).getFirst().winner());
        assertFalse(m.eventsInRound(1).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("demos")
    void bombSitesAreResolved(String demo) {
        Match m = matches.get(demo);
        for (BombPlantedEvent plant : m.events(BombPlantedEvent.class)) {
            assertNotEquals(BombSite.UNKNOWN, plant.site(), plant::toString);
            assertNotNull(plant.player(), plant::toString);
        }
        for (BombDefusedEvent defuse : m.events(BombDefusedEvent.class)) {
            assertNotEquals(BombSite.UNKNOWN, defuse.site(), defuse::toString);
        }
    }

    @ParameterizedTest
    @MethodSource("demos")
    void grenadeThrowersAreResolved(String demo) {
        List<GrenadeDetonateEvent> grenades = matches.get(demo).events(GrenadeDetonateEvent.class);
        assertFalse(grenades.isEmpty());
        for (GrenadeType type : List.of(GrenadeType.HE_GRENADE, GrenadeType.FLASHBANG, GrenadeType.SMOKE, GrenadeType.MOLOTOV)) {
            List<GrenadeDetonateEvent> ofType = grenades.stream().filter(g -> g.type() == type).toList();
            assertFalse(ofType.isEmpty(), type::toString);
            long resolved = ofType.stream().filter(g -> g.thrower() != null).count();
            // a thrower that disconnected before the detonation can not be resolved
            assertTrue(resolved >= ofType.size() * 0.95, type + ": " + resolved + "/" + ofType.size());
        }
    }

    // ---------------------------------------------------------------- modularity

    @Test
    void customAndOptionalModules() throws IOException {
        AtomicInteger deaths = new AtomicInteger();
        ParserModule counter = new ParserModule() {
            @Override
            public void onGameEvent(RawGameEvent event, ParserContext context) {
                if (event.name().equals("player_death")) {
                    deaths.incrementAndGet();
                }
            }
        };
        DemoParser parser = DemoParser.builder()
                .withDefaultModules()
                .module(WeaponFireModule::new)
                .module(() -> new GenericEventModule(java.util.Set.of("item_pickup")))
                .module(() -> counter)
                .build();
        Match m = parser.parse(DEMOS.resolve("demo2.dem"));

        assertEquals(m.kills().size(), deaths.get());
        assertFalse(m.events(WeaponFireEvent.class).isEmpty());
        List<GenericGameEvent> pickups = m.events(GenericGameEvent.class);
        assertFalse(pickups.isEmpty());
        assertTrue(pickups.stream().allMatch(e -> e.name().equals("item_pickup")));
        assertEquals(matches.get("demo2.dem").finalScore(), m.finalScore());
    }

    @Test
    void parserWithoutModulesOnlyReadsMetadata() throws IOException {
        Match m = DemoParser.builder().build().parse(DEMOS.resolve("demo3.dem"));
        assertEquals("de_dust2", m.mapName());
        assertTrue(m.events().isEmpty());
        assertTrue(m.rounds().isEmpty());
    }

    @Test
    void parsesFromStream() throws IOException {
        try (InputStream in = Files.newInputStream(DEMOS.resolve("demo1.dem"))) {
            Match m = DemoParser.create().parse(in);
            assertEquals(matches.get("demo1.dem").finalScore(), m.finalScore());
            assertEquals(matches.get("demo1.dem").events().size(), m.events().size());
        }
    }

    @Test
    void truncatedDemoIsParsedAsFarAsPossible() throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(DEMOS.resolve("demo2.dem"))) {
            head = in.readNBytes(60 * 1024 * 1024);
        }
        Match m = DemoParser.create().parse(new ByteArrayInputStream(head));
        assertFalse(m.rounds().isEmpty());
        assertTrue(m.rounds().size() < matches.get("demo2.dem").rounds().size());
        Round first = m.rounds().getFirst();
        assertArrayEquals(
                new int[]{first.startTick(), first.endTick()},
                new int[]{matches.get("demo2.dem").rounds().getFirst().startTick(),
                        matches.get("demo2.dem").rounds().getFirst().endTick()});
    }
}
