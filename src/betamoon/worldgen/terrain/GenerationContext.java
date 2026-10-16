package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.spatial.FieldContext;
import java.util.Arrays;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.WorldChunkManager;

/**
 * Detached inputs for one raw chunk, with no world or ability to generate
 * neighboring chunks.
 */
public final class GenerationContext {
    public final FieldContext fields;
    public final int chunkX;
    public final int chunkZ;
    private final BiomeGenBase[] biomes;
    public final double[] preliminarySurface = new double[256];
    private byte[] materialInput;
    private final int[] materialSurface = new int[256];
    private CompiledTerrainSource.HeightSampler envelope;
    private byte[] solidDepth;
    private byte[] liquidDepth;
    private final double[] slopes = new double[256];
    private final boolean[] sampledSlopes = new boolean[256];
    public RawGenerationNeighborhood rawNeighborhood;
    public TerrainSource terrainSource;

    public GenerationContext(FieldContext fields, int chunkX, int chunkZ, BiomeGenBase[] biomes) {
        if (biomes.length < 256 || chunkX < -1875000 || chunkX > 1874999 || chunkZ < -1875000 || chunkZ > 1874999) {
            throw new IllegalArgumentException("Invalid raw generation chunk coordinates or biome samples");
        }
        this.fields = fields;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.biomes = biomes.clone();
        Arrays.fill(preliminarySurface, -1);
    }

    public BiomeGenBase biome(int x, int z) {
        return biomes[x * 16 + z];
    }

    public static GenerationContext preview(FieldContext fields, int chunkX, int chunkZ) {
        BiomeGenBase[] biomes;
        if (fields.environment instanceof WorldChunkManager) {
            biomes = ((WorldChunkManager) fields.environment).loadBlockGeneratorData(null, chunkX * 16, chunkZ * 16, 16,
                    16);
        } else {
            biomes = new BiomeGenBase[256];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    biomes[x * 16 + z] = fields.environment == null
                            ? BiomeGenBase.plains
                            : fields.environment.biome(chunkX * 16 + x, chunkZ * 16 + z);
                }
            }
        }
        return new GenerationContext(fields, chunkX, chunkZ, biomes);
    }

    public void captureMaterialInput(ChunkBlockBuffer buffer, CompiledTerrainSource.HeightSampler envelope) {
        materialInput = buffer.copyBlocks();
        solidDepth = null;
        liquidDepth = null;
        Arrays.fill(sampledSlopes, false);
        TerrainHeightmaps materialHeights = new TerrainHeightmaps(buffer);
        this.envelope = envelope;
        // Resolve all maps before material writes begin; conditions share one occupancy
        // snapshot.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                materialSurface[z * 16 + x] = materialHeights.worldSurface(x, z);
            }
        }
    }

    public int inputBlock(int x, int y, int z) {
        if (materialInput == null) {
            throw new IllegalStateException("Material occupancy context is unavailable");
        }
        return y < 0 || y > 127 ? 0 : materialInput[(x << 11) | (z << 7) | y] & 255;
    }

    public int inputSurface(int x, int z) {
        if (materialInput == null) {
            throw new IllegalStateException("Material occupancy context is unavailable");
        }
        return materialSurface[z * 16 + x];
    }

    public double slope(int x, int z) {
        if (envelope == null) {
            throw new IllegalStateException("Slope material conditions require a preliminary height envelope");
        }
        int column = z * 16 + x;
        if (sampledSlopes[column]) {
            return slopes[column];
        }
        int wx = worldX(x);
        int wz = worldZ(z);
        double dx = (envelope.height(fields, wx + 1, wz) - envelope.height(fields, wx - 1, wz)) / 2;
        double dz = (envelope.height(fields, wx, wz + 1) - envelope.height(fields, wx, wz - 1)) / 2;
        double slope = StrictMath.sqrt(dx * dx + dz * dz);
        sampledSlopes[column] = true;
        slopes[column] = slope;
        return slope;
    }

    public int depthAbove(int x, int y, int z, boolean fluid) {
        if (solidDepth == null) {
            solidDepth = new byte[ChunkBlockBuffer.CELL_COUNT];
            liquidDepth = new byte[ChunkBlockBuffer.CELL_COUNT];
            for (int cx = 0; cx < 16; cx++) {
                for (int cz = 0; cz < 16; cz++) {
                    int solids = 0;
                    int liquids = 0;
                    for (int cy = 127; cy >= 0; cy--) {
                        int index = (cx << 11) | (cz << 7) | cy;
                        solidDepth[index] = (byte) solids;
                        liquidDepth[index] = (byte) liquids;
                        Block block = Block.blocksList[materialInput[index] & 255];
                        boolean liquid = block != null && block.blockMaterial.getIsLiquid();
                        solids = block != null && !liquid ? solids + 1 : 0;
                        liquids = liquid ? liquids + 1 : 0;
                    }
                }
            }
        }
        return (fluid ? liquidDepth : solidDepth)[(x << 11) | (z << 7) | y] & 255;
    }

    public boolean biomeMatches(int x, int z, String selector) {
        if (fields.environment != null) {
            return fields.environment.biomeMatches(selector, worldX(x), worldZ(z));
        }
        BiomeGenBase biome = biome(x, z);
        BiomeDefinition definition = BiomeGenRegistry.definitionFor(biome);
        return selector.startsWith("#")
                ? definition != null && definition.tags.contains(selector.substring(1))
                : definition != null && definition.key.toString().equals(selector) || biome.biomeName.equals(selector);
    }

    public int worldX(int x) {
        return chunkX * 16 + x;
    }

    public int worldZ(int z) {
        return chunkZ * 16 + z;
    }
}
