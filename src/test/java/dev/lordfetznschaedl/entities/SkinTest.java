package dev.lordfetznschaedl.entities;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinTest {

    private static Skin skin(int def, Skin.Category category, int paintKit, float wear, int statTrak) {
        return new Skin(def, "item", category, 1, paintKit, 0, wear, statTrak, null, List.of(), 0);
    }

    @Test
    void exteriorFollowsTheWearRanges() {
        assertEquals(Skin.Exterior.FACTORY_NEW, Skin.Exterior.of(0f));
        assertEquals(Skin.Exterior.FACTORY_NEW, Skin.Exterior.of(0.0699f));
        assertEquals(Skin.Exterior.MINIMAL_WEAR, Skin.Exterior.of(0.07f));
        assertEquals(Skin.Exterior.FIELD_TESTED, Skin.Exterior.of(0.15f));
        assertEquals(Skin.Exterior.WELL_WORN, Skin.Exterior.of(0.38f));
        assertEquals(Skin.Exterior.BATTLE_SCARRED, Skin.Exterior.of(0.45f));
        assertEquals(Skin.Exterior.BATTLE_SCARRED, Skin.Exterior.of(1f));
    }

    @Test
    void itemWithoutFinish() {
        Skin vanilla = skin(507, Skin.Category.KNIFE, 0, 0f, -1);
        assertFalse(vanilla.painted());
        assertFalse(vanilla.hasStatTrak());
        assertNull(vanilla.exterior());

        Skin painted = skin(7, Skin.Category.WEAPON, 44, 0.2f, 12);
        assertTrue(painted.painted());
        assertTrue(painted.hasStatTrak());
        assertEquals(Skin.Exterior.FIELD_TESTED, painted.exterior());
    }

    @Test
    void stickersAreCopied() {
        List<Skin.Sticker> stickers = new ArrayList<>(List.of(new Skin.Sticker(0, 6612, 0f)));
        Skin skin = new Skin(7, "AK-47", Skin.Category.WEAPON, 1, 0, 0, 0f, -1, null, stickers, 0);
        stickers.clear();
        assertEquals(1, skin.stickers().size());
    }

    @Test
    void playerEqualityIgnoresSkins() {
        Player plain = new Player(76561198000000001L, "alice", false);
        Player withSkins = new Player(76561198000000001L, "alice", false,
                List.of(skin(7, Skin.Category.WEAPON, 44, 0.2f, -1), skin(5030, Skin.Category.GLOVES, 10045, 0.1f, -1)));
        assertEquals(plain, withSkins);
        assertEquals(plain.hashCode(), withSkins.hashCode());
        assertTrue(plain.skins().isEmpty());
        assertEquals(1, withSkins.skins(Skin.Category.GLOVES).size());
        assertTrue(withSkins.skins(Skin.Category.KNIFE).isEmpty());
    }
}
