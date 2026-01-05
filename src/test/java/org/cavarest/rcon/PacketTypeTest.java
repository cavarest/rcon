package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PacketType class.
 */
class PacketTypeTest {

    @Nested
    @DisplayName("Packet Type Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("SERVERDATA_RESPONSE_VALUE should be 0")
        void shouldHaveCorrectResponseValue() {
            assertEquals(0, PacketType.SERVERDATA_RESPONSE_VALUE);
        }

        @Test
        @DisplayName("SERVERDATA_AUTH should be 3")
        void shouldHaveCorrectAuthValue() {
            assertEquals(3, PacketType.SERVERDATA_AUTH);
        }

        @Test
        @DisplayName("SERVERDATA_AUTH_RESPONSE should be 2")
        void shouldHaveCorrectAuthResponseValue() {
            assertEquals(2, PacketType.SERVERDATA_AUTH_RESPONSE);
        }

        @Test
        @DisplayName("SERVERDATA_EXECCOMMAND should be 2")
        void shouldHaveCorrectExecCommandValue() {
            assertEquals(2, PacketType.SERVERDATA_EXECCOMMAND);
        }

        @Test
        @DisplayName("AUTH_RESPONSE and EXECCOMMAND should have same value")
        void authResponseAndExecCommandShouldHaveSameValue() {
            assertEquals(PacketType.SERVERDATA_AUTH_RESPONSE, PacketType.SERVERDATA_EXECCOMMAND);
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return correct name for SERVERDATA_RESPONSE_VALUE")
        void shouldReturnCorrectNameForResponseValue() {
            assertEquals("SERVERDATA_RESPONSE_VALUE", PacketType.toString(PacketType.SERVERDATA_RESPONSE_VALUE));
        }

        @Test
        @DisplayName("Should return correct name for SERVERDATA_AUTH")
        void shouldReturnCorrectNameForAuth() {
            assertEquals("SERVERDATA_AUTH", PacketType.toString(PacketType.SERVERDATA_AUTH));
        }

        @Test
        @DisplayName("Should return correct name for SERVERDATA_AUTH_RESPONSE")
        void shouldReturnCorrectNameForAuthResponse() {
            assertEquals("SERVERDATA_AUTH_RESPONSE", PacketType.toString(PacketType.SERVERDATA_AUTH_RESPONSE));
        }

        @Test
        @DisplayName("Should return correct name for EXECCOMMAND (same value as AUTH_RESPONSE)")
        void shouldReturnCorrectNameForExecCommand() {
            // SERVERDATA_EXECCOMMAND and SERVERDATA_AUTH_RESPONSE both have value 2
            // The toString method returns the first matching constant name
            // Since AUTH_RESPONSE is checked first, value 2 returns "SERVERDATA_AUTH_RESPONSE"
            String result = PacketType.toString(PacketType.SERVERDATA_EXECCOMMAND);
            // This will actually return SERVERDATA_AUTH_RESPONSE due to implementation order
            assertEquals("SERVERDATA_AUTH_RESPONSE", result);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 1, 4, 100, Integer.MAX_VALUE})
        @DisplayName("Should return UNKNOWN for invalid packet types")
        void shouldReturnUnknownForInvalidTypes(int type) {
            if (type != 0 && type != 2 && type != 3) {
                String result = PacketType.toString(type);
                assertTrue(result.startsWith("UNKNOWN("));
                assertTrue(result.contains(String.valueOf(type)));
            }
        }
    }

    @Nested
    @DisplayName("Class Structure Tests")
    class ClassStructureTests {

        @Test
        @DisplayName("PacketType should be final")
        void shouldBeFinalClass() {
            assertTrue(java.lang.reflect.Modifier.isFinal(PacketType.class.getModifiers()));
        }

        @Test
        @DisplayName("Should not have public constructor")
        void shouldNotHavePublicConstructor() {
            assertEquals(0, PacketType.class.getConstructors().length);
        }
    }
}
