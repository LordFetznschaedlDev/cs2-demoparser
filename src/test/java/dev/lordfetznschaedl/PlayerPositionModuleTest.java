package dev.lordfetznschaedl;

import dev.lordfetznschaedl.businesslogic.modules.PlayerPositionModule;
import dev.lordfetznschaedl.entities.BombSite;
import dev.lordfetznschaedl.entities.Match;
import dev.lordfetznschaedl.entities.MatchHalf;
import dev.lordfetznschaedl.entities.events.BombPlantedEvent;
import dev.lordfetznschaedl.entities.events.KillEvent;
import dev.lordfetznschaedl.entities.events.PlayerPositionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the {@link PlayerPositionModule} on demo2 (de_dust2).
 */
@LocalOnly
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerPositionModuleTest {

    private static final Path DEMO = Path.of("src", "test", "resources", "demos", "demo2.dem");

    /** Bombsite centers of de_dust2 as networked by the game (CCSPlayerResource). */
    private static final float[] SITE_A = {1112f, 2480f};
    private static final float[] SITE_B = {-1536f, 2680f};

    private Match match;
    private List<PlayerPositionEvent> positions;

    @BeforeAll
    void parse() throws IOException {
        assumeTrue(Files.isRegularFile(DEMO), "test demo missing: " + DEMO.toAbsolutePath());
        match = DemoParser.builder().withDefaultModules().module(PlayerPositionModule::new).build().parse(DEMO);
        positions = match.events(PlayerPositionEvent.class);
    }

    @Test
    void notPartOfTheDefaultModules() throws IOException {
        assertTrue(DemoParser.create().parse(DEMO).events(PlayerPositionEvent.class).isEmpty());
    }

    @Test
    void samplesAllLivingPlayersAtTheInterval() {
        assertFalse(positions.isEmpty());
        Map<Integer, List<PlayerPositionEvent>> byTick = positions.stream()
                .collect(Collectors.groupingBy(PlayerPositionEvent::tick));
        List<Integer> ticks = byTick.keySet().stream().sorted().toList();
        for (int i = 1; i < ticks.size(); i++) {
            assertTrue(ticks.get(i) - ticks.get(i - 1) >= PlayerPositionModule.DEFAULT_INTERVAL_TICKS);
        }
        for (List<PlayerPositionEvent> sample : byTick.values()) {
            assertTrue(sample.size() <= 10);
            assertEquals(sample.size(), sample.stream().map(p -> p.player().id()).distinct().count());
            for (PlayerPositionEvent p : sample) {
                assertTrue(p.health() > 0 && p.health() <= 100, p::toString);
                assertTrue(p.pitch() >= -90f && p.pitch() <= 90f, p::toString);
            }
        }
        // at the start of a round (freeze time) all ten players are alive
        long fullSamples = byTick.values().stream().filter(s -> s.size() == 10).count();
        assertTrue(fullSamples > match.rounds().size());
        assertTrue(positions.stream().allMatch(p -> p.round() > 0), "no warmup samples by default");
    }

    @Test
    void sidesMatchTheHalves() {
        for (PlayerPositionEvent p : positions) {
            MatchHalf half = match.halfOfRound(p.round()).orElseThrow();
            assertEquals(half.sideOf(p.player()), p.team(), p::toString);
        }
    }

    /** Players can not teleport: consecutive samples of a living player are close to each other. */
    @Test
    void movementIsContinuous() {
        Map<String, PlayerPositionEvent> previous = new HashMap<>();
        int checked = 0;
        for (PlayerPositionEvent p : positions) {
            PlayerPositionEvent before = previous.put(p.player().id(), p);
            if (before == null || before.round() != p.round()
                    || p.tick() - before.tick() != PlayerPositionModule.DEFAULT_INTERVAL_TICKS) {
                continue; // first sample of a life / round
            }
            // 16 ticks = 0.25 s; running is ~250 units/s, falling can be faster
            assertTrue(p.distanceTo(before) < 250, () -> before + " -> " + p);
            checked++;
        }
        assertTrue(checked > 10_000);
    }

    @Test
    void plantersStandOnTheBombsite() {
        for (BombPlantedEvent plant : match.events(BombPlantedEvent.class)) {
            PlayerPositionEvent last = positions.stream()
                    .filter(p -> p.player().id().equals(plant.player().id()) && p.tick() <= plant.tick())
                    .reduce((a, b) -> b).orElseThrow();
            float[] center = plant.site() == BombSite.A ? SITE_A : SITE_B;
            double distance = Math.hypot(last.x() - center[0], last.y() - center[1]);
            assertTrue(distance < 300, () -> plant + " but player was at " + last);
        }
    }

    @Test
    void victimsAreNotSampledAfterTheirDeathInTheSameRound() {
        for (KillEvent kill : match.kills()) {
            if (kill.round() == 0) {
                continue;
            }
            boolean sampledAfterDeath = positions.stream().anyMatch(p -> p.round() == kill.round()
                    && p.tick() > kill.tick() + 1 && p.player().id().equals(kill.victim().id()));
            assertFalse(sampledAfterDeath, kill::toString);
        }
    }

    @Test
    void customInterval() throws IOException {
        Match everySecond = DemoParser.builder().withDefaultModules()
                .module(() -> new PlayerPositionModule(64, false)).build().parse(DEMO);
        int samples = everySecond.events(PlayerPositionEvent.class).size();
        // a quarter of the samples of the 16 tick default, give or take rounding at round boundaries
        assertEquals(positions.size() / 4.0, samples, positions.size() * 0.02);
    }
}
