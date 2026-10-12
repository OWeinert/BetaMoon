package betamoon.worldgen;

import java.util.Collections;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;

/** Verifies typed identities, stable seeds, validation, and atomic publication. */
public final class WorldGenFoundationTest {
    private WorldGenFoundationTest() {
    }

    public static void main(String[] arguments) {
        require(Block.stone != null, "Vanilla blocks are initialized");
        verifyKeys();
        verifySeeds();
        verifyPublication();
        System.out.println("World-generation foundation checks passed.");
    }

    private static void verifyKeys() {
        WorldGenKey shortKey = WorldGenKey.parse("example:copper", WorldGenKind.PLACEMENT);
        WorldGenKey typedKey = WorldGenKey.parse("example:placement/copper", WorldGenKind.PLACEMENT);
        require(shortKey.equals(typedKey), "Short keys normalize to their registry kind");
        expectFailure(new Runnable() {
            @Override
            public void run() {
                WorldGenKey.parse("example:tree/copper", WorldGenKind.PLACEMENT);
            }
        }, "Mismatched typed keys are rejected");
    }

    private static void verifySeeds() {
        WorldGenKey copper = WorldGenKey.parse("example:copper", WorldGenKind.PLACEMENT);
        long first = SeedMixer.generationSeed(12345L, "minecraft:overworld", "after_vanilla_population", -3, 8,
                copper, 17L);
        long repeated = SeedMixer.generationSeed(12345L, "minecraft:overworld", "after_vanilla_population", -3, 8,
                copper, 17L);
        long otherChunk = SeedMixer.generationSeed(12345L, "minecraft:overworld", "after_vanilla_population", 8,
                -3, copper, 17L);
        require(first == repeated, "Seed vectors are repeatable");
        require(first != otherChunk, "Chunk coordinates are ordered seed inputs");
        require(first == -2885606938322811712L, "Stable seed vector changed unexpectedly: " + first);
    }

    private static void verifyPublication() {
        WorldGenRegistry.clear();
        try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication("first.lua", "First")) {
            WorldGenRegistry.addOreGen("example:copper", Block.oreIron.blockID, 4, 8, 3, 40,
                    GenerationDimension.OVERWORLD, Integer.valueOf(Block.stone.blockID), null, 23L);
            batch.publish();
        }
        require(WorldGenRegistry.snapshot().size() == 1, "Published owner snapshot is visible");
        require("example:placement/copper".equals(WorldGenRegistry.snapshot().get(0).key),
                "Published description contains the normalized key");
        require("after_vanilla_population".equals(WorldGenRegistry.snapshot().get(0).stage),
                "Actual population hook is reported honestly");

        expectFailure(new Runnable() {
            @Override
            public void run() {
                try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication("first.lua",
                        "First")) {
                    WorldGenRegistry.addOreGen("example:duplicate", Block.oreGold.blockID, 1, 4, 2, 20,
                            GenerationDimension.OVERWORLD, null, null, 0L);
                    WorldGenRegistry.addOreGen("example:duplicate", Block.oreDiamond.blockID, 1, 4, 2, 20,
                            GenerationDimension.OVERWORLD, null, null, 0L);
                    batch.publish();
                }
            }
        }, "Duplicate batch keys fail publication");
        require(WorldGenRegistry.snapshot().size() == 1
                && "example:placement/copper".equals(WorldGenRegistry.snapshot().get(0).key),
                "Failed publication preserves the active snapshot");

        expectFailure(new Runnable() {
            @Override
            public void run() {
                try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication("invalid.lua",
                        "Invalid")) {
                    WorldGenRegistry.addOreGen("invalid:high", Block.oreGold.blockID, 1, 4, 0, 128,
                            GenerationDimension.OVERWORLD, null, null, 0L);
                }
            }
        }, "Out-of-world heights are rejected");

        WorldGenRegistry.retainOwners(Collections.singleton("missing.lua"));
        require(WorldGenRegistry.snapshot().isEmpty(), "Removed owners are pruned atomically");
        WorldGenRegistry.clear();
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (LuaError | IllegalArgumentException expected) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
