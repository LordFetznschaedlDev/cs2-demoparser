package dev.lordfetznschaedl.entities;

/**
 * A participant of the match.
 *
 * @param steamId 64 bit SteamID, {@code 0} for bots
 * @param name    the in-game name (the last one seen in the demo)
 * @param bot     whether the player is a bot
 */
public record Player(long steamId, String name, boolean bot) {

    /** Stable identifier: the SteamID for humans, the name for bots. */
    public String id() {
        return bot ? "BOT:" + name : Long.toString(steamId);
    }

    @Override
    public String toString() {
        return name;
    }
}
