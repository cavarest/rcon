package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PacketReader class.
 */
class PacketReaderTest {

    private final PacketCodec codec = new PacketCodec();

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create reader with source and codec")
        void shouldCreateWithSourceAndCodec() {
            PacketReader.Source source = createMockSource(new ArrayList<>());
            PacketReader reader = new PacketReader(source, codec);
            assertNotNull(reader);
        }
    }

    @Nested
    @DisplayName("Read Tests")
    class ReadTests {

        @Test
        @DisplayName("Should read a complete packet")
        void shouldReadCompletePacket() throws IOException {
            List<byte[]> packets = new ArrayList<>();

            // Create a valid packet: length=14, requestId=1, type=2, payload="test", + nulls
            // Total size: 4 (length field) + 14 (length value) = 18 bytes
            ByteBuffer packetBuffer = ByteBuffer.allocate(18)
                    .order(ByteOrder.LITTLE_ENDIAN);
            packetBuffer.putInt(14);  // length (includes everything after this field)
            packetBuffer.putInt(1);   // requestId
            packetBuffer.putInt(2);   // type
            packetBuffer.put("test".getBytes(StandardCharsets.UTF_8));
            packetBuffer.put((byte) 0);  // null terminator
            packetBuffer.put((byte) 0);  // null terminator

            packets.add(packetBuffer.array());

            PacketReader.Source source = createMockSource(packets);
            PacketReader reader = new PacketReader(source, codec);

            Packet packet = reader.read();

            assertEquals(1, packet.requestId);
            assertEquals(2, packet.type);
            assertEquals("test", packet.payload);
        }

        @Test
        @DisplayName("Should read packet with maximum payload size")
        void shouldReadPacketWithMaximumPayload() throws IOException {
            List<byte[]> packets = new ArrayList<>();

            // Create a packet with 4096 byte payload
            int payloadSize = 4096;
            int length = 4 + 4 + payloadSize + 2;  // requestId + type + payload + nulls
            int totalSize = 4 + length;  // length field + rest of packet

            ByteBuffer packetBuffer = ByteBuffer.allocate(totalSize)
                    .order(ByteOrder.LITTLE_ENDIAN);
            packetBuffer.putInt(length);
            packetBuffer.putInt(1);
            packetBuffer.putInt(2);
            packetBuffer.put(new byte[payloadSize]);
            packetBuffer.put((byte) 0);
            packetBuffer.put((byte) 0);

            packets.add(packetBuffer.array());

            PacketReader.Source source = createMockSource(packets);
            PacketReader reader = new PacketReader(source, codec);

            Packet packet = reader.read();

            assertEquals(1, packet.requestId);
            assertEquals(2, packet.type);
            assertEquals(payloadSize, packet.payload.length());
        }

        @Test
        @DisplayName("Should throw exception for invalid packet length")
        void shouldThrowForInvalidLength() {
            List<byte[]> packets = new ArrayList<>();

            // Invalid: length < 10 (minimum 4+4+2)
            ByteBuffer packetBuffer = ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN);
            packetBuffer.putInt(8);  // too small

            packets.add(packetBuffer.array());

            PacketReader.Source source = createMockSource(packets);
            PacketReader reader = new PacketReader(source, codec);

            assertThrows(IOException.class, reader::read);
        }

        @Test
        @DisplayName("Should throw exception for oversized packet")
        void shouldThrowForOversizedPacket() {
            List<byte[]> packets = new ArrayList<>();

            // Invalid: length > MAX_SERVER_PAYLOAD_SIZE + 6
            ByteBuffer packetBuffer = ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN);
            packetBuffer.putInt(Rcon.MAX_SERVER_PAYLOAD_SIZE + 100);

            packets.add(packetBuffer.array());

            PacketReader.Source source = createMockSource(packets);
            PacketReader reader = new PacketReader(source, codec);

            assertThrows(IOException.class, reader::read);
        }

        @Test
        @DisplayName("Should handle partial reads correctly")
        void shouldHandlePartialReads() throws IOException {
            List<byte[]> packets = new ArrayList<>();

            // Create a valid packet
            ByteBuffer packetBuffer = ByteBuffer.allocate(18)
                    .order(ByteOrder.LITTLE_ENDIAN);
            packetBuffer.putInt(14);
            packetBuffer.putInt(1);
            packetBuffer.putInt(2);
            packetBuffer.put("test".getBytes(StandardCharsets.UTF_8));
            packetBuffer.put((byte) 0);
            packetBuffer.put((byte) 0);

            packets.add(packetBuffer.array());

            PacketReader.Source source = createPartialReadSource(packets);
            PacketReader reader = new PacketReader(source, codec);

            Packet packet = reader.read();

            assertEquals(1, packet.requestId);
            assertEquals(2, packet.type);
            assertEquals("test", packet.payload);
        }
    }

    @Nested
    @DisplayName("Source Functional Interface Tests")
    class SourceInterfaceTests {

        @Test
        @DisplayName("Source should be a functional interface")
        void sourceShouldBeFunctionalInterface() {
            assertTrue(PacketReader.Source.class.isInterface());
        }

        @Test
        @DisplayName("Source should have single abstract method")
        void sourceShouldHaveSingleAbstractMethod() {
            assertEquals(1, java.util.Arrays.stream(PacketReader.Source.class.getMethods())
                    .filter(method -> !method.isDefault() && !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                    .count());
        }
    }

    /**
     * Creates a mock Source that reads from the provided packet list.
     * Each packet in the list is a complete RCON packet (including length field).
     */
    private PacketReader.Source createMockSource(List<byte[]> packets) {
        return new PacketReader.Source() {
            private byte[] allData = null;
            private int position = 0;

            private void init() {
                if (allData == null) {
                    synchronized (packets) {
                        if (packets.isEmpty()) {
                            allData = new byte[0];
                            return;
                        }

                        // Calculate total size
                        int totalSize = 0;
                        for (byte[] packet : packets) {
                            totalSize += packet.length;
                        }
                        allData = new byte[totalSize];

                        // Copy all packets
                        int offset = 0;
                        while (!packets.isEmpty()) {
                            byte[] packet = packets.remove(0);
                            System.arraycopy(packet, 0, allData, offset, packet.length);
                            offset += packet.length;
                        }
                    }
                }
            }

            @Override
            public void readFully(byte[] buffer, int offset, int length) throws IOException {
                init();

                if (position + length > allData.length) {
                    throw new EOFException("Requested " + length + " bytes but only " +
                        (allData.length - position) + " bytes available");
                }

                System.arraycopy(allData, position, buffer, offset, length);
                position += length;
            }
        };
    }

    /**
     * Creates a mock Source that simulates partial reads (reads 1 byte at a time).
     */
    private PacketReader.Source createPartialReadSource(List<byte[]> packets) {
        return new PacketReader.Source() {
            private byte[] allData = null;
            private int position = 0;

            private void init() {
                if (allData == null) {
                    synchronized (packets) {
                        if (packets.isEmpty()) {
                            return;
                        }

                        // Calculate total size
                        int totalSize = 0;
                        for (byte[] packet : packets) {
                            totalSize += packet.length;
                        }
                        allData = new byte[totalSize];

                        // Copy all packets
                        int offset = 0;
                        while (!packets.isEmpty()) {
                            byte[] packet = packets.remove(0);
                            System.arraycopy(packet, 0, allData, offset, packet.length);
                            offset += packet.length;
                        }
                    }
                }
            }

            @Override
            public void readFully(byte[] buffer, int offset, int length) throws IOException {
                init();
                if (allData == null) {
                    throw new EOFException("No data available");
                }

                // Copy byte by byte to simulate partial reads
                for (int i = 0; i < length; i++) {
                    if (position >= allData.length) {
                        throw new EOFException("End of data reached");
                    }
                    buffer[offset + i] = allData[position++];
                }
            }
        };
    }
}
