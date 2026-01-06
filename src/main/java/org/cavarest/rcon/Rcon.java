package org.cavarest.rcon;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Main RCON client class for communicating with Source game servers.
 *
 * This class provides a high-level API for:
 * - Connecting to RCON servers
 * - Authenticating with a password
 * - Executing commands and receiving responses
 *
 * Example usage:
 * <pre>{@code
 * try (Rcon rcon = Rcon.connect("localhost", 25575)) {
 *     rcon.authenticate("password");
 *     String response = rcon.sendCommand("say Hello World");
 *     System.out.println("Response: " + response);
 * } catch (IOException e) {
 *     System.err.println("RCON error: " + e.getMessage());
 * }
 * }</pre>
 *
 * @see <a href="https://developer.valvesoftware.com/wiki/RCON">Source RCON Protocol</a>
 */
public class Rcon implements Closeable {

    /** The underlying byte channel for communication */
    private final ByteChannel channel;

    /** The packet reader for receiving responses */
    private final PacketReader reader;

    /** The packet writer for sending requests */
    private final PacketWriter writer;

    /** Request counter for matching requests with responses */
    private volatile int requestCounter;

    /** Connection timeout in milliseconds */
    private int connectTimeout = 5000;

    /** Read timeout in milliseconds */
    private int readTimeout = 5000;

    /** Whether verbose logging is enabled */
    private boolean verbose = false;

    /** Fragment resolution strategy */
    private FragmentResolutionStrategy fragmentStrategy = FragmentResolutionStrategy.ACTIVE_PROBE;

    /** Timeout for fragment resolution (used with TIMEOUT strategy) */
    private long fragmentTimeoutMillis = 100;

    /**
     * Creates a new Rcon instance with the specified channel and codecs.
     *
     * @param channel The underlying byte channel
     * @param readBufferCapacity The capacity of the read buffer
     * @param writeBufferCapacity The capacity of the write buffer
     * @param codec The packet codec to use
     */
    Rcon(final ByteChannel channel, final int readBufferCapacity,
         final int writeBufferCapacity, final PacketCodec codec) {
        this.channel = channel;
        this.reader = new PacketReader(channel::read, readBufferCapacity, codec);
        this.writer = new PacketWriter(channel::write, writeBufferCapacity, codec);
    }

    /**
     * Connects to an RCON server at the specified address.
     *
     * @param remote The socket address to connect to
     * @return A new Rcon instance connected to the server
     * @throws IOException if the connection fails
     */
    public static Rcon connect(final SocketAddress remote) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(remote);

        // Set read timeout on the socket
        channel.socket().setSoTimeout(5000);

        Rcon rcon = new RconBuilder()
                .withChannel(channel)
                .build();

        return rcon;
    }

    /**
     * Connects to an RCON server at the specified hostname and port.
     *
     * @param hostname The server hostname
     * @param port The RCON port
     * @return A new Rcon instance connected to the server
     * @throws IOException if the connection fails
     */
    public static Rcon connect(final String hostname, final int port) throws IOException {
        return connect(new InetSocketAddress(hostname, port));
    }

    /**
     * Creates a new builder for configuring an Rcon instance.
     *
     * @return A new RconBuilder instance
     */
    public static RconBuilder newBuilder() {
        return new RconBuilder();
    }

    /**
     * Authenticates with the RCON server using the specified password.
     *
     * @param password The RCON password
     * @return true if authentication was successful, false otherwise
     * @throws IOException if a communication error occurs
     */
    public boolean authenticate(final String password) throws IOException {
        Packet response;

        synchronized (this) {
            response = writeAndRead(PacketType.SERVERDATA_AUTH, password);

            // Handle CS:GO quirk: empty response before auth response
            if (response.type == PacketType.SERVERDATA_RESPONSE_VALUE) {
                response = read(response.requestId);
            }
        }

        if (response.type != PacketType.SERVERDATA_AUTH_RESPONSE) {
            throw new IOException("Invalid auth response type: " + response.type);
        }
        return response.isValid();
    }

    /**
     * Authenticates with the RCON server and throws an exception on failure.
     *
     * @param password The RCON password
     * @throws IOException if authentication fails
     */
    public void tryAuthenticate(final String password) throws IOException {
        if (!authenticate(password)) {
            throw new IOException("Authentication failed");
        }
    }

    /**
     * Sends a command to the RCON server and returns the response.
     * Uses the default fragment resolution strategy (ACTIVE_PROBE).
     *
     * @param command The command to execute
     * @return The server's response
     * @throws IOException if a communication error occurs
     */
    public String sendCommand(final String command) throws IOException {
        return sendCommand(command, fragmentStrategy);
    }

    /**
     * Sends a command to the RCON server with the specified fragment resolution strategy.
     *
     * @param command The command to execute
     * @param strategy The strategy for handling fragmented responses
     * @return The server's response
     * @throws IOException if a communication error occurs
     */
    public String sendCommand(final String command, final FragmentResolutionStrategy strategy) throws IOException {
        if (strategy == null) {
            throw new IllegalArgumentException("Fragment resolution strategy cannot be null");
        }

        switch (strategy) {
            case PACKET_SIZE:
                return sendCommandWithPacketSizeStrategy(command);
            case TIMEOUT:
                return sendCommandWithTimeoutStrategy(command);
            case ACTIVE_PROBE:
            default:
                return sendCommandWithActiveProbeStrategy(command);
        }
    }

    /**
     * Sends a command using the PACKET_SIZE fragment resolution strategy.
     * Waits until we receive a packet with a payload length less than 4096 bytes.
     *
     * @param command The command to execute
     * @return The server's response
     * @throws IOException if a communication error occurs
     */
    private synchronized String sendCommandWithPacketSizeStrategy(final String command) throws IOException {
        final StringBuilder responseBuilder = new StringBuilder();
        final int requestId = requestCounter++;

        // Send the command
        writer.write(new Packet(requestId, PacketType.SERVERDATA_EXECCOMMAND, command));

        // Read packets until we get one with payload < 4096
        while (true) {
            final Packet response = reader.read();

            if (response.type != PacketType.SERVERDATA_RESPONSE_VALUE) {
                throw new IOException("Wrong command response type: " + response.type);
            }
            if (!response.isValid()) {
                throw new IOException("Invalid command response: " + response.payload);
            }

            responseBuilder.append(response.payload);

            // Check if this is the last packet (payload < 4096)
            // Maximum S→C packet payload is 4096 bytes
            if (response.payload.length() < 4096) {
                break;
            }
        }

        return responseBuilder.toString();
    }

    /**
     * Sends a command using the TIMEOUT fragment resolution strategy.
     * Waits for a fixed timeout period after receiving any packet.
     *
     * @param command The command to execute
     * @return The server's response
     * @throws IOException if a communication error occurs
     */
    private synchronized String sendCommandWithTimeoutStrategy(final String command) throws IOException {
        final StringBuilder responseBuilder = new StringBuilder();
        final int requestId = requestCounter++;
        final long endTime = System.currentTimeMillis() + fragmentTimeoutMillis;

        // Send the command
        writer.write(new Packet(requestId, PacketType.SERVERDATA_EXECCOMMAND, command));

        // Read packets until timeout
        while (System.currentTimeMillis() < endTime) {
            final Packet response = reader.read();

            if (response.type != PacketType.SERVERDATA_RESPONSE_VALUE) {
                throw new IOException("Wrong command response type: " + response.type);
            }
            if (!response.isValid()) {
                throw new IOException("Invalid command response: " + response.payload);
            }

            responseBuilder.append(response.payload);

            // Reset the end time after receiving a packet
            // Keep reading as long as packets arrive within the timeout
        }

        return responseBuilder.toString();
    }

    /**
     * Sends a command using the ACTIVE_PROBE fragment resolution strategy.
     * Sends a second command packet to probe for the end of the response only if needed.
     *
     * @param command The command to execute
     * @return The server's response
     * @throws IOException if a communication error occurs
     */
    private synchronized String sendCommandWithActiveProbeStrategy(final String command) throws IOException {
        final StringBuilder responseBuilder = new StringBuilder();
        final int requestId = requestCounter++;

        // Send the main command
        writer.write(new Packet(requestId, PacketType.SERVERDATA_EXECCOMMAND, command));

        // Read the first response packet
        Packet response = reader.read();

        if (response.type != PacketType.SERVERDATA_RESPONSE_VALUE) {
            throw new IOException("Wrong command response type: " + response.type);
        }
        if (!response.isValid()) {
            throw new IOException("Invalid command response: " + response.payload);
        }

        responseBuilder.append(response.payload);

        // If the payload is exactly 4096 bytes, there might be more fragments
        // Send a probe command to detect when all fragments have been received
        if (response.payload.length() >= 4096) {
            final int probeRequestId = requestCounter++;
            writer.write(new Packet(probeRequestId, PacketType.SERVERDATA_EXECCOMMAND, ""));

            // Read remaining fragments until we get the probe response
            while (true) {
                response = reader.read();

                if (response.type != PacketType.SERVERDATA_RESPONSE_VALUE) {
                    throw new IOException("Wrong command response type: " + response.type);
                }

                // Check if this is a response to our probe command
                if (response.requestId == probeRequestId) {
                    // We've received the probe response, so we're done
                    break;
                }

                if (!response.isValid()) {
                    throw new IOException("Invalid command response: " + response.payload);
                }

                responseBuilder.append(response.payload);
            }
        }

        return responseBuilder.toString();
    }

    /**
     * Sends a command with a timeout.
     *
     * @param command The command to execute
     * @param timeout The maximum time to wait
     * @param unit The time unit for the timeout
     * @return The server's response
     * @throws IOException if a communication error occurs
     * @throws TimeoutException if the operation times out
     */
    public String sendCommand(final String command, final long timeout, final TimeUnit unit)
            throws IOException, TimeoutException {
        // TODO: Implement timeout support
        return sendCommand(command);
    }

    /**
     * Writes a packet and reads the response.
     * This method is synchronized to ensure request/response matching.
     *
     * @param packetType The type of packet to send
     * @param payload The payload to send
     * @return The response packet
     * @throws IOException if a communication error occurs
     */
    private synchronized Packet writeAndRead(final int packetType, final String payload)
            throws IOException {
        final int requestId = requestCounter++;
        writer.write(new Packet(requestId, packetType, payload));
        return read(requestId);
    }

    /**
     * Reads a response packet matching the expected request ID.
     *
     * @param expectedRequestId The request ID to match
     * @return The response packet
     * @throws IOException if a communication error occurs
     */
    private synchronized Packet read(final int expectedRequestId) throws IOException {
        final Packet response = reader.read();

        if (response.isValid() && response.requestId != expectedRequestId) {
            throw new IOException(
                String.format("Unexpected response id (%d -> %d)", expectedRequestId, response.requestId)
            );
        }
        return response;
    }

    /**
     * Checks if the underlying channel is connected.
     *
     * @return true if the channel is connected
     */
    public boolean isConnected() {
        return channel.isOpen() && channel instanceof SocketChannel && ((SocketChannel) channel).isConnected();
    }

    /**
     * Enables or disables verbose logging.
     *
     * @param verbose true to enable verbose logging
     */
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the connection timeout.
     *
     * @param timeout The timeout in milliseconds
     */
    public void setConnectTimeout(final int timeout) {
        this.connectTimeout = timeout;
    }

    /**
     * Sets the fragment resolution strategy.
     *
     * @param strategy The strategy to use for handling fragmented responses
     */
    public void setFragmentResolutionStrategy(final FragmentResolutionStrategy strategy) {
        this.fragmentStrategy = strategy;
    }

    /**
     * Sets the timeout for fragment resolution (used with TIMEOUT strategy).
     *
     * @param timeout The timeout value
     * @param unit The time unit for the timeout
     */
    public void setFragmentTimeout(final long timeout, final TimeUnit unit) {
        this.fragmentTimeoutMillis = unit.toMillis(timeout);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * Builder class for creating Rcon instances with custom configuration.
     */
    public static class RconBuilder {

        private ByteChannel channel;
        private Integer readBufferCapacity = PacketReader.DEFAULT_BUFFER_CAPACITY;
        private Integer writeBufferCapacity = PacketWriter.DEFAULT_BUFFER_CAPACITY;
        private PacketCodec codec = new PacketCodec();

        /**
         * Sets the channel to use for communication.
         *
         * @param channel The byte channel
         * @return This builder for chaining
         */
        public RconBuilder withChannel(ByteChannel channel) {
            this.channel = channel;
            return this;
        }

        /**
         * Sets the read buffer capacity.
         *
         * @param readBufferCapacity The capacity in bytes
         * @return This builder for chaining
         */
        public RconBuilder withReadBufferCapacity(final int readBufferCapacity) {
            this.readBufferCapacity = readBufferCapacity;
            return this;
        }

        /**
         * Sets the write buffer capacity.
         *
         * @param writeBufferCapacity The capacity in bytes
         * @return This builder for chaining
         */
        public RconBuilder withWriteBufferCapacity(final int writeBufferCapacity) {
            this.writeBufferCapacity = writeBufferCapacity;
            return this;
        }

        /**
         * Sets the charset for encoding/decoding.
         *
         * @param charset The charset to use
         * @return This builder for chaining
         */
        public RconBuilder withCharset(java.nio.charset.Charset charset) {
            this.codec = new PacketCodec(charset);
            return this;
        }

        /**
         * Builds and returns the Rcon instance.
         *
         * @return A new Rcon instance
         * @throws NullPointerException if no channel was specified
         */
        public Rcon build() {
            if (channel == null) {
                throw new NullPointerException("channel must be specified");
            }
            return new Rcon(channel, readBufferCapacity, writeBufferCapacity, codec);
        }
    }
}
