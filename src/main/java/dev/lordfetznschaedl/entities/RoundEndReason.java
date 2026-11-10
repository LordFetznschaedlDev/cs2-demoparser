package dev.lordfetznschaedl.entities;

/**
 * Why a round ended (values of {@code m_eRoundWinReason}).
 */
public enum RoundEndReason {
    UNKNOWN(0),
    TARGET_BOMBED(1),
    VIP_ESCAPED(2),
    VIP_KILLED(3),
    TERRORISTS_ESCAPED(4),
    CTS_PREVENT_ESCAPE(5),
    ESCAPING_TERRORISTS_NEUTRALIZED(6),
    BOMB_DEFUSED(7),
    CTS_WIN(8),
    TERRORISTS_WIN(9),
    ROUND_DRAW(10),
    ALL_HOSTAGES_RESCUED(11),
    TARGET_SAVED(12),
    HOSTAGES_NOT_RESCUED(13),
    TERRORISTS_NOT_ESCAPED(14),
    VIP_NOT_ESCAPED(15),
    GAME_COMMENCING(16),
    TERRORISTS_SURRENDER(17),
    CTS_SURRENDER(18),
    TERRORISTS_PLANTED(19),
    CTS_REACHED_HOSTAGE(20);

    private final int code;

    RoundEndReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static RoundEndReason fromCode(int code) {
        for (RoundEndReason reason : values()) {
            if (reason.code == code) {
                return reason;
            }
        }
        return UNKNOWN;
    }
}
