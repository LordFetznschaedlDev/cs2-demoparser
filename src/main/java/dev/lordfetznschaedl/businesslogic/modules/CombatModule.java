package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.entities.events.KillEvent;
import dev.lordfetznschaedl.entities.events.PlayerBlindEvent;
import dev.lordfetznschaedl.entities.events.PlayerHurtEvent;

/**
 * Kills, damage and flashes ({@code player_death}, {@code player_hurt}, {@code player_blind}).
 */
public final class CombatModule implements ParserModule {

    private final boolean includeDamage;

    /** Records kills, damage and flashes. */
    public CombatModule() {
        this(true);
    }

    /**
     * @param includeDamage whether {@link PlayerHurtEvent}s and {@link PlayerBlindEvent}s are recorded
     *                      (kills are always recorded)
     */
    public CombatModule(boolean includeDamage) {
        this.includeDamage = includeDamage;
    }

    @Override
    public void onGameEvent(RawGameEvent e, ParserContext ctx) {
        switch (e.name()) {
            case "player_death" -> {
                int attacker = e.getInt("attacker", -1);
                int victim = e.getInt("userid", -1);
                ctx.emit(new KillEvent(
                        e.tick(), ctx.currentRound(),
                        ctx.playerBySlot(attacker), ctx.teamBySlot(attacker),
                        ctx.playerBySlot(victim), ctx.teamBySlot(victim),
                        ctx.playerBySlot(e.getInt("assister", -1)),
                        e.getString("weapon"),
                        e.getBoolean("headshot"),
                        e.getInt("penetrated", 0) > 0,
                        e.getBoolean("thrusmoke"),
                        e.getBoolean("noscope"),
                        e.getBoolean("attackerblind"),
                        e.getBoolean("assistedflash"),
                        e.getBoolean("attackerinair"),
                        e.getFloat("distance", 0f)));
            }
            case "player_hurt" -> {
                if (includeDamage) {
                    ctx.emit(new PlayerHurtEvent(
                            e.tick(), ctx.currentRound(),
                            ctx.playerBySlot(e.getInt("attacker", -1)),
                            ctx.playerBySlot(e.getInt("userid", -1)),
                            e.getString("weapon"),
                            e.getInt("dmg_health", 0),
                            e.getInt("dmg_armor", 0),
                            e.getInt("health", 0),
                            e.getInt("armor", 0),
                            e.getInt("hitgroup", 0)));
                }
            }
            case "player_blind" -> {
                if (includeDamage) {
                    ctx.emit(new PlayerBlindEvent(
                            e.tick(), ctx.currentRound(),
                            ctx.playerBySlot(e.getInt("attacker", -1)),
                            ctx.playerBySlot(e.getInt("userid", -1)),
                            e.getFloat("blind_duration", 0f)));
                }
            }
            default -> {
            }
        }
    }
}
