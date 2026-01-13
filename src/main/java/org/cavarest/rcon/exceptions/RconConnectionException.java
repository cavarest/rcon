package org.cavarest.rcon.exceptions;

/**
 * Exception thrown when RCON connection fails or is lost.
 */
public class RconConnectionException extends RconException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new RconConnectionException with the specified message.
     *
     * @param message The error message
     */
    public RconConnectionException(final String message) {
        super(message);
    }

    /**
     * Creates a new RconConnectionException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public RconConnectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
