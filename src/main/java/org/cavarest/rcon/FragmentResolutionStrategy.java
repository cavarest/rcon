package org.cavarest.rcon;

/**
 * Strategy for handling fragmented RCON command responses.
 *
 * The Minecraft server can fragment responses across multiple packets when the
 * response is larger than the maximum packet size (4096 bytes for S→C packets).
 * There are several approaches to determine when all fragments have been received:
 *
 * <ul>
 *   <li>{@link #TIMEOUT}: Wait for a fixed time period after the last packet.
 *       This is reliable but adds latency to every command.</li>
 *   <li>{@link #ACTIVE_PROBE}: Send a second command packet. The server will respond to
 *       the second command, and from this we know we've already received the full response
 *       to the first command. This is the default and most reliable method.</li>
 * </ul>
 *
 * @see Rcon#sendCommand(String, FragmentResolutionStrategy)
 */
public enum FragmentResolutionStrategy {

    /**
     * Wait for a fixed timeout period after receiving any packet.
     *
     * This approach waits for a specified timeout duration after the last packet
     * is received before considering the response complete. It is reliable but
     * adds unnecessary latency to commands that don't produce fragmented responses.
     */
    TIMEOUT,

    /**
     * Send a second command packet to probe for the end of the response.
     *
     * This approach sends a second command (typically a no-op command like "list")
     * after the first command. The server will respond to the second command, and
     * from this we know we've already received the full response to the first command.
     * This is the default and most reliable method.
     */
    ACTIVE_PROBE
}
