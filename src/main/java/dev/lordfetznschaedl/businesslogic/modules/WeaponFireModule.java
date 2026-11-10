package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.entities.events.WeaponFireEvent;

/**
 * Every shot ({@code weapon_fire}). Not part of the default modules because of the event volume.
 */
public final class WeaponFireModule implements ParserModule {

    @Override
    public void onGameEvent(RawGameEvent e, ParserContext ctx) {
        if (e.name().equals("weapon_fire")) {
            String weapon = e.getString("weapon");
            if (weapon != null && weapon.startsWith("weapon_")) {
                weapon = weapon.substring("weapon_".length());
            }
            ctx.emit(new WeaponFireEvent(e.tick(), ctx.currentRound(), ctx.playerBySlot(e.getInt("userid", -1)), weapon));
        }
    }
}
