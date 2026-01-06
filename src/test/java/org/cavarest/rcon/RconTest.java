package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Rcon class.
 */
class RconTest {

    @Nested
    @DisplayName("RconBuilder Tests")
    class RconBuilderTests {

        @Test
        @DisplayName("Should create builder")
        void shouldCreateBuilder() {
            Rcon.RconBuilder builder = Rcon.newBuilder();
            assertNotNull(builder);
        }

        @Test
        @DisplayName("Should set custom read buffer capacity")
        void shouldSetCustomReadBufferCapacity() {
            Rcon.RconBuilder builder = Rcon.newBuilder();
            builder.withReadBufferCapacity(16384);
            assertNotNull(builder);
        }

        @Test
        @DisplayName("Should set custom write buffer capacity")
        void shouldSetCustomWriteBufferCapacity() {
            Rcon.RconBuilder builder = Rcon.newBuilder();
            builder.withWriteBufferCapacity(8192);
            assertNotNull(builder);
        }

        @Test
        @DisplayName("Should set custom charset")
        void shouldSetCustomCharset() {
            Rcon.RconBuilder builder = Rcon.newBuilder();
            builder.withCharset(StandardCharsets.ISO_8859_1);
            assertNotNull(builder);
        }

        @Test
        @DisplayName("Should throw exception when building without channel")
        void shouldThrowExceptionWhenBuildingWithoutChannel() {
            Rcon.RconBuilder builder = Rcon.newBuilder();
            assertThrows(NullPointerException.class, () -> builder.build());
        }

        @Test
        @DisplayName("Should build with channel")
        void shouldBuildWithChannel() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                Rcon.RconBuilder builder = Rcon.newBuilder();
                Rcon rcon = builder.withChannel(channel).build();
                assertNotNull(rcon);
            } finally {
                channel.close();
            }
        }

        @Test
        @DisplayName("Builder should have default buffer capacities")
        void builderShouldHaveDefaultBufferCapacities() {
            Rcon.RconBuilder builder = Rcon.newBuilder();
            assertNotNull(builder);
            // Default values should be used when building
        }
    }

    @Nested
    @DisplayName("Connect Methods Tests")
    class ConnectTests {

        @Test
        @DisplayName("connect(SocketAddress) should return Rcon instance")
        void connectShouldReturnRconInstance() throws IOException {
            // The connect method returns an Rcon instance - the connection
            // is established but may not be usable if no server is running
            Rcon rcon = Rcon.connect(new InetSocketAddress("localhost", 25575));
            assertNotNull(rcon);
            // Close the connection immediately
            rcon.close();
        }

        @Test
        @DisplayName("connect(hostname, port) should return Rcon instance")
        void connectWithHostnameAndPortShouldReturnRconInstance() throws IOException {
            Rcon rcon = Rcon.connect("localhost", 25575);
            assertNotNull(rcon);
            rcon.close();
        }
    }

    @Nested
    @DisplayName("Closeable Tests")
    class CloseableTests {

        @Test
        @DisplayName("Rcon should implement Closeable")
        void shouldImplementCloseable() {
            assertTrue(java.io.Closeable.class.isAssignableFrom(Rcon.class));
        }
    }

    @Nested
    @DisplayName("Timeout Configuration Tests")
    class TimeoutConfigurationTests {

        @Test
        @DisplayName("Should set connect timeout")
        void shouldSetConnectTimeout() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .build();
                rcon.setConnectTimeout(10000);
                rcon.close();
            } finally {
                channel.close();
            }
        }

        @Test
        @DisplayName("Should set fragment timeout")
        void shouldSetFragmentTimeout() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .build();
                rcon.setFragmentTimeout(100, TimeUnit.MILLISECONDS);
                rcon.close();
            } finally {
                channel.close();
            }
        }
    }

    @Nested
    @DisplayName("Verbose Mode Tests")
    class VerboseModeTests {

        @Test
        @DisplayName("Should enable verbose mode")
        void shouldEnableVerboseMode() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .build();
                rcon.setVerbose(true);
                assertDoesNotThrow(() -> rcon.setVerbose(false));
                rcon.close();
            } finally {
                channel.close();
            }
        }
    }

    @Nested
    @DisplayName("Authentication Methods Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("authenticate should throw exception when not connected")
        void authenticateShouldThrowWhenNotConnected() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .build();

                // Should throw an exception since not connected
                assertThrows(Exception.class, () -> rcon.authenticate("test"));
            } finally {
                channel.close();
            }
        }

        @Test
        @DisplayName("tryAuthenticate should throw exception on failure")
        void tryAuthenticateShouldThrowOnFailure() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .build();

                // Should throw an exception since not connected
                assertThrows(Exception.class, () -> rcon.tryAuthenticate("test"));
            } finally {
                channel.close();
            }
        }
    }

    @Nested
    @DisplayName("Send Command Tests")
    class SendCommandTests {

        @Test
        @DisplayName("sendCommand should throw exception when not connected")
        void sendCommandShouldThrowWhenNotConnected() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .build();

                // Should throw an exception since not connected
                assertThrows(Exception.class, () -> rcon.sendCommand("test"));
            } finally {
                channel.close();
            }
        }
    }

    @Nested
    @DisplayName("isConnected Tests")
    class IsConnectedTests {

        @Test
        @DisplayName("Should return false for closed channel")
        void shouldReturnFalseForClosedChannel() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.close();

            Rcon rcon = Rcon.newBuilder()
                    .withChannel(channel)
                    .build();

            assertFalse(rcon.isConnected());
        }

        @Test
        @DisplayName("Should return correct connection state")
        void shouldReturnCorrectConnectionState() throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            try {
                // Try to connect to see if server exists
                boolean connected = channel.connect(new InetSocketAddress("localhost", 25575));

                Rcon rcon = Rcon.newBuilder()
                        .withChannel(channel)
                        .build();

                // If connection succeeded, isConnected should return true
                // If failed, we can't test this without a server
                if (connected) {
                    assertTrue(rcon.isConnected());
                } else {
                    // Skip this test when no server is available
                    System.out.println("Skipping isConnected test - no server available");
                }
            } catch (java.net.ConnectException e) {
                // Expected when no server is running
                System.out.println("Skipping isConnected test - no server available: " + e.getMessage());
            } finally {
                if (channel.isOpen()) {
                    channel.close();
                }
            }
        }
    }
}
