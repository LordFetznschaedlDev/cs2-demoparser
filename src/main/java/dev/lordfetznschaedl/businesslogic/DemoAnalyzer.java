package dev.lordfetznschaedl.businesslogic;

import dev.lordfetznschaedl.businesslogic.demo.DemoFrame;
import dev.lordfetznschaedl.businesslogic.demo.DemoStreamProcessor;
import dev.lordfetznschaedl.businesslogic.demo.DemoVisitor;
import dev.lordfetznschaedl.businesslogic.demo.NetMessageType;
import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.businesslogic.entitystate.EntityListener;
import dev.lordfetznschaedl.businesslogic.entitystate.EntityManager;
import dev.lordfetznschaedl.businesslogic.gameevents.GameEventDecoder;
import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;
import dev.lordfetznschaedl.businesslogic.io.ProtoReader;
import dev.lordfetznschaedl.businesslogic.modules.MatchBuilder;
import dev.lordfetznschaedl.businesslogic.modules.ParserContext;
import dev.lordfetznschaedl.businesslogic.modules.ParserModule;
import dev.lordfetznschaedl.businesslogic.stringtables.StringTableManager;
import dev.lordfetznschaedl.entities.DemoInfo;
import dev.lordfetznschaedl.entities.Match;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Runs one parse: decodes the demo stream, keeps string tables and entity state up to date and
 * forwards game events and entity changes to the registered {@link ParserModule}s.
 * <p>
 * An instance is single-use; create a new one for every demo.
 */
public final class DemoAnalyzer implements DemoVisitor, EntityListener {

    private static final int MAX_WARNINGS = 100;

    private final List<ParserModule> modules;
    private final boolean strict;
    private final StringTableManager stringTables = new StringTableManager();
    private final GameEventDecoder gameEvents = new GameEventDecoder();
    private final EntityManager entities = new EntityManager();
    private final MatchBuilder match = new MatchBuilder();
    private final ParserContext context = new ParserContext(entities, match);

    private int currentTick = Integer.MIN_VALUE;
    private int lastTick;
    private int suppressedWarnings;
    private boolean entitiesBroken;

    // demo info
    private String mapName = "";
    private String serverName = "";
    private String clientName = "";
    private String gameDirectory = "";
    private int buildNumber;
    private int networkProtocol;
    private double tickInterval;
    private float playbackTime = -1;
    private int playbackTicks = -1;

    /**
     * @param modules modules in the order they are notified
     * @param strict  whether malformed data aborts the parse instead of being recorded as a warning
     */
    public DemoAnalyzer(List<ParserModule> modules, boolean strict) {
        this.modules = List.copyOf(modules);
        this.strict = strict;
        stringTables.addListener((table, entry) -> {
            if (table.name().equals("instancebaseline") && entry.value() != null) {
                entities.onBaseline(entry.key(), entry.value());
            }
        });
        entities.addListener(this);
    }

    public Match analyze(InputStream input) throws IOException {
        long start = System.nanoTime();
        for (ParserModule module : modules) {
            module.onStart(context);
        }
        new DemoStreamProcessor(this).process(input);
        for (ParserModule module : modules) {
            module.onFinish(context);
        }
        if (suppressedWarnings > 0) {
            match.addWarning(suppressedWarnings + " further warnings suppressed");
        }
        match.info(buildInfo());
        match.parseDuration(Duration.ofNanos(System.nanoTime() - start));
        return match.build();
    }

    // ------------------------------------------------------------------ DemoVisitor

    @Override
    public void onFrame(DemoFrame frame) {
        advanceTick(frame.tick());
        switch (frame.command()) {
            case FILE_HEADER -> readFileHeader(frame.payload());
            case FILE_INFO -> readFileInfo(frame.payload());
            case SEND_TABLES -> entities.onSendTables(frame.payload());
            case CLASS_INFO -> entities.onClassInfo(frame.payload());
            default -> {
            }
        }
    }

    @Override
    public void onStringTableSnapshot(byte[] payload, int tick) {
        guarded("string table snapshot", () -> stringTables.onSnapshot(payload));
    }

    @Override
    public void onNetMessage(int type, byte[] payload, int tick) {
        switch (type) {
            case NetMessageType.SVC_SERVER_INFO -> readServerInfo(payload);
            case NetMessageType.SVC_CREATE_STRING_TABLE -> guarded("string table", () -> stringTables.onCreate(payload));
            case NetMessageType.SVC_UPDATE_STRING_TABLE -> guarded("string table", () -> stringTables.onUpdate(payload));
            case NetMessageType.SVC_CLEAR_ALL_STRING_TABLES -> stringTables.clear();
            case NetMessageType.SVC_PACKET_ENTITIES -> {
                if (!entitiesBroken) {
                    try {
                        entities.onPacketEntities(payload, Math.max(tick, 0));
                    } catch (RuntimeException e) {
                        // entity state can not be trusted after a failed update
                        entitiesBroken = true;
                        failure("entity update failed, entity tracking stopped", e);
                    }
                }
            }
            case NetMessageType.GE_SOURCE1_LEGACY_GAME_EVENT_LIST -> gameEvents.readDescriptorList(payload);
            case NetMessageType.GE_SOURCE1_LEGACY_GAME_EVENT -> guarded("game event", () -> {
                RawGameEvent event = gameEvents.readEvent(payload, Math.max(tick, 0));
                if (event != null) {
                    for (ParserModule module : modules) {
                        module.onGameEvent(event, context);
                    }
                }
            });
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ EntityListener

    @Override
    public void onEntityCreated(Entity entity, int tick) {
        for (ParserModule module : modules) {
            module.onEntityCreated(entity, context);
        }
    }

    @Override
    public void onEntityUpdated(Entity entity, Set<String> changed, int tick) {
        for (ParserModule module : modules) {
            module.onEntityUpdated(entity, changed, context);
        }
    }

    @Override
    public void onEntityDeleted(Entity entity, int tick) {
        for (ParserModule module : modules) {
            module.onEntityDeleted(entity, context);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void advanceTick(int tick) {
        if (tick < 0 || tick == currentTick) {
            return;
        }
        currentTick = tick;
        lastTick = Math.max(lastTick, tick);
        context.tick(tick);
        for (ParserModule module : modules) {
            module.onTick(tick, context);
        }
    }

    private void guarded(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            failure(what + " could not be decoded", e);
        }
    }

    private void failure(String message, RuntimeException e) {
        if (strict) {
            throw e instanceof DemoFormatException dfe ? dfe : new DemoFormatException(message, e);
        }
        if (match.warningCount() < MAX_WARNINGS) {
            context.warn(message + ": " + e.getMessage());
        } else {
            suppressedWarnings++;
        }
    }

    /** CDemoFileHeader. */
    private void readFileHeader(byte[] payload) {
        ProtoReader r = new ProtoReader(payload);
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 2 -> networkProtocol = r.readInt32();
                case 3 -> serverName = r.readString();
                case 4 -> clientName = r.readString();
                case 5 -> mapName = r.readString();
                case 6 -> gameDirectory = r.readString();
                case 13 -> buildNumber = r.readInt32();
                default -> r.skip();
            }
        }
    }

    /** CDemoFileInfo. */
    private void readFileInfo(byte[] payload) {
        ProtoReader r = new ProtoReader(payload);
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 1 -> playbackTime = r.readFloat();
                case 2 -> playbackTicks = r.readInt32();
                default -> r.skip();
            }
        }
    }

    /** CSVCMsg_ServerInfo. */
    private void readServerInfo(byte[] payload) {
        ProtoReader r = new ProtoReader(payload);
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 11 -> entities.onServerInfo(r.readInt32());
                case 13 -> tickInterval = r.readFloat();
                case 15 -> {
                    String map = r.readString();
                    if (mapName.isEmpty()) {
                        mapName = map;
                    }
                }
                default -> r.skip();
            }
        }
    }

    private DemoInfo buildInfo() {
        int ticks = playbackTicks > 0 ? playbackTicks : lastTick;
        double tickRate;
        if (tickInterval > 0) {
            tickRate = Math.round(1.0 / tickInterval * 100.0) / 100.0;
        } else if (playbackTime > 0 && playbackTicks > 0) {
            tickRate = Math.round(playbackTicks / playbackTime);
        } else {
            tickRate = 64;
        }
        Duration duration = playbackTime > 0
                ? Duration.ofMillis((long) (playbackTime * 1000))
                : Duration.ofMillis((long) (ticks / tickRate * 1000));
        return new DemoInfo(mapName, serverName, clientName, gameDirectory, buildNumber, networkProtocol,
                tickRate, ticks, duration);
    }
}
