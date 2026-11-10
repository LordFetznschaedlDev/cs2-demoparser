package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * A player was flashed.
 */
public record PlayerBlindEvent(
        int tick,
        int round,
        Player attacker,
        Player victim,
        float duration) implements GameEvent {
}
