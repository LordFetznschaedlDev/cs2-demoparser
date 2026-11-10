package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * A player fired a weapon (includes knife swings and grenade throws).
 */
public record WeaponFireEvent(
        int tick,
        int round,
        Player player,
        String weapon) implements GameEvent {
}
