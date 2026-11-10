package dev.lordfetznschaedl.entities;

import java.util.List;
import java.util.Objects;

/**
 * A participant of the match.
 * <p>
 * Equality is based on the identity ({@code steamId}, {@code name}, {@code bot}) only, not on the skins.
 *
 * @param steamId 64 bit SteamID, {@code 0} for bots
 * @param name    the in-game name (the last one seen in the demo)
 * @param bot     whether the player is a bot
 * @param skins   cosmetic items of the player (weapon and knife skins, gloves), in the order they were
 *                first seen; only filled when the SkinModule is used, otherwise empty. Every
 *                {@code Player} instance of a parse, including those referenced by events, shares the same
 *                list, which is complete once parsing finished.
 */
public record Player(long steamId, String name, boolean bot, List<Skin> skins) {

    public Player {
        skins = skins == null ? List.of() : skins;
    }

    /** A player without skins. */
    public Player(long steamId, String name, boolean bot) {
        this(steamId, name, bot, List.of());
    }

    /** Stable identifier: the SteamID for humans, the name for bots. */
    public String id() {
        return bot ? "BOT:" + name : Long.toString(steamId);
    }

    /** The skins of one category, e.g. {@code skins(Skin.Category.KNIFE)}. */
    public List<Skin> skins(Skin.Category category) {
        return skins.stream().filter(s -> s.category() == category).toList();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Player p && steamId == p.steamId && bot == p.bot && name.equals(p.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(steamId, name, bot);
    }

    @Override
    public String toString() {
        return name;
    }
}
