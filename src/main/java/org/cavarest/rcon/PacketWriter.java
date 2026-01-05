package org.cavarest.rcon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and encodes RCON packets to a data destination.
 * 
 * This class handles the low-level writing of packet data to a socket channel,
 * managing a send buffer and encoding packets before transmission.
 * 
 * @see PacketCodec
 * @see Packet
 */
public class PacketWriter {

    /** Default write buffer capacity (1460 bytes, typical MTU size) */
    public static final int DEFAULT_BUFFER_CAPACITY = 1460;

    /** The data destination for writing packet bytes */
    private final Destination destination;

    /** The codec for encoding packets */
    private final PacketCodec codec;

    /** The send buffer for storing encoded data */
    private final ByteBuffer buffer;

    /**
     * Functional interface for writing bytes to a data destination.
     */
    @FunctionalInterface
    public interface Destination {
        /**
         * Writes bytes from the provided buffer.
         *
         * @param source The buffer containing data to write
         * @return The number of bytes written
         * @throws IOException if a write error occurs
         */
        int write(ByteBuffer source) throws IOException;
    }

    /**
     * Creates a new PacketWriter with the specified destination and buffer capacity.
     *
     * @param destination The data destination for writing packet bytes
     * @param bufferCapacity The capacity of the send buffer
     * @param codec The codec for encoding packets
     */
    public PacketWriter(final Destination destination, final int bufferCapacity, 
                        final PacketCodec codec) {
        this.destination = destination;
        this.codec = codec;
        this.buffer = ByteBuffer.allocate(bufferCapacity)
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Creates a new PacketWriter with default buffer capacity.
     *
     * @param destination The data destination for writing packet bytes
     * @param codec The codec for encoding packets
     */
    public PacketWriter(final Destination destination, final PacketCodec codec) {
        this(destination, DEFAULT_BUFFER_CAPACITY, codec);
    }

    /**
     * Encodes and writes a complete RCON packet.
     * This method blocks until all packet data is written.
     *
     * @param packet The packet to write
     * @return The total number of bytes written
     * @throws IOException if a write error occurs
     * @throws IllegalArgumentException if the packet payload is too large
     */
    public int write(final Packet packet) throws IOException {
        // Validate packet size
        codec.validatePacket(packet);

        // Clear buffer and encode packet
        buffer.clear();
        buffer.position(Integer.BYTES); // Leave space for length field
        codec.encode(packet, buffer);
        
        // Write length at the beginning
        buffer.putInt(0, buffer.position() - Integer.BYTES);
        
        // Flip buffer for reading and write to destination
        buffer.flip();
        return destination.write(buffer);
    }

    /**
     * Encodes a packet without writing to the destination.
     * Useful for testing or when manual control over writing is needed.
     *
     * @param packet The packet to encode
     * @return A byte array containing the encoded packet
     */
    public byte[] encodeToBytes(final Packet packet) {
        codec.validatePacket(packet);
        buffer.clear();
        buffer.position(Integer.BYTES);
        codec.encode(packet, buffer);
        buffer.putInt(0, buffer.position() - Integer.BYTES);
        buffer.flip();
        
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    /**
     * Gets the current buffer capacity.
     *
     * @return The buffer capacity in bytes
     */
    public int getBufferCapacity() {
        return buffer.capacity();
    }

    /**
     * Gets the current buffer position.
     *
     * @return The current buffer position
     */
    public int getBufferPosition() {
        return buffer.position();
    }
}
