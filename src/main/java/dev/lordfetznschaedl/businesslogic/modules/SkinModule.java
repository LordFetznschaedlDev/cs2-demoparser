package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Skin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the cosmetic items of every player (weapon and knife skins, gloves) into
 * {@link Player#skins()}.
 * <p>
 * Every weapon entity carries the econ item it was created from: definition index, the owner's
 * account id, the inventory item id and its attributes ({@code m_NetworkedDynamicAttributes}).
 * Gloves are the {@code m_EconGloves} item of the player pawn. An item belongs to the player whose
 * inventory it comes from, also when someone else picked it up. Default items (item id {@code 0})
 * are not recorded.
 * <p>
 * Items are read when their entity is deleted and at the end of the demo, so a StatTrak counter shows
 * its last value. Only items that appear in the match are known: a skin of a weapon the player never
 * bought or picked up is not in the demo.
 */
public final class SkinModule implements ParserModule {

    /** Offset from a 32 bit Steam account id to the 64 bit SteamID of an individual account. */
    static final long STEAM_ID_BASE = 76561197960265728L;

    private static final String PAWN_CLASS = "CCSPlayerPawn";
    private static final String WEAPON_ATTRIBUTES = "m_AttributeManager.m_Item.m_NetworkedDynamicAttributes.m_Attributes";
    private static final String GLOVE_ATTRIBUTES = "m_EconGloves.m_NetworkedDynamicAttributes.m_Attributes";

    // attribute definition indices (items_game.txt)
    private static final int PAINT_KIT = 6;
    private static final int PAINT_SEED = 7;
    private static final int PAINT_WEAR = 8;
    private static final int STAT_TRAK = 80;
    private static final int STICKER_FIRST = 113; // slot n: 113 + 4n = id, 114 + 4n = wear
    private static final int STICKER_SLOTS = 5;
    private static final int CHARM = 299;

    /** Gloves are part of the side's loadout, so they can change when a player switches sides. */
    @Override
    public void onEntityUpdated(Entity entity, Set<String> changed, ParserContext ctx) {
        if ((changed.contains("m_nEconGlovesChanged") || changed.contains("m_iItemIDLow"))
                && entity.className().equals(PAWN_CLASS)) {
            read(entity, ctx);
        }
    }

    @Override
    public void onEntityDeleted(Entity entity, ParserContext ctx) {
        read(entity, ctx);
    }

    @Override
    public void onFinish(ParserContext ctx) {
        for (Entity entity : List.copyOf(ctx.entities().all())) {
            read(entity, ctx);
        }
    }

    private void read(Entity entity, ParserContext ctx) {
        if (!(entity.get("m_iItemDefinitionIndex") instanceof Number def)) {
            return;
        }
        boolean pawn = entity.className().equals(PAWN_CLASS);
        if (!pawn && entity.get("m_iClip1") == null) {
            return; // not a weapon, e.g. a chicken or a team intro model
        }
        Skin.Category category = ItemDefinitions.category(def.intValue());
        if (category == null || (pawn != (category == Skin.Category.GLOVES))) {
            return;
        }
        long itemId = ((long) entity.getInt("m_iItemIDHigh", 0) << 32) | (entity.getInt("m_iItemIDLow", 0) & 0xFFFFFFFFL);
        int account = entity.getInt("m_iAccountID", 0);
        if (itemId == 0 || account == 0) {
            return; // default item, not from an inventory
        }
        Player owner = ownerOf(account, ctx);
        if (owner == null) {
            return;
        }
        Map<Integer, Float> attributes = attributes(entity, pawn ? GLOVE_ATTRIBUTES : WEAPON_ATTRIBUTES);
        ctx.putSkin(owner.id(), skin(def.intValue(), category, itemId, entity, attributes));
    }

    /** The player with the account id; owners are players of the match, so they have a controller or were seen. */
    private static Player ownerOf(int account, ParserContext ctx) {
        long steamId = STEAM_ID_BASE + (account & 0xFFFFFFFFL);
        for (Entity controller : ctx.controllers()) {
            if (controller.getLong("m_steamID", 0) == steamId) {
                return ctx.player(controller);
            }
        }
        for (Player known : ctx.knownPlayers()) {
            if (known.steamId() == steamId) {
                return known;
            }
        }
        return null;
    }

    private static Map<Integer, Float> attributes(Entity entity, String prefix) {
        Map<Integer, Float> attributes = new HashMap<>();
        int count = entity.getInt(prefix, 0);
        for (int i = 0; i < count; i++) {
            String element = prefix + "." + i + ".";
            if (entity.get(element + "m_iAttributeDefinitionIndex") instanceof Number index
                    && entity.get(element + "m_iRawValue32") instanceof Number value) {
                attributes.put(index.intValue(), value.floatValue());
            }
        }
        return attributes;
    }

    static Skin skin(int def, Skin.Category category, long itemId, Entity entity, Map<Integer, Float> attributes) {
        List<Skin.Sticker> stickers = new ArrayList<>();
        for (int slot = 0; slot < STICKER_SLOTS; slot++) {
            int id = intAttribute(attributes, STICKER_FIRST + 4 * slot, 0);
            if (id > 0) {
                stickers.add(new Skin.Sticker(slot, id, attributes.getOrDefault(STICKER_FIRST + 4 * slot + 1, 0f)));
            }
        }
        String nameTag = entity.getString("m_szCustomName");
        return new Skin(def, ItemDefinitions.name(def), category, itemId,
                (int) attributes.getOrDefault(PAINT_KIT, 0f).floatValue(),
                (int) Math.floor(attributes.getOrDefault(PAINT_SEED, 0f)),
                attributes.getOrDefault(PAINT_WEAR, 0f),
                intAttribute(attributes, STAT_TRAK, -1),
                nameTag == null || nameTag.isEmpty() ? null : nameTag,
                stickers,
                intAttribute(attributes, CHARM, 0));
    }

    /** Integer attributes are stored as the raw bits of the 32 bit value. */
    private static int intAttribute(Map<Integer, Float> attributes, int index, int fallback) {
        Float value = attributes.get(index);
        return value == null ? fallback : Float.floatToRawIntBits(value);
    }
}
