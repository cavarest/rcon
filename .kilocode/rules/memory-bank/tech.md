# Technologies

## Development Environment

- **Operating System**: macOS Sequoia
- **Java Version**: Java 11 (minimum)
- **Build Tool**: Gradle 8.5
- **IDE**: VS Code with Kilo Code extension

## Programming Language

- **Java**: Version 11+ (sourceCompatibility and targetCompatibility set to VERSION_11)
- **Character Encoding**: UTF-8 (default), configurable via `PacketCodec`

## Build System

### Gradle Configuration

- **Gradle Version**: 8.5 (via wrapper)
- **Gradle Plugins**: `java-library`
- **Distribution**: https://services.gradle.org/distributions/gradle-8.5-bin.zip

### Build Configuration Details

```gradle
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

jar {
    manifest {
        attributes(
            'Implementation-Title': 'org.cavarest.rcon',
            'Implementation-Version': version
        )
    }
}
```

## Dependencies

### Runtime Dependencies

None (pure Java standard library only)

### Test Dependencies

- **JUnit Jupiter**: 5.10.1 (`org.junit.jupiter:junit-jupiter`)
- **JUnit Platform Launcher**: (`org.junit.platform:junit-platform-launcher`)

### Dependency Management

- **Repository**: Maven Central (`mavenCentral()`)
- **No external runtime dependencies** for minimal footprint

## Key Libraries and APIs Used

### Java Standard Library

- `java.nio.*` - NIO for efficient socket I/O
  - `ByteBuffer` - Buffer management
  - `ByteOrder` - Little-endian byte order
  - `SocketChannel` - TCP socket communication
  - `InetSocketAddress` - Network address representation

- `java.util.concurrent.*` - Concurrency utilities
  - `TimeUnit` - Timeout conversion
  - `TimeoutException` - Timeout handling

- `java.util.logging.*` - Logging support (via `RconClient`)

- `java.io.*` - I/O utilities
  - `Closeable` - Resource management
  - `IOException` - I/O error handling
  - `EOFException` - End-of-stream detection

### Testing Framework

- **JUnit 5 (Jupiter)**: Modern testing framework for Java
  - Annotations: `@Test`, `@BeforeEach`, `@AfterEach`, etc.
  - Assertions: `assertEquals`, `assertThrows`, etc.
  - Parameterized tests for protocol variations

## Development Tools

### Version Control

- **Git**: For source control
- **GitHub MCP**: For repository operations

### Build and Test

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Generate JAR
./gradlew jar
```

### Code Quality

- **Rubocop**: Ruby code style (if applicable)
- No Java linter configured yet (potential improvement)

## External Integrations

### Minecraft Servers

The library is tested against Minecraft servers of various versions via Docker. Integration testing setup is described in `/Users/mulgogi/src/cavarest/pilaf`.

### Protocol Reference

- [Source RCON Protocol](https://developer.valvesoftware.com/wiki/RCON) - Official Valve documentation

## Technical Constraints

1. **Java 11 Minimum**: Cannot use newer Java features (records, pattern matching for switch, etc.)
2. **Blocking I/O**: Uses blocking `SocketChannel` for simplicity
3. **Single-Threaded Requests**: Synchronized methods prevent concurrent request/response handling
4. **No Async API**: Futures/CompletableFuture not yet implemented

## Tool Usage Patterns

- **Filesystem MCP**: For file operations and project structure
- **GitHub MCP**: For repository operations and CI/CD
- **Context7 MCP**: For library documentation
- **Memory MCP**: For knowledge graph persistence
- **Sequential Thinking MCP**: For problem-solving
