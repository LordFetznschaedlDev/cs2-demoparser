package dev.lordfetznschaedl.businesslogic.demo;

import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;
import dev.lordfetznschaedl.businesslogic.io.Snappy;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Streams the frames of a CS2 ({@code PBDEMS2}) demo file one by one, so that large demos
 * never have to be held in memory completely.
 */
public final class DemoFrameReader implements Closeable {

    private static final byte[] MAGIC = "PBDEMS2\0".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FRAME_SIZE = 64 * 1024 * 1024;

    private final DataInputStream in;
    private boolean finished;

    public DemoFrameReader(InputStream input) throws IOException {
        this.in = new DataInputStream(new BufferedInputStream(input, 1 << 16));
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new DemoFormatException("Not a CS2 demo file (expected PBDEMS2 header)");
        }
        // two little-endian int32 offsets (file info / spawn groups) which are not needed for streaming
        in.skipNBytes(8);
    }

    /**
     * @return the next frame, or {@code null} when the end of the demo has been reached
     */
    public DemoFrame next() throws IOException {
        if (finished) {
            return null;
        }
        int rawCommand;
        try {
            rawCommand = readVarInt();
        } catch (EOFException e) {
            finished = true;
            return null;
        }
        int tick = readVarInt();
        int size = readVarInt();
        if (size < 0 || size > MAX_FRAME_SIZE) {
            throw new DemoFormatException("Invalid frame size " + size);
        }
        byte[] payload = new byte[size];
        try {
            in.readFully(payload);
        } catch (EOFException e) {
            // demo files of matches that were cut off may end in the middle of a frame
            finished = true;
            return null;
        }

        boolean compressed = (rawCommand & DemoCommand.COMPRESSED_FLAG) != 0;
        DemoCommand command = DemoCommand.fromId(rawCommand & ~DemoCommand.COMPRESSED_FLAG);
        if (compressed) {
            payload = Snappy.decompress(payload);
        }
        if (command == DemoCommand.STOP) {
            finished = true;
        }
        return new DemoFrame(command, tick, payload);
    }

    private int readVarInt() throws IOException {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException();
            }
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new DemoFormatException("Malformed varint in frame header");
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
