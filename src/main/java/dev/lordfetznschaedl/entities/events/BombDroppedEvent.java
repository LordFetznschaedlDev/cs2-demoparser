package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * The bomb carrier dropped the bomb (or died).
 */
public record BombDroppedEvent(
        int tick,
        int round,
        Player player) implements GameEvent {
}
