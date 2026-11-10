package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.entities.Skin;

import java.util.Map;
import static java.util.Map.entry;

/**
 * English names of the item definitions that can carry a skin (weapons, knives, gloves), keyed by
 * {@code m_iItemDefinitionIndex}. Grenades, the C4 and other items without finishes are not listed.
 */
public final class ItemDefinitions {

    private static final Map<Integer, String> WEAPONS = Map.ofEntries(
            entry(1, "Desert Eagle"), entry(2, "Dual Berettas"), entry(3, "Five-SeveN"), entry(4, "Glock-18"),
            entry(7, "AK-47"), entry(8, "AUG"), entry(9, "AWP"), entry(10, "FAMAS"), entry(11, "G3SG1"),
            entry(13, "Galil AR"), entry(14, "M249"), entry(16, "M4A4"), entry(17, "MAC-10"), entry(19, "P90"),
            entry(23, "MP5-SD"), entry(24, "UMP-45"), entry(25, "XM1014"), entry(26, "PP-Bizon"), entry(27, "MAG-7"),
            entry(28, "Negev"), entry(29, "Sawed-Off"), entry(30, "Tec-9"), entry(31, "Zeus x27"), entry(32, "P2000"),
            entry(33, "MP7"), entry(34, "MP9"), entry(35, "Nova"), entry(36, "P250"), entry(38, "SCAR-20"),
            entry(39, "SG 553"), entry(40, "SSG 08"), entry(60, "M4A1-S"), entry(61, "USP-S"), entry(63, "CZ75-Auto"),
            entry(64, "R8 Revolver"));

    private static final Map<Integer, String> KNIVES = Map.ofEntries(
            entry(42, "Knife (CT)"), entry(59, "Knife (T)"), entry(500, "Bayonet"), entry(503, "Classic Knife"),
            entry(505, "Flip Knife"), entry(506, "Gut Knife"), entry(507, "Karambit"), entry(508, "M9 Bayonet"),
            entry(509, "Huntsman Knife"), entry(512, "Falchion Knife"), entry(514, "Bowie Knife"),
            entry(515, "Butterfly Knife"), entry(516, "Shadow Daggers"), entry(517, "Paracord Knife"),
            entry(518, "Survival Knife"), entry(519, "Ursus Knife"), entry(520, "Navaja Knife"),
            entry(521, "Nomad Knife"), entry(522, "Stiletto Knife"), entry(523, "Talon Knife"),
            entry(525, "Skeleton Knife"), entry(526, "Kukri Knife"));

    private static final Map<Integer, String> GLOVES = Map.ofEntries(
            entry(4725, "Broken Fang Gloves"), entry(5027, "Bloodhound Gloves"), entry(5030, "Sport Gloves"),
            entry(5031, "Driver Gloves"), entry(5032, "Hand Wraps"), entry(5033, "Moto Gloves"),
            entry(5034, "Specialist Gloves"), entry(5035, "Hydra Gloves"));

    private ItemDefinitions() {
    }

    /** Category of a skinnable item definition, or {@code null} for anything else (grenades, C4, ...). */
    public static Skin.Category category(int itemDefinitionIndex) {
        if (WEAPONS.containsKey(itemDefinitionIndex)) {
            return Skin.Category.WEAPON;
        }
        if (KNIVES.containsKey(itemDefinitionIndex) || (itemDefinitionIndex >= 500 && itemDefinitionIndex < 600)) {
            return Skin.Category.KNIFE;
        }
        if (GLOVES.containsKey(itemDefinitionIndex)) {
            return Skin.Category.GLOVES;
        }
        return null;
    }

    /** English name, e.g. {@code "AK-47"}; {@code "Item <index>"} for an unknown definition. */
    public static String name(int itemDefinitionIndex) {
        String name = WEAPONS.get(itemDefinitionIndex);
        if (name == null) {
            name = KNIVES.get(itemDefinitionIndex);
        }
        if (name == null) {
            name = GLOVES.get(itemDefinitionIndex);
        }
        return name != null ? name : "Item " + itemDefinitionIndex;
    }
}
