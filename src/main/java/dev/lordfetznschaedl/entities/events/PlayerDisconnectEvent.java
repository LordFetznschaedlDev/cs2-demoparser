package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * A player left the server.
 */
public record PlayerDisconnectEvent(
        int tick,
        int round,
        Player player,
        int reason) implements GameEvent {
}
