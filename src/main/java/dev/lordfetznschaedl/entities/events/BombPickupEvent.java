package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * A player picked up the bomb.
 */
public record BombPickupEvent(
        int tick,
        int round,
        Player player) implements GameEvent {
}
