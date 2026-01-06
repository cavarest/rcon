package org.cavarest.rcon;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level RCON client for Minecraft server communication.
 *
 * This class provides a simplified API for:
 * - Connecting to Minecraft RCON servers
 * - Executing commands with proper error handling
 * - Logging support for debugging
 *
 * This is the main class that PILAF uses to communicate with Minecraft servers.
 *
 * Example usage:
 * <pre>{@code
 * try (RconClient client = new RconClient("localhost", 25575, "password")) {
 *     client.connect();
 *     String result = client.sendCommand("say Hello from PILAF!");
 *     System.out.println("Command result: " + result);
 * } catch (IOException e) {
 *     System.err.println("RCON error: " + e.getMessage());
 * }
 * }</pre>
 */
public class RconClient implements Closeable {

    private static final Logger logger = Logger.getLogger(RconClient.class.getName());

    /** Default RCON port for Minecraft servers */
    public static final int DEFAULT_PORT = 25575;

    /** Default connection timeout in milliseconds */
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

    /** Default command timeout in milliseconds */
    public static final int DEFAULT_COMMAND_TIMEOUT = 10000;

    private final String host;
    private final int port;
    private final String password;
    private final int connectTimeout;
    private final int commandTimeout;

    private Rcon rcon;
    private boolean connected = false;
    private boolean verbose = false;
    private FragmentResolutionStrategy fragmentStrategy = FragmentResolutionStrategy.ACTIVE_PROBE;
    private long fragmentTimeoutMillis = 100;

    /**
     * Creates a new RconClient with the specified connection parameters.
     *
     * @param host The Minecraft server hostname
     * @param port The RCON port
     * @param password The RCON password
     */
    public RconClient(final String host, final int port, final String password) {
        this(host, port, password, DEFAULT_CONNECT_TIMEOUT, DEFAULT_COMMAND_TIMEOUT);
    }

    /**
     * Creates a new RconClient with custom timeouts.
     *
     * @param host The Minecraft server hostname
     * @param port The RCON port
     * @param password The RCON password
     * @param connectTimeout The connection timeout in milliseconds
     * @param commandTimeout The command timeout in milliseconds
     */
    public RconClient(final String host, final int port, final String password,
                      final int connectTimeout, final int commandTimeout) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.connectTimeout = connectTimeout;
        this.commandTimeout = commandTimeout;
    }

    /**
     * Connects to the RCON server and authenticates.
     *
     * @throws IOException if connection or authentication fails
     */
    public void connect() throws IOException {
        if (connected) {
            log(Level.WARNING, "Already connected to RCON server");
            return;
        }

        log(Level.INFO, "Connecting to RCON server at " + host + ":" + port);

        try {
            // Create socket channel with timeout
            java.nio.channels.SocketChannel channel = java.nio.channels.SocketChannel.open();
            channel.configureBlocking(true);
            channel.socket().setSoTimeout(connectTimeout);
            channel.connect(new InetSocketAddress(host, port));

            // Create RCON instance
            rcon = new Rcon.RconBuilder()
                    .withChannel(channel)
                    .withReadBufferCapacity(8192)
                    .withWriteBufferCapacity(4096)
                    .build();

            // Authenticate
            if (!rcon.authenticate(password)) {
                channel.close();
                throw new IOException("RCON authentication failed");
            }

            connected = true;
            log(Level.INFO, "Successfully connected to RCON server");

        } catch (IOException e) {
            log(Level.SEVERE, "Failed to connect to RCON server: " + e.getMessage());
            throw new IOException("RCON connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a command to the server and returns the response.
     * Uses the default fragment resolution strategy (ACTIVE_PROBE).
     *
     * @param command The command to execute
     * @return The server's response, or an empty string if no response
     * @throws IOException if not connected or a communication error occurs
     */
    public String sendCommand(final String command) throws IOException {
        return sendCommand(command, fragmentStrategy);
    }

    /**
     * Sends a command to the server with the specified fragment resolution strategy.
     *
     * @param command The command to execute
     * @param strategy The strategy for handling fragmented responses
     * @return The server's response, or an empty string if no response
     * @throws IOException if not connected or a communication error occurs
     */
    public String sendCommand(final String command, final FragmentResolutionStrategy strategy) throws IOException {
        if (!connected || rcon == null) {
            throw new IOException("Not connected to RCON server");
        }

        log(Level.INFO, "Sending command: " + command);

        try {
            String response = rcon.sendCommand(command, strategy);
            log(Level.INFO, "Response: " + (response != null ? response : "(null)"));
            return response != null ? response : "";
        } catch (IOException e) {
            log(Level.SEVERE, "Command failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Sends a command with arguments.
     *
     * @param command The base command
     * @param args The command arguments
     * @return The server's response
     * @throws IOException if a communication error occurs
     */
    public String sendCommand(final String command, final String... args) throws IOException {
        String fullCommand = command;
        for (String arg : args) {
            fullCommand += " " + arg;
        }
        return sendCommand(fullCommand);
    }

    /**
     * Sends a command and returns a boolean indicating success.
     * For commands that don't return meaningful output.
     *
     * @param command The command to execute
     * @return true if the command was sent successfully
     */
    public boolean trySendCommand(final String command) {
        try {
            sendCommand(command);
            return true;
        } catch (IOException e) {
            log(Level.WARNING, "Command failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the connection is still active.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected && rcon != null && rcon.isConnected();
    }

    /**
     * Enables or disables verbose logging.
     *
     * @param verbose true to enable verbose logging
     */
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
        if (rcon != null) {
            rcon.setVerbose(verbose);
        }
    }

    /**
     * Sets the fragment resolution strategy for handling fragmented command responses.
     *
     * @param strategy The strategy to use
     */
    public void setFragmentResolutionStrategy(final FragmentResolutionStrategy strategy) {
        this.fragmentStrategy = strategy;
        if (rcon != null) {
            rcon.setFragmentResolutionStrategy(strategy);
        }
    }

    /**
     * Sets the timeout for fragment resolution (used with TIMEOUT strategy).
     *
     * @param timeout The timeout value
     * @param unit The time unit for the timeout
     */
    public void setFragmentTimeout(final long timeout, final java.util.concurrent.TimeUnit unit) {
        this.fragmentTimeoutMillis = unit.toMillis(timeout);
        if (rcon != null) {
            rcon.setFragmentTimeout(timeout, unit);
        }
    }

    /**
     * Logs a message if verbose mode is enabled.
     *
     * @param level The log level
     * @param message The message to log
     */
    private void log(final Level level, final String message) {
        if (verbose || level.intValue() >= Level.INFO.intValue()) {
            logger.log(level, "[RCON] " + message);
        }
    }

    @Override
    public void close() throws IOException {
        if (connected && rcon != null) {
            log(Level.INFO, "Closing RCON connection");
            try {
                rcon.close();
            } catch (IOException e) {
                log(Level.WARNING, "Error closing connection: " + e.getMessage());
            }
            rcon = null;
            connected = false;
            log(Level.INFO, "RCON connection closed");
        }
    }

    /**
     * Gets the host address.
     *
     * @return The hostname
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port number.
     *
     * @return The RCON port
     */
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return String.format("RconClient{host=%s, port=%d, connected=%s}", host, port, connected);
    }
}
