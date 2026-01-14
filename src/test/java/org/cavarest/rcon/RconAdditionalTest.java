package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional unit tests for Rcon to improve coverage.
 * These tests use actual socket connections to exercise more code paths.
 */
class RconAdditionalTest {

    @Nested
    @DisplayName("Socket Connection Tests")
    class SocketConnectionTests {

        @Test
        @DisplayName("Should create and close Rcon with socket")
        void shouldCreateAndCloseRconWithSocket() throws IOException {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.configureBlocking(true);
                try (Rcon rcon = Rcon.newBuilder().withChannel(channel).build()) {
                    assertNotNull(rcon);
                }
            }
        }

        @Test
        @DisplayName("Should handle connection timeout")
        void shouldHandleConnectionTimeout() {
            // Try to connect to a host that won't respond
            // Using a non-routable IP should cause a timeout
            assertThrows(IOException.class, () -> {
                Rcon.connect("192.0.2.1", 19999); // TEST-NET-1, should not respond
            });
        }

        @Test
        @DisplayName("Should handle connection to non-existent server")
        void shouldHandleConnectionToNonExistentServer() {
            // Connect to localhost on a port that's unlikely to be in use
            int unlikelyPort = 39999;
            IOException exception = assertThrows(IOException.class, () -> Rcon.connect("localhost", unlikelyPort));
            assertTrue(exception.getMessage().contains("Connection refused") ||
                       exception.getMessage().contains("refused"));
        }
    }

    @Nested
    @DisplayName("Server Communication Tests")
    class ServerCommunicationTests {

        @Test
        @DisplayName("Should attempt authentication with minimal server")
        void shouldAttemptAuthenticationWithMinimalServer() throws Exception {
            // Create a minimal server that accepts connection but sends invalid RCON response
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                int port = serverChannel.socket().getLocalPort();

                // Thread to accept connection and send some data
                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel clientChannel = serverChannel.accept();
                        // Send a minimal invalid response (not proper RCON)
                        ByteBuffer buffer = ByteBuffer.allocate(10);
                        buffer.put(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
                        buffer.flip();
                        clientChannel.write(buffer);
                        // Don't close immediately
                        Thread.sleep(100);
                        clientChannel.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                });
                serverThread.start();

                // Try to connect and authenticate (should fail but exercise code)
                try {
                    try (Rcon rcon = Rcon.connect("localhost", port)) {
                        boolean result = rcon.authenticate("test");
                        assertFalse(result, "Auth should fail with invalid server");
                    }
                } catch (IOException e) {
                    // Expected - the server sent invalid response
                    assertTrue(e.getMessage() != null && !e.getMessage().isEmpty());
                }

                serverThread.join(1000);
            }
        }
    }

    @Nested
    @DisplayName("Timeout Strategy Tests")
    class TimeoutStrategyTests {

        @Test
        @DisplayName("Should attempt sendCommand with TIMEOUT strategy")
        void shouldAttemptSendCommandWithTimeoutStrategy() throws Exception {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.configureBlocking(true);
                try (Rcon rcon = Rcon.newBuilder().withChannel(channel).build()) {
                    rcon.setFragmentResolutionStrategy(FragmentResolutionStrategy.TIMEOUT);
                    rcon.setFragmentTimeout(50, TimeUnit.MILLISECONDS);

                    assertThrows(Exception.class, () -> rcon.sendCommand("test"));
                }
            }
        }
    }

    @Nested
    @DisplayName("Active Probe Strategy Tests")
    class ActiveProbeStrategyTests {

        @Test
        @DisplayName("Should attempt sendCommand with ACTIVE_PROBE strategy")
        void shouldAttemptSendCommandWithActiveProbeStrategy() throws Exception {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.configureBlocking(true);
                try (Rcon rcon = Rcon.newBuilder().withChannel(channel).build()) {
                    rcon.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

                    assertThrows(Exception.class, () -> rcon.sendCommand("test"));
                }
            }
        }
    }

    @Nested
    @DisplayName("Builder Configuration Tests")
    class BuilderConfigurationTests {

        @Test
        @DisplayName("Builder should create Rcon with all configurations")
        void builderShouldCreateRconWithAllConfigurations() throws Exception {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.configureBlocking(true);
                try (Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .withReadBufferCapacity(8192)
                        .withWriteBufferCapacity(4096)
                        .withCharset(java.nio.charset.StandardCharsets.UTF_8)
                        .build()) {

                    assertNotNull(rcon);
                    assertFalse(rcon.isConnected());
                }
            }
        }

        @Test
        @DisplayName("Builder methods should be chainable")
        void builderMethodsShouldBeChainable() {
            Rcon.RconBuilder builder = Rcon.newBuilder();

            // Test that all methods return the builder for chaining
            assertSame(builder, builder.withReadBufferCapacity(4096));
            assertSame(builder, builder.withWriteBufferCapacity(2048));
            assertSame(builder, builder.withCharset(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTests {

        @Test
        @DisplayName("IOException should contain meaningful error message")
        void ioExceptionShouldContainMeaningfulMessage() throws Exception {
            // Try to connect to a non-existent server instead
            IOException exception = assertThrows(IOException.class, () -> Rcon.connect("localhost", 49999));
            assertNotNull(exception.getMessage());
            // The message should contain some information about the connection failure
            assertTrue(exception.getMessage().length() > 0);
        }
    }

    @Nested
    @DisplayName("State Tests")
    class StateTests {

        @Test
        @DisplayName("Multiple close calls should be safe")
        void multipleCloseCallsShouldBeSafe() throws Exception {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            Rcon rcon = Rcon.newBuilder().withChannel(channel).build();

            assertDoesNotThrow(() -> rcon.close());
            assertDoesNotThrow(() -> rcon.close());
            assertDoesNotThrow(() -> rcon.close());
        }

        @Test
        @DisplayName("Setting configuration after close should not throw")
        void settingConfigAfterCloseShouldNotThrow() throws Exception {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            Rcon rcon = Rcon.newBuilder().withChannel(channel).build();

            rcon.close();

            assertDoesNotThrow(() -> rcon.setFragmentTimeout(100, TimeUnit.MILLISECONDS));
            assertDoesNotThrow(() -> rcon.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE));
        }
    }

    @Nested
    @DisplayName("Null Handling Tests")
    class NullHandlingTests {

        @Test
        @DisplayName("authenticate should handle null password gracefully")
        void authenticateShouldHandleNullPassword() throws Exception {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.configureBlocking(true);
                try (Rcon rcon = Rcon.newBuilder().withChannel(channel).build()) {
                    // This should either throw or return false
                    // The behavior depends on implementation
                    assertThrows(Exception.class, () -> rcon.authenticate((String) null));
                }
            }
        }

        @Test
        @DisplayName("sendCommand should handle null command gracefully")
        void sendCommandShouldHandleNullCommand() throws Exception {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.configureBlocking(true);
                try (Rcon rcon = Rcon.newBuilder().withChannel(channel).build()) {
                    assertThrows(Exception.class, () -> rcon.sendCommand((String) null));
                }
            }
        }
    }

    @Nested
    @DisplayName("Strategy Tests")
    class StrategyTests {

        @Test
        @DisplayName("Should handle all strategy enum values")
        void shouldHandleAllStrategyEnumValues() {
            FragmentResolutionStrategy[] values = FragmentResolutionStrategy.values();
            assertEquals(2, values.length);
            assertEquals(FragmentResolutionStrategy.TIMEOUT, values[0]);
            assertEquals(FragmentResolutionStrategy.ACTIVE_PROBE, values[1]);
        }

        @Test
        @DisplayName("Should have correct strategy names")
        void shouldHaveCorrectStrategyNames() {
            assertEquals("TIMEOUT", FragmentResolutionStrategy.TIMEOUT.name());
            assertEquals("ACTIVE_PROBE", FragmentResolutionStrategy.ACTIVE_PROBE.name());
        }

        @Test
        @DisplayName("Should handle strategy ordinal")
        void shouldHandleStrategyOrdinal() {
            assertEquals(0, FragmentResolutionStrategy.TIMEOUT.ordinal());
            assertEquals(1, FragmentResolutionStrategy.ACTIVE_PROBE.ordinal());
        }

        @Test
        @DisplayName("valueOf should find correct strategy")
        void valueOfShouldFindCorrectStrategy() {
            assertEquals(FragmentResolutionStrategy.TIMEOUT, FragmentResolutionStrategy.valueOf("TIMEOUT"));
            assertEquals(FragmentResolutionStrategy.ACTIVE_PROBE, FragmentResolutionStrategy.valueOf("ACTIVE_PROBE"));
        }

        @Test
        @DisplayName("valueOf should throw for invalid name")
        void valueOfShouldThrowForInvalidName() {
            assertThrows(IllegalArgumentException.class, () -> FragmentResolutionStrategy.valueOf("INVALID"));
        }
    }
}
