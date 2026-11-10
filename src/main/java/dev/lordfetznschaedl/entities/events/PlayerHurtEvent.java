package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * A player took damage.
 */
public record PlayerHurtEvent(
        int tick,
        int round,
        Player attacker,
        Player victim,
        String weapon,
        int healthDamage,
        int armorDamage,
        int remainingHealth,
        int remainingArmor,
        int hitGroup) implements GameEvent {
}
