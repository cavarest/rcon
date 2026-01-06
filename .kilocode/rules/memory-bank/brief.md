# Cavarest RCON Library

The Cavarest RCON library is a Java library for interacting with Minecraft servers using the RCON protocol. It provides a simple and easy-to-use API for sending commands to the server and receiving responses.

## Purpose

The purpose of this library is to allow developers to easily integrate RCON functionality into their Java applications. This can be useful for a variety of purposes, such as server administration, automation, and monitoring.

Remote console (RCON) is a TCP/IP-based protocol that allows server administrators to remotely execute commands. Introduced in Java Edition Beta 1.9 Prerelease 4, it's an implementation of the Source RCON protocol for Minecraft.

## Project Identity

- **Group ID**: `org.cavarest`
- **Artifact ID**: `cavarest-rcon`
- **Version**: `0.1.0`
- **Package**: `org.cavarest.rcon`

## Features

- Connect to Minecraft servers using the RCON protocol
- Send commands to the server and receive responses
- Support for multiple Minecraft server versions
- Easy-to-use API for developers
- Lightweight and efficient implementation
- Provides proper handling of RCON packets and responses
- Proper error handling and detailed logging
- Support for both synchronous and asynchronous command execution
- Thread-safe design for concurrent usage

## Architecture Overview

The library follows a layered architecture:

```
┌─────────────────────────────────────────┐
│           RconClient (High-level)        │  ← Simplified API for end users
├─────────────────────────────────────────┤
│              Rcon (Core API)             │  ← Main client class with builder pattern
├─────────────────────────────────────────┤
│        PacketReader / PacketWriter       │  ← Low-level I/O handling
├─────────────────────────────────────────┤
│             PacketCodec                  │  ← Binary encoding/decoding
├─────────────────────────────────────────┤
│               Packet                     │  ← Domain model for RCON packets
└─────────────────────────────────────────┘
```

## Comparison with Other Implementations

- **java-rcon** (`/Users/mulgogi/src/cavarest/java-rcon`): A Java RCON library that is no longer well maintained, somewhat outdated, and lacks comprehensive tests.

- **Minecraft-rcon** (`/Users/mulgogi/src/cavarest/Minecraft-rcon`): A VSCode extension providing RCON functionality for Minecraft servers, written in TypeScript.

Cavarest RCon aims to be a more modern, well-tested, and feature-rich alternative to these existing implementations and a full superset of their features.

## Testing

Through Docker, Minecraft servers of different versions can be spun up to test against. This ensures compatibility and reliability across various server versions.

An example of how this is done through Docker is available at `/Users/mulgogi/src/cavarest/pilaf`.

## RCON Protocol Notes

### Packet Format

Integers are little-endian, in contrast with the Java Edition protocol.

Responses are sent back with the same Request ID that you send. In the event of an auth failure (i.e., your login is incorrect, or you're trying to send commands without first logging in), request ID will be set to -1.

| Field name | Field type | Notes |
|------------|------------|-------|
| Length     | int32      | Length of remainder of packet |
| Request ID | int32      | Client-generated ID |
| Type       | int32      | 3 for login, 2 to run a command, 0 for a multi-packet response |
| Payload    | byte[]     | NULL-terminated ASCII text |
| 1-byte pad | byte       | NULL |

### Character Encoding

Some servers (e.g., Craftbukkit for Minecraft 1.4.7) reply with color codes prefixed by a section sign (byte 0xA7 or 167). This is not part of the US-ASCII charset and will cause errors for clients that strictly use US-ASCII charset.

Using ISO-LATIN-1/ISO-8859_1 charset instead of US-ASCII yields better results for those servers.

### Packet Types

- **3 (SERVERDATA_AUTH)**: Login. Outgoing payload is the password. If the server returns a packet with the same request ID, auth was successful (note: packet type is 2, not 3). If request ID is -1, auth failed.

- **2 (SERVERDATA_EXECCOMMAND)**: Command. Outgoing payload is the command to run.

- **0 (SERVERDATA_RESPONSE_VALUE)**: Command response. Incoming payload is the output of the command.

### Fragmentation

- Maximum C→S packet payload length: 1446 bytes
- Maximum S→C packet payload length: 4096 bytes

The Minecraft server can fragment responses across multiple packets. There's no simple way to know when the last response packet has been received. Approaches include:

Method 1. Wait until we receive a packet with a payload length < 4096 (not 100%
reliable!)

Method 2.  Wait for n seconds

Method 3. Send two command packets; the second command triggers a response from
the server with the same Request ID, and from this we know we've already
received the full response to the first command.
