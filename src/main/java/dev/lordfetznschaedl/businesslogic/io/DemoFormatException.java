package dev.lordfetznschaedl.businesslogic.io;

/**
 * Thrown when a demo file (or a message inside it) does not match the expected binary format.
 */
public class DemoFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DemoFormatException(String message) {
        super(message);
    }

    public DemoFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
