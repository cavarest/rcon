package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PacketCodec class.
 */
class PacketCodecTest {

    private final PacketCodec codec = new PacketCodec();

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create codec with UTF-8 charset by default")
        void shouldCreateWithDefaultCharset() {
            PacketCodec defaultCodec = new PacketCodec();
            assertNotNull(defaultCodec);
        }

        @Test
        @DisplayName("Should create codec with specified charset")
        void shouldCreateWithSpecifiedCharset() {
            PacketCodec iso88591Codec = new PacketCodec(StandardCharsets.ISO_8859_1);
            assertNotNull(iso88591Codec);
        }
    }

    @Nested
    @DisplayName("encode() Tests")
    class EncodeTests {

        @Test
        @DisplayName("Should encode packet with request ID")
        void shouldEncodeRequestId() {
            Packet packet = new Packet(12345, PacketType.SERVERDATA_AUTH, "password");
            ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
            codec.encode(packet, buffer);

            buffer.flip();
            assertEquals(12345, buffer.getInt());
        }

        @Test
        @DisplayName("Should encode packet with type")
        void shouldEncodeType() {
            Packet packet = new Packet(1, PacketType.SERVERDATA_AUTH, "password");
            ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
            codec.encode(packet, buffer);

            buffer.position(4); // Skip request ID
            assertEquals(PacketType.SERVERDATA_AUTH, buffer.getInt());
        }

        @Test
        @DisplayName("Should encode payload with null terminators")
        void shouldEncodePayloadWithNullTerminators() {
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, "test");
            ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
            codec.encode(packet, buffer);

            buffer.position(8); // Skip request ID and type
            byte[] payloadBytes = new byte[6]; // "test" (4 bytes) + 2 null terminators
            buffer.get(payloadBytes);
            assertEquals('t', payloadBytes[0]);
            assertEquals('e', payloadBytes[1]);
            assertEquals('s', payloadBytes[2]);
            assertEquals('t', payloadBytes[3]);
            assertEquals(0, payloadBytes[4]); // First null terminator
            assertEquals(0, payloadBytes[5]); // Second null terminator
        }

        @Test
        @DisplayName("Should encode empty payload")
        void shouldEncodeEmptyPayload() {
            Packet packet = new Packet(1, PacketType.SERVERDATA_AUTH);
            ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
            codec.encode(packet, buffer);

            buffer.position(8); // Skip request ID and type
            assertEquals(0, buffer.get());
            assertEquals(0, buffer.get());
        }

        @Test
        @DisplayName("Should handle large payload")
        void shouldHandleLargePayload() {
            StringBuilder largePayload = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                largePayload.append("x");
            }
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, largePayload.toString());
            ByteBuffer buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
            assertDoesNotThrow(() -> codec.encode(packet, buffer));
        }
    }

    @Nested
    @DisplayName("decode() Tests")
    class DecodeTests {

        @Test
        @DisplayName("Should decode request ID")
        void shouldDecodeRequestId() {
            // Create a manually encoded packet
            ByteBuffer buffer = createEncodedPacket(12345, PacketType.SERVERDATA_RESPONSE_VALUE, "response");
            buffer.flip();
            Packet packet = codec.decode(buffer, buffer.remaining());
            assertEquals(12345, packet.requestId);
        }

        @Test
        @DisplayName("Should decode packet type")
        void shouldDecodePacketType() {
            ByteBuffer buffer = createEncodedPacket(1, PacketType.SERVERDATA_AUTH_RESPONSE, "");
            buffer.flip();
            Packet packet = codec.decode(buffer, buffer.remaining());
            assertEquals(PacketType.SERVERDATA_AUTH_RESPONSE, packet.type);
        }

        @Test
        @DisplayName("Should decode payload correctly")
        void shouldDecodePayload() {
            ByteBuffer buffer = createEncodedPacket(1, PacketType.SERVERDATA_RESPONSE_VALUE, "Hello World");
            buffer.flip();
            Packet packet = codec.decode(buffer, buffer.remaining());
            assertEquals("Hello World", packet.payload);
        }

        @Test
        @DisplayName("Should decode empty payload")
        void shouldDecodeEmptyPayload() {
            ByteBuffer buffer = createEncodedPacket(1, PacketType.SERVERDATA_RESPONSE_VALUE, "");
            buffer.flip();
            Packet packet = codec.decode(buffer, buffer.remaining());
            assertEquals("", packet.payload);
        }

        @Test
        @DisplayName("Should decode special characters")
        void shouldDecodeSpecialCharacters() {
            String payload = "Line1\nLine2\tTab";
            ByteBuffer buffer = createEncodedPacket(1, PacketType.SERVERDATA_RESPONSE_VALUE, payload);
            buffer.flip();
            Packet packet = codec.decode(buffer, buffer.remaining());
            assertEquals(payload, packet.payload);
        }

        private ByteBuffer createEncodedPacket(int requestId, int type, String payload) {
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            int totalLength = 4 + 4 + payloadBytes.length + 2;
            ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(requestId);
            buffer.putInt(type);
            buffer.put(payloadBytes);
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            return buffer;
        }
    }

    @Nested
    @DisplayName("calculateEncodedSize() Tests")
    class CalculateEncodedSizeTests {

        @Test
        @DisplayName("Should calculate size for empty payload")
        void shouldCalculateSizeForEmptyPayload() {
            Packet packet = new Packet(1, PacketType.SERVERDATA_AUTH);
            int size = codec.calculateEncodedSize(packet);
            assertEquals(4 + 4 + 0 + 2, size); // requestId + type + payload + 2 nulls
        }

        @Test
        @DisplayName("Should calculate size for payload with content")
        void shouldCalculateSizeForPayloadWithContent() {
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, "test");
            int size = codec.calculateEncodedSize(packet);
            assertEquals(4 + 4 + 4 + 2, size); // requestId + type + "test" (4 bytes) + 2 nulls
        }

        @ParameterizedTest
        @CsvSource({
            "1, 2, '', 10",
            "1, 2, 'a', 11",
            "1, 2, 'ab', 12",
            "1, 2, 'abc', 13"
        })
        void shouldCalculateSizeCorrectly(int requestId, int type, String payload, int expectedSize) {
            Packet packet = new Packet(requestId, type, payload);
            int size = codec.calculateEncodedSize(packet);
            assertEquals(expectedSize, size);
        }
    }

    @Nested
    @DisplayName("validatePacket() Tests")
    class ValidatePacketTests {

        @Test
        @DisplayName("Should pass validation for valid packet")
        void shouldPassValidationForValidPacket() {
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, "test");
            assertDoesNotThrow(() -> codec.validatePacket(packet));
        }

        @Test
        @DisplayName("Should throw exception for packet exceeding max size")
        void shouldThrowExceptionForPacketExceedingMaxSize() {
            StringBuilder largePayload = new StringBuilder();
            for (int i = 0; i < PacketCodec.MAX_PAYLOAD_SIZE + 1; i++) {
                largePayload.append("x");
            }
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, largePayload.toString());
            assertThrows(IllegalArgumentException.class, () -> codec.validatePacket(packet));
        }

        @Test
        @DisplayName("Should pass validation at exact max size")
        void shouldPassValidationAtExactMaxSize() {
            StringBuilder maxPayload = new StringBuilder();
            for (int i = 0; i < PacketCodec.MAX_PAYLOAD_SIZE; i++) {
                maxPayload.append("x");
            }
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, maxPayload.toString());
            assertDoesNotThrow(() -> codec.validatePacket(packet));
        }

        @Test
        @DisplayName("Should have correct max payload size constant")
        void shouldHaveCorrectMaxPayloadSize() {
            assertEquals(1446, PacketCodec.MAX_PAYLOAD_SIZE);
        }
    }

    @Nested
    @DisplayName("Charset Handling Tests")
    class CharsetHandlingTests {

        @Test
        @DisplayName("Should handle UTF-8 charset")
        void shouldHandleUtf8Charset() {
            PacketCodec utf8Codec = new PacketCodec(StandardCharsets.UTF_8);
            String unicodePayload = "Hello 世界";
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, unicodePayload);

            ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
            utf8Codec.encode(packet, buffer);
            buffer.flip();

            Packet decoded = utf8Codec.decode(buffer, buffer.remaining());
            assertEquals(unicodePayload, decoded.payload);
        }

        @Test
        @DisplayName("Should handle ISO-8859-1 charset for color codes")
        void shouldHandleIso8859ForColorCodes() {
            PacketCodec iso88591Codec = new PacketCodec(StandardCharsets.ISO_8859_1);
            // Minecraft color code prefix is 0xA7 (167)
            String colorPayload = "§aGreen Text";
            Packet packet = new Packet(1, PacketType.SERVERDATA_EXECCOMMAND, colorPayload);

            ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
            iso88591Codec.encode(packet, buffer);
            buffer.flip();

            Packet decoded = iso88591Codec.decode(buffer, buffer.remaining());
            assertEquals(colorPayload, decoded.payload);
        }
    }

    @Nested
    @DisplayName("Round-trip Tests")
    class RoundTripTests {

        @ParameterizedTest
        @ValueSource(strings = {"", "test", "Hello World", "Line1\nLine2", "Special: !@#$%^&*()"})
        @DisplayName("Should preserve data through encode/decode cycle")
        void shouldPreserveDataThroughEncodeDecode(String payload) {
            Packet original = new Packet(42, PacketType.SERVERDATA_RESPONSE_VALUE, payload);

            ByteBuffer buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
            codec.encode(original, buffer);
            buffer.flip();

            Packet decoded = codec.decode(buffer, buffer.remaining());

            assertEquals(original.requestId, decoded.requestId);
            assertEquals(original.type, decoded.type);
            assertEquals(original.payload, decoded.payload);
        }
    }
}
