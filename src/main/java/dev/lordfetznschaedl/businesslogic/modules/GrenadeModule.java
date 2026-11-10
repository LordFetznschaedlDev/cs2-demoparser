package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.events.GrenadeDetonateEvent;
import dev.lordfetznschaedl.entities.events.GrenadeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grenade detonations (HE, flash, smoke, decoy) and molotov/incendiary fires.
 * <p>
 * Fire events only reference the inferno entity, which is created right after the event; its
 * owner is the thrower's pawn. Fire events are therefore emitted once that entity exists
 * (or at the latest on the next tick).
 */
public final class GrenadeModule implements ParserModule {

    private record PendingFire(RawGameEvent event, int round) {
    }

    private final Map<Integer, PendingFire> pendingFires = new LinkedHashMap<>();

    @Override
    public void onGameEvent(RawGameEvent e, ParserContext ctx) {
        GrenadeType type = switch (e.name()) {
            case "hegrenade_detonate" -> GrenadeType.HE_GRENADE;
            case "flashbang_detonate" -> GrenadeType.FLASHBANG;
            case "smokegrenade_detonate" -> GrenadeType.SMOKE;
            case "decoy_detonate" -> GrenadeType.DECOY;
            case "inferno_startburn" -> GrenadeType.MOLOTOV;
            default -> null;
        };
        if (type == null) {
            return;
        }
        if (type == GrenadeType.MOLOTOV) {
            int index = e.getInt("entityid", -1);
            Entity inferno = ctx.entities().get(index);
            if (inferno == null || !inferno.className().equals("CInferno")) {
                pendingFires.put(index, new PendingFire(e, ctx.currentRound()));
                return;
            }
            emit(e, ctx.currentRound(), GrenadeType.MOLOTOV, ownerOf(inferno, ctx), ctx);
            return;
        }
        emit(e, ctx.currentRound(), type, thrower(e, ctx), ctx);
    }

    @Override
    public void onEntityCreated(Entity entity, ParserContext ctx) {
        if (!pendingFires.isEmpty() && entity.className().equals("CInferno")) {
            PendingFire fire = pendingFires.remove(entity.index());
            if (fire != null) {
                emit(fire.event(), fire.round(), GrenadeType.MOLOTOV, ownerOf(entity, ctx), ctx);
            }
        }
    }

    @Override
    public void onTick(int tick, ParserContext ctx) {
        flushPending(ctx);
    }

    @Override
    public void onFinish(ParserContext ctx) {
        flushPending(ctx);
    }

    private void flushPending(ParserContext ctx) {
        if (pendingFires.isEmpty()) {
            return;
        }
        List<PendingFire> stale = new ArrayList<>(pendingFires.values());
        pendingFires.clear();
        for (PendingFire fire : stale) {
            emit(fire.event(), fire.round(), GrenadeType.MOLOTOV, null, ctx);
        }
    }

    private static void emit(RawGameEvent e, int round, GrenadeType type, Player thrower, ParserContext ctx) {
        ctx.emit(new GrenadeDetonateEvent(e.tick(), round, type, thrower,
                e.getFloat("x", 0f), e.getFloat("y", 0f), e.getFloat("z", 0f)));
    }

    private static Player ownerOf(Entity inferno, ParserContext ctx) {
        return inferno.get("m_hOwnerEntity") instanceof Number owner ? ctx.playerByPawnHandle(owner.longValue()) : null;
    }

    private static Player thrower(RawGameEvent e, ParserContext ctx) {
        if (e.has("userid")) {
            return ctx.playerBySlot(e.getInt("userid", -1));
        }
        return e.has("userid_pawn") ? ctx.playerByPawnHandle(e.getLong("userid_pawn", -1)) : null;
    }
}
