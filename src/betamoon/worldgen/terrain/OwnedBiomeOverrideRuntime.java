package betamoon.worldgen.terrain;

import java.util.List;

/** Applies the same saved package order to an owned raw terrain pipeline. */
final class OwnedBiomeOverrideRuntime {
    private OwnedBiomeOverrideRuntime() {
    }

    static void replaceTerrain(GenerationContext context, ChunkBlockBuffer buffer,
            List<BiomeRegionOverride> overrides) {
        for (BiomeRegionOverride override : overrides) {
            if (override.terrain != null) {
                ChunkBlockBuffer replacement = new ChunkBlockBuffer();
                GenerationContext source = new GenerationContext(context.fields, context.chunkX, context.chunkZ,
                        context.copyBiomes());
                source.terrainSource = override.terrain;
                override.terrain.fill(source, replacement);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!selected(context, override, x, z)) {
                            continue;
                        }
                        for (int y = 0; y < 128; y++) {
                            buffer.set(x, y, z, replacement.blockId(x, y, z), replacement.metadata(x, y, z));
                        }
                        context.preliminarySurface[z * 16 + x] = source.preliminarySurface[z * 16 + x];
                    }
                }
            }
            BiomeOverrideOperations.applyMaterials(context, buffer, override);
        }
        context.rawNeighborhood = null;
    }

    static void applySurfaces(GenerationContext context, ChunkBlockBuffer buffer, byte[] before,
            byte[] beforeMetadata, List<BiomeRegionOverride> overrides) {
        for (BiomeRegionOverride override : overrides) {
            if (override.surfaceMode.equals("inherit")) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!selected(context, override, x, z)) {
                        continue;
                    }
                    int start = (x << 11) | (z << 7);
                    for (int y = 0; y < 128; y++) {
                        int cell = start | y;
                        int damage = beforeMetadata[cell >> 1] >> ((cell & 1) * 4) & 15;
                        buffer.setGenerated(cell, before[cell] & 255, damage);
                    }
                    if (override.surface != null) {
                        buffer.applySurface(override.surface, context, x, z);
                    }
                }
            }
        }
    }

    static void applyCarvers(GenerationContext context, ChunkBlockBuffer buffer, byte[] before,
            byte[] beforeMetadata, List<BiomeRegionOverride> overrides) {
        for (BiomeRegionOverride override : overrides) {
            if (override.carverMode.equals("inherit")) {
                continue;
            }
            if (override.carverMode.equals("replace") || override.carverMode.equals("none")) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!selected(context, override, x, z)) {
                            continue;
                        }
                        int start = (x << 11) | (z << 7);
                        for (int y = 0; y < 128; y++) {
                            int cell = start | y;
                            int damage = beforeMetadata[cell >> 1] >> ((cell & 1) * 4) & 15;
                            buffer.setGenerated(cell, before[cell] & 255, damage);
                        }
                    }
                }
            }
            if (!override.carvers.isEmpty()) {
                ChunkBlockBuffer carved = new ChunkBlockBuffer();
                carved.restore(buffer.copyBlocks(), buffer.copyMetadata());
                for (CompiledCarver carver : override.carvers) {
                    carver.apply(context, carved);
                }
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!selected(context, override, x, z)) {
                            continue;
                        }
                        for (int y = 0; y < 128; y++) {
                            buffer.set(x, y, z, carved.blockId(x, y, z), carved.metadata(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private static boolean selected(GenerationContext context, BiomeRegionOverride override, int x, int z) {
        return BiomeOverrideOperations.selected(context, override, x, z);
    }
}
