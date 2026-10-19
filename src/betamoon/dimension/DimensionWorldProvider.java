package betamoon.dimension;

import betamoon.worldgen.terrain.BetaMoonChunkProvider;
import betamoon.worldgen.terrain.NativeGeneratorFamily;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.WorldProvider;

/**
 * Configurable provider identity; explicit environment consumers own gameplay
 * rules.
 */
public final class DimensionWorldProvider extends WorldProvider {
    public final DimensionGenerationBinding binding;
    private final int numericId;

    public DimensionWorldProvider(DimensionGenerationBinding binding, int numericId) {
        if (numericId < 2 || numericId == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid custom provider ID");
        }
        this.binding = binding;
        this.numericId = numericId;
    }

    @Override
    protected void registerWorldChunkManager() {
        worldType = numericId;
        hasNoSky = !binding.definition.environment.skylight;
        // Do not make water, beds, sky, weather and clocks share one overloaded flag.
        isNether = false;
        isHellWorld = false;
        worldChunkMgr = NativeGeneratorFamily.climate(worldObj, binding.nativeBase);
    }

    @Override
    protected void generateLightBrightnessTable() {
        float ambient = binding.definition.environment.ambientLight;
        for (int level = 0; level <= 15; level++) {
            float darkness = 1.0F - (float) level / 15.0F;
            lightBrightnessTable[level] = (1.0F - darkness) / (darkness * 3.0F + 1.0F) * (1.0F - ambient) + ambient;
        }
    }

    @Override
    public IChunkProvider getChunkProvider() {
        IChunkProvider nativeProvider = NativeGeneratorFamily.provider(worldObj, binding.nativeBase);
        return binding.runtimeProfile == null
                ? nativeProvider
                : new BetaMoonChunkProvider(worldObj, nativeProvider, binding.runtimeProfile);
    }

    @Override
    public float calculateCelestialAngle(long time, float partialTick) {
        DimensionEnvironment environment = binding.definition.environment;
        return environment.fixedCelestial
                ? environment.celestialAngle
                : super.calculateCelestialAngle(time, partialTick);
    }

    @Override
    public float getCloudHeight() {
        return binding.definition.environment.cloudHeight;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        // Initialization is performed by the bounded arrival/bootstrap service.
        return false;
    }

    @Override
    public boolean canRespawnHere() {
        // The checked respawn adapter resolves the separate declaration policy.
        return true;
    }
}
