package org.cavarest.rcon;

/**
 * Represents an RCON packet as defined by the Source RCON protocol.
 * 
 * RCON Packet Structure:
 * - requestId: A 32-bit integer identifying the request/response pair
 * - type: A 32-bit integer indicating the packet type (auth, command, response)
 * - payload: A null-terminated string containing the command or response data
 * 
 * @see <a href="https://developer.valvesoftware.com/wiki/RCON">Source RCON Protocol</a>
 */
public class Packet {

    /** The request ID used to match requests with responses */
    public final int requestId;

    /** The packet type (AUTH, AUTH_RESPONSE, EXECCOMMAND, RESPONSE_VALUE) */
    public final int type;

    /** The payload string (command or response) */
    public final String payload;

    /**
     * Creates a new RCON packet.
     *
     * @param requestId The request identifier
     * @param type The packet type
     * @param payload The payload string
     */
    public Packet(final int requestId, final int type, final String payload) {
        this.requestId = requestId;
        this.type = type;
        this.payload = payload != null ? payload : "";
    }

    /**
     * Creates a new RCON packet with an empty payload.
     *
     * @param requestId The request identifier
     * @param type The packet type
     */
    public Packet(final int requestId, final int type) {
        this(requestId, type, "");
    }

    /**
     * Checks if this packet is valid.
     * A packet is valid if its requestId is not -1.
     *
     * @return true if the packet is valid
     */
    public boolean isValid() {
        return requestId != -1;
    }

    @Override
    public String toString() {
        return String.format("Packet{requestId=%d, type=%d, payload='%s'}", 
            requestId, type, payload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Packet packet = (Packet) o;
        return requestId == packet.requestId && 
               type == packet.type && 
               (payload != null ? payload.equals(packet.payload) : packet.payload == null);
    }

    @Override
    public int hashCode() {
        int result = requestId;
        result = 31 * result + type;
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        return result;
    }
}
