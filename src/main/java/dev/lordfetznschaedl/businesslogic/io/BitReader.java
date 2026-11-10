package dev.lordfetznschaedl.businesslogic.io;

import java.nio.charset.StandardCharsets;

/**
 * Reads a little-endian, LSB-first bit stream as used by Source 2 network packets and string tables.
 */
public final class BitReader {

    private final byte[] data;
    private final long bitLimit;
    private long bitPosition;

    public BitReader(byte[] data) {
        this.data = data;
        this.bitLimit = (long) data.length * 8;
    }

    public long remainingBits() {
        return bitLimit - bitPosition;
    }

    public boolean hasRemaining(int bits) {
        return remainingBits() >= bits;
    }

    public boolean readBoolean() {
        ensure(1);
        boolean value = ((data[(int) (bitPosition >>> 3)] >>> (bitPosition & 7)) & 1) != 0;
        bitPosition++;
        return value;
    }

    /** Reads up to 32 bits as an unsigned value (returned in an int; values with 32 bits may be negative). */
    public int readBits(int count) {
        if (count == 0) {
            return 0;
        }
        if (count < 0 || count > 32) {
            throw new IllegalArgumentException("Bit count out of range: " + count);
        }
        ensure(count);
        long result = 0;
        int written = 0;
        while (written < count) {
            int byteIndex = (int) (bitPosition >>> 3);
            int bitOffset = (int) (bitPosition & 7);
            int take = Math.min(8 - bitOffset, count - written);
            int chunk = ((data[byteIndex] & 0xFF) >>> bitOffset) & ((1 << take) - 1);
            result |= (long) chunk << written;
            written += take;
            bitPosition += take;
        }
        return (int) result;
    }

    public int readByte() {
        return readBits(8);
    }

    public byte[] readBytes(int length) {
        byte[] out = new byte[length];
        if ((bitPosition & 7) == 0) {
            ensure((long) length * 8);
            System.arraycopy(data, (int) (bitPosition >>> 3), out, 0, length);
            bitPosition += (long) length * 8;
        } else {
            for (int i = 0; i < length; i++) {
                out[i] = (byte) readBits(8);
            }
        }
        return out;
    }

    /** Reads {@code bitCount} bits into a byte array; the last byte holds the remaining bits. */
    public byte[] readBitsAsBytes(int bitCount) {
        byte[] out = readBytes(bitCount / 8);
        int rest = bitCount % 8;
        if (rest == 0) {
            return out;
        }
        byte[] extended = new byte[out.length + 1];
        System.arraycopy(out, 0, extended, 0, out.length);
        extended[out.length] = (byte) readBits(rest);
        return extended;
    }

    /** Valve's "UBitVar" encoding used for message types and some lengths. */
    public int readUBitVar() {
        int value = readBits(6);
        return switch (value & 0x30) {
            case 16 -> (value & 15) | (readBits(4) << 4);
            case 32 -> (value & 15) | (readBits(8) << 4);
            case 48 -> (value & 15) | (readBits(28) << 4);
            default -> value;
        };
    }

    public int readVarInt32() {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new DemoFormatException("Malformed varint32 in bit stream");
    }

    public long readVarInt64() {
        long result = 0;
        for (int shift = 0; shift < 70; shift += 7) {
            int b = readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new DemoFormatException("Malformed varint64 in bit stream");
    }

    /** Zig-zag encoded signed varint. */
    public int readSignedVarInt32() {
        int raw = readVarInt32();
        return (raw >>> 1) ^ -(raw & 1);
    }

    /** Zig-zag encoded signed varint (64 bit). */
    public long readSignedVarInt64() {
        long raw = readVarInt64();
        return (raw >>> 1) ^ -(raw & 1);
    }

    public long readLong64() {
        long low = readBits(32) & 0xFFFFFFFFL;
        long high = readBits(32) & 0xFFFFFFFFL;
        return low | high << 32;
    }

    public float readFloat32() {
        return Float.intBitsToFloat(readBits(32));
    }

    /** Variable length unsigned int used in entity field paths. */
    public int readUBitVarFieldPath() {
        if (readBoolean()) {
            return readBits(2);
        }
        if (readBoolean()) {
            return readBits(4);
        }
        if (readBoolean()) {
            return readBits(10);
        }
        if (readBoolean()) {
            return readBits(17);
        }
        return readBits(31);
    }

    /** Source engine bit coordinate. */
    public float readCoord() {
        boolean hasInt = readBoolean();
        boolean hasFraction = readBoolean();
        if (!hasInt && !hasFraction) {
            return 0f;
        }
        boolean negative = readBoolean();
        int intValue = hasInt ? readBits(14) + 1 : 0;
        int fraction = hasFraction ? readBits(5) : 0;
        float value = intValue + fraction * (1f / 32f);
        return negative ? -value : value;
    }

    public float readAngle(int bits) {
        return (readBits(bits) & 0xFFFFFFFFL) * 360f / (float) (1L << bits);
    }

    public float readNormal() {
        boolean negative = readBoolean();
        float value = readBits(11) * (1f / ((1 << 11) - 1));
        return negative ? -value : value;
    }

    public float[] read3BitNormal() {
        float[] v = new float[3];
        boolean hasX = readBoolean();
        boolean hasY = readBoolean();
        if (hasX) {
            v[0] = readNormal();
        }
        if (hasY) {
            v[1] = readNormal();
        }
        boolean negativeZ = readBoolean();
        float squared = v[0] * v[0] + v[1] * v[1];
        v[2] = squared < 1f ? (float) Math.sqrt(1f - squared) : 0f;
        if (negativeZ) {
            v[2] = -v[2];
        }
        return v;
    }

    /** Reads a zero-terminated UTF-8 string. */
    public String readNullTerminatedString() {
        var buffer = new java.io.ByteArrayOutputStream();
        while (true) {
            int b = readByte();
            if (b == 0) {
                break;
            }
            buffer.write(b);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private void ensure(long bits) {
        if (bitPosition + bits > bitLimit) {
            throw new DemoFormatException("Bit stream overflow: need " + bits + " bits, " + remainingBits() + " left");
        }
    }
}
