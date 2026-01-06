# Architecture

## System Architecture

The Cavarest RCON library follows a layered, object-oriented architecture with clear separation of concerns. Each layer handles specific responsibilities and communicates with adjacent layers through well-defined interfaces.

## Source Code Structure

```
src/main/java/org/cavarest/rcon/
├── Packet.java           # Domain model for RCON protocol packets
├── PacketType.java       # Protocol constants (packet types)
├── PacketCodec.java      # Binary encoding/decoding of packets
├── PacketReader.java     # Low-level packet reading from socket
├── PacketWriter.java     # Low-level packet writing to socket
├── Rcon.java             # Core RCON client with builder pattern
└── RconClient.java       # High-level simplified API
```

## Component Relationships

```
                    ┌──────────────────┐
                    │   RconClient     │  ← Entry point for users
                    │  (High-level)    │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │      Rcon        │  ← Core client
                    │  (Builder Pattern)│
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼─────┐ ┌──────▼─────┐ ┌─────▼──────┐
     │ PacketReader │ │PacketWriter│ │ Request ID │
     │              │ │            │ │ Management │
     └──────┬───────┘ └─────┬──────┘ └────────────┘
            │               │
     ┌──────▼───────┐ ┌─────▼──────┐
     │ PacketCodec  │ │  ByteBuf   │
     │ (Encoding)   │ │ Management │
     └──────┬───────┘ └────────────┘
            │
     ┌──────▼───────┐
     │   Packet     │  ← Immutable domain model
     │   PacketType │  ← Constants
     └──────────────┘
```

## Key Design Patterns

### 1. Builder Pattern (Rcon.RconBuilder)

The `Rcon` class uses a fluent builder pattern for configuration:

```java
Rcon rcon = Rcon.newBuilder()
    .withChannel(channel)
    .withReadBufferCapacity(8192)
    .withWriteBufferCapacity(4096)
    .withCharset(StandardCharsets.UTF_8)
    .build();
```

### 2. Functional Interfaces (Source/Destination)

`PacketReader.Source` and `PacketWriter.Destination` use functional interfaces for I/O abstraction, allowing the same code to work with different transport mechanisms.

### 3. Immutable Domain Model

`Packet` is an immutable value object with proper `equals()`, `hashCode()`, and `toString()` implementations.

### 4. Layered Architecture

- **Domain Layer**: `Packet`, `PacketType`
- **Codec Layer**: `PacketCodec` (binary serialization)
- **I/O Layer**: `PacketReader`, `PacketWriter`
- **Service Layer**: `Rcon`, `RconClient`

## Critical Implementation Details

### Byte Order

All integers are stored in **little-endian** byte order (as per RCON protocol), requiring explicit `ByteOrder.LITTLE_ENDIAN` configuration.

### Buffer Management

- `PacketReader`: Uses a 4KB default receive buffer
- `PacketWriter`: Uses a 1460-byte default send buffer (typical MTU size)
- Both use double-buffering pattern with `compact()` after reads

### Request/Response Matching

The `requestCounter` field ensures unique request IDs. The `synchronized` keyword on `writeAndRead()` and `read()` methods prevents race conditions when multiple threads share an Rcon instance.

### Character Encoding

Default is UTF-8, but the `PacketCodec` can be configured with any `Charset` (e.g., ISO-8859-1 for legacy servers with color codes).

## Data Flow

### Sending a Command

```
User Code
    │
    ▼
RconClient.sendCommand("say hello")
    │
    ▼
Rcon.sendCommand("say hello")
    │
    ▼
synchronized writeAndRead(EXECCOMMAND, "say hello")
    │
    ├─→ Generate request ID (requestCounter++)
    ├─→ Create Packet(requestId, type, payload)
    │
    ├─→ PacketWriter.write(packet)
    │   │
    │   ├─→ PacketCodec.validatePacket(packet)
    │   ├─→ PacketCodec.encode(packet, buffer)
    │   └─→ Write to socket channel
    │
    └─→ PacketReader.read()
        │
        ├─→ Read 4 bytes for packet length
        ├─→ Read complete packet
        ├─→ PacketCodec.decode(buffer, length)
        └─→ Return decoded Packet
```

## Error Handling Strategy

1. **Validation**: `PacketCodec.validatePacket()` checks payload size limits
2. **IOException Propagation**: I/O errors propagate up to user code
3. **Authentication Failures**: `authenticate()` returns false or throws IOException
4. **Protocol Violations**: Mismatched request IDs throw IOException
5. **Connection State**: `isConnected()` checks channel state

## Thread Safety

- `Rcon` class uses `synchronized` on `writeAndRead()` and `read()` methods
- `requestCounter` is declared `volatile` for visibility
- `RconClient` is not thread-safe (single-threaded usage expected)
