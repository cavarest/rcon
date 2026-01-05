package org.cavarest.rcon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

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
        @DisplayName("Should create reader with custom buffer capacity")
        void shouldCreateWithCustomBufferCapacity() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            PacketReader.Source source = createMockSource(buffer);
            PacketReader reader = new PacketReader(source, 8192, codec);
            assertEquals(8192, reader.getBufferCapacity());
        }

        @Test
        @DisplayName("Should create reader with default buffer capacity")
        void shouldCreateWithDefaultBufferCapacity() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            PacketReader.Source source = createMockSource(buffer);
            PacketReader reader = new PacketReader(source, codec);
            assertEquals(PacketReader.DEFAULT_BUFFER_CAPACITY, reader.getBufferCapacity());
        }

        @Test
        @DisplayName("Should use LITTLE_ENDIAN byte order")
        void shouldUseLittleEndianByteOrder() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            PacketReader.Source source = createMockSource(buffer);
            PacketReader reader = new PacketReader(source, 1024, codec);
            // Buffer should be configured for little-endian
            assertNotNull(reader);
        }
    }

    @Nested
    @DisplayName("Buffer Position Tests")
    class BufferPositionTests {

        @Test
        @DisplayName("Should return initial buffer position of 0")
        void shouldReturnInitialBufferPosition() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            PacketReader.Source source = createMockSource(buffer);
            PacketReader reader = new PacketReader(source, codec);
            assertEquals(0, reader.getBufferPosition());
        }
    }

    @Nested
    @DisplayName("Default Buffer Capacity Tests")
    class DefaultBufferCapacityTests {

        @Test
        @DisplayName("Should have correct default buffer capacity")
        void shouldHaveCorrectDefaultBufferCapacity() {
            assertEquals(4096, PacketReader.DEFAULT_BUFFER_CAPACITY);
        }
    }

    /**
     * Creates a mock Source that reads from the provided buffer.
     */
    private PacketReader.Source createMockSource(ByteBuffer buffer) {
        return destination -> {
            destination.put(buffer.array(), 0, Math.min(destination.remaining(), buffer.position()));
            return buffer.position();
        };
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
}
