package dev.lordfetznschaedl.entities;

import java.util.List;

/**
 * A cosmetic inventory item a player used in the match: a weapon or knife skin or a pair of gloves
 * (only recorded by the SkinModule).
 * <p>
 * The demo only contains numeric ids. Names of paint kits, stickers and charms are not part of the
 * demo; they can be looked up in the game's {@code items_game.txt}.
 *
 * @param itemDefinitionIndex what the item is, e.g. {@code 7} = AK-47, {@code 507} = Karambit, {@code 5030} = Sport Gloves
 * @param itemName            English name of the item definition, e.g. {@code "AK-47"}
 * @param category            weapon, knife or gloves
 * @param itemId              unique id of the item in the owner's inventory
 * @param paintKit            the finish ("skin"), {@code 0} for an item without one (e.g. a vanilla knife)
 * @param paintSeed           pattern seed (networked as a float; rounded down)
 * @param wear                float value between 0 (factory new) and 1
 * @param statTrak            StatTrak kill count, {@code -1} if the item has no StatTrak counter
 * @param nameTag             custom name, or {@code null}
 * @param stickers            applied stickers
 * @param charmId             id of the attached charm (keychain), {@code 0} if there is none
 */
public record Skin(
        int itemDefinitionIndex,
        String itemName,
        Category category,
        long itemId,
        int paintKit,
        int paintSeed,
        float wear,
        int statTrak,
        String nameTag,
        List<Sticker> stickers,
        int charmId) {

    public Skin {
        stickers = List.copyOf(stickers);
    }

    public enum Category {
        WEAPON, KNIFE, GLOVES
    }

    /** Exterior quality derived from the wear value. */
    public enum Exterior {
        FACTORY_NEW, MINIMAL_WEAR, FIELD_TESTED, WELL_WORN, BATTLE_SCARRED;

        public static Exterior of(float wear) {
            if (wear < 0.07f) {
                return FACTORY_NEW;
            }
            if (wear < 0.15f) {
                return MINIMAL_WEAR;
            }
            if (wear < 0.38f) {
                return FIELD_TESTED;
            }
            if (wear < 0.45f) {
                return WELL_WORN;
            }
            return BATTLE_SCARRED;
        }
    }

    /**
     * A sticker on a weapon.
     *
     * @param slot      sticker slot on the weapon (0 – 4)
     * @param stickerId the sticker kit
     * @param wear      scrape amount, {@code 0} = untouched
     */
    public record Sticker(int slot, int stickerId, float wear) {
    }

    /** Whether the item has a finish ({@code paintKit != 0}). */
    public boolean painted() {
        return paintKit != 0;
    }

    public boolean hasStatTrak() {
        return statTrak >= 0;
    }

    /** Exterior of the finish, or {@code null} for an item without one. */
    public Exterior exterior() {
        return painted() ? Exterior.of(wear) : null;
    }
}
