package org.cavarest.rcon.exceptions;

/**
 * Exception thrown when RCON protocol violations are detected.
 */
public class RconProtocolException extends RconException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new RconProtocolException with the specified message.
     *
     * @param message The error message
     */
    public RconProtocolException(final String message) {
        super(message);
    }

    /**
     * Creates a new RconProtocolException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public RconProtocolException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
