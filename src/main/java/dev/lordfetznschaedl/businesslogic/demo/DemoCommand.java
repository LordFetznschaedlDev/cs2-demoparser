package dev.lordfetznschaedl.businesslogic.demo;

/**
 * Top level demo commands ({@code EDemoCommands} in Valve's demo.proto).
 */
public enum DemoCommand {
    STOP(0),
    FILE_HEADER(1),
    FILE_INFO(2),
    SYNC_TICK(3),
    SEND_TABLES(4),
    CLASS_INFO(5),
    STRING_TABLES(6),
    PACKET(7),
    SIGNON_PACKET(8),
    CONSOLE_CMD(9),
    CUSTOM_DATA(10),
    CUSTOM_DATA_CALLBACKS(11),
    USER_CMD(12),
    FULL_PACKET(13),
    SAVE_GAME(14),
    SPAWN_GROUPS(15),
    ANIMATION_DATA(16),
    ANIMATION_HEADER_DATA(17),
    UNKNOWN(-1);

    /** Flag OR-ed into the command id when the frame payload is Snappy compressed. */
    public static final int COMPRESSED_FLAG = 64;

    private static final DemoCommand[] BY_ID = new DemoCommand[18];

    static {
        for (DemoCommand command : values()) {
            if (command.id >= 0) {
                BY_ID[command.id] = command;
            }
        }
    }

    private final int id;

    DemoCommand(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static DemoCommand fromId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : UNKNOWN;
    }
}
