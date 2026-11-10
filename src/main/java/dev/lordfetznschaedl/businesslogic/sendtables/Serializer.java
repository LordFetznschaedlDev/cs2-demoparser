package dev.lordfetznschaedl.businesslogic.sendtables;

import dev.lordfetznschaedl.businesslogic.io.BitReader;
import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A flattened serializer describing the network layout of an entity class (or an embedded struct).
 */
public final class Serializer {

    private final String name;
    private final int version;
    private final List<Field> fields;
    private final Map<Long, String> nameCache = new HashMap<>();

    Serializer(String name, int version, List<Field> fields) {
        this.name = name;
        this.version = version;
        this.fields = fields;
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public List<Field> fields() {
        return fields;
    }

    /**
     * Reads one entity update (changed field paths followed by their values) and passes every
     * decoded property to {@code sink} as (name, value).
     *
     * @param state current properties of the entity, needed to resolve polymorphic sub-tables
     */
    public void readFields(BitReader reader, Function<String, Object> state, BiConsumer<String, Object> sink) {
        List<FieldPath> paths = FieldPathReader.read(reader);
        for (FieldPath path : paths) {
            DecodeContext ctx = new DecodeContext(this, state);
            FieldDecoder decoder = decoderFor(path, 0, ctx);
            Object value = decoder.decode(reader);
            sink.accept(nameOf(path, ctx), value);
        }
    }

    /** Resolution context: gives polymorphic fields access to the entity's chosen sub-type. */
    static final class DecodeContext {
        final Serializer root;
        final Function<String, Object> state;
        boolean polymorphic;

        DecodeContext(Serializer root, Function<String, Object> state) {
            this.root = root;
            this.state = state;
        }

        /** Returns the value currently stored for the property addressed by {@code path[0..last]}. */
        Object valueAt(FieldPath path, int last) {
            polymorphic = true;
            if (state == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            root.appendName(path.prefix(last), 0, sb, null);
            return state.apply(sb.toString());
        }
    }

    FieldDecoder decoderFor(FieldPath path, int pos, DecodeContext ctx) {
        int index = path.get(pos);
        if (index < 0 || index >= fields.size()) {
            throw new DemoFormatException("Field path " + path + " out of range for serializer " + name);
        }
        return fields.get(index).decoderFor(path, pos + 1, ctx);
    }

    void appendName(FieldPath path, int pos, StringBuilder out, DecodeContext ctx) {
        int index = path.get(pos);
        if (index < 0 || index >= fields.size()) {
            throw new DemoFormatException("Field path " + path + " out of range for serializer " + name);
        }
        fields.get(index).appendName(path, pos + 1, out, ctx);
    }

    /** Resolves the dotted property name of a field path, e.g. {@code m_pGameRules.m_totalRoundsPlayed}. */
    public String nameOf(FieldPath path) {
        return nameOf(path, new DecodeContext(this, null));
    }

    String nameOf(FieldPath path, DecodeContext ctx) {
        long key = ctx.polymorphic ? -1 : path.packedKey();
        if (key >= 0) {
            String cached = nameCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        StringBuilder sb = new StringBuilder(48);
        appendName(path, 0, sb, ctx);
        String resolved = sb.toString();
        if (key >= 0 && !ctx.polymorphic) {
            nameCache.put(key, resolved);
        }
        return resolved;
    }

    @Override
    public String toString() {
        return name + "(" + version + ")";
    }
}
