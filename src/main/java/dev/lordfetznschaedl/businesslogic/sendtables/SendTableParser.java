package dev.lordfetznschaedl.businesslogic.sendtables;

import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;
import dev.lordfetznschaedl.businesslogic.io.ProtoReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds {@link Serializer}s from the {@code CDemoSendTables} frame, which wraps a
 * length-prefixed {@code CSVCMsg_FlattenedSerializer}.
 */
public final class SendTableParser {

    /** Types that are embedded tables even though they are not declared as pointers. */
    private static final Set<String> POINTER_TYPES = Set.of(
            "CBodyComponent", "CLightComponent", "CPhysicsComponent", "CRenderComponent", "CPlayerLocalData");

    private record RawField(int varTypeSym, int varNameSym, int bitCount, Float low, Float high, int encodeFlags,
                            int serializerNameSym, int encoderSym, List<Integer> polymorphicSyms) {
    }

    private SendTableParser() {
    }

    public static Map<String, Serializer> parse(byte[] sendTablesFrame) {
        // CDemoSendTables: data = 1 (varint length + CSVCMsg_FlattenedSerializer)
        byte[] data = null;
        ProtoReader outer = new ProtoReader(sendTablesFrame);
        while (outer.next()) {
            if (outer.fieldNumber() == 1) {
                data = outer.readBytes();
            } else {
                outer.skip();
            }
        }
        if (data == null) {
            throw new DemoFormatException("Send tables frame without data");
        }
        int pos = 0;
        int length = 0;
        for (int shift = 0; ; shift += 7) {
            int b = data[pos++] & 0xFF;
            length |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return parseFlattenedSerializer(new ProtoReader(data, pos, length));
    }

    private static Map<String, Serializer> parseFlattenedSerializer(ProtoReader r) {
        List<ProtoReader> serializerMessages = new ArrayList<>();
        List<String> symbols = new ArrayList<>();
        List<RawField> rawFields = new ArrayList<>();
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 1 -> serializerMessages.add(r.readMessage());
                case 2 -> symbols.add(r.readString());
                case 3 -> rawFields.add(readField(r.readMessage()));
                default -> r.skip();
            }
        }

        Map<String, Serializer> serializers = new HashMap<>();
        Map<Integer, Field> fieldCache = new HashMap<>();
        for (ProtoReader s : serializerMessages) {
            int nameSym = 0;
            int version = 0;
            List<Integer> fieldIndices = new ArrayList<>();
            while (s.next()) {
                switch (s.fieldNumber()) {
                    case 1 -> nameSym = s.readInt32();
                    case 2 -> version = s.readInt32();
                    case 3 -> {
                        if (s.wireType() == ProtoReader.WIRE_LENGTH_DELIMITED) {
                            ProtoReader packed = s.readMessage();
                            while (packed.hasRemaining()) {
                                fieldIndices.add((int) packed.readPackedVarint());
                            }
                        } else {
                            fieldIndices.add(s.readInt32());
                        }
                    }
                    default -> s.skip();
                }
            }
            List<Field> fields = new ArrayList<>(fieldIndices.size());
            for (int index : fieldIndices) {
                Field field = fieldCache.get(index);
                if (field == null) {
                    field = createField(rawFields.get(index), symbols, serializers);
                    fieldCache.put(index, field);
                }
                fields.add(field);
            }
            String name = symbols.get(nameSym);
            serializers.put(name, new Serializer(name, version, List.copyOf(fields)));
        }
        return serializers;
    }

    private static RawField readField(ProtoReader f) {
        int varType = -1;
        int varName = -1;
        int bitCount = 0;
        Float low = null;
        Float high = null;
        int flags = 0;
        int serializerName = -1;
        int encoder = -1;
        List<Integer> polymorphic = new ArrayList<>();
        while (f.next()) {
            switch (f.fieldNumber()) {
                case 1 -> varType = f.readInt32();
                case 2 -> varName = f.readInt32();
                case 3 -> bitCount = f.readInt32();
                case 4 -> low = f.readFloat();
                case 5 -> high = f.readFloat();
                case 6 -> flags = f.readInt32();
                case 7 -> serializerName = f.readInt32();
                case 10 -> encoder = f.readInt32();
                case 11 -> {
                    ProtoReader type = f.readMessage();
                    while (type.next()) {
                        if (type.fieldNumber() == 1) {
                            polymorphic.add(type.readInt32());
                        } else {
                            type.skip();
                        }
                    }
                }
                default -> f.skip();
            }
        }
        return new RawField(varType, varName, bitCount, low, high, flags, serializerName, encoder, polymorphic);
    }

    private static Field createField(RawField raw, List<String> symbols, Map<String, Serializer> serializers) {
        String serializerName = raw.serializerNameSym() >= 0 ? symbols.get(raw.serializerNameSym()) : null;
        Field field = new Field(
                symbols.get(raw.varNameSym()),
                symbols.get(raw.varTypeSym()),
                raw.bitCount(), raw.low(), raw.high(), raw.encodeFlags(),
                raw.encoderSym() >= 0 ? symbols.get(raw.encoderSym()) : null,
                serializerName);

        if (serializerName != null) {
            field.serializer = serializers.get(serializerName);
            if (field.serializer == null) {
                throw new DemoFormatException("Serializer " + serializerName + " referenced before definition");
            }
            if (!raw.polymorphicSyms().isEmpty()) {
                // polymorphic pointer: presence bit followed by the index of the concrete type
                List<Serializer> types = new ArrayList<>();
                types.add(field.serializer);
                for (int sym : raw.polymorphicSyms()) {
                    Serializer type = serializers.get(symbols.get(sym));
                    types.add(type != null ? type : field.serializer);
                }
                field.polymorphicSerializers = List.copyOf(types);
                field.model = Field.Model.FIXED_TABLE;
                field.baseDecoder = r -> r.readBoolean() ? (Object) r.readUBitVar() : (Object) Boolean.FALSE;
            } else if (field.type.pointer() || POINTER_TYPES.contains(field.type.baseType())) {
                field.model = Field.Model.FIXED_TABLE;
                field.baseDecoder = FieldDecoders.BOOLEAN;
            } else {
                field.model = Field.Model.VARIABLE_TABLE;
                field.baseDecoder = FieldDecoders.UNSIGNED;
            }
        } else if (field.type.count() > 0 && !"char".equals(field.type.baseType())) {
            field.model = Field.Model.FIXED_ARRAY;
            field.decoder = FieldDecoders.forField(field);
        } else if (isVector(field.type.baseType())) {
            field.model = Field.Model.VARIABLE_ARRAY;
            field.baseDecoder = FieldDecoders.UNSIGNED;
            FieldType element = field.type.genericType();
            field.childDecoder = element != null
                    ? FieldDecoders.forType(element.baseType(), field)
                    : FieldDecoders.UNSIGNED;
        } else {
            field.model = Field.Model.SIMPLE;
            field.decoder = FieldDecoders.forField(field);
        }
        return field;
    }

    private static boolean isVector(String baseType) {
        return baseType.equals("CUtlVector") || baseType.equals("CNetworkUtlVectorBase")
                || baseType.equals("CUtlVectorEmbeddedNetworkVar");
    }
}
