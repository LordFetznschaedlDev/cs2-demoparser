package dev.lordfetznschaedl.businesslogic.sendtables;

import dev.lordfetznschaedl.businesslogic.io.BitReader;

import java.util.Set;

/**
 * Chooses the wire decoder for a field based on its type, encoder and bit count.
 */
final class FieldDecoders {

    static final FieldDecoder BOOLEAN = BitReader::readBoolean;
    static final FieldDecoder UNSIGNED = BitReader::readVarInt32;
    static final FieldDecoder UNSIGNED_64 = BitReader::readVarInt64;
    static final FieldDecoder FIXED_64 = BitReader::readLong64;
    static final FieldDecoder SIGNED = BitReader::readSignedVarInt32;
    static final FieldDecoder SIGNED_64 = BitReader::readSignedVarInt64;
    static final FieldDecoder STRING = BitReader::readNullTerminatedString;
    static final FieldDecoder NOSCALE = BitReader::readFloat32;
    static final FieldDecoder COORD = BitReader::readCoord;
    static final FieldDecoder SIMULATION_TIME = r -> r.readVarInt32() * (1f / 30f);
    static final FieldDecoder AMMO = r -> r.readVarInt32() - 1;
    static final FieldDecoder NORMAL_VECTOR = BitReader::read3BitNormal;

    private static final Set<String> STRING_TYPES = Set.of("char", "CUtlString", "CUtlSymbolLarge", "CGlobalSymbol");
    private static final Set<String> SIGNED_TYPES = Set.of("int8", "int16", "int32");
    private static final Set<String> COMPONENT_TYPES = Set.of("CBodyComponent", "CPhysicsComponent", "CRenderComponent");

    private FieldDecoders() {
    }

    static FieldDecoder forField(Field field) {
        return forType(field.type.baseType(), field);
    }

    static FieldDecoder forType(String baseType, Field field) {
        if ("m_iClip1".equals(field.name)) {
            return AMMO;
        }
        if (baseType.equals("bool") || COMPONENT_TYPES.contains(baseType)) {
            return BOOLEAN;
        }
        if (STRING_TYPES.contains(baseType)) {
            return STRING;
        }
        if (SIGNED_TYPES.contains(baseType)) {
            return SIGNED;
        }
        return switch (baseType) {
            case "int64" -> SIGNED_64;
            case "uint64", "CStrongHandle", "ResourceId_t" -> "fixed64".equals(field.encoder) ? FIXED_64 : UNSIGNED_64;
            case "float32", "CNetworkedQuantizedFloat" -> floatDecoder(field);
            case "float64" -> r -> Double.longBitsToDouble(r.readLong64());
            case "GameTime_t" -> NOSCALE;
            case "Vector", "VectorWS" -> vectorDecoder(field, 3);
            case "Vector2D" -> vectorDecoder(field, 2);
            case "Vector4D", "Quaternion" -> vectorDecoder(field, 4);
            case "QAngle" -> qAngleDecoder(field);
            // position + rotation quaternion; only seen in rarely populated vectors
            case "CTransform" -> vectorDecoder(field, 7);
            case "CUtlBinaryBlock" -> r -> r.readBytes(r.readVarInt32());
            default -> UNSIGNED;
        };
    }

    private static FieldDecoder floatDecoder(Field field) {
        if ("m_flSimulationTime".equals(field.name) || "m_flAnimTime".equals(field.name)) {
            return SIMULATION_TIME;
        }
        if (field.encoder != null) {
            switch (field.encoder) {
                case "coord" -> {
                    return COORD;
                }
                case "simtime" -> {
                    return SIMULATION_TIME;
                }
                case "runetime" -> {
                    return r -> (float) r.readBits(4);
                }
                default -> {
                }
            }
        }
        if (field.bitCount <= 0 || field.bitCount >= 32) {
            return NOSCALE;
        }
        return new QuantizedFloatDecoder(field.bitCount, field.encodeFlags, field.lowValue, field.highValue);
    }

    private static FieldDecoder vectorDecoder(Field field, int size) {
        if (size == 3 && "normal".equals(field.encoder)) {
            return NORMAL_VECTOR;
        }
        FieldDecoder component = floatDecoder(field);
        return r -> {
            float[] v = new float[size];
            for (int i = 0; i < size; i++) {
                v[i] = ((Number) component.decode(r)).floatValue();
            }
            return v;
        };
    }

    private static FieldDecoder qAngleDecoder(Field field) {
        if ("qangle_precise".equals(field.encoder)) {
            return r -> {
                float[] v = new float[3];
                boolean hasX = r.readBoolean();
                boolean hasY = r.readBoolean();
                boolean hasZ = r.readBoolean();
                if (hasX) {
                    v[0] = r.readAngle(20) - 180f;
                }
                if (hasY) {
                    v[1] = r.readAngle(20) - 180f;
                }
                if (hasZ) {
                    v[2] = r.readAngle(20) - 180f;
                }
                return v;
            };
        }
        int bits = field.bitCount;
        if ("qangle_pitch_yaw".equals(field.encoder)) {
            int n = bits > 0 ? bits : 32;
            return r -> new float[]{r.readAngle(n), r.readAngle(n), 0f};
        }
        if (bits > 0 && bits <= 32) {
            return r -> new float[]{r.readAngle(bits), r.readAngle(bits), r.readAngle(bits)};
        }
        return r -> {
            float[] v = new float[3];
            boolean hasX = r.readBoolean();
            boolean hasY = r.readBoolean();
            boolean hasZ = r.readBoolean();
            if (hasX) {
                v[0] = r.readCoord();
            }
            if (hasY) {
                v[1] = r.readCoord();
            }
            if (hasZ) {
                v[2] = r.readCoord();
            }
            return v;
        };
    }
}
