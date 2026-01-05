package org.cavarest.rcon;

/**
 * RCON Packet Type constants as defined by the Source RCON protocol.
 *
 * The protocol uses 32-bit integers to identify packet types:
 * - SERVERDATA_RESPONSE_VALUE (0): Response to a command or auth
 * - SERVERDATA_AUTH (3): Authentication request
 * - SERVERDATA_AUTH_RESPONSE (2): Authentication response
 * - SERVERDATA_EXECCOMMAND (2): Command execution request
 *
 * Note: SERVERDATA_EXECCOMMAND and SERVERDATA_AUTH_RESPONSE share the same
 * value (2), distinguished by context (auth phase vs command phase).
 *
 * @see <a href="https://developer.valvesoftware.com/wiki/RCON">Source RCON Protocol</a>
 */
public final class PacketType {

    /** Private constructor to prevent instantiation */
    private PacketType() {}

    /**
     * Response value packet type.
     * Used for both command responses and authentication responses during command phase.
     */
    public static final int SERVERDATA_RESPONSE_VALUE = 0;

    /**
     * Authentication request packet type.
     * Sent by the client to authenticate with the server.
     */
    public static final int SERVERDATA_AUTH = 3;

    /**
     * Authentication response packet type.
     * Sent by the server in response to an authentication request.
     * A value of -1 indicates authentication failure.
     */
    public static final int SERVERDATA_AUTH_RESPONSE = 2;

    /**
     * Command execution request packet type.
     * Sent by the client to execute a server command.
     */
    public static final int SERVERDATA_EXECCOMMAND = 2;

    /**
     * Returns a human-readable name for a packet type.
     *
     * @param type The packet type constant
     * @return A string representation of the packet type
     */
    public static String toString(int type) {
        if (type == SERVERDATA_RESPONSE_VALUE) {
            return "SERVERDATA_RESPONSE_VALUE";
        } else if (type == SERVERDATA_AUTH) {
            return "SERVERDATA_AUTH";
        } else if (type == SERVERDATA_AUTH_RESPONSE) {
            return "SERVERDATA_AUTH_RESPONSE";
        } else if (type == SERVERDATA_EXECCOMMAND) {
            return "SERVERDATA_EXECCOMMAND";
        } else {
            return "UNKNOWN(" + type + ")";
        }
    }
}
