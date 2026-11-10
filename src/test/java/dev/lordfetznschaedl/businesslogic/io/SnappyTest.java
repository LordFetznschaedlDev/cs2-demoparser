package dev.lordfetznschaedl.businesslogic.io;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnappyTest {

    @Test
    void decodesLiteral() {
        // length 5, literal tag ((5 - 1) << 2), "hello"
        byte[] compressed = {5, (byte) (4 << 2), 'h', 'e', 'l', 'l', 'o'};
        assertEquals("hello", new String(Snappy.decompress(compressed), StandardCharsets.US_ASCII));
    }

    @Test
    void decodesOverlappingCopy() {
        // "ab" followed by a 1-byte-offset copy of length 6 at offset 2 -> "abababab"
        int copyTag = 1 | ((6 - 4) << 2); // offset high bits are 0
        byte[] compressed = {8, (byte) (1 << 2), 'a', 'b', (byte) copyTag, 2};
        assertEquals("abababab", new String(Snappy.decompress(compressed), StandardCharsets.US_ASCII));
    }

    @Test
    void decodesTwoByteOffsetCopy() {
        // "xyz" + copy(len 3, offset 3) using a 2 byte offset element
        int copyTag = 2 | ((3 - 1) << 2);
        byte[] compressed = {6, (byte) (2 << 2), 'x', 'y', 'z', (byte) copyTag, 3, 0};
        assertArrayEquals("xyzxyz".getBytes(StandardCharsets.US_ASCII), Snappy.decompress(compressed));
    }

    @Test
    void rejectsInvalidOffsets() {
        int copyTag = 1;
        byte[] compressed = {4, (byte) copyTag, 9};
        assertThrows(DemoFormatException.class, () -> Snappy.decompress(compressed));
    }

    @Test
    void rejectsLengthMismatch() {
        byte[] compressed = {9, (byte) (1 << 2), 'a', 'b'};
        assertThrows(DemoFormatException.class, () -> Snappy.decompress(compressed));
    }
}
