package dev.lordfetznschaedl;

import dev.lordfetznschaedl.businesslogic.modules.SkinModule;
import dev.lordfetznschaedl.entities.Match;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Skin;
import dev.lordfetznschaedl.entities.events.KillEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the {@link SkinModule} on demo2 (de_dust2).
 */
@LocalOnly
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkinModuleTest {

    private static final Path DEMO = Path.of("src", "test", "resources", "demos", "demo2.dem");

    /** Paint kit "Case Hardened". */
    private static final int CASE_HARDENED = 44;

    private Match match;

    @BeforeAll
    void parse() throws IOException {
        assumeTrue(Files.isRegularFile(DEMO), "test demo missing: " + DEMO.toAbsolutePath());
        match = DemoParser.builder().withDefaultModules().module(SkinModule::new).build().parse(DEMO);
    }

    private List<Skin> allSkins() {
        return match.players().stream().flatMap(p -> p.skins().stream()).toList();
    }

    @Test
    void notPartOfTheDefaultModules() throws IOException {
        assertTrue(DemoParser.create().parse(DEMO).players().stream().allMatch(p -> p.skins().isEmpty()));
    }

    @Test
    void everyPlayerHasSkins() {
        assertEquals(10, match.players().size());
        for (Player player : match.players()) {
            assertFalse(player.skins().isEmpty(), player::name);
        }
    }

    @Test
    void valuesAreInTheirRanges() {
        List<Skin> skins = allSkins();
        assertTrue(skins.size() > 50);
        for (Skin skin : skins) {
            assertTrue(skin.itemId() > 0, skin::toString);
            assertTrue(skin.wear() >= 0f && skin.wear() <= 1f, skin::toString);
            assertTrue(skin.paintSeed() >= 0 && skin.paintSeed() <= 1000, skin::toString);
            assertTrue(skin.paintKit() >= 0, skin::toString);
            for (Skin.Sticker sticker : skin.stickers()) {
                assertTrue(sticker.slot() >= 0 && sticker.slot() < 5, skin::toString);
                assertTrue(sticker.stickerId() > 0, skin::toString);
            }
            if (skin.category() == Skin.Category.GLOVES) {
                assertTrue(skin.paintKit() >= 10000, "glove finishes have ids from 10000: " + skin);
            }
        }
        assertTrue(skins.stream().anyMatch(Skin::hasStatTrak));
        assertTrue(skins.stream().anyMatch(s -> !s.stickers().isEmpty()));
        assertTrue(skins.stream().anyMatch(s -> s.nameTag() != null));
        assertTrue(skins.stream().anyMatch(s -> s.charmId() > 0));
    }

    @Test
    void itemsAreRecordedOncePerPlayer() {
        for (Player player : match.players()) {
            assertEquals(player.skins().size(), player.skins().stream().map(Skin::itemId).distinct().count(),
                    player::name);
        }
        // an item belongs to exactly one inventory
        List<Skin> skins = allSkins();
        assertEquals(skins.size(), skins.stream().map(Skin::itemId).distinct().count());
    }

    @Test
    void knivesAndGloves() {
        List<Skin> knives = allSkins().stream().filter(s -> s.category() == Skin.Category.KNIFE).toList();
        List<Skin> gloves = allSkins().stream().filter(s -> s.category() == Skin.Category.GLOVES).toList();
        assertFalse(knives.isEmpty());
        assertFalse(gloves.isEmpty());
        assertTrue(knives.stream().noneMatch(k -> k.itemDefinitionIndex() == 42 || k.itemDefinitionIndex() == 59),
                "default knives are not inventory items");
        assertTrue(allSkins().stream().anyMatch(s -> s.paintKit() == CASE_HARDENED && s.category() == Skin.Category.KNIFE));
    }

    @Test
    void playersInEventsShareTheSkins() {
        KillEvent kill = match.kills().stream().filter(k -> k.attacker() != null && !k.attacker().skins().isEmpty())
                .findFirst().orElseThrow();
        Player listed = match.players().stream().filter(p -> p.equals(kill.attacker())).findFirst().orElseThrow();
        assertEquals(listed.skins(), kill.attacker().skins());
        assertSame(listed.skins().getFirst(), kill.attacker().skins().getFirst());
    }

    @Test
    void skinsAreReadOnly() {
        Player player = match.players().getFirst();
        assertThrows(UnsupportedOperationException.class, () -> player.skins().clear());
    }
}
