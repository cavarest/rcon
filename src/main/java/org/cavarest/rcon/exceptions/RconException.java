package org.cavarest.rcon.exceptions;

import java.io.IOException;

/**
 * Base exception for all RCON-related errors.
 * Extends IOException to maintain backward compatibility with existing code.
 */
public class RconException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new RconException with the specified message.
     *
     * @param message The error message
     */
    public RconException(final String message) {
        super(message);
    }

    /**
     * Creates a new RconException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public RconException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
