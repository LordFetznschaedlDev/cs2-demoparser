package dev.lordfetznschaedl.businesslogic.demo;

import dev.lordfetznschaedl.businesslogic.io.BitReader;
import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;
import dev.lordfetznschaedl.businesslogic.io.ProtoReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a demo stream and dispatches frames and the network messages inside packets to a {@link DemoVisitor}.
 */
public final class DemoStreamProcessor {

    private final DemoVisitor visitor;

    public DemoStreamProcessor(DemoVisitor visitor) {
        this.visitor = visitor;
    }

    public void process(InputStream input) throws IOException {
        try (DemoFrameReader reader = new DemoFrameReader(input)) {
            DemoFrame frame;
            while ((frame = reader.next()) != null) {
                visitor.onFrame(frame);
                switch (frame.command()) {
                    case PACKET, SIGNON_PACKET -> processPacket(frame.payload(), frame.tick());
                    case FULL_PACKET -> processFullPacket(frame);
                    case STRING_TABLES -> visitor.onStringTableSnapshot(frame.payload(), frame.tick());
                    default -> {
                        // handled (if at all) through onFrame
                    }
                }
            }
        }
    }

    /** CDemoFullPacket: string_table = 1 (CDemoStringTables), packet = 2 (CDemoPacket). */
    private void processFullPacket(DemoFrame frame) {
        ProtoReader r = new ProtoReader(frame.payload());
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 1 -> visitor.onStringTableSnapshot(r.readBytes(), frame.tick());
                case 2 -> processPacket(r.readBytes(), frame.tick());
                default -> r.skip();
            }
        }
    }

    /** CDemoPacket: data = 3 holds the bit packed network messages. */
    private void processPacket(byte[] payload, int tick) {
        ProtoReader r = new ProtoReader(payload);
        while (r.next()) {
            if (r.fieldNumber() == 3) {
                processMessages(r.readBytes(), tick);
            } else {
                r.skip();
            }
        }
    }

    private void processMessages(byte[] data, int tick) {
        BitReader bits = new BitReader(data);
        // each message needs at least 6 bits for the type plus 8 bits for the size
        while (bits.remainingBits() >= 14) {
            int type = bits.readUBitVar();
            int size = bits.readVarInt32();
            if (size < 0 || (long) size * 8 > bits.remainingBits()) {
                throw new DemoFormatException("Net message " + type + " exceeds packet bounds");
            }
            byte[] message = bits.readBytes(size);
            visitor.onNetMessage(type, message, tick);
        }
    }
}
