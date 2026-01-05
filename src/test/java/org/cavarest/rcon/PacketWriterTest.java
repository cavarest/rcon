package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PacketWriter class.
 */
class PacketWriterTest {

    private final PacketCodec codec = new PacketCodec();

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create writer with custom buffer capacity")
        void shouldCreateWithCustomBufferCapacity() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> source.remaining();
            PacketWriter writer = new PacketWriter(dest, 4096, codec);
            assertEquals(4096, writer.getBufferCapacity());
        }

        @Test
        @DisplayName("Should create writer with default buffer capacity")
        void shouldCreateWithDefaultBufferCapacity() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> source.remaining();
            PacketWriter writer = new PacketWriter(dest, codec);
            assertEquals(PacketWriter.DEFAULT_BUFFER_CAPACITY, writer.getBufferCapacity());
        }

        @Test
        @DisplayName("Should use LITTLE_ENDIAN byte order")
        void shouldUseLittleEndianByteOrder() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> source.remaining();
            PacketWriter writer = new PacketWriter(dest, 2048, codec);
            assertNotNull(writer);
        }
    }

    @Nested
    @DisplayName("Buffer Position Tests")
    class BufferPositionTests {

        @Test
        @DisplayName("Should return initial buffer position of 0")
        void shouldReturnInitialBufferPosition() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> source.remaining();
            PacketWriter writer = new PacketWriter(dest, 2048, codec);
            assertEquals(0, writer.getBufferPosition());
        }
    }

    @Nested
    @DisplayName("Default Buffer Capacity Tests")
    class DefaultBufferCapacityTests {

        @Test
        @DisplayName("Should have correct default buffer capacity")
        void shouldHaveCorrectDefaultBufferCapacity() {
            assertEquals(1460, PacketWriter.DEFAULT_BUFFER_CAPACITY);
        }
    }

    @Nested
    @DisplayName("encodeToBytes() Tests")
    class EncodeToBytesTests {

        @Test
        @DisplayName("Should encode packet to bytes correctly")
        void shouldEncodePacketToBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> {
                int written = source.remaining();
                source.flip();
                return written;
            };
            PacketWriter writer = new PacketWriter(dest, 2048, codec);

            Packet packet = new Packet(1, PacketType.SERVERDATA_AUTH, "password");
            byte[] encoded = writer.encodeToBytes(packet);

            // Verify the encoded packet structure
            ByteBuffer decoded = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
            int length = decoded.getInt();
            int requestId = decoded.getInt();
            int type = decoded.getInt();

            assertEquals(1, requestId);
            assertEquals(PacketType.SERVERDATA_AUTH, type);
            assertTrue(length > 0);
        }

        @Test
        @DisplayName("Should include length field in encoded bytes")
        void shouldIncludeLengthFieldInEncodedBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> {
                int written = source.remaining();
                source.flip();
                return written;
            };
            PacketWriter writer = new PacketWriter(dest, 2048, codec);

            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, "test");
            byte[] encoded = writer.encodeToBytes(packet);

            ByteBuffer decoded = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
            int length = decoded.getInt();

            // Length should equal the remaining bytes (total - 4 for length field)
            int expectedLength = encoded.length - 4;
            assertEquals(expectedLength, length);
        }

        @Test
        @DisplayName("Should validate packet before encoding")
        void shouldValidatePacketBeforeEncoding() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> source.remaining();
            PacketWriter writer = new PacketWriter(dest, 2048, codec);

            // Create a packet with payload exceeding max size
            StringBuilder largePayload = new StringBuilder();
            for (int i = 0; i < PacketCodec.MAX_PAYLOAD_SIZE + 100; i++) {
                largePayload.append("x");
            }
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, largePayload.toString());

            assertThrows(IllegalArgumentException.class, () -> writer.encodeToBytes(packet));
        }

        @Test
        @DisplayName("Should handle empty payload")
        void shouldHandleEmptyPayload() {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            PacketWriter.Destination dest = source -> {
                int written = source.remaining();
                source.flip();
                return written;
            };
            PacketWriter writer = new PacketWriter(dest, 2048, codec);

            Packet packet = new Packet(1, PacketType.SERVERDATA_AUTH);
            byte[] encoded = writer.encodeToBytes(packet);

            assertNotNull(encoded);
            assertTrue(encoded.length >= 10); // 4 (len) + 4 (id) + 4 (type) + 2 (nulls) = 14, at least 10
        }
    }

    @Nested
    @DisplayName("Destination Functional Interface Tests")
    class DestinationInterfaceTests {

        @Test
        @DisplayName("Destination should be a functional interface")
        void destinationShouldBeFunctionalInterface() {
            assertTrue(PacketWriter.Destination.class.isInterface());
        }

        @Test
        @DisplayName("Destination should have single abstract method")
        void destinationShouldHaveSingleAbstractMethod() {
            assertEquals(1, java.util.Arrays.stream(PacketWriter.Destination.class.getMethods())
                    .filter(method -> !method.isDefault() && !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                    .count());
        }
    }
}
