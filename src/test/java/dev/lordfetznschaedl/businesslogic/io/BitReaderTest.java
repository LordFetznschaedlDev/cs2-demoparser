package dev.lordfetznschaedl.businesslogic.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitReaderTest {

    @Test
    void readsBitsLeastSignificantFirst() {
        BitReader r = new BitReader(new byte[]{(byte) 0b1010_0101, (byte) 0xFF});
        assertTrue(r.readBoolean());
        assertFalse(r.readBoolean());
        assertEquals(0b01, r.readBits(2));
        assertEquals(0b1010, r.readBits(4));
        assertEquals(0xFF, r.readBits(8));
        assertEquals(0, r.remainingBits());
    }

    @Test
    void readsAcrossByteBoundaries() {
        BitReader r = new BitReader(new byte[]{(byte) 0xF0, (byte) 0x0F});
        r.readBits(4);
        assertEquals(0xFF, r.readBits(8));
    }

    @Test
    void readsFull32BitValues() {
        BitReader r = new BitReader(new byte[]{0x00, 0x00, (byte) 0x80, 0x3F});
        assertEquals(1.0f, r.readFloat32());
    }

    @Test
    void readsVarInts() {
        // 300 = 0b1_0010_1100 -> 0xAC 0x02
        assertEquals(300, new BitReader(new byte[]{(byte) 0xAC, 0x02}).readVarInt32());
        // zig-zag: 3 -> -2
        assertEquals(-2, new BitReader(new byte[]{0x03}).readSignedVarInt32());
        assertEquals(1L << 40, new BitReader(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x20})
                .readVarInt64());
    }

    @Test
    void readsUBitVar() {
        // low 6 bits = 0b01_0011 -> (value & 0x30) == 16 -> 4 more bits appended above the low nibble
        BitReader r = new BitReader(new byte[]{(byte) 0b1001_0011, 0b0000_0010});
        int expected = 0b0011 | (0b1010 << 4);
        assertEquals(expected, r.readUBitVar());
    }

    @Test
    void readsNullTerminatedStrings() {
        BitReader r = new BitReader(new byte[]{'d', 'e', '_', 'x', 0, 'y'});
        assertEquals("de_x", r.readNullTerminatedString());
        assertEquals('y', r.readByte());
    }

    @Test
    void readsUnalignedBytes() {
        BitReader r = new BitReader(new byte[]{(byte) 0b1111_1110, 0x01});
        assertFalse(r.readBoolean());
        assertArrayEquals(new byte[]{(byte) 0xFF}, r.readBytes(1));
    }

    @Test
    void readsCoordinates() {
        // hasInt=1, hasFraction=0, negative=1, int=(9)+1 -> -10.0
        int bits = 1 | (0 << 1) | (1 << 2) | (9 << 3);
        BitReader r = new BitReader(new byte[]{(byte) bits, (byte) (bits >>> 8), (byte) (bits >>> 16)});
        assertEquals(-10f, r.readCoord());
    }

    @Test
    void failsOnOverflow() {
        BitReader r = new BitReader(new byte[]{1});
        r.readBits(5);
        assertThrows(DemoFormatException.class, () -> r.readBits(4));
    }
}
