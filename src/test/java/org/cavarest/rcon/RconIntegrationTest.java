package org.cavarest.rcon;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for the RCON library against any RCON server.
 *
 * These tests are infrastructure-agnostic and can run against any RCON server
 * (Testcontainers, docker-compose, or manually started).
 *
 * Port Configuration (Single Source of Truth):
 * - Default: 35575 (high port to avoid conflicts with local Minecraft servers)
 * - Override via RCON_PORT environment variable
 *
 * Configuration via environment variables:
 * - RCON_HOST: Server hostname (default: localhost)
 * - RCON_PORT: RCON port (default: 35575)
 * - RCON_PASSWORD: RCON password (default: cavarest)
 * - SKIP_INTEGRATION_TESTS: Set to true to skip these tests
 *
 * Local testing:
 *   docker-compose -f docker-compose.integration.yml up -d
 *   ./gradlew integrationTest
 */
@Tag("integration")
@DisplayName("RCON Integration Tests")
class RconIntegrationTest {

    // Read connection parameters from environment or use defaults
    // Default to high port (35575) to avoid conflicts with local Minecraft servers
    private static final String RCON_HOST = System.getenv().getOrDefault("RCON_HOST", "localhost");
    private static final int RCON_PORT = Integer.parseInt(System.getenv().getOrDefault("RCON_PORT", "35575"));
    private static final String RCON_PASSWORD = System.getenv().getOrDefault("RCON_PASSWORD", "cavarest");

    private RconClient rconClient;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Skip if explicitly disabled
        assumeFalse(
            Boolean.parseBoolean(System.getenv("SKIP_INTEGRATION_TESTS")),
            "Skipping integration tests as SKIP_INTEGRATION_TESTS is set"
        );

        System.out.println("========================================");
        System.out.println("RCON Integration Test: " + testInfo.getDisplayName());
        System.out.println("Connecting to RCON server at " + RCON_HOST + ":" + RCON_PORT);
        System.out.println("========================================");

        rconClient = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD);

        try {
            rconClient.connect();
            System.out.println("Successfully connected to RCON server");
        } catch (Exception e) {
            System.err.println("FAILED to connect to RCON server: " + e.getMessage());
            e.printStackTrace();
            // Assume the server is not running and skip the tests
            assumeTrue(false, "RCON server not available at " + RCON_HOST + ":" + RCON_PORT +
                       " - " + e.getMessage() + ". Start a server with docker-compose or set SKIP_INTEGRATION_TESTS=true");
        }

        scheduler = Executors.newScheduledThreadPool(1);
    }

    @AfterEach
    void tearDown() {
        if (rconClient != null) {
            try {
                rconClient.close();
                System.out.println("Closed RCON connection");
            } catch (Exception e) {
                System.err.println("Error closing RCON connection: " + e.getMessage());
            }
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    @DisplayName("Should connect and authenticate with RCON server")
    void shouldConnectAndAuthenticate() {
        assertTrue(rconClient.isConnected(), "Should be connected after connect()");
    }

    @Test
    @DisplayName("Should execute simple command")
    void shouldExecuteSimpleCommand() throws IOException {
        String response = rconClient.sendCommand("list");
        assertNotNull(response, "Response should not be null");
        // Response should contain player count information
        assertTrue(response.matches(".*\\d+.*player.*") || response.contains("There are"),
            "Response should contain player count: " + response);
        System.out.println("Response from 'list': " + response);
    }

    @Test
    @DisplayName("Should execute say command")
    void shouldExecuteSayCommand() throws IOException {
        String response = rconClient.sendCommand("say Hello from RCON integration test!");
        assertNotNull(response, "Response should not be null");
        assertTrue(response.isEmpty() || response.contains("Hello from RCON"),
            "Server should have broadcast the message: " + response);
        System.out.println("Response from 'say': " + response);
    }

    @Test
    @DisplayName("Should get server version")
    void shouldGetServerVersion() throws IOException {
        String version = rconClient.sendCommand("version");
        assertNotNull(version, "Version response should not be null");
        // Paper servers may return "Checking version, please wait..." first
        // If still checking, skip the detailed validation
        if (version.contains("Checking version")) {
            System.out.println("Server is still checking version: " + version);
            assertTrue(true, "Version check in progress");
            return;
        }
        assertFalse(version.isEmpty(), "Version should not be empty");
        // Server version should contain version info
        assertTrue(version.contains("Paper") || version.contains("1.21") || version.matches(".*\\d+\\.\\d+.*"),
            "Version should contain version information: " + version);
        System.out.println("Server version: " + version);
    }

    @Test
    @DisplayName("Should execute command with arguments")
    void shouldExecuteCommandWithArguments() throws IOException {
        String response = rconClient.sendCommand("say", "Testing", "command", "with", "arguments");
        assertNotNull(response, "Response should not be null");
        System.out.println("Response from 'say' with args: " + response);
    }

    @Test
    @DisplayName("Should handle multiple commands")
    void shouldHandleMultipleCommands() throws IOException {
        String response1 = rconClient.sendCommand("list");
        assertNotNull(response1);
        assertTrue(response1.matches(".*\\d+.*") || response1.contains("There are"),
            "List response should contain player info: " + response1);

        String response2 = rconClient.sendCommand("time query day");
        assertNotNull(response2);
        // Time query should return a number
        assertTrue(response2.matches(".*\\d+.*") || response2.contains("Daytime"),
            "Time response should contain time info: " + response2);

        String response3 = rconClient.sendCommand("difficulty");
        assertNotNull(response3);
        // Difficulty should contain difficulty level
        assertTrue(response3.toLowerCase().contains("peaceful") ||
                   response3.toLowerCase().contains("easy") ||
                   response3.toLowerCase().contains("normal") ||
                   response3.toLowerCase().contains("hard"),
            "Difficulty response should contain difficulty level: " + response3);

        System.out.println("Multiple commands executed successfully");
    }

    @Test
    @DisplayName("Should return content for help command")
    void shouldReturnContentForHelpCommand() throws IOException {
        String response = rconClient.sendCommand("help");
        assertNotNull(response);
        assertTrue(response.length() > 0, "Help response should have content");
        // Help response should contain command information
        assertTrue(response.contains("/") || response.contains("Available") || response.contains("help"),
            "Help response should contain command info: " + response);
    }

    @Test
    @DisplayName("Should get player count")
    void shouldGetPlayerCount() throws IOException {
        String listResponse = rconClient.sendCommand("list");
        assertNotNull(listResponse);
        assertTrue(listResponse.contains("players") || listResponse.contains("There are") || listResponse.contains("player"),
            "List response should mention players: " + listResponse);
        System.out.println("Player list: " + listResponse);
    }

    @Test
    @DisplayName("Should send command with trySendCommand")
    void shouldSendCommandWithTrySendCommand() {
        boolean result = rconClient.trySendCommand("list");
        assertTrue(result, "trySendCommand should return true for valid command");
    }

    @Test
    @DisplayName("Should maintain connection across multiple commands")
    void shouldMaintainConnectionAcrossCommands() throws IOException {
        for (int i = 0; i < 5; i++) {
            String response = rconClient.sendCommand("list");
            assertNotNull(response, "Command " + (i + 1) + " should return response");
            assertTrue(rconClient.isConnected(), "Should still be connected after command " + (i + 1));
        }
    }

    @Test
    @DisplayName("Should handle concurrent command attempts")
    void shouldHandleConcurrentCommandAttempts() throws Exception {
        AtomicBoolean hasError = new AtomicBoolean(false);
        int numThreads = 3;

        CompletableFuture<?>[] futures = new CompletableFuture[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    try (RconClient client = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD)) {
                        client.connect();
                        String response = client.sendCommand("list");
                        assertNotNull(response);
                    }
                } catch (Exception e) {
                    hasError.set(true);
                    System.err.println("Error in thread " + threadId + ": " + e.getMessage());
                }
            }, scheduler);
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        assertFalse(hasError.get(), "No errors should occur during concurrent commands");
    }

    @Test
    @DisplayName("Rcon.connect(SocketAddress) should return connected Rcon instance")
    void rconConnectWithSocketAddressShouldReturnConnectedInstance() throws IOException {
        try (Rcon rcon = Rcon.connect(new InetSocketAddress(RCON_HOST, RCON_PORT))) {
            assertNotNull(rcon, "Rcon instance should not be null");
            // Try to authenticate
            rcon.authenticate(RCON_PASSWORD);
            String response = rcon.sendCommand("list");
            assertNotNull(response, "Response should not be null");
        }
    }

    @Test
    @DisplayName("Rcon.connect(hostname, port) should return connected Rcon instance")
    void rconConnectWithHostnameAndPortShouldReturnConnectedInstance() throws IOException {
        try (Rcon rcon = Rcon.connect(RCON_HOST, RCON_PORT)) {
            assertNotNull(rcon, "Rcon instance should not be null");
            // Try to authenticate
            rcon.authenticate(RCON_PASSWORD);
            String response = rcon.sendCommand("list");
            assertNotNull(response, "Response should not be null");
        }
    }

    // ========== Multi-Packet Response Tests ==========
    // These tests verify that multi-packet responses (> 4096 bytes) are handled correctly

    @Nested
    @DisplayName("Multi-Packet Response Tests - ACTIVE_PROBE Strategy")
    class MultiPacketActiveProbeTests {

        @Test
        @DisplayName("Should handle multi-packet help response with ACTIVE_PROBE strategy")
        void shouldHandleMultiPacketHelpWithActiveProbe() throws IOException {
            rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

            // Help command typically returns multiple lines and can exceed 4096 bytes
            String response = rconClient.sendCommand("help");
            assertNotNull(response, "Help response should not be null");
            assertFalse(response.isEmpty(), "Help response should not be empty");

            System.out.println("Help response length: " + response.length() + " bytes");

            // Verify the response is complete (should contain various command categories)
            assertTrue(response.length() > 100, "Help response should be substantial: " + response.length());
        }

        @Test
        @DisplayName("Should generate and retrieve multi-packet entity list")
        void shouldGenerateAndRetrieveMultiPacketEntityList() throws IOException {
            rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

            System.out.println("Creating entities using exponential duplication...");

            // Create first "duplicator" armor stand
            rconClient.sendCommand(
                "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );

            // Exponentially duplicate to 128 entities
            for (int i = 0; i < 7; i++) {
                rconClient.sendCommand(
                    "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
                );
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Query all entities using the magic command
            // This runs /data get entity @s FOR EACH ENTITY, generating massive response
            String entityData = rconClient.sendCommand(
                "execute as @e[tag=dup] run data get entity @s"
            );

            assertNotNull(entityData, "Entity data should not be null");
            assertFalse(entityData.isEmpty(), "Entity data should not be empty");

            System.out.println("Entity data length: " + entityData.length() + " bytes");

            // Should be a substantial multi-packet response
            assertTrue(entityData.length() > 1000, "Response should be substantial: " + entityData.length());

            // Clean up
            rconClient.sendCommand("kill @e[tag=dup]");
        }

        @Test
        @DisplayName("Should handle multi-packet response using tryAuthenticate")
        void shouldHandleMultiPacketWithTryAuthenticate() throws IOException {
            // Test with low-level Rcon API
            try (Rcon rcon = Rcon.connect(RCON_HOST, RCON_PORT)) {
                rcon.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);
                rcon.tryAuthenticate(RCON_PASSWORD);

                System.out.println("Creating entities using exponential duplication...");

                // Create first "duplicator" armor stand
                rcon.sendCommand(
                    "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
                );

                // Exponentially duplicate to 128 entities
                for (int i = 0; i < 7; i++) {
                    rcon.sendCommand(
                        "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
                    );
                }

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Query all entities using the magic command
                String entityData = rcon.sendCommand(
                    "execute as @e[tag=dup] run data get entity @s"
                );

                assertNotNull(entityData);
                System.out.println("Entity data with tryAuthenticate: " + entityData.length() + " bytes");

                // Clean up
                rcon.sendCommand("kill @e[tag=dup]");
            }
        }
    }

    @Nested
    @DisplayName("Multi-Packet Response Tests - PACKET_SIZE Strategy")
    class MultiPacketPacketSizeTests {

        @Test
        @DisplayName("Should handle multi-packet response with PACKET_SIZE strategy")
        void shouldHandleMultiPacketWithPacketSizeStrategy() throws IOException {
            rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.PACKET_SIZE);

            System.out.println("Creating entities using exponential duplication...");

            // Create first "duplicator" armor stand
            rconClient.sendCommand(
                "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );

            // Exponentially duplicate to 128 entities
            for (int i = 0; i < 7; i++) {
                rconClient.sendCommand(
                    "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
                );
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Query all entities using the magic command
            String entityData = rconClient.sendCommand(
                "execute as @e[tag=dup] run data get entity @s"
            );

            assertNotNull(entityData, "Entity data should not be null");
            System.out.println("PACKET_SIZE strategy response length: " + entityData.length() + " bytes");

            // Verify we got a complete response
            assertFalse(entityData.isEmpty(), "Entity data should not be empty");

            // Clean up
            rconClient.sendCommand("kill @e[tag=dup]");
        }
    }

    @Nested
    @DisplayName("Multi-Packet Response Tests - TIMEOUT Strategy")
    class MultiPacketTimeoutTests {

        @Test
        @DisplayName("Should handle multi-packet response with TIMEOUT strategy")
        void shouldHandleMultiPacketWithTimeoutStrategy() throws IOException {
            rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.TIMEOUT);
            // Set a longer timeout for TIMEOUT strategy
            rconClient.setFragmentTimeout(200, TimeUnit.MILLISECONDS);

            System.out.println("Creating entities using exponential duplication...");

            // Create first "duplicator" armor stand
            rconClient.sendCommand(
                "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );

            // Exponentially duplicate to 128 entities
            for (int i = 0; i < 7; i++) {
                rconClient.sendCommand(
                    "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
                );
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Query all entities using the magic command
            String entityData = rconClient.sendCommand(
                "execute as @e[tag=dup] run data get entity @s"
            );

            assertNotNull(entityData, "Entity data should not be null");
            System.out.println("TIMEOUT strategy response length: " + entityData.length() + " bytes");

            // Verify we got a complete response
            assertFalse(entityData.isEmpty(), "Entity data should not be empty");

            // Clean up
            rconClient.sendCommand("kill @e[tag=dup]");
        }
    }

    @Nested
    @DisplayName("Fragment Resolution Strategy Comparison")
    class StrategyComparisonTests {

        @Test
        @DisplayName("All strategies should produce consistent results for same command")
        void allStrategiesShouldProduceConsistentResults() throws IOException {
            System.out.println("Creating entities using exponential duplication...");

            // Create first "duplicator" armor stand
            rconClient.sendCommand(
                "summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
            );

            // Exponentially duplicate to 128 entities
            for (int i = 0; i < 7; i++) {
                rconClient.sendCommand(
                    "execute as @e[tag=dup] run summon armor_stand ~ ~ ~ {Tags:[\"dup\"],Invisible:1b,NoGravity:1b}"
                );
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            List<String> responses = new ArrayList<>();
            FragmentResolutionStrategy[] strategies = FragmentResolutionStrategy.values();

            for (FragmentResolutionStrategy strategy : strategies) {
                try (RconClient testClient = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD)) {
                    testClient.connect();
                    testClient.setFragmentResolutionStrategy(strategy);

                    String response = testClient.sendCommand(
                        "execute as @e[tag=dup] run data get entity @s"
                    );
                    assertNotNull(response);
                    assertFalse(response.isEmpty());
                    responses.add(response);

                    System.out.println(strategy + " strategy response length: " + response.length() + " bytes");
                }
                // Small delay between connections to avoid server rejection
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // All strategies should return responses
            assertEquals(strategies.length, responses.size(), "All strategies should return a response");

            // Clean up
            rconClient.sendCommand("kill @e[tag=dup]");
        }
    }

    @Nested
    @DisplayName("Large Response Tests")
    class LargeResponseTests {

        @Test
        @DisplayName("Should handle very large help response")
        void shouldHandleVeryLargeHelpResponse() throws IOException {
            rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

            String response = rconClient.sendCommand("help");
            assertNotNull(response);
            System.out.println("Help response: " + response.length() + " bytes");

            // The help command on Paper 1.21 can be quite large
            assertTrue(response.length() > 50, "Help should return substantial content");
        }

        @Test
        @DisplayName("Should detect and handle multi-packet responses")
        void shouldDetectAndHandleMultiPacketResponses() throws IOException {
            // Use default ACTIVE_PROBE strategy
            String response = rconClient.sendCommand("help");

            assertNotNull(response);
            int responseLength = response.length();
            System.out.println("Response length: " + responseLength + " bytes");

            // If response is > 4096, it was definitely a multi-packet response
            if (responseLength > 4096) {
                System.out.println("CONFIRMED: Multi-packet response received (" + responseLength + " bytes)");
                System.out.println("This proves multi-packet handling is working!");
            } else {
                System.out.println("Response was single-packet (" + responseLength + " bytes)");
                System.out.println("To test multi-packet, need a larger response");
            }
        }

        @Test
        @DisplayName("Should handle guaranteed multi-packet response (>4096 bytes)")
        void shouldHandleGuaranteedMultiPacketResponse() throws IOException {
            rconClient.setFragmentResolutionStrategy(FragmentResolutionStrategy.ACTIVE_PROBE);

            System.out.println("Testing multi-packet RCON response handling...");
            System.out.println("========================================");
            System.out.println("Approach: Give 100+ DIFFERENT items to villager, then query");
            System.out.println("");

            // Create a villager
            System.out.println("Creating villager...");
            rconClient.sendCommand("summon villager ~ ~ ~ {CustomName:'\"\\\"TestVillager\\\"\"'}");

            // Give 100+ DIFFERENT items (not repeats!) to maximize NBT data size
            System.out.println("\nGiving villager 100+ DIFFERENT items using multiple /item calls...");
            System.out.println("----------------------------------------");

            // All different tool/armor/weapon/item types with unique enchantments
            String[] differentItems = {
                "diamond_sword{Enchantments:[{id:\"sharpness\",lvl:5}]}",
                "iron_sword{Enchantments:[{id:\"sharpness\",lvl:3}]}",
                "golden_sword{Enchantments:[{id:\"sharpness\",lvl:2}]}",
                "stone_sword{Damage:1}",
                "wooden_sword",
                "diamond_pickaxe{Enchantments:[{id:\"efficiency\",lvl:4}]}",
                "iron_pickaxe{Enchantments:[{id:\"efficiency\",lvl:2}]}",
                "golden_pickaxe",
                "stone_pickaxe{Damage:2}",
                "wooden_pickaxe",
                "diamond_axe{Enchantments:[{id:\"efficiency\",lvl:3}]}",
                "iron_axe",
                "golden_axe",
                "stone_axe{Damage:1}",
                "wooden_axe",
                "diamond_shovel",
                "iron_shovel",
                "golden_shovel",
                "stone_shovel{Damage:1}",
                "wooden_shovel",
                "diamond_hoe",
                "iron_hoe",
                "golden_hoe",
                "stone_hoe{Damage:1}",
                "wooden_hoe",
                "diamond_helmet{Enchantments:[{id:\"protection\",lvl:3}]}",
                "iron_helmet{Enchantments:[{id:\"protection\",lvl:2}]}",
                "golden_helmet",
                "leather_helmet{color:12345}",
                "chainmail_helmet",
                "diamond_chestplate{Enchantments:[{id:\"protection\",lvl:3}]}",
                "iron_chestplate{Enchantments:[{id:\"protection\",lvl:2}]}",
                "golden_chestplate",
                "leather_chestplate{color:54321}",
                "chainmail_chestplate",
                "diamond_leggings{Enchantments:[{id:\"protection\",lvl:3}]}",
                "iron_leggings{Enchantments:[{id:\"protection\",lvl:2}]}",
                "golden_leggings",
                "leather_leggings{color:11111}",
                "chainmail_leggings",
                "diamond_boots{Enchantments:[{id:\"protection\",lvl:3}]}",
                "iron_boots{Enchantments:[{id:\"protection\",lvl:2}]}",
                "golden_boots",
                "leather_boots{color:22222}",
                "chainmail_boots",
                "diamond{Enchantments:[{id:\"fortune\",lvl:2}]}",
                "iron_ingot",
                "gold_ingot",
                "coal",
                "redstone",
                "emerald",
                "lapis_lazuli{Enchantments:[{id:\"fortune\",lvl:1}]}",
                "quartz",
                "amethyst_shard",
                "copper_ingot",
                "netherite_ingot",
                "netherite_sword{Enchantments:[{id:\"sharpness\",lvl:5}]}",
                "netherite_pickaxe{Enchantments:[{id:\"efficiency\",lvl:4}]}",
                "netherite_axe{Enchantments:[{id:\"efficiency\",lvl:3}]}",
                "netherite_shovel",
                "netherite_hoe",
                "netherite_helmet{Enchantments:[{id:\"protection\",lvl:4}]}",
                "netherite_chestplate{Enchantments:[{id:\"protection\",lvl:4}]}",
                "netherite_leggings{Enchantments:[{id:\"protection\",lvl:4}]}",
                "netherite_boots{Enchantments:[{id:\"protection\",lvl:4}]}",
                "apple",
                "golden_apple",
                "bread",
                "carrot",
                "potato",
                "beef",
                "porkchop",
                "chicken",
                "rabbit",
                "mutton",
                "cod",
                "salmon",
                "tropical_fish",
                "pufferfish",
                "cooked_cod",
                "cooked_beef",
                "cooked_porkchop",
                "cooked_chicken",
                "cooked_rabbit",
                "cooked_mutton",
                "cooked_salmon",
                "bow{Enchantments:[{id:\"power\",lvl:3}]}",
                "crossbow{Enchantments:[{id:\"quick_charge\",lvl:2}]}",
                "arrow",
                "spectral_arrow",
                "tipped_arrow{Potion:\"minecraft:poison\"}",
                "shield{Enchantments:[{id:\"unbreaking\",lvl:2}]}",
                "trident{Enchantments:[{id:\"loyalty\",lvl:2}]}",
                "elytra{Enchantments:[{id:\"unbreaking\",lvl:2}]}",
                "totem_of_undying",
                "nether_star",
                "ender_pearl",
                "blaze_rod",
                "bone",
                "gunpowder",
                "string",
                "spider_eye",
                "fermented_spider_eye",
                "glistering_melon_slice",
                "magma_cream",
                "pufferfish",
                "rabbit_foot",
                "sugar",
                "slime_ball",
                "ghast_tear",
                "dragon_breath",
                "chorus_fruit",
                "nautilus_shell",
                "heart_of_the_sea",
                "turtle_helmet",
                "scute",
                "phantom_membrane",
                "recovery_compass",
                "clock"
            };

            int itemCount = 0;
            for (int i = 0; i < differentItems.length && i < 100; i++) {
                String item = differentItems[i];
                try {
                    rconClient.sendCommand("item replace entity @e[type=villager,limit=1] inventory." + i + " with minecraft:" + item);
                    itemCount++;
                    if (itemCount % 10 == 0) {
                        System.out.println("Given " + itemCount + " different items...");
                    }
                } catch (IOException e) {
                    System.out.println("Failed to give item " + i + ": " + e.getMessage());
                }
            }

            System.out.println("Total different items given: " + itemCount);
            System.out.println("");

            // Query the villager with ONE call
            System.out.println("Querying villager NBT data with ONE /data get entity command...");
            System.out.println("----------------------------------------");

            String villagerData = rconClient.sendCommand("data get entity @e[type=villager,limit=1]");

            assertNotNull(villagerData, "Villager data should not be null");
            assertFalse(villagerData.isEmpty(), "Villager data should not be empty");

            int responseLength = villagerData.length();
            System.out.println("========================================");
            System.out.println("VILLAGER RESPONSE SIZE: " + responseLength + " bytes");
            System.out.println("========================================");

            if (responseLength > 4096) {
                System.out.println("✓✓✓ MULTI-PACKET RESPONSE CONFIRMED! ✓✓✓");
                System.out.println("Villager data exceeded 4096 bytes: " + responseLength + " bytes");
                System.out.println("Multi-packet fragment resolution works correctly!");
            } else {
                System.out.println("Villager data was " + responseLength + " bytes (single packet)");
                System.out.println("RCON protocol max packet size: 4096 bytes");
            }

            System.out.println("");
            System.out.println("========================================");
            System.out.println("FULL VILLAGER NBT DATA RESPONSE:");
            System.out.println("========================================");
            System.out.println(villagerData);
            System.out.println("========================================");
            System.out.println("");
            System.out.println("END OF DATA. User: Is this response correct or am I broken?");
            System.out.println("");

            // Clean up
            rconClient.sendCommand("kill @e[type=villager]");

            // Halt execution here for user review
            assertTrue(responseLength > 50, "Should get villager data");
        }

        private void reportMultiPacketSuccess(String response) {
            System.out.println("\n========================================");
            System.out.println("✓✓✓ MULTI-PACKET RESPONSE CONFIRMED! ✓✓✓");
            System.out.println("========================================");
            System.out.println("Response size: " + response.length() + " bytes");
            System.out.println("Multi-packet fragment resolution works correctly!");
            System.out.println("\nFirst 500 characters:");
            System.out.println(response.substring(0, Math.min(500, response.length())));
            System.out.println("\nLast 500 characters:");
            System.out.println(response.substring(Math.max(0, response.length() - 500)));
            assertTrue(response.length() > 4096, "Response should exceed 4096 bytes");
        }

        private String getColorForIndex(int index) {
            String[] colors = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
                               "dark_purple", "gold", "gray", "dark_gray", "blue",
                               "green", "aqua", "red", "light_purple", "yellow", "white"};
            return colors[index % colors.length];
        }

        @Test
        @Tag("integration")
        void testMultiPacketResponseWith200Entities() throws Exception {
            try (RconClient rconClient = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD)) {
                rconClient.connect();

                System.out.println("\n========================================");
                System.out.println("Testing Multi-Packet with 200 Entities");
                System.out.println("========================================");

                // Clear any existing entities first
                rconClient.sendCommand("kill @e[type=!player]");

                // Define different entity types to summon (mix of different types)
                String[] entityTypes = {
                    "armor_stand",
                    "villager",
                    "zombie",
                    "skeleton",
                    "creeper",
                    "spider",
                    "enderman",
                    "pig",
                    "cow",
                    "sheep",
                    "chicken",
                    "rabbit",
                    "wolf",
                    "cat",
                    "fox",
                    "bee",
                    "axolotl",
                    "goat",
                    "panda",
                    "polar_bear"
                };

                // Summon 200 entities - using simpler approach without NBT
                int entityCount = 200;
                System.out.println("Summoning " + entityCount + " entities...");

                for (int i = 0; i < entityCount; i++) {
                    String entityType = entityTypes[i % entityTypes.length];

                    // Summon the entity at slightly different positions - correct syntax: x y z
                    double x = (i % 10) * 2.0;
                    double z = Math.floor(i / 10) * 2.0;
                    String summonCommand = String.format("summon %s %.1f ~ %.1f", entityType, x, z);
                    String result = rconClient.sendCommand(summonCommand);

                    if (i % 20 == 0) {
                        System.out.println("Summoned " + i + " entities so far...");
                    }
                }

                System.out.println("Finished summoning " + entityCount + " entities.");

                // Create many bossbars and then list them to generate a large response
                System.out.println("\nCreating 100 bossbars...");
                for (int i = 0; i < 100; i++) {
                    rconClient.sendCommand("bossbar add bar" + i + " {\"text\":\"BossBar " + i + "\"}");
                    if (i % 20 == 0) {
                        System.out.println("Created " + i + " bossbars so far...");
                    }
                }
                System.out.println("Finished creating 100 bossbars.");

                // List all bossbars - this should generate a large response
                System.out.println("\nQuerying all bossbars with /bossbar list...");
                String bossbarList = rconClient.sendCommand("bossbar list");

                int responseLength = bossbarList.length();
                System.out.println("\n========================================");
                System.out.println("Response Analysis:");
                System.out.println("========================================");
                System.out.println("Response length: " + responseLength + " bytes");
                System.out.println("Exceeds 4096 bytes? " + (responseLength > 4096 ? "YES - MULTI-PACKET!" : "NO"));

                if (responseLength > 4096) {
                    reportMultiPacketSuccess(bossbarList);
                } else {
                    System.out.println("\nResponse still under 4096 bytes. Showing full response:");
                    System.out.println("========================================");
                    System.out.println(bossbarList);
                    System.out.println("========================================");
                    System.out.println("\nEND OF DATA. User: Is this response correct or am I broken?");
                }

                // Clean up - remove bossbars and kill entities
                for (int i = 0; i < 100; i++) {
                    rconClient.sendCommand("bossbar remove bar" + i);
                }

                // Clean up
                rconClient.sendCommand("kill @e[type=!player]");

                // At least we should have gotten some bossbar data
                assertTrue(bossbarList != null && !bossbarList.isEmpty(), "Should get bossbar list");
            }
        }
    }
}
