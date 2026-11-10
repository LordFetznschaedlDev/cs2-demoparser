package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.entities.BombSite;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.events.BombBeginDefuseEvent;
import dev.lordfetznschaedl.entities.events.BombBeginPlantEvent;
import dev.lordfetznschaedl.entities.events.BombDefusedEvent;
import dev.lordfetznschaedl.entities.events.BombDroppedEvent;
import dev.lordfetznschaedl.entities.events.BombExplodedEvent;
import dev.lordfetznschaedl.entities.events.BombPickupEvent;
import dev.lordfetznschaedl.entities.events.BombPlantedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Bomb plants, defuses, explosions and bomb carrier changes.
 * <p>
 * The {@code site} key of bomb events is the entity index of the bomb site trigger. It is mapped
 * to A/B through the {@code m_nBombSite} property of the planted C4 ({@code 0} = A, {@code 1} = B),
 * and remembered for later events on the same site.
 */
public final class BombModule implements ParserModule {

    private final Map<Integer, BombSite> sitesByTrigger = new HashMap<>();
    private BombSite lastPlantedSite = BombSite.UNKNOWN;

    @Override
    public void onGameEvent(RawGameEvent e, ParserContext ctx) {
        switch (e.name()) {
            case "bomb_beginplant" -> ctx.emit(new BombBeginPlantEvent(e.tick(), ctx.currentRound(),
                    player(e, ctx), siteOf(e.getInt("site", -1), -1, ctx)));
            case "bomb_planted" -> {
                lastPlantedSite = siteOf(e.getInt("site", -1), e.getLong("c4", -1), ctx);
                ctx.emit(new BombPlantedEvent(e.tick(), ctx.currentRound(), player(e, ctx), lastPlantedSite));
            }
            case "bomb_begindefuse" -> ctx.emit(new BombBeginDefuseEvent(e.tick(), ctx.currentRound(),
                    player(e, ctx), e.getBoolean("haskit")));
            case "bomb_defused" -> ctx.emit(new BombDefusedEvent(e.tick(), ctx.currentRound(), player(e, ctx),
                    siteOf(e.getInt("site", -1), e.getLong("c4", -1), ctx)));
            case "bomb_exploded" -> ctx.emit(new BombExplodedEvent(e.tick(), ctx.currentRound(),
                    siteOf(e.getInt("site", -1), e.getLong("c4", -1), ctx)));
            case "bomb_dropped" -> ctx.emit(new BombDroppedEvent(e.tick(), ctx.currentRound(), player(e, ctx)));
            case "bomb_pickup" -> ctx.emit(new BombPickupEvent(e.tick(), ctx.currentRound(), player(e, ctx)));
            default -> {
            }
        }
    }

    private static Player player(RawGameEvent e, ParserContext ctx) {
        if (e.has("userid")) {
            return ctx.playerBySlot(e.getInt("userid", -1));
        }
        return e.has("userid_pawn") ? ctx.playerByPawnHandle(e.getLong("userid_pawn", -1)) : null;
    }

    private BombSite siteOf(int triggerIndex, long c4Handle, ParserContext ctx) {
        BombSite known = sitesByTrigger.get(triggerIndex);
        if (known != null) {
            return known;
        }
        BombSite site = siteByPosition(ctx.entities().get(triggerIndex), ctx);
        if (site == BombSite.UNKNOWN) {
            Entity c4 = c4Handle >= 0 ? ctx.entities().byHandle(c4Handle) : null;
            if (c4 != null && c4.className().equals("CPlantedC4") && c4.get("m_nBombSite") instanceof Number n) {
                site = n.intValue() == 0 ? BombSite.A : n.intValue() == 1 ? BombSite.B : BombSite.UNKNOWN;
            }
        }
        if (site != BombSite.UNKNOWN && triggerIndex >= 0) {
            sitesByTrigger.put(triggerIndex, site);
        }
        if (site == BombSite.UNKNOWN && c4Handle >= 0) {
            return lastPlantedSite;
        }
        return site;
    }

    /**
     * The center of the trigger's bounds coincides with the site centers the player resource
     * networks ({@code m_bombsiteCenterA/B}).
     */
    private static BombSite siteByPosition(Entity trigger, ParserContext ctx) {
        if (trigger == null || !(trigger.get("m_vecMins") instanceof float[] min)
                || !(trigger.get("m_vecMaxs") instanceof float[] max)) {
            return BombSite.UNKNOWN;
        }
        var resources = ctx.entities().byClass("CCSPlayerResource");
        if (resources.isEmpty()
                || !(resources.getFirst().get("m_bombsiteCenterA") instanceof float[] a)
                || !(resources.getFirst().get("m_bombsiteCenterB") instanceof float[] b)) {
            return BombSite.UNKNOWN;
        }
        float[] center = new float[3];
        for (int i = 0; i < 3; i++) {
            center[i] = (min[i] + max[i]) / 2f;
        }
        return distanceSquared(center, a) <= distanceSquared(center, b) ? BombSite.A : BombSite.B;
    }

    private static float distanceSquared(float[] p, float[] q) {
        float sum = 0;
        for (int i = 0; i < 3; i++) {
            sum += (p[i] - q[i]) * (p[i] - q[i]);
        }
        return sum;
    }
}
