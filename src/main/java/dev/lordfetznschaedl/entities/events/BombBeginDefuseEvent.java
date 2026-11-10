package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * A player started defusing.
 */
public record BombBeginDefuseEvent(
        int tick,
        int round,
        Player player,
        boolean hasKit) implements GameEvent {
}
