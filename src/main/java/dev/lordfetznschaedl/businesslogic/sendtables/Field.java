package dev.lordfetznschaedl.businesslogic.sendtables;

import java.util.List;

/**
 * One field of a flattened serializer, including how it is laid out on the wire.
 */
public final class Field {

    /** How a field's value(s) are addressed by field paths. */
    public enum Model {
        /** A single value. */
        SIMPLE,
        /** A fixed size array of simple values: {@code [field, index]}. */
        FIXED_ARRAY,
        /** An embedded (pointer) serializer: {@code [field]} = present flag, {@code [field, sub...]} = sub field. */
        FIXED_TABLE,
        /** A growable array of simple values: {@code [field]} = length, {@code [field, index]} = element. */
        VARIABLE_ARRAY,
        /** A growable array of serializers: {@code [field]} = length, {@code [field, index, sub...]} = sub field. */
        VARIABLE_TABLE
    }

    /** Property name; qualified with the send node if the plain name is not unique in its serializer. */
    String name;
    /** Path of the structure the field belongs to, e.g. {@code m_AttributeManager.m_Item}. */
    String sendNode;
    final FieldType type;
    final String varType;
    final int bitCount;
    final Float lowValue;
    final Float highValue;
    final int encodeFlags;
    final String encoder;
    final String serializerName;

    Model model = Model.SIMPLE;
    Serializer serializer;
    /** For polymorphic pointers: index 0 is the declared type, followed by the possible sub types. */
    List<Serializer> polymorphicSerializers = List.of();
    FieldDecoder decoder;
    FieldDecoder baseDecoder;
    FieldDecoder childDecoder;

    Field(String name, String varType, int bitCount, Float lowValue, Float highValue, int encodeFlags,
          String encoder, String serializerName) {
        this.name = name;
        this.varType = varType;
        this.type = FieldType.parse(varType);
        this.bitCount = bitCount;
        this.lowValue = lowValue;
        this.highValue = highValue;
        this.encodeFlags = encodeFlags;
        this.encoder = encoder;
        this.serializerName = serializerName;
    }

    public String name() {
        return name;
    }

    public String varType() {
        return varType;
    }

    public Model model() {
        return model;
    }

    public Serializer serializer() {
        return serializer;
    }

    public boolean isPolymorphic() {
        return !polymorphicSerializers.isEmpty();
    }

    /** Returns the decoder for the field path {@code path} whose component {@code pos - 1} selected this field. */
    FieldDecoder decoderFor(FieldPath path, int pos, Serializer.DecodeContext ctx) {
        return switch (model) {
            case SIMPLE, FIXED_ARRAY -> decoder;
            case FIXED_TABLE -> path.last() == pos - 1 ? baseDecoder : tableSerializer(path, pos, ctx).decoderFor(path, pos, ctx);
            case VARIABLE_ARRAY -> path.last() == pos ? childDecoder : baseDecoder;
            case VARIABLE_TABLE -> path.last() >= pos + 1 ? serializer.decoderFor(path, pos + 1, ctx) : baseDecoder;
        };
    }

    /** Appends the property name for the field path to {@code out}. */
    void appendName(FieldPath path, int pos, StringBuilder out, Serializer.DecodeContext ctx) {
        out.append(name);
        switch (model) {
            case SIMPLE -> {
            }
            case FIXED_ARRAY, VARIABLE_ARRAY -> {
                if (path.last() >= pos) {
                    out.append('.').append(path.get(pos));
                }
            }
            case FIXED_TABLE -> {
                if (path.last() >= pos) {
                    out.append('.');
                    tableSerializer(path, pos, ctx).appendName(path, pos, out, ctx);
                }
            }
            case VARIABLE_TABLE -> {
                if (path.last() >= pos) {
                    out.append('.').append(path.get(pos));
                    if (path.last() >= pos + 1) {
                        out.append('.');
                        serializer.appendName(path, pos + 1, out, ctx);
                    }
                }
            }
        }
    }

    /** The serializer of an embedded table; for polymorphic pointers the sub type the entity currently uses. */
    private Serializer tableSerializer(FieldPath path, int pos, Serializer.DecodeContext ctx) {
        if (!isPolymorphic() || ctx == null) {
            return serializer;
        }
        Object chosen = ctx.valueAt(path, pos - 1);
        if (chosen instanceof Integer index && index >= 0 && index < polymorphicSerializers.size()) {
            return polymorphicSerializers.get(index);
        }
        return serializer;
    }

    @Override
    public String toString() {
        return name + " : " + varType + " (" + model + ")";
    }
}
