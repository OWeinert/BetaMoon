package betamoon.worldgen.terrain;

import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.spatial.FieldContext;
import net.minecraft.src.Block;

/** Shared deterministic column decisions and detached material edits. */
final class BiomeOverrideOperations {
    private BiomeOverrideOperations() {
    }

    static boolean selected(GenerationContext context, BiomeRegionOverride override, int x, int z) {
        return selected(context.fields, override, context.worldX(x), context.worldZ(z));
    }

    static boolean selected(FieldContext fields, BiomeRegionOverride override, int x, int z) {
        return selected(fields, override, override.influence(fields, x, z), x, z);
    }

    static boolean selected(FieldContext fields, BiomeRegionOverride override, double influence, int x, int z) {
        if (influence <= 0 || influence >= 1) {
            return influence >= 1;
        }
        long location = ((long) Math.floorDiv(x, 4) << 32) ^ Math.floorDiv(z, 4);
        long random = SeedMixer.derive(fields.seed ^ SeedMixer.hash(override.key), location);
        return (random >>> 11) * 0x1.0p-53 < influence;
    }

    static void applyMaterials(GenerationContext context, ChunkBlockBuffer buffer, BiomeRegionOverride override) {
        if (override.material == null) {
            return;
        }
        context.captureMaterialInput(buffer, null);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!selected(context, override, x, z)) {
                    continue;
                }
                for (int y = 0; y < 128; y++) {
                    int existing = buffer.blockId(x, y, z);
                    if (existing == 0 || existing == Block.bedrock.blockID
                            || override.materialMode.equals("add") && existing != Block.stone.blockID) {
                        continue;
                    }
                    TerrainBlockState state = override.material.sample(context, x, y, z);
                    if (state != null) {
                        context.writeGenerated(buffer, x, y, z, state);
                    }
                }
            }
        }
    }
}
