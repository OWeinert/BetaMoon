package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.ScalarField;
import java.util.Random;
import net.minecraft.src.NoiseGeneratorOctaves;

/**
 * Version 1 of Beta's 5x17x5 overworld density basis, detached from world/chunk
 * mutation.
 */
public strictfp final class BetaDensitySampler {
    private final NoiseGeneratorOctaves lower;
    private final NoiseGeneratorOctaves upper;
    private final NoiseGeneratorOctaves blend;
    private final NoiseGeneratorOctaves scale;
    private final NoiseGeneratorOctaves depth;
    private final ThreadLocal<Workspace> workspaces = ThreadLocal.withInitial(Workspace::new);

    public BetaDensitySampler(long seed) {
        Random random = new Random(seed);
        lower = new NoiseGeneratorOctaves(random, 16);
        upper = new NoiseGeneratorOctaves(random, 16);
        blend = new NoiseGeneratorOctaves(random, 8);
        new NoiseGeneratorOctaves(random, 4);
        new NoiseGeneratorOctaves(random, 4);
        scale = new NoiseGeneratorOctaves(random, 10);
        depth = new NoiseGeneratorOctaves(random, 16);
    }

    public double[] sample(GenerationContext context, double baseHeight, double heightScale, double detailScale) {
        return sample(context, baseHeight, heightScale, detailScale, null, null);
    }

    public double[] sample(GenerationContext context, double baseHeight, double heightScale, double detailScale,
            ScalarField continental, ScalarField erosion) {
        Workspace shared = workspaces.get();
        Workspace workspace = shared.inUse ? new Workspace() : shared;
        workspace.inUse = true;
        try {
            return sample(context, baseHeight, heightScale, detailScale, continental, erosion, workspace);
        } finally {
            workspace.inUse = false;
        }
    }

    private double[] sample(GenerationContext context, double baseHeight, double heightScale, double detailScale,
            ScalarField continental, ScalarField erosion, Workspace workspace) {
        int originX = context.chunkX * 4;
        int originZ = context.chunkZ * 4;
        double[] scales = scale.func_4109_a(workspace.scales, originX, originZ, 5, 5, 1.121, 1.121, 0.5);
        double[] depths = depth.func_4109_a(workspace.depths, originX, originZ, 5, 5, 200, 200, 0.5);
        double frequency = 684.412;
        double[] blends = blend.generateNoiseOctaves(workspace.blends, originX, 0, originZ, 5, 17, 5, frequency / 80,
                frequency / 160, frequency / 80);
        double[] lows = lower.generateNoiseOctaves(workspace.lows, originX, 0, originZ, 5, 17, 5, frequency, frequency,
                frequency);
        double[] highs = upper.generateNoiseOctaves(workspace.highs, originX, 0, originZ, 5, 17, 5, frequency,
                frequency, frequency);
        double[] output = new double[425];
        int index = 0;
        int column = 0;
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                int climateX = context.worldX(x * 3 + 1);
                int climateZ = context.worldZ(z * 3 + 1);
                double temperature = context.fields.environment.biomeValue("temperature", climateX, climateZ);
                double humidity = context.fields.environment.biomeValue("humidity", climateX, climateZ) * temperature;
                double climateShape = 1 - humidity;
                climateShape *= climateShape;
                climateShape *= climateShape;
                climateShape = 1 - climateShape;
                double terrainScale = (scales[column] + 256) / 512;
                if (erosion != null) {
                    terrainScale = (erosion.sample(context.fields, climateX, 0, climateZ) + 1) / 2;
                }
                terrainScale *= climateShape;
                if (terrainScale > 1) {
                    terrainScale = 1;
                }
                double terrainDepth = depths[column] / 8000;
                if (continental != null) {
                    terrainDepth = continental.sample(context.fields, climateX, 0, climateZ);
                }
                if (terrainDepth < 0) {
                    terrainDepth = -terrainDepth * 0.3;
                }
                terrainDepth = terrainDepth * 3 - 2;
                if (terrainDepth < 0) {
                    terrainDepth /= 2;
                    if (terrainDepth < -1) {
                        terrainDepth = -1;
                    }
                    terrainDepth /= 1.4;
                    terrainDepth /= 2;
                    terrainScale = 0;
                } else {
                    if (terrainDepth > 1) {
                        terrainDepth = 1;
                    }
                    terrainDepth /= 8;
                }
                if (terrainScale < 0) {
                    terrainScale = 0;
                }
                terrainScale += 0.5;
                terrainDepth = terrainDepth * 17 / 16;
                double center = 17.0 / 2 + terrainDepth * 4;
                center += (baseHeight - 68) / 8;
                column++;
                for (int y = 0; y < 17; y++) {
                    double falloff = (y - center) * 12 / terrainScale / heightScale;
                    if (falloff < 0) {
                        falloff *= 4;
                    }
                    double low = lows[index] / 512;
                    double high = highs[index] / 512;
                    double factor = (blends[index] / 10 + 1) / 2;
                    double density = factor < 0 ? low : factor > 1 ? high : low + (high - low) * factor;
                    density = density * detailScale - falloff;
                    if (y > 13) {
                        double fade = (double) ((float) (y - 13) / 3.0f);
                        density = density * (1 - fade) + -10 * fade;
                    }
                    output[index++] = density;
                }
            }
        }
        return output;
    }

    private static final class Workspace {
        private final double[] scales = new double[25];
        private final double[] depths = new double[25];
        private final double[] blends = new double[425];
        private final double[] lows = new double[425];
        private final double[] highs = new double[425];
        private boolean inUse;
    }
}
