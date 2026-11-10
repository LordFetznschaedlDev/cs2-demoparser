package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.entities.Team;
import dev.lordfetznschaedl.entities.events.PlayerPositionEvent;

/**
 * Samples the position, view angles and health of every living player at a fixed tick interval
 * and records them as {@link PlayerPositionEvent}s.
 * <p>
 * Not part of the default modules because of the event volume: with the default interval of
 * {@value #DEFAULT_INTERVAL_TICKS} ticks (4 samples per second at 64 tick) a competitive match yields
 * roughly 50,000 – 100,000 samples.
 * <p>
 * CS2 networks a position as a grid cell plus an offset within it ({@code CBodyComponent.m_cellX} and
 * {@code CBodyComponent.m_vecX}); the world coordinate is {@code cell * 512 - 16384 + offset}.
 */
public final class PlayerPositionModule implements ParserModule {

    public static final int DEFAULT_INTERVAL_TICKS = 16;

    private static final String PAWN_CLASS = "CCSPlayerPawn";
    private static final float CELL_WIDTH = 1 << 9;
    private static final float MAX_COORDINATE = 16384f;

    private final int intervalTicks;
    private final boolean includeWarmup;
    private int lastTick = -1;
    private long lastSampleTick = Long.MIN_VALUE / 2;

    /** Samples every {@value #DEFAULT_INTERVAL_TICKS} ticks during the match (not in warmup). */
    public PlayerPositionModule() {
        this(DEFAULT_INTERVAL_TICKS, false);
    }

    /**
     * @param intervalTicks ticks between two samples; {@code 1} records every tick
     * @param includeWarmup whether positions are also recorded before the match started
     */
    public PlayerPositionModule(int intervalTicks, boolean includeWarmup) {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("intervalTicks must be at least 1");
        }
        this.intervalTicks = intervalTicks;
        this.includeWarmup = includeWarmup;
    }

    /**
     * Called before the data of {@code tick} is applied, so the entity state is that of the
     * previous tick, which is the tick the sample is recorded for.
     */
    @Override
    public void onTick(int tick, ParserContext ctx) {
        if (lastTick >= 0 && lastTick - lastSampleTick >= intervalTicks) {
            sample(lastTick, ctx);
        }
        lastTick = tick;
    }

    @Override
    public void onFinish(ParserContext ctx) {
        if (lastTick >= 0 && lastTick - lastSampleTick >= intervalTicks) {
            sample(lastTick, ctx);
        }
    }

    private void sample(int tick, ParserContext ctx) {
        int round = ctx.currentRound();
        if (round == 0 && !includeWarmup) {
            return;
        }
        lastSampleTick = tick;
        for (Entity controller : ctx.controllers()) {
            Team team = Team.fromNumber(controller.getInt("m_iTeamNum", 0));
            if (!team.isPlaying()) {
                continue;
            }
            Entity pawn = ctx.entities().byHandle(controller.getLong("m_hPlayerPawn", -1));
            if (pawn == null || !pawn.className().equals(PAWN_CLASS) || !isAlive(pawn)) {
                continue;
            }
            float pitch = 0f;
            float yaw = 0f;
            if (pawn.get("m_angEyeAngles") instanceof float[] angles && angles.length >= 2) {
                pitch = angles[0];
                yaw = angles[1];
            }
            ctx.emit(new PlayerPositionEvent(tick, round, ctx.player(controller), team,
                    coordinate(pawn, "X"), coordinate(pawn, "Y"), coordinate(pawn, "Z"),
                    pitch, yaw, pawn.getInt("m_iHealth", 0)));
        }
    }

    private static boolean isAlive(Entity pawn) {
        return pawn.getInt("m_lifeState", 0) == 0 && pawn.getInt("m_iHealth", 0) > 0;
    }

    private static float coordinate(Entity pawn, String axis) {
        int cell = pawn.getInt("CBodyComponent.m_cell" + axis, 0);
        float offset = pawn.getFloat("CBodyComponent.m_vec" + axis, 0f);
        return cell * CELL_WIDTH - MAX_COORDINATE + offset;
    }
}
