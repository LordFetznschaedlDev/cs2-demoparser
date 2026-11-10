package dev.lordfetznschaedl.entities;

import java.time.Duration;

/**
 * Metadata of the demo file itself.
 *
 * @param mapName         e.g. {@code de_mirage}
 * @param serverName      name of the server that recorded the demo
 * @param clientName      name of the recording client (e.g. SourceTV)
 * @param gameDirectory   game directory reported by the server
 * @param buildNumber     game build the demo was recorded with
 * @param networkProtocol network protocol version
 * @param tickRate        server ticks per second
 * @param totalTicks      number of ticks in the demo
 * @param duration        playback duration
 */
public record DemoInfo(
        String mapName,
        String serverName,
        String clientName,
        String gameDirectory,
        int buildNumber,
        int networkProtocol,
        double tickRate,
        int totalTicks,
        Duration duration) {

    /** Converts a tick to the time elapsed since the start of the demo. */
    public Duration tickToTime(int tick) {
        if (tickRate <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos((long) (Math.max(tick, 0) / tickRate * 1_000_000_000L));
    }
}
