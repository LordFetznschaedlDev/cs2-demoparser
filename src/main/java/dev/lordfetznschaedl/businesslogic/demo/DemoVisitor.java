package dev.lordfetznschaedl.businesslogic.demo;

/**
 * Callback interface for the low level demo stream. All methods have empty defaults so
 * implementations only override what they need.
 */
public interface DemoVisitor {

    /** Called for every top level frame (packets included) before its content is dispatched. */
    default void onFrame(DemoFrame frame) {
    }

    /** Called for every network message contained in a packet frame. */
    default void onNetMessage(int type, byte[] payload, int tick) {
    }

    /** Called for {@code CDemoStringTables} snapshots (stand-alone or as part of a full packet). */
    default void onStringTableSnapshot(byte[] payload, int tick) {
    }
}
