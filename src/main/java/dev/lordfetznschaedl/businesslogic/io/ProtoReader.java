package dev.lordfetznschaedl.businesslogic.io;

import java.nio.charset.StandardCharsets;

/**
 * Minimal streaming decoder for the protobuf wire format.
 * <p>
 * Usage:
 * <pre>{@code
 * var r = new ProtoReader(bytes);
 * while (r.next()) {
 *     switch (r.fieldNumber()) {
 *         case 1 -> name = r.readString();
 *         default -> r.skip();
 *     }
 * }
 * }</pre>
 * Avoids a dependency on generated protobuf classes; only the fields the parser needs are decoded.
 */
public final class ProtoReader {

    public static final int WIRE_VARINT = 0;
    public static final int WIRE_FIXED64 = 1;
    public static final int WIRE_LENGTH_DELIMITED = 2;
    public static final int WIRE_FIXED32 = 5;

    private final byte[] data;
    private final int limit;
    private int position;
    private int fieldNumber;
    private int wireType;

    public ProtoReader(byte[] data) {
        this(data, 0, data.length);
    }

    public ProtoReader(byte[] data, int offset, int length) {
        this.data = data;
        this.position = offset;
        this.limit = offset + length;
    }

    /** Advances to the next field. Returns {@code false} at the end of the message. */
    public boolean next() {
        if (position >= limit) {
            return false;
        }
        long tag = readRawVarint();
        fieldNumber = (int) (tag >>> 3);
        wireType = (int) (tag & 7);
        return true;
    }

    /** Whether unread bytes remain (useful for packed repeated fields). */
    public boolean hasRemaining() {
        return position < limit;
    }

    /** Reads one element of a packed repeated varint field (see {@link #readMessage()}). */
    public long readPackedVarint() {
        return readRawVarint();
    }

    public int fieldNumber() {
        return fieldNumber;
    }

    public int wireType() {
        return wireType;
    }

    public long readVarint() {
        expect(WIRE_VARINT);
        return readRawVarint();
    }

    public int readInt32() {
        return (int) readVarint();
    }

    public boolean readBool() {
        return readVarint() != 0;
    }

    public int readSInt32() {
        int raw = (int) readVarint();
        return (raw >>> 1) ^ -(raw & 1);
    }

    public float readFloat() {
        expect(WIRE_FIXED32);
        return Float.intBitsToFloat(readRawFixed32());
    }

    public long readFixed64() {
        expect(WIRE_FIXED64);
        return readRawFixed64();
    }

    /** Reads a numeric field regardless of whether it is encoded as varint or fixed width. */
    public long readAnyLong() {
        return switch (wireType) {
            case WIRE_VARINT -> readRawVarint();
            case WIRE_FIXED64 -> readRawFixed64();
            case WIRE_FIXED32 -> readRawFixed32() & 0xFFFFFFFFL;
            default -> throw new DemoFormatException("Field " + fieldNumber + " is not numeric");
        };
    }

    public byte[] readBytes() {
        expect(WIRE_LENGTH_DELIMITED);
        int length = (int) readRawVarint();
        checkAvailable(length);
        byte[] out = new byte[length];
        System.arraycopy(data, position, out, 0, length);
        position += length;
        return out;
    }

    public String readString() {
        expect(WIRE_LENGTH_DELIMITED);
        int length = (int) readRawVarint();
        checkAvailable(length);
        String s = new String(data, position, length, StandardCharsets.UTF_8);
        position += length;
        return s;
    }

    /** Returns a reader for an embedded message without copying the bytes. */
    public ProtoReader readMessage() {
        expect(WIRE_LENGTH_DELIMITED);
        int length = (int) readRawVarint();
        checkAvailable(length);
        ProtoReader nested = new ProtoReader(data, position, length);
        position += length;
        return nested;
    }

    public void skip() {
        switch (wireType) {
            case WIRE_VARINT -> readRawVarint();
            case WIRE_FIXED64 -> advance(8);
            case WIRE_LENGTH_DELIMITED -> advance((int) readRawVarint());
            case WIRE_FIXED32 -> advance(4);
            default -> throw new DemoFormatException("Unsupported wire type " + wireType);
        }
    }

    private void expect(int expected) {
        if (wireType != expected) {
            throw new DemoFormatException("Field " + fieldNumber + ": expected wire type " + expected + " but got " + wireType);
        }
    }

    private long readRawVarint() {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            checkAvailable(1);
            byte b = data[position++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new DemoFormatException("Malformed varint");
    }

    private int readRawFixed32() {
        checkAvailable(4);
        int value = (data[position] & 0xFF)
                | (data[position + 1] & 0xFF) << 8
                | (data[position + 2] & 0xFF) << 16
                | (data[position + 3] & 0xFF) << 24;
        position += 4;
        return value;
    }

    private long readRawFixed64() {
        long low = readRawFixed32() & 0xFFFFFFFFL;
        long high = readRawFixed32() & 0xFFFFFFFFL;
        return low | high << 32;
    }

    private void advance(int count) {
        checkAvailable(count);
        position += count;
    }

    private void checkAvailable(int count) {
        if (count < 0 || position + count > limit) {
            throw new DemoFormatException("Truncated protobuf message");
        }
    }
}
