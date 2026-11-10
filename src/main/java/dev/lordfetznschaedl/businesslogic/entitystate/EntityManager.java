package dev.lordfetznschaedl.businesslogic.entitystate;

import dev.lordfetznschaedl.businesslogic.io.BitReader;
import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;
import dev.lordfetznschaedl.businesslogic.io.ProtoReader;
import dev.lordfetznschaedl.businesslogic.sendtables.SendTableParser;
import dev.lordfetznschaedl.businesslogic.sendtables.Serializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Maintains the state of all networked entities by applying {@code CSVCMsg_PacketEntities} messages.
 */
public final class EntityManager {

    /** Number of bits of an entity handle that encode the entity index. */
    public static final int INDEX_BITS = 14;
    public static final int INDEX_MASK = (1 << INDEX_BITS) - 1;
    /** Handle value meaning "no entity". */
    public static final int INVALID_HANDLE = 0xFFFFFF;

    private Map<String, Serializer> serializers = Map.of();
    private final Map<Integer, ServerClass> classes = new HashMap<>();
    private final Map<Integer, byte[]> rawBaselines = new HashMap<>();
    private final Map<Integer, Map<String, Object>> decodedBaselines = new HashMap<>();
    private final Map<Integer, Entity> entities = new HashMap<>();
    private final Map<String, Map<Integer, Entity>> entitiesByClass = new HashMap<>();
    private final List<EntityListener> listeners = new ArrayList<>();
    private int classIdBits;
    private boolean receivedFullSnapshot;

    public void addListener(EntityListener listener) {
        listeners.add(listener);
    }

    public Entity get(int index) {
        return entities.get(index);
    }

    /** Resolves a {@code CHandle} property value to its entity (or {@code null}). */
    public Entity byHandle(long handle) {
        if (handle == INVALID_HANDLE || handle < 0) {
            return null;
        }
        return entities.get((int) (handle & INDEX_MASK));
    }

    public Collection<Entity> all() {
        return entities.values();
    }

    /** All entities of a class, ordered by entity index. */
    public List<Entity> byClass(String className) {
        Map<Integer, Entity> ofClass = entitiesByClass.get(className);
        return ofClass == null ? List.of() : List.copyOf(ofClass.values());
    }

    private void register(Entity entity) {
        Entity previous = entities.put(entity.index(), entity);
        if (previous != null) {
            unregisterClass(previous);
        }
        entitiesByClass.computeIfAbsent(entity.className(), k -> new TreeMap<>()).put(entity.index(), entity);
    }

    private Entity unregister(int index) {
        Entity removed = entities.remove(index);
        if (removed != null) {
            unregisterClass(removed);
        }
        return removed;
    }

    private void unregisterClass(Entity entity) {
        Map<Integer, Entity> ofClass = entitiesByClass.get(entity.className());
        if (ofClass != null && ofClass.get(entity.index()) == entity) {
            ofClass.remove(entity.index());
        }
    }

    public boolean isReady() {
        return !classes.isEmpty() && classIdBits > 0;
    }

    /** Handles the {@code DEM_SendTables} frame. */
    public void onSendTables(byte[] payload) {
        serializers = SendTableParser.parse(payload);
    }

    /** Handles the {@code DEM_ClassInfo} frame ({@code CDemoClassInfo}). */
    public void onClassInfo(byte[] payload) {
        ProtoReader r = new ProtoReader(payload);
        while (r.next()) {
            if (r.fieldNumber() != 1) {
                r.skip();
                continue;
            }
            ProtoReader c = r.readMessage();
            int id = -1;
            String networkName = "";
            while (c.next()) {
                switch (c.fieldNumber()) {
                    case 1 -> id = c.readInt32();
                    case 2 -> networkName = c.readString();
                    default -> c.skip();
                }
            }
            classes.put(id, new ServerClass(id, networkName, serializers.get(networkName)));
        }
        if (classIdBits == 0) {
            classIdBits = bitsFor(classes.size());
        }
    }

    /** Handles {@code CSVCMsg_ServerInfo} to learn the size of class ids. */
    public void onServerInfo(int maxClasses) {
        if (maxClasses > 0) {
            classIdBits = bitsFor(maxClasses);
        }
    }

    /** Called for entries of the {@code instancebaseline} string table (key = class id). */
    public void onBaseline(String key, byte[] value) {
        try {
            int classId = Integer.parseInt(key);
            rawBaselines.put(classId, value);
            decodedBaselines.remove(classId);
        } catch (NumberFormatException ignored) {
            // not a class baseline
        }
    }

    /** Handles {@code CSVCMsg_PacketEntities}. */
    public void onPacketEntities(byte[] message, int tick) {
        int updatedEntries = 0;
        boolean isDelta = false;
        byte[] data = null;
        ProtoReader r = new ProtoReader(message);
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 2 -> updatedEntries = r.readInt32();
                case 3 -> isDelta = r.readBool();
                case 7 -> data = r.readBytes();
                default -> r.skip();
            }
        }
        if (data == null || !isReady()) {
            return;
        }
        if (!isDelta) {
            // later full snapshots (from DEM_FullPacket) only repeat state we already have
            if (receivedFullSnapshot) {
                return;
            }
            receivedFullSnapshot = true;
        }

        BitReader bits = new BitReader(data);
        int index = -1;
        for (int i = 0; i < updatedEntries; i++) {
            index += bits.readUBitVar() + 1;
            int command = bits.readBits(2);
            if ((command & 1) == 0) {
                if ((command & 2) != 0) {
                    create(index, bits, tick);
                } else {
                    update(index, bits, tick);
                }
            } else if ((command & 2) != 0) {
                Entity removed = unregister(index);
                if (removed != null) {
                    for (EntityListener l : listeners) {
                        l.onEntityDeleted(removed, tick);
                    }
                }
            }
            // command == 1: entity left the PVS, state is kept
        }
    }

    private void create(int index, BitReader bits, int tick) {
        int classId = bits.readBits(classIdBits);
        int serial = bits.readBits(17);
        bits.readVarInt32(); // unknown, always present in CS2

        ServerClass serverClass = classes.get(classId);
        if (serverClass == null || serverClass.serializer() == null) {
            throw new DemoFormatException("Unknown entity class id " + classId);
        }
        Entity entity = new Entity(index, serial, serverClass, baselineOf(serverClass));
        serverClass.serializer().readFields(bits, entity::get, entity::set);
        register(entity);
        for (EntityListener l : listeners) {
            l.onEntityCreated(entity, tick);
        }
    }

    private void update(int index, BitReader bits, int tick) {
        Entity entity = entities.get(index);
        if (entity == null) {
            throw new DemoFormatException("Update for unknown entity " + index);
        }
        if (listeners.isEmpty()) {
            entity.serverClass().serializer().readFields(bits, entity::get, entity::set);
            return;
        }
        Set<String> changed = new HashSet<>();
        entity.serverClass().serializer().readFields(bits, entity::get, (name, value) -> {
            entity.set(name, value);
            changed.add(name);
        });
        for (EntityListener l : listeners) {
            l.onEntityUpdated(entity, changed, tick);
        }
    }

    private Map<String, Object> baselineOf(ServerClass serverClass) {
        Map<String, Object> cached = decodedBaselines.get(serverClass.id());
        if (cached != null) {
            return cached;
        }
        Map<String, Object> decoded = new HashMap<>();
        byte[] raw = rawBaselines.get(serverClass.id());
        if (raw != null && raw.length > 0) {
            try {
                serverClass.serializer().readFields(new BitReader(raw), decoded::get, decoded::put);
            } catch (RuntimeException e) {
                throw new DemoFormatException("Failed to decode baseline of " + serverClass.name(), e);
            }
        }
        decodedBaselines.put(serverClass.id(), decoded);
        return decoded;
    }

    private static int bitsFor(int count) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(count, 1));
    }
}
