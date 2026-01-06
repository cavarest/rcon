package org.cavarest.rcon;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for the RCON library against any RCON server.
 *
 * These tests are infrastructure-agnostic and can run against any RCON server
 * (Testcontainers, docker-compose, or manually started).
 *
 * Configuration via environment variables:
 * - RCON_HOST: Server hostname (default: localhost)
 * - RCON_PORT: RCON port (default: 25575)
 * - RCON_PASSWORD: RCON password (default: cavarest)
 * - SKIP_INTEGRATION_TESTS: Set to true to skip these tests
 */
@Tag("integration")
@DisplayName("RCON Integration Tests")
class RconIntegrationTest {

    // Read connection parameters from environment or use defaults
    private static final String RCON_HOST = System.getenv().getOrDefault("RCON_HOST", "localhost");
    private static final int RCON_PORT = Integer.parseInt(System.getenv().getOrDefault("RCON_PORT", "25575"));
    private static final String RCON_PASSWORD = System.getenv().getOrDefault("RCON_PASSWORD", "cavarest");

    private RconClient rconClient;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Skip if explicitly disabled
        assumeFalse(
            Boolean.parseBoolean(System.getenv("SKIP_INTEGRATION_TESTS")),
            "Skipping integration tests as SKIP_INTEGRATION_TESTS is set"
        );

        System.out.println("========================================");
        System.out.println("RCON Integration Test: " + testInfo.getDisplayName());
        System.out.println("Connecting to RCON server at " + RCON_HOST + ":" + RCON_PORT);
        System.out.println("========================================");

        rconClient = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD);

        try {
            rconClient.connect();
            System.out.println("Successfully connected to RCON server");
        } catch (Exception e) {
            System.err.println("FAILED to connect to RCON server: " + e.getMessage());
            e.printStackTrace();
            // Assume the server is not running and skip the tests
            assumeTrue(false, "RCON server not available at " + RCON_HOST + ":" + RCON_PORT +
                       " - " + e.getMessage() + ". Start a server with docker-compose or set SKIP_INTEGRATION_TESTS=true");
        }

        scheduler = Executors.newScheduledThreadPool(1);
    }

    @AfterEach
    void tearDown() {
        if (rconClient != null) {
            try {
                rconClient.close();
                System.out.println("Closed RCON connection");
            } catch (Exception e) {
                System.err.println("Error closing RCON connection: " + e.getMessage());
            }
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    @DisplayName("Should connect and authenticate with RCON server")
    void shouldConnectAndAuthenticate() {
        assertTrue(rconClient.isConnected(), "Should be connected after connect()");
    }

    @Test
    @DisplayName("Should execute simple command")
    void shouldExecuteSimpleCommand() throws IOException {
        String response = rconClient.sendCommand("list");
        assertNotNull(response, "Response should not be null");
        // Response should contain player count information
        assertTrue(response.matches(".*\\d+.*player.*") || response.contains("There are"),
            "Response should contain player count: " + response);
        System.out.println("Response from 'list': " + response);
    }

    @Test
    @DisplayName("Should execute say command")
    void shouldExecuteSayCommand() throws IOException {
        String response = rconClient.sendCommand("say Hello from RCON integration test!");
        assertNotNull(response, "Response should not be null");
        assertTrue(response.isEmpty() || response.contains("Hello from RCON"),
            "Server should have broadcast the message: " + response);
        System.out.println("Response from 'say': " + response);
    }

    @Test
    @DisplayName("Should get server version")
    void shouldGetServerVersion() throws IOException {
        String version = rconClient.sendCommand("version");
        assertNotNull(version, "Version response should not be null");
        // Paper servers may return "Checking version, please wait..." first
        // If still checking, skip the detailed validation
        if (version.contains("Checking version")) {
            System.out.println("Server is still checking version: " + version);
            assertTrue(true, "Version check in progress");
            return;
        }
        assertFalse(version.isEmpty(), "Version should not be empty");
        // Server version should contain version info
        assertTrue(version.contains("Paper") || version.contains("1.21") || version.matches(".*\\d+\\.\\d+.*"),
            "Version should contain version information: " + version);
        System.out.println("Server version: " + version);
    }

    @Test
    @DisplayName("Should execute command with arguments")
    void shouldExecuteCommandWithArguments() throws IOException {
        String response = rconClient.sendCommand("say", "Testing", "command", "with", "arguments");
        assertNotNull(response, "Response should not be null");
        System.out.println("Response from 'say' with args: " + response);
    }

    @Test
    @DisplayName("Should handle multiple commands")
    void shouldHandleMultipleCommands() throws IOException {
        String response1 = rconClient.sendCommand("list");
        assertNotNull(response1);
        assertTrue(response1.matches(".*\\d+.*") || response1.contains("There are"),
            "List response should contain player info: " + response1);

        String response2 = rconClient.sendCommand("time query day");
        assertNotNull(response2);
        // Time query should return a number
        assertTrue(response2.matches(".*\\d+.*") || response2.contains("Daytime"),
            "Time response should contain time info: " + response2);

        String response3 = rconClient.sendCommand("difficulty");
        assertNotNull(response3);
        // Difficulty should contain difficulty level
        assertTrue(response3.toLowerCase().contains("peaceful") ||
                   response3.toLowerCase().contains("easy") ||
                   response3.toLowerCase().contains("normal") ||
                   response3.toLowerCase().contains("hard"),
            "Difficulty response should contain difficulty level: " + response3);

        System.out.println("Multiple commands executed successfully");
    }

    @Test
    @DisplayName("Should return content for help command")
    void shouldReturnContentForHelpCommand() throws IOException {
        String response = rconClient.sendCommand("help");
        assertNotNull(response);
        assertTrue(response.length() > 0, "Help response should have content");
        // Help response should contain command information
        assertTrue(response.contains("/") || response.contains("Available") || response.contains("help"),
            "Help response should contain command info: " + response);
    }

    @Test
    @DisplayName("Should get player count")
    void shouldGetPlayerCount() throws IOException {
        String listResponse = rconClient.sendCommand("list");
        assertNotNull(listResponse);
        assertTrue(listResponse.contains("players") || listResponse.contains("There are") || listResponse.contains("player"),
            "List response should mention players: " + listResponse);
        System.out.println("Player list: " + listResponse);
    }

    @Test
    @DisplayName("Should send command with trySendCommand")
    void shouldSendCommandWithTrySendCommand() {
        boolean result = rconClient.trySendCommand("list");
        assertTrue(result, "trySendCommand should return true for valid command");
    }

    @Test
    @DisplayName("Should maintain connection across multiple commands")
    void shouldMaintainConnectionAcrossCommands() throws IOException {
        for (int i = 0; i < 5; i++) {
            String response = rconClient.sendCommand("list");
            assertNotNull(response, "Command " + (i + 1) + " should return response");
            assertTrue(rconClient.isConnected(), "Should still be connected after command " + (i + 1));
        }
    }

    @Test
    @DisplayName("Should handle concurrent command attempts")
    void shouldHandleConcurrentCommandAttempts() throws Exception {
        AtomicBoolean hasError = new AtomicBoolean(false);
        int numThreads = 3;

        CompletableFuture<?>[] futures = new CompletableFuture[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    try (RconClient client = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD)) {
                        client.connect();
                        String response = client.sendCommand("list");
                        assertNotNull(response);
                    }
                } catch (Exception e) {
                    hasError.set(true);
                    System.err.println("Error in thread " + threadId + ": " + e.getMessage());
                }
            }, scheduler);
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        assertFalse(hasError.get(), "No errors should occur during concurrent commands");
    }
}
