package org.cavarest.rcon;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads and decodes RCON packets from a data source.
 * 
 * This class handles the low-level reading of packet data from a socket channel,
 * managing a receive buffer and decoding complete packets.
 * 
 * The read operation is blocking and will wait for sufficient data to be available
 * before returning a decoded packet.
 * 
 * @see PacketCodec
 * @see Packet
 */
public class PacketReader {

    /** Default read buffer capacity (4 KB) */
    public static final int DEFAULT_BUFFER_CAPACITY = 4096;

    /** The data source for reading packet bytes */
    private final Source source;

    /** The codec for decoding packets */
    private final PacketCodec codec;

    /** The receive buffer for storing incoming data */
    private final ByteBuffer buffer;

    /**
     * Functional interface for reading bytes from a data source.
     */
    @FunctionalInterface
    public interface Source {
        /**
         * Reads bytes into the provided buffer.
         *
         * @param destination The buffer to read into
         * @return The number of bytes read, or -1 if end of stream
         * @throws IOException if a read error occurs
         */
        int read(ByteBuffer destination) throws IOException;
    }

    /**
     * Creates a new PacketReader with the specified source and buffer capacity.
     *
     * @param source The data source for reading packet bytes
     * @param bufferCapacity The capacity of the receive buffer
     * @param codec The codec for decoding packets
     */
    public PacketReader(final Source source, final int bufferCapacity, final PacketCodec codec) {
        this.source = source;
        this.codec = codec;
        this.buffer = ByteBuffer.allocate(bufferCapacity)
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Creates a new PacketReader with default buffer capacity.
     *
     * @param source The data source for reading packet bytes
     * @param codec The codec for decoding packets
     */
    public PacketReader(final Source source, final PacketCodec codec) {
        this(source, DEFAULT_BUFFER_CAPACITY, codec);
    }

    /**
     * Reads and decodes a complete RCON packet.
     * This method blocks until a complete packet is available.
     *
     * @return The decoded packet
     * @throws IOException if a read error occurs or connection is closed
     */
    public Packet read() throws IOException {
        // Read the packet length first (4 bytes)
        readUntilAvailable(Integer.BYTES);
        buffer.flip();
        final int length = buffer.getInt();
        buffer.compact();

        // Validate length
        if (length < Integer.BYTES + Integer.BYTES + 2) {
            throw new IOException("Invalid packet length: " + length);
        }

        // Read the complete packet
        readUntilAvailable(length);
        buffer.flip();
        final Packet packet = codec.decode(buffer, length);
        buffer.compact();

        return packet;
    }

    /**
     * Ensures that at least the specified number of bytes are available in the buffer.
     * This method blocks until sufficient data is available.
     *
     * @param bytesAvailable The minimum number of bytes required
     * @throws IOException if end of stream is reached before sufficient data
     */
    private void readUntilAvailable(final int bytesAvailable) throws IOException {
        while (buffer.position() < bytesAvailable) {
            int bytesRead = source.read(buffer);
            if (bytesRead == -1) {
                throw new EOFException("Connection closed while reading packet data");
            }
        }
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
