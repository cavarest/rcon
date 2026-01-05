package org.cavarest.rcon;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Encodes and decodes RCON packets using the Source RCON protocol binary format.
 * 
 * RCON Packet Binary Format:
 * <pre>
 * +--------+--------+--------+--------+...+--------+--------+
 * |  Length (int32)  | RequestId (int32) |  Type (int32)  |
 * +--------+--------+--------+--------+...+--------+--------+
 * |  Payload (string, null-terminated)   |  0x00  |  0x00  |
 * +--------+--------+--------+--------+...+--------+--------+
 * </pre>
 * 
 * - All multi-byte values are little-endian
 * - The length includes all bytes after the length field itself
 * - Payload is null-terminated with two extra null bytes for padding
 * 
 * @see <a href="https://developer.valvesoftware.com/wiki/RCON">Source RCON Protocol</a>
 */
public class PacketCodec {

    /** The charset used for encoding/decoding payload strings */
    private final Charset charset;

    /** Maximum payload size (1446 bytes as per protocol limit) */
    public static final int MAX_PAYLOAD_SIZE = 1446;

    /**
     * Creates a new PacketCodec with the specified charset.
     *
     * @param charset The charset to use for encoding/decoding
     */
    public PacketCodec(final Charset charset) {
        this.charset = charset;
    }

    /**
     * Creates a new PacketCodec using UTF-8 charset.
     */
    public PacketCodec() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * Encodes a packet into the provided ByteBuffer.
     * The buffer must have sufficient capacity (at least 14 bytes + payload length).
     *
     * @param packet The packet to encode
     * @param destination The buffer to write encoded data to
     */
    public void encode(final Packet packet, final ByteBuffer destination) {
        // Write request ID
        destination.putInt(packet.requestId);
        // Write packet type
        destination.putInt(packet.type);
        // Write payload (with null terminator padding handled by packet structure)
        destination.put(charset.encode(packet.payload));
        // Write two null bytes for packet termination
        destination.put((byte) 0x00);
        destination.put((byte) 0x00);
    }

    /**
     * Decodes a packet from the provided ByteBuffer.
     *
     * @param source The buffer containing encoded packet data
     * @param length The total length of the packet (including header)
     * @return The decoded packet
     */
    public Packet decode(final ByteBuffer source, final int length) {
        int requestId = source.getInt();
        int packetType = source.getInt();

        // Calculate payload length (total length minus header and two null terminators)
        int payloadLength = length - Integer.BYTES - Integer.BYTES - 2;

        // Save current limit
        int originalLimit = source.limit();
        
        // Set limit to read only the payload portion
        source.limit(source.position() + payloadLength);
        
        // Decode payload string
        String payload = charset.decode(source).toString();
        
        // Restore original limit
        source.limit(originalLimit);

        // Skip the two null terminator bytes
        source.get(); // First null byte
        source.get(); // Second null byte

        return new Packet(requestId, packetType, payload);
    }

    /**
     * Calculates the encoded size of a packet.
     *
     * @param packet The packet to calculate size for
     * @return The total encoded size in bytes
     */
    public int calculateEncodedSize(final Packet packet) {
        // Header (4 bytes requestId + 4 bytes type) + payload + 2 null bytes
        return Integer.BYTES + Integer.BYTES + 
               charset.encode(packet.payload).remaining() + 2;
    }

    /**
     * Validates that a packet's payload doesn't exceed the protocol limit.
     *
     * @param packet The packet to validate
     * @throws IllegalArgumentException if payload exceeds maximum size
     */
    public void validatePacket(final Packet packet) {
        if (packet.payload.length() > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                "Packet payload exceeds maximum size of " + MAX_PAYLOAD_SIZE + " bytes"
            );
        }
    }
}
