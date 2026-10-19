package betamoon.dimension;

import java.util.Random;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.NoiseGeneratorOctaves2;
import net.minecraft.src.WorldChunkManager;

/**
 * Detached native Beta climate, seeded without constructing or opening a World.
 */
public final class DimensionPreviewClimate extends WorldChunkManager {
    private final NoiseGeneratorOctaves2 temperatures;
    private final NoiseGeneratorOctaves2 humidities;
    private final NoiseGeneratorOctaves2 variation;

    public DimensionPreviewClimate(long seed) {
        temperatures = new NoiseGeneratorOctaves2(new Random(seed * 9871L), 4);
        humidities = new NoiseGeneratorOctaves2(new Random(seed * 39811L), 4);
        variation = new NoiseGeneratorOctaves2(new Random(seed * 543321L), 2);
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] output, int x, int z, int width, int height) {
        if (width != height) {
            // Native Beta's rectangular climate method accidentally uses width twice.
            // Point sampling preserves its actual point contract without out-of-bounds
            // arrays.
            if (output == null || output.length < width * height) {
                output = new BiomeGenBase[width * height];
            }
            double[] resultTemperature = new double[width * height];
            double[] resultHumidity = new double[width * height];
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < height; dz++) {
                    int index = dx * height + dz;
                    output[index] = loadBlockGeneratorData(null, x + dx, z + dz, 1, 1)[0];
                    resultTemperature[index] = temperature[0];
                    resultHumidity[index] = humidity[0];
                }
            }
            temperature = resultTemperature;
            humidity = resultHumidity;
            return output;
        }
        int count = width * height;
        if (count < 1 || count > 65536) {
            throw new IllegalArgumentException("Climate preview area exceeds its budget");
        }
        if (output == null || output.length < count) {
            output = new BiomeGenBase[count];
        }
        temperature = temperatures.func_4112_a(temperature, x, z, width, height, (double) 0.025F, (double) 0.025F,
                0.25D);
        humidity = humidities.func_4112_a(humidity, x, z, width, height, (double) 0.05F, (double) 0.05F, 1.0D / 3.0D);
        field_4196_c = variation.func_4112_a(field_4196_c, x, z, width, height, 0.25D, 0.25D, 0.5882352941176471D);
        for (int index = 0; index < count; index++) {
            double noise = field_4196_c[index] * 1.1D + 0.5D;
            double t = (temperature[index] * 0.15D + 0.7D) * 0.99D + noise * 0.01D;
            double h = (humidity[index] * 0.15D + 0.5D) * 0.998D + noise * 0.002D;
            t = 1.0D - (1.0D - t) * (1.0D - t);
            temperature[index] = Math.max(0, Math.min(1, t));
            humidity[index] = Math.max(0, Math.min(1, h));
            output[index] = BiomeGenBase.getBiomeFromLookup(temperature[index], humidity[index]);
        }
        return output;
    }

    @Override
    public double[] getTemperatures(double[] output, int x, int z, int width, int height) {
        loadBlockGeneratorData(null, x, z, width, height);
        if (output == null || output.length < width * height) {
            output = new double[width * height];
        }
        System.arraycopy(temperature, 0, output, 0, width * height);
        return output;
    }

    @Override
    public double getTemperature(int x, int z) {
        double t = temperatures.func_4112_a(null, x, z, 1, 1, (double) 0.025F, (double) 0.025F, 0.5D)[0];
        double v = variation.func_4112_a(null, x, z, 1, 1, 0.25D, 0.25D, 0.5882352941176471D)[0] * 1.1D + 0.5D;
        t = (t * 0.15D + 0.7D) * 0.99D + v * 0.01D;
        return Math.max(0, Math.min(1, 1.0D - (1.0D - t) * (1.0D - t)));
    }
}
