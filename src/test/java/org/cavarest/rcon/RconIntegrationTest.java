package org.cavarest.rcon;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Integration tests for the RCON library against a real Minecraft server.
 *
 * These tests use Testcontainers to spin up a real Minecraft server with RCON enabled.
 * They require Docker to be running and are typically run in CI/CD environments.
 *
 * To skip these tests locally, set the environment variable SKIP_INTEGRATION_TESTS=true
 */
@Testcontainers
@Tag("integration")
@DisplayName("RCON Integration Tests")
class RconIntegrationTest {

    private static final String RCON_PASSWORD = "cavarest";
    private static final int RCON_PORT = 25575;
    private static final int SERVER_PORT = 25565;
    private static final String MINECRAFT_VERSION = "1.21.8";

    @Container
    public static GenericContainer<?> minecraftServer = new GenericContainer<>("papermc/paper:" + MINECRAFT_VERSION)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("minecraft")
            .withExposedPorts(SERVER_PORT, RCON_PORT)
            .withEnv("EULA", "TRUE")
            .withEnv("ONLINE_MODE", "false")
            .withEnv("SERVER_PORT", String.valueOf(SERVER_PORT))
            .withEnv("RCON_PORT", String.valueOf(RCON_PORT))
            .withEnv("RCON_PASSWORD", RCON_PASSWORD)
            .withEnv("MAX_PLAYERS", "5")
            .withEnv("MEMORY", "512M")
            .withLogConsumer(output -> System.out.println("[MC] " + output.getUtf8String()))
            .withStartupTimeout(Duration.ofSeconds(300));

    private RconClient rconClient;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        assumeFalse(
            Boolean.parseBoolean(System.getenv("SKIP_INTEGRATION_TESTS")),
            "Skipping integration tests as SKIP_INTEGRATION_TESTS is set"
        );

        String host = minecraftServer.getHost();
        int port = minecraftServer.getMappedPort(RCON_PORT);

        System.out.println("Connecting to Minecraft RCON at " + host + ":" + port);

        rconClient = new RconClient(host, port, RCON_PASSWORD);

        try {
            rconClient.connect();
            System.out.println("Successfully connected to Minecraft RCON");
        } catch (Exception e) {
            fail("Failed to connect to Minecraft RCON: " + e.getMessage());
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
    @DisplayName("Should connect and authenticate with Minecraft server")
    void shouldConnectAndAuthenticate() {
        assertTrue(rconClient.isConnected(), "Should be connected after connect()");
    }

    @Test
    @DisplayName("Should execute simple command")
    void shouldExecuteSimpleCommand() throws IOException {
        String response = rconClient.sendCommand("list");
        assertNotNull(response, "Response should not be null");
        System.out.println("Response from 'list': " + response);
    }

    @Test
    @DisplayName("Should execute say command")
    void shouldExecuteSayCommand() throws IOException {
        String response = rconClient.sendCommand("say Hello from RCON integration test!");
        assertNotNull(response, "Response should not be null");
        System.out.println("Response from 'say': " + response);
    }

    @Test
    @DisplayName("Should get server version")
    void shouldGetServerVersion() throws IOException {
        String version = rconClient.sendCommand("version");
        assertNotNull(version, "Version response should not be null");
        assertTrue(version.contains("Paper") || version.contains("1.21"),
            "Version should contain Paper or 1.21: " + version);
        System.out.println("Server version: " + version);
    }

    @Test
    @DisplayName("Should execute command with arguments")
    void shouldExecuteCommandWithArguments() throws IOException {
        String response = rconClient.sendCommand("minecraft", "say", "Testing command with arguments");
        assertNotNull(response, "Response should not be null");
    }

    @Test
    @DisplayName("Should handle multiple commands")
    void shouldHandleMultipleCommands() throws IOException {
        String response1 = rconClient.sendCommand("list");
        assertNotNull(response1);

        String response2 = rconClient.sendCommand("time query day");
        assertNotNull(response2);

        String response3 = rconClient.sendCommand("difficulty");
        assertNotNull(response3);

        System.out.println("Multiple commands executed successfully");
    }

    @Test
    @DisplayName("Should return content for help command")
    void shouldReturnContentForHelpCommand() throws IOException {
        String response = rconClient.sendCommand("help");
        assertNotNull(response);
        assertTrue(response.length() > 0, "Help response should have content");
    }

    @Test
    @DisplayName("Should handle invalid command gracefully")
    void shouldHandleInvalidCommandGracefully() throws IOException {
        String response = rconClient.sendCommand("nonexistent_command_xyz");
        assertNotNull(response);
        System.out.println("Invalid command response: " + response);
    }

    @Test
    @DisplayName("Should get player count")
    void shouldGetPlayerCount() throws IOException {
        String listResponse = rconClient.sendCommand("list");
        assertNotNull(listResponse);
        assertTrue(listResponse.contains("players") || listResponse.contains("There are"),
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
                    try (RconClient client = new RconClient(
                            minecraftServer.getHost(),
                            minecraftServer.getMappedPort(RCON_PORT),
                            RCON_PASSWORD)) {
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
