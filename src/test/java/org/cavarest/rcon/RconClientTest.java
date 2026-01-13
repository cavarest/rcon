package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RconClient class.
 */
class RconClientTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create client with all parameters")
        void shouldCreateWithAllParameters() {
            RconClient client = new RconClient("localhost", 25575, "password", 5000, 10000);
            assertNotNull(client);
        }

        @Test
        @DisplayName("Should create client with default timeouts")
        void shouldCreateWithDefaultTimeouts() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertNotNull(client);
        }

        @Test
        @DisplayName("Should store host correctly")
        void shouldStoreHostCorrectly() {
            RconClient client = new RconClient("testhost", 25575, "password");
            assertEquals("testhost", client.getHost());
        }

        @Test
        @DisplayName("Should store port correctly")
        void shouldStorePortCorrectly() {
            RconClient client = new RconClient("localhost", 12345, "password");
            assertEquals(12345, client.getPort());
        }
    }

    @Nested
    @DisplayName("Default Constants Tests")
    class DefaultConstantsTests {

        @Test
        @DisplayName("Should have correct default port")
        void shouldHaveCorrectDefaultPort() {
            assertEquals(25575, RconClient.DEFAULT_PORT);
        }

        @Test
        @DisplayName("Should have correct default connect timeout")
        void shouldHaveCorrectDefaultConnectTimeout() {
            assertEquals(5000, RconClient.DEFAULT_CONNECT_TIMEOUT);
        }

        @Test
        @DisplayName("Should have correct default command timeout")
        void shouldHaveCorrectDefaultCommandTimeout() {
            assertEquals(10000, RconClient.DEFAULT_COMMAND_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Closeable Tests")
    class CloseableTests {

        @Test
        @DisplayName("RconClient should implement Closeable")
        void shouldImplementCloseable() {
            assertTrue(java.io.Closeable.class.isAssignableFrom(RconClient.class));
        }

        @Test
        @DisplayName("close() should handle already closed connection")
        void closeShouldHandleAlreadyClosed() throws IOException {
            RconClient client = new RconClient("localhost", 25575, "password");
            // Should not throw even if not connected
            assertDoesNotThrow(() -> client.close());
        }
    }

    @Nested
    @DisplayName("Verbose Mode Tests")
    class VerboseModeTests {

        @Test
        @DisplayName("Should enable verbose mode")
        void shouldEnableVerboseMode() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertDoesNotThrow(() -> client.setVerbose(true));
        }

        @Test
        @DisplayName("Should disable verbose mode")
        void shouldDisableVerboseMode() {
            RconClient client = new RconClient("localhost", 25575, "password");
            client.setVerbose(true);
            assertDoesNotThrow(() -> client.setVerbose(false));
        }
    }

    @Nested
    @DisplayName("Connect Tests")
    class ConnectTests {

        @Test
        @DisplayName("connect() should throw IOException when no server")
        void connectShouldThrowWhenNoServer() throws IOException {
            RconClient client = new RconClient("localhost", 25575, "password");
            try {
                client.connect();
                fail("Expected IOException");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("RCON connection failed") ||
                           e.getMessage().contains("Connection refused") ||
                           e.getMessage().contains("timed out"));
            }
        }

        @Test
        @DisplayName("connect() should handle already connected state")
        void connectShouldHandleAlreadyConnected() throws IOException {
            RconClient client = new RconClient("localhost", 25575, "password");
            // First connect attempt will fail
            try {
                client.connect();
            } catch (IOException e) {
                // Expected
            }

            // Second connect attempt should be handled gracefully
            try {
                client.connect();
            } catch (IOException e) {
                // Also expected
            }
        }

        @Test
        @DisplayName("isConnected() should return false initially")
        void isConnectedShouldReturnFalseInitially() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertFalse(client.isConnected());
        }
    }

    @Nested
    @DisplayName("Send Command Tests")
    class SendCommandTests {

        @Test
        @DisplayName("sendCommand() should throw IOException when not connected")
        void sendCommandShouldThrowWhenNotConnected() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertThrows(IOException.class, () -> client.sendCommand("test"));
        }

        @Test
        @DisplayName("sendCommand() with args should build command")
        void sendCommandWithArgsShouldBuildCommand() throws IOException {
            RconClient client = new RconClient("localhost", 25575, "password");
            try {
                client.sendCommand("give", "player", "diamond", "64");
                fail("Expected IOException");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("Not connected") ||
                           e.getMessage().contains("connection failed") ||
                           e.getMessage().contains("refused") ||
                           e.getMessage().contains("timed out"));
            }
        }

        @Test
        @DisplayName("trySendCommand() should return false when not connected")
        void trySendCommandShouldReturnFalseWhenNotConnected() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertFalse(client.trySendCommand("test"));
        }
    }

    @Nested
    @DisplayName("Fragment Resolution Strategy Tests")
    class FragmentResolutionStrategyTests {

        @Test
        @DisplayName("Should set fragment resolution strategy")
        void shouldSetFragmentResolutionStrategy() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertDoesNotThrow(() -> client.setFragmentResolutionStrategy(FragmentResolutionStrategy.PACKET_SIZE));
            assertDoesNotThrow(() -> client.setFragmentResolutionStrategy(FragmentResolutionStrategy.TIMEOUT));
            assertDoesNotThrow(() -> client.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE));
        }
    }

    @Nested
    @DisplayName("Fragment Timeout Tests")
    class FragmentTimeoutTests {

        @Test
        @DisplayName("Should set fragment timeout")
        void shouldSetFragmentTimeout() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertDoesNotThrow(() -> client.setFragmentTimeout(100, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    @Nested
    @DisplayName("Send Command with Strategy Tests")
    class SendCommandWithStrategyTests {

        @Test
        @DisplayName("sendCommand() with strategy should throw when not connected")
        void sendCommandWithStrategyShouldThrowWhenNotConnected() {
            RconClient client = new RconClient("localhost", 25575, "password");
            assertThrows(IOException.class, () -> client.sendCommand("test", FragmentResolutionStrategy.ACTIVE_PROBE));
        }
    }

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString should contain host")
        void toStringShouldContainHost() {
            RconClient client = new RconClient("testhost", 25575, "password");
            String result = client.toString();
            assertTrue(result.contains("testhost"));
        }

        @Test
        @DisplayName("toString should contain port")
        void toStringShouldContainPort() {
            RconClient client = new RconClient("localhost", 12345, "password");
            String result = client.toString();
            assertTrue(result.contains("12345"));
        }

        @Test
        @DisplayName("toString should contain connected status")
        void toStringShouldContainConnectedStatus() {
            RconClient client = new RconClient("localhost", 25575, "password");
            String result = client.toString();
            assertTrue(result.contains("connected="));
        }
    }
}
