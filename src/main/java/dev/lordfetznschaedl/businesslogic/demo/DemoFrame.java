package dev.lordfetznschaedl.businesslogic.demo;

/**
 * One top-level frame of a demo file with an already decompressed payload.
 *
 * @param command the demo command
 * @param tick    the game tick the frame belongs to ({@code -1} for signon frames)
 * @param payload protobuf encoded message matching the command
 */
public record DemoFrame(DemoCommand command, int tick, byte[] payload) {
}
