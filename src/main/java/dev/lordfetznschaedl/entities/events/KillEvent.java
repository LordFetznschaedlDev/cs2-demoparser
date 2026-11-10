package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Team;

/**
 * A player died.
 *
 * @param attacker  killer, {@code null} for world damage / fall damage
 * @param assister assisting player or {@code null}
 * @param weapon    weapon name as used by the game, e.g. {@code ak47}
 * @param wallbang  the bullet penetrated at least one object
 */
public record KillEvent(
        int tick,
        int round,
        Player attacker,
        Team attackerTeam,
        Player victim,
        Team victimTeam,
        Player assister,
        String weapon,
        boolean headshot,
        boolean wallbang,
        boolean throughSmoke,
        boolean noScope,
        boolean attackerBlind,
        boolean assistedByFlash,
        boolean attackerInAir,
        float distance) implements GameEvent {

    public boolean isSuicide() {
        return attacker == null || (victim != null && attacker.id().equals(victim.id()));
    }

    public boolean isTeamKill() {
        return !isSuicide() && attackerTeam != null && attackerTeam == victimTeam && attackerTeam.isPlaying();
    }
}
