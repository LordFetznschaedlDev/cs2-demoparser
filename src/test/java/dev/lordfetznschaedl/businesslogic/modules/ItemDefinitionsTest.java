package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.entities.Skin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemDefinitionsTest {

    @Test
    void namesAndCategories() {
        assertEquals("AK-47", ItemDefinitions.name(7));
        assertEquals(Skin.Category.WEAPON, ItemDefinitions.category(7));
        assertEquals("M4A1-S", ItemDefinitions.name(60));
        assertEquals("Karambit", ItemDefinitions.name(507));
        assertEquals(Skin.Category.KNIFE, ItemDefinitions.category(507));
        assertEquals("Sport Gloves", ItemDefinitions.name(5030));
        assertEquals(Skin.Category.GLOVES, ItemDefinitions.category(5030));
    }

    @Test
    void unknownKnivesAreStillKnives() {
        assertEquals(Skin.Category.KNIFE, ItemDefinitions.category(599));
        assertEquals("Item 599", ItemDefinitions.name(599));
    }

    @Test
    void itemsWithoutSkinsHaveNoCategory() {
        assertNull(ItemDefinitions.category(44)); // HE grenade
        assertNull(ItemDefinitions.category(49)); // C4
        assertNull(ItemDefinitions.category(0));
    }
}
