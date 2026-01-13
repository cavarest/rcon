package org.cavarest.rcon.exceptions;

/**
 * Exception thrown when RCON authentication fails.
 */
public class RconAuthenticationException extends RconException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new RconAuthenticationException with the specified message.
     *
     * @param message The error message
     */
    public RconAuthenticationException(final String message) {
        super(message);
    }

    /**
     * Creates a new RconAuthenticationException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public RconAuthenticationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
