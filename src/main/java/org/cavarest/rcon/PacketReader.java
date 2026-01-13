package org.cavarest.rcon;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads and decodes RCON packets from a data source.
 *
 * This class handles the low-level reading of packet data from a socket channel,
 * using simple blocking reads to ensure complete packets are received.
 *
 * The read operation is blocking and will wait for sufficient data to be available
 * before returning a decoded packet. Each read processes exactly one complete packet.
 *
 * @see PacketCodec
 * @see Packet
 */
public class PacketReader {

    /** The data source for reading packet bytes */
    private final Source source;

    /** The codec for decoding packets */
    private final PacketCodec codec;

    /**
     * Functional interface for reading bytes from a data source.
     */
    @FunctionalInterface
    public interface Source {
        /**
         * Reads exactly the specified number of bytes into the buffer.
         * This method should block until all bytes are read or throw an exception.
         *
         * @param buffer The buffer to read into
         * @param offset The offset in the buffer to start writing
         * @param length The number of bytes to read
         * @throws IOException if a read error occurs or end of stream is reached
         */
        void readFully(byte[] buffer, int offset, int length) throws IOException;
    }

    /**
     * Creates a new PacketReader with the specified source and codec.
     *
     * @param source The data source for reading packet bytes
     * @param codec The codec for decoding packets
     */
    public PacketReader(final Source source, final PacketCodec codec) {
        this.source = source;
        this.codec = codec;
    }

    /**
     * Reads and decodes a complete RCON packet.
     * This method blocks until a complete packet is available.
     *
     * @return The decoded packet
     * @throws IOException if a read error occurs or connection is closed
     */
    public Packet read() throws IOException {
        // Read packet length (4 bytes) - little endian
        byte[] lengthBytes = new byte[Integer.BYTES];
        source.readFully(lengthBytes, 0, lengthBytes.length);

        int length = ByteBuffer.wrap(lengthBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        // Validate length (minimum: requestId (4) + type (4) + null terminators (2))
        if (length < Integer.BYTES + Integer.BYTES + 2) {
            throw new IOException("Invalid packet length: " + length);
        }

        // Validate maximum length (prevent memory exhaustion attacks)
        // The length field includes: requestId (4) + type (4) + payload + nulls (2)
        int maxPayloadLength = Rcon.MAX_SERVER_PAYLOAD_SIZE + Integer.BYTES + Integer.BYTES + 2;
        if (length > maxPayloadLength) {
            throw new IOException("Packet length exceeds maximum: " + length + " > " + maxPayloadLength);
        }

        // Read the full packet (requestId + type + payload + null terminators)
        byte[] packetBytes = new byte[length];
        source.readFully(packetBytes, 0, packetBytes.length);

        // Decode packet from bytes
        ByteBuffer buffer = ByteBuffer.wrap(packetBytes)
                .order(ByteOrder.LITTLE_ENDIAN);
        return codec.decode(buffer, length);
    }
}
