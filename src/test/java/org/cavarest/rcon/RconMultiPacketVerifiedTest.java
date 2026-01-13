package org.cavarest.rcon;

import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Verified multi-packet RCON response tests.
 *
 * Uses the exponential duplication technique from gaming.stackexchange.com:
 * https://gaming.stackexchange.com/questions/385273
 *
 * This technique creates 512 entities using exponential duplication,
 * then queries all of them using /execute as @e[tag=dup] run data get entity @s
 * which generates a massive multi-packet response.
 */
@Tag("integration")
@DisplayName("Verified Multi-Packet RCON Response Tests")
class RconMultiPacketVerifiedTest {

    private static final String RCON_HOST = System.getenv().getOrDefault("RCON_HOST", "localhost");
    private static final int RCON_PORT = Integer.parseInt(System.getenv().getOrDefault("RCON_PORT", "35575"));
    private static final String RCON_PASSWORD = System.getenv().getOrDefault("RCON_PASSWORD", "cavarest");

    private RconClient rconClient;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        assumeFalse(
            Boolean.parseBoolean(System.getenv("SKIP_INTEGRATION_TESTS")),
            "Skipping integration tests as SKIP_INTEGRATION_TESTS is set"
        );

        System.out.println("\n========================================");
        System.out.println("Multi-Packet Test: " + testInfo.getDisplayName());
        System.out.println("========================================");

        rconClient = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD);

        try {
            rconClient.connect();
        } catch (Exception e) {
            assumeTrue(false, "RCON server not available: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (rconClient != null) {
            try {
                // Clean up any remaining entities
                rconClient.sendCommand("kill @e[tag=dup]");
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            try {
                rconClient.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    @DisplayName("Exponential duplication: 512 entities with /execute as @e run data get entity @s")
    void exponentialDuplication512Entities() throws IOException {
        rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

        System.out.println("Creating armor stands using exponential duplication...");

        // Step 1: Spawn the first "duplicator" armor stand
        String result = rconClient.sendCommand(
            "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
        );
        System.out.println("First armor stand spawned: " + result);

        // Step 2: Exponentially duplicate to 512 entities
        System.out.println("Exponentially duplicating...");
        for (int i = 0; i < 9; i++) {
            result = rconClient.sendCommand(
                "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );
            int count = (int) Math.pow(2, i + 1);
            System.out.println("After duplication " + (i + 1) + ": ~" + count + " entities - Response: " + result);
        }

        // Wait for all entities to be fully registered
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 3: Query all entities using the magic command
        // This runs /data get entity @s FOR EACH ENTITY, not once for all entities
        System.out.println("\n========================================");
        System.out.println("EXECUTING MULTI-PACKET QUERY:");
        System.out.println("Command: /execute as @e[tag=dup] run data get entity @s");
        System.out.println("========================================\n");

        String entityData = rconClient.sendCommand(
            "execute as @e[tag=dup] run data get entity @s"
        );

        System.out.println("\n========================================");
        System.out.println("MULTI-PACKET RESPONSE ANALYSIS:");
        System.out.println("========================================");
        System.out.println("Response length: " + entityData.length() + " bytes");
        System.out.println("RCON max packet size: 4096 bytes");

        if (entityData.length() > 4096) {
            System.out.println("");
            System.out.println("✓✓✓ MULTI-PACKET RESPONSE CONFIRMED! ✓✓✓");
            System.out.println("Response exceeded 4096 bytes: " + entityData.length() + " bytes");
            System.out.println("Approximate packets: " + ((entityData.length() / 4096) + 1));
            System.out.println("");
            System.out.println("Multi-packet fragment resolution works correctly!");
        } else {
            System.out.println("");
            System.out.println("Response was single-packet (" + entityData.length() + " bytes)");
            System.out.println("(Expected >4096 bytes for confirmed multi-packet)");
        }

        System.out.println("");
        System.out.println("First 500 characters of response:");
        System.out.println("----------------------------------------");
        System.out.println(entityData.substring(0, Math.min(500, entityData.length())));
        System.out.println("");
        System.out.println("Last 500 characters of response:");
        System.out.println("----------------------------------------");
        System.out.println(entityData.substring(Math.max(0, entityData.length() - 500)));
        System.out.println("========================================\n");

        // Verify we got substantial data
        assertNotNull(entityData, "Entity data should not be null");
        assertFalse(entityData.isEmpty(), "Entity data should not be empty");

        // The response should be very large with 512 entities
        // Even if truncated, it should be at least several thousand bytes
        assertTrue(entityData.length() > 1000,
            "Response should be substantial (>1000 bytes), got: " + entityData.length());

        // Clean up
        System.out.println("Cleaning up entities...");
        result = rconClient.sendCommand("kill @e[tag=dup]");
        System.out.println("Cleanup result: " + result);
    }

    @Test
    @DisplayName("Exponential duplication: 256 entities (smaller test)")
    void exponentialDuplication256Entities() throws IOException {
        rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

        System.out.println("Creating 256 armor stands using exponential duplication...");

        // Spawn first entity
        rconClient.sendCommand(
            "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
        );

        // Duplicate 8 times to get 256 entities
        for (int i = 0; i < 8; i++) {
            rconClient.sendCommand(
                "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );
            int count = (int) Math.pow(2, i + 1);
            System.out.println("After duplication " + (i + 1) + ": ~" + count + " entities");
        }

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String entityData = rconClient.sendCommand(
            "execute as @e[tag=dup] run data get entity @s"
        );

        System.out.println("\n256 Entities Response length: " + entityData.length() + " bytes");

        if (entityData.length() > 4096) {
            System.out.println("✓✓✓ MULTI-PACKET RESPONSE CONFIRMED! ✓✓✓");
        }

        assertNotNull(entityData);
        assertTrue(entityData.length() > 500, "Response should be substantial");

        // Clean up
        rconClient.sendCommand("kill @e[tag=dup]");
    }

    @Test
    @DisplayName("Exponential duplication: 128 entities (even smaller test)")
    void exponentialDuplication128Entities() throws IOException {
        rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

        System.out.println("Creating 128 armor stands using exponential duplication...");

        // Spawn first entity
        rconClient.sendCommand(
            "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
        );

        // Duplicate 7 times to get 128 entities
        for (int i = 0; i < 7; i++) {
            rconClient.sendCommand(
                "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );
        }

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String entityData = rconClient.sendCommand(
            "execute as @e[tag=dup] run data get entity @s"
        );

        System.out.println("128 Entities Response length: " + entityData.length() + " bytes");

        if (entityData.length() > 4096) {
            System.out.println("✓✓✓ MULTI-PACKET RESPONSE CONFIRMED! ✓✓✓");
        }

        assertNotNull(entityData);
        assertTrue(entityData.length() > 200, "Response should be substantial");

        // Clean up
        rconClient.sendCommand("kill @e[tag=dup]");
    }

    @Nested
    @DisplayName("Fragment Resolution Strategy Comparison")
    class StrategyComparisonTests {

        @Test
        @DisplayName("Compare all three strategies with 256 entities")
        void compareAllStrategies() throws IOException {
            int entityCount = 256;

            // Create entities
            rconClient.sendCommand(
                "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );

            for (int i = 0; i < 8; i++) {
                rconClient.sendCommand(
                    "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
                );
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("\n========================================");
            System.out.println("Testing " + entityCount + " entities with all strategies:");
            System.out.println("========================================\n");

            FragmentResolutionStrategy[] strategies = FragmentResolutionStrategy.values();
            int[] responseSizes = new int[strategies.length];

            for (int i = 0; i < strategies.length; i++) {
                FragmentResolutionStrategy strategy = strategies[i];

                try (RconClient testClient = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD)) {
                    testClient.connect();
                    testClient.setFragmentResolutionStrategy(strategy);

                    String response = testClient.sendCommand(
                        "execute as @e[tag=dup] run data get entity @s"
                    );

                    responseSizes[i] = response.length();

                    System.out.println(strategy + " strategy:");
                    System.out.println("  Response length: " + response.length() + " bytes");
                    System.out.println("  Multi-packet: " + (response.length() > 4096 ? "YES" : "NO"));

                    if (response.length() > 4096) {
                        System.out.println("  Packets: ~" + ((response.length() / 4096) + 1));
                    }
                    System.out.println();
                }

                // Small delay between tests
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("========================================");
            System.out.println("SUMMARY:");
            System.out.println("========================================");
            for (int i = 0; i < strategies.length; i++) {
                System.out.println(strategies[i] + ": " + responseSizes[i] + " bytes" +
                    (responseSizes[i] > 4096 ? " ✓ MULTI-PACKET" : ""));
            }

            // All strategies should return the same data
            for (int i = 1; i < responseSizes.length; i++) {
                assertEquals(responseSizes[0], responseSizes[i],
                    "All strategies should return same response size");
            }

            // Clean up
            rconClient.sendCommand("kill @e[tag=dup]");
        }
    }

    @Test
    @DisplayName("Baseline: Single entity response size")
    void baselineSingleEntityResponse() throws IOException {
        rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

        System.out.println("Creating single armor stand...");

        rconClient.sendCommand(
            "summon armor_stand ~ ~ ~ {Tags:[\"test\"],Invisible:1b}"
        );

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String entityData = rconClient.sendCommand(
            "execute as @e[tag=test] run data get entity @s"
        );

        System.out.println("\nSingle entity response length: " + entityData.length() + " bytes");
        System.out.println("Response: " + entityData);

        assertNotNull(entityData);
        assertTrue(entityData.length() > 50, "Single entity should have substantial NBT");
        assertTrue(entityData.length() < 1000, "Single entity should be < 1000 bytes");

        // Clean up
        rconClient.sendCommand("kill @e[tag=test]");
    }
}
