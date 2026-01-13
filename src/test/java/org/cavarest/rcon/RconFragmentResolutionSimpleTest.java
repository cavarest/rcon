package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests for RCON fragment resolution strategies.
 * These tests use basic mock servers to exercise the code paths.
 */
class RconFragmentResolutionSimpleTest {

    /**
     * Creates a minimal RCON response packet.
     */
    private static ByteBuffer createRconPacket(int requestId, int type, String payload) {
        byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = 4 + 4 + payloadBytes.length + 2;

        ByteBuffer buffer = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(length);
        buffer.putInt(requestId);
        buffer.putInt(type);
        buffer.put(payloadBytes);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.flip();
        return buffer;
    }

    @Nested
    @DisplayName("Basic RCON Communication Tests")
    class BasicCommunicationTests {

        @Test
        @DisplayName("Should authenticate and receive response")
        void shouldAuthenticateAndReceiveResponse() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                // Server thread
                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        // Read auth request
                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);

                        // Send auth response
                        ByteBuffer authResponse = createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, "");
                        client.write(authResponse);

                        // Read command
                        readBuffer.clear();
                        client.read(readBuffer);

                        // Send command response
                        ByteBuffer cmdResponse = createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "TestResponse");
                        client.write(cmdResponse);

                        Thread.sleep(100);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    boolean authResult = rcon.authenticate("test");
                    assertTrue(authResult, "Auth should succeed");

                    String response = rcon.sendCommandWithPacketSizeStrategy("test");
                    assertEquals("TestResponse", response);
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("Should handle authentication failure")
        void shouldHandleAuthenticationFailure() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);

                        // Send auth failure (requestId = -1)
                        ByteBuffer authResponse = createRconPacket(-1, PacketType.SERVERDATA_AUTH_RESPONSE, "");
                        client.write(authResponse);

                        Thread.sleep(100);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    boolean authResult = rcon.authenticate("test");
                    assertFalse(authResult, "Auth should fail with -1 requestId");
                }

                serverThread.join(500);
            }
        }
    }

    @Nested
    @DisplayName("Fragment Resolution Strategy Tests")
    class FragmentStrategyTests {

        @Test
        @DisplayName("PACKET_SIZE strategy should handle small response")
        void packetSizeStrategyShouldHandleSmallResponse() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        // Send small response (< 4096)
                        client.write(createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "Small"));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    String response = rcon.sendCommandWithPacketSizeStrategy("test");
                    assertEquals("Small", response);
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("TIMEOUT strategy should receive response")
        void timeoutStrategyShouldReceiveResponse() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "TimeoutOK"));

                        // Close connection immediately after sending response
                        // The TIMEOUT strategy should handle this gracefully
                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    // The TIMEOUT strategy uses socket timeout, so no need to set fragment timeout
                    String response = rcon.sendCommandWithTimeoutStrategy("test");
                    assertEquals("TimeoutOK", response);
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("ACTIVE_PROBE strategy should receive response")
        void activeProbeStrategyShouldReceiveResponse() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        // Send small response (won't trigger probe)
                        client.write(createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "ProbeOK"));

                        // Read possible probe (ignore if present)
                        readBuffer.clear();
                        readBuffer.limit(100);
                        int bytesRead = client.read(readBuffer);
                        if (bytesRead > 0) {
                            client.write(createRconPacket(1, PacketType.SERVERDATA_RESPONSE_VALUE, ""));
                        }

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    String response = rcon.sendCommandWithActiveProbeStrategy("test");
                    assertEquals("ProbeOK", response);
                }

                serverThread.join(500);
            }
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw IOException for wrong packet type")
        void shouldThrowForWrongPacketType() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        // Send wrong type (AUTH instead of RESPONSE_VALUE)
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH, "Wrong"));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    assertThrows(IOException.class, () -> rcon.sendCommandWithPacketSizeStrategy("test"));
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("Should throw IOException for invalid response")
        void shouldThrowForInvalidResponse() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        // Send invalid response (requestId = -1)
                        client.write(createRconPacket(-1, PacketType.SERVERDATA_RESPONSE_VALUE, "Invalid"));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    assertThrows(IOException.class, () -> rcon.sendCommandWithPacketSizeStrategy("test"));
                }

                serverThread.join(500);
            }
        }
    }

    @Nested
    @DisplayName("Public API Tests")
    class PublicApiTests {

        @Test
        @DisplayName("tryAuthenticate should throw on auth failure")
        void tryAuthenticateShouldThrowOnFailure() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);

                        // Send auth failure (requestId = -1)
                        client.write(createRconPacket(-1, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    IOException ex = assertThrows(IOException.class, () -> rcon.tryAuthenticate("test"));
                    assertTrue(ex.getMessage().contains("Authentication failed"));
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("tryAuthenticate should succeed on valid auth")
        void tryAuthenticateShouldSucceedOnValidAuth() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);

                        // Send auth success
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.tryAuthenticate("test");
                    // Should not throw
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("sendCommand with default strategy should work")
        void sendCommandWithDefaultStrategyShouldWork() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "DefaultOK"));

                        // Handle possible active probe
                        readBuffer.clear();
                        readBuffer.limit(100);
                        int bytesRead = client.read(readBuffer);
                        if (bytesRead > 0) {
                            client.write(createRconPacket(1, PacketType.SERVERDATA_RESPONSE_VALUE, ""));
                        }

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    String response = rcon.sendCommand("test");
                    assertEquals("DefaultOK", response);
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("sendCommand with PACKET_SIZE strategy should work")
        void sendCommandWithPacketSizeStrategyShouldWork() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "PacketSizeOK"));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    String response = rcon.sendCommand("test", FragmentResolutionStrategy.PACKET_SIZE);
                    assertEquals("PacketSizeOK", response);
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("sendCommand with TIMEOUT strategy should work")
        void sendCommandWithTimeoutStrategyShouldWork() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "TimeoutStrategyOK"));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    String response = rcon.sendCommand("test", FragmentResolutionStrategy.TIMEOUT);
                    assertEquals("TimeoutStrategyOK", response);
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("sendCommand with ACTIVE_PROBE strategy should work")
        void sendCommandWithActiveProbeStrategyShouldWork() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        readBuffer.clear();
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_RESPONSE_VALUE, "ProbeStrategyOK"));

                        // Handle possible active probe
                        readBuffer.clear();
                        readBuffer.limit(100);
                        int bytesRead = client.read(readBuffer);
                        if (bytesRead > 0) {
                            client.write(createRconPacket(1, PacketType.SERVERDATA_RESPONSE_VALUE, ""));
                        }

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    String response = rcon.sendCommand("test", FragmentResolutionStrategy.ACTIVE_PROBE);
                    assertEquals("ProbeStrategyOK", response);
                }

                serverThread.join(500);
            }
        }

        @Test
        @DisplayName("sendCommand with null strategy should throw")
        void sendCommandWithNullStrategyShouldThrow() throws Exception {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
                serverChannel.bind(new InetSocketAddress("localhost", 0));
                serverChannel.configureBlocking(true);
                int port = serverChannel.socket().getLocalPort();

                Thread serverThread = new Thread(() -> {
                    try {
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(true);

                        ByteBuffer readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                        client.read(readBuffer);
                        client.write(createRconPacket(0, PacketType.SERVERDATA_AUTH_RESPONSE, ""));

                        Thread.sleep(50);
                        client.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                Thread.sleep(100);

                try (Rcon rcon = Rcon.connect("localhost", port)) {
                    rcon.authenticate("test");
                    assertThrows(IllegalArgumentException.class, () -> rcon.sendCommand("test", (FragmentResolutionStrategy) null));
                }

                serverThread.join(500);
            }
        }
    }
}
