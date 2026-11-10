package dev.lordfetznschaedl.businesslogic.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtoReaderTest {

    /** Tiny protobuf writer for test input. */
    private static final class Writer {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void varint(long v) {
            while ((v & ~0x7FL) != 0) {
                out.write((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
            out.write((int) v);
        }

        Writer intField(int field, long value) {
            varint((long) field << 3);
            varint(value);
            return this;
        }

        Writer bytesField(int field, byte[] value) {
            varint(((long) field << 3) | 2);
            varint(value.length);
            out.writeBytes(value);
            return this;
        }

        Writer floatField(int field, float value) {
            varint(((long) field << 3) | 5);
            int bits = Float.floatToIntBits(value);
            for (int i = 0; i < 4; i++) {
                out.write(bits >>> (8 * i));
            }
            return this;
        }

        byte[] bytes() {
            return out.toByteArray();
        }
    }

    @Test
    void readsScalarsStringsAndNestedMessages() {
        byte[] nested = new Writer().intField(1, 42).bytes();
        byte[] message = new Writer()
                .intField(1, 150)
                .bytesField(2, "de_mirage".getBytes(StandardCharsets.UTF_8))
                .floatField(3, 0.015625f)
                .bytesField(4, nested)
                .intField(5, 1)
                .bytes();

        ProtoReader r = new ProtoReader(message);
        assertTrue(r.next());
        assertEquals(150, r.readInt32());
        assertTrue(r.next());
        assertEquals("de_mirage", r.readString());
        assertTrue(r.next());
        assertEquals(0.015625f, r.readFloat());
        assertTrue(r.next());
        ProtoReader inner = r.readMessage();
        assertTrue(inner.next());
        assertEquals(1, inner.fieldNumber());
        assertEquals(42, inner.readInt32());
        assertFalse(inner.next());
        assertTrue(r.next());
        assertTrue(r.readBool());
        assertFalse(r.next());
    }

    @Test
    void skipsUnknownFields() {
        byte[] message = new Writer().bytesField(7, new byte[]{1, 2, 3}).floatField(8, 1f).intField(9, 5).bytes();
        ProtoReader r = new ProtoReader(message);
        r.next();
        r.skip();
        r.next();
        r.skip();
        r.next();
        assertEquals(9, r.fieldNumber());
        assertEquals(5, r.readInt32());
    }

    @Test
    void rejectsWrongWireType() {
        ProtoReader r = new ProtoReader(new Writer().intField(1, 5).bytes());
        r.next();
        assertThrows(DemoFormatException.class, r::readString);
    }

    @Test
    void rejectsTruncatedMessages() {
        ProtoReader r = new ProtoReader(new byte[]{0x12, 0x05, 'a'});
        r.next();
        assertThrows(DemoFormatException.class, r::readBytes);
    }
}
