package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;

/**
 * Pins declared surface rules with the profile; refreshes never replace its
 * generation rules.
 */
public final class ProfileSurfaceRules {
    private final Map<BiomeGenBase, SurfaceRuleSet> rules = new IdentityHashMap<BiomeGenBase, SurfaceRuleSet>();
    private final Map<BiomeGenBase, int[]> materials = new IdentityHashMap<BiomeGenBase, int[]>();

    public ProfileSurfaceRules() {
        for (BiomeGenBase biome : BiomeGenRegistry.copyClimateLookup()) {
            pinMaterials(biome);
        }
        for (BiomeDefinition definition : BiomeGenRegistry.definitions()) {
            if (definition.surface != null) {
                SurfaceRuleSet rule = BiomeGenRegistry.surfaceFor(definition.surface);
                if (rule == null) {
                    throw new IllegalArgumentException("Missing profile surface: " + definition.surface);
                }
                rules.put(definition.biome, rule);
                for (SurfaceRuleSet.Layer layer : rule.layers) {
                    ChunkBlockBuffer.validateState(layer.blockId, 0);
                }
                if (rule.underwaterBlock != null) {
                    ChunkBlockBuffer.validateState(rule.underwaterBlock, 0);
                }
            }
            pinMaterials(definition.biome);
        }
    }

    public ProfileSurfaceRules withCatalog(BiomeGenRegistry.WorldCatalog catalog) {
        return new ProfileSurfaceRules(this, catalog);
    }

    private ProfileSurfaceRules(ProfileSurfaceRules source, BiomeGenRegistry.WorldCatalog catalog) {
        rules.putAll(source.rules);
        materials.putAll(source.materials);
        for (BiomeDefinition definition : catalog.definitions()) {
            if (definition.surface != null) {
                SurfaceRuleSet surface = catalog.surface(definition.surface);
                if (surface == null) {
                    throw new IllegalArgumentException("Missing dimension surface: " + definition.surface);
                }
                rules.put(definition.biome, surface);
            }
            pinMaterials(definition.biome);
        }
    }

    private void pinMaterials(BiomeGenBase biome) {
        if (!materials.containsKey(biome)) {
            int top = biome.topBlock & 255;
            int filler = biome.fillerBlock & 255;
            ChunkBlockBuffer.validateState(top, 0);
            ChunkBlockBuffer.validateState(filler, 0);
            materials.put(biome, new int[]{top, filler});
        }
    }

    public void applyDeclared(long seed, int chunkX, int chunkZ, byte[] blocks, BiomeGenBase[] biomes) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                SurfaceRuleSet rule = rules.get(biomes[x * 16 + z]);
                if (rule != null) {
                    rule.apply(seed, chunkX, chunkZ, blocks, x, z);
                }
            }
        }
    }

    public void apply(GenerationContext context, ChunkBlockBuffer buffer) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BiomeGenBase biome = context.biome(x, z);
                SurfaceRuleSet rule = rules.get(biome);
                if (rule != null) {
                    buffer.applySurface(rule, context, x, z);
                    continue;
                }
                int[] material = materials.get(biome);
                int top = material == null ? biome.topBlock & 255 : material[0];
                int filler = material == null ? biome.fillerBlock & 255 : material[1];
                int remaining = 0;
                boolean underwater = false;
                for (int y = 127; y >= 0; y--) {
                    int block = buffer.blockId(x, y, z);
                    if (block == 0 || Block.blocksList[block].blockMaterial.getIsLiquid()) {
                        remaining = 0;
                        underwater = block != 0;
                    } else if (block == Block.stone.blockID) {
                        int replacement = remaining == 0 ? underwater ? filler : top : remaining < 4 ? filler : block;
                        if (replacement != block) {
                            buffer.set(x, y, z, replacement, 0);
                        }
                        remaining++;
                    } else {
                        remaining = 4;
                    }
                }
            }
        }
    }
}
