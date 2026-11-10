package dev.lordfetznschaedl.businesslogic.io;

/**
 * Decompressor for the raw (unframed) Snappy format, which Valve uses for compressed demo frames
 * and string table data.
 */
public final class Snappy {

    private Snappy() {
    }

    public static byte[] decompress(byte[] input) {
        return decompress(input, 0, input.length);
    }

    public static byte[] decompress(byte[] input, int offset, int length) {
        int end = offset + length;
        int pos = offset;

        // Preamble: uncompressed length as varint
        long uncompressedLength = 0;
        int shift = 0;
        while (true) {
            if (pos >= end) {
                throw new DemoFormatException("Snappy: truncated length preamble");
            }
            int b = input[pos++] & 0xFF;
            uncompressedLength |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
            if (shift > 32) {
                throw new DemoFormatException("Snappy: length preamble too long");
            }
        }
        if (uncompressedLength > Integer.MAX_VALUE - 16) {
            throw new DemoFormatException("Snappy: uncompressed length too large");
        }

        byte[] out = new byte[(int) uncompressedLength];
        int outPos = 0;

        while (pos < end) {
            int tag = input[pos++] & 0xFF;
            int copyOffset;
            int copyLength;
            switch (tag & 3) {
                case 0 -> { // literal
                    int literalLength = tag >>> 2;
                    if (literalLength >= 60) {
                        int extraBytes = literalLength - 59;
                        literalLength = 0;
                        for (int i = 0; i < extraBytes; i++) {
                            literalLength |= (input[pos++] & 0xFF) << (8 * i);
                        }
                    }
                    literalLength += 1;
                    if (pos + literalLength > end || outPos + literalLength > out.length) {
                        throw new DemoFormatException("Snappy: literal out of bounds");
                    }
                    System.arraycopy(input, pos, out, outPos, literalLength);
                    pos += literalLength;
                    outPos += literalLength;
                    continue;
                }
                case 1 -> {
                    copyLength = ((tag >>> 2) & 7) + 4;
                    copyOffset = ((tag >>> 5) << 8) | (input[pos++] & 0xFF);
                }
                case 2 -> {
                    copyLength = (tag >>> 2) + 1;
                    copyOffset = (input[pos] & 0xFF) | (input[pos + 1] & 0xFF) << 8;
                    pos += 2;
                }
                default -> {
                    copyLength = (tag >>> 2) + 1;
                    copyOffset = (input[pos] & 0xFF) | (input[pos + 1] & 0xFF) << 8
                            | (input[pos + 2] & 0xFF) << 16 | (input[pos + 3] & 0xFF) << 24;
                    pos += 4;
                }
            }
            if (copyOffset <= 0 || copyOffset > outPos || outPos + copyLength > out.length) {
                throw new DemoFormatException("Snappy: invalid copy (offset " + copyOffset + ")");
            }
            int from = outPos - copyOffset;
            if (copyOffset >= copyLength) {
                System.arraycopy(out, from, out, outPos, copyLength);
                outPos += copyLength;
            } else {
                for (int i = 0; i < copyLength; i++) {
                    out[outPos++] = out[from + i];
                }
            }
        }
        if (outPos != out.length) {
            throw new DemoFormatException("Snappy: decompressed " + outPos + " bytes, expected " + out.length);
        }
        return out;
    }
}
