# Product Description

## What is Cavarest RCON Library?

Cavest RCON Library is a Java-based implementation of the Source RCON protocol, specifically designed for communicating with Minecraft servers. It provides both low-level packet handling and high-level APIs for seamless integration into Java applications.

## Problems It Solves

1. **Protocol Complexity**: The RCON protocol uses little-endian byte order (unlike Java's default big-endian), requires specific packet structure with null-terminated payloads, and handles packet fragmentation. This library abstracts away these complexities.

2. **Connection Management**: Provides proper socket channel handling with configurable timeouts, connection state management, and graceful disconnection.

3. **Authentication Handling**: Implements the authentication flow correctly, including handling edge cases like CS:GO/Some Minecraft servers that send an empty response before the auth response.

4. **Packet Matching**: Ensures request/response matching using request IDs, preventing race conditions in concurrent scenarios.

5. **Character Encoding**: Properly handles legacy Minecraft servers that use ISO-8859-1 encoding for color codes.

## How It Should Work

### Low-Level Expectations

- **Packet Encoding/Decoding**: Convert between Java objects and binary data following the RCON protocol specification
- **Buffer Management**: Efficiently handle incoming/outgoing data using NIO ByteBuffer
- **Blocking I/O**: Use blocking reads to simplify the API while maintaining reliability
- **Error Handling**: Detect and report protocol violations, connection issues, and authentication failures

### High-Level Expectations

- **Connection**: Connect to any RCON-enabled server at hostname:port
- **Authentication**: Authenticate with a password and handle failures gracefully
- **Command Execution**: Send commands and receive text responses
- **Resource Management**: Support try-with-resources for automatic cleanup
- **Configuration**: Allow customization of buffers, timeouts, and character encoding

### Performance Expectations

- Minimal memory allocations through buffer reuse
- Efficient packet processing without unnecessary copies
- Support for concurrent connections (thread-safe design)

## User Experience Goals

1. **Simplicity**: A developer should be able to connect and send a command in fewer than 10 lines of code.

2. **Reliability**: The library should handle network glitches, server quirks, and edge cases gracefully without crashing.

3. **Debuggability**: Verbose logging mode to help diagnose connection issues.

4. **Flexibility**: Allow customization of encoding, buffer sizes, and timeouts for different use cases.

## Target Use Cases

- Server administration tools
- Automated server maintenance scripts
- Plugin administration interfaces
- Monitoring and alerting systems
- Multi-server management dashboards
