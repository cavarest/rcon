package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Packet class.
 */
class PacketTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create packet with all parameters")
        void shouldCreatePacketWithAllParameters() {
            Packet packet = new Packet(1, 2, "test payload");
            assertEquals(1, packet.requestId);
            assertEquals(2, packet.type);
            assertEquals("test payload", packet.payload);
        }

        @Test
        @DisplayName("Should create packet with empty payload")
        void shouldCreatePacketWithEmptyPayload() {
            Packet packet = new Packet(1, 2);
            assertEquals(1, packet.requestId);
            assertEquals(2, packet.type);
            assertEquals("", packet.payload);
        }

        @Test
        @DisplayName("Should handle null payload by converting to empty string")
        void shouldHandleNullPayload() {
            Packet packet = new Packet(1, 2, null);
            assertEquals("", packet.payload);
        }

        @Test
        @DisplayName("Should create packet with special characters in payload")
        void shouldHandleSpecialCharacters() {
            String specialPayload = "Hello\nWorld\t!";
            Packet packet = new Packet(1, 2, specialPayload);
            assertEquals(specialPayload, packet.payload);
        }

        @Test
        @DisplayName("Should create packet with unicode characters in payload")
        void shouldHandleUnicodeCharacters() {
            String unicodePayload = "Hello 世界 🌍";
            Packet packet = new Packet(1, 2, unicodePayload);
            assertEquals(unicodePayload, packet.payload);
        }
    }

    @Nested
    @DisplayName("isValid() Tests")
    class IsValidTests {

        @Test
        @DisplayName("Should return true for valid request ID")
        void shouldReturnTrueForValidRequestId() {
            Packet packet = new Packet(1, PacketType.SERVERDATA_AUTH);
            assertTrue(packet.isValid());
        }

        @Test
        @DisplayName("Should return true for zero request ID")
        void shouldReturnTrueForZeroRequestId() {
            Packet packet = new Packet(0, PacketType.SERVERDATA_AUTH);
            assertTrue(packet.isValid());
        }

        @Test
        @DisplayName("Should return false for negative request ID (auth failure)")
        void shouldReturnFalseForNegativeRequestId() {
            Packet packet = new Packet(-1, PacketType.SERVERDATA_AUTH);
            assertFalse(packet.isValid());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode() Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Should be equal to packet with same values")
        void shouldBeEqualToSameValues() {
            Packet packet1 = new Packet(1, 2, "test");
            Packet packet2 = new Packet(1, 2, "test");
            assertEquals(packet1, packet2);
            assertEquals(packet1.hashCode(), packet2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal to packet with different request ID")
        void shouldNotBeEqualWithDifferentRequestId() {
            Packet packet1 = new Packet(1, 2, "test");
            Packet packet2 = new Packet(2, 2, "test");
            assertNotEquals(packet1, packet2);
        }

        @Test
        @DisplayName("Should not be equal to packet with different type")
        void shouldNotBeEqualWithDifferentType() {
            Packet packet1 = new Packet(1, 2, "test");
            Packet packet2 = new Packet(1, 3, "test");
            assertNotEquals(packet1, packet2);
        }

        @Test
        @DisplayName("Should not be equal to packet with different payload")
        void shouldNotBeEqualWithDifferentPayload() {
            Packet packet1 = new Packet(1, 2, "test1");
            Packet packet2 = new Packet(1, 2, "test2");
            assertNotEquals(packet1, packet2);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            Packet packet = new Packet(1, 2, "test");
            assertNotEquals(null, packet);
        }

        @Test
        @DisplayName("Should not be equal to different class")
        void shouldNotBeEqualToDifferentClass() {
            Packet packet = new Packet(1, 2, "test");
            assertNotEquals("not a packet", packet);
        }

        @Test
        @DisplayName("Should be equal to itself")
        void shouldBeEqualToItself() {
            Packet packet = new Packet(1, 2, "test");
            assertEquals(packet, packet);
        }

        @Test
        @DisplayName("Packets with null payload should be equal")
        void packetsWithNullPayloadShouldBeEqual() {
            Packet packet1 = new Packet(1, 2, null);
            Packet packet2 = new Packet(1, 2, null);
            assertEquals(packet1, packet2);
            assertEquals(packet1.hashCode(), packet2.hashCode());
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return formatted string")
        void shouldReturnFormattedString() {
            Packet packet = new Packet(1, 2, "test");
            String result = packet.toString();
            assertTrue(result.contains("Packet{"));
            assertTrue(result.contains("requestId=1"));
            assertTrue(result.contains("type=2"));
            assertTrue(result.contains("payload='test'"));
        }

        @Test
        @DisplayName("Should handle empty payload in toString")
        void shouldHandleEmptyPayloadInToString() {
            Packet packet = new Packet(1, 2);
            String result = packet.toString();
            assertTrue(result.contains("payload=''"));
        }
    }
}
