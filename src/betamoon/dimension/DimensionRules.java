package betamoon.dimension;

import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/**
 * Per-consumer environment rules with literal native fallbacks when no custom
 * binding is installed.
 */
public final class DimensionRules {
    private DimensionRules() {
    }

    public static DimensionEnvironment environment(WorldProvider provider) {
        return provider instanceof DimensionWorldProvider
                ? ((DimensionWorldProvider) provider).binding.definition.environment
                : null;
    }

    public static boolean hideSky(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.isNether : !environment.sky;
    }

    public static boolean hideClouds(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.isNether : !environment.clouds;
    }

    public static boolean randomCompass(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.isNether : environment.compass.equals("random");
    }

    public static boolean randomClock(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.isNether : environment.clock.equals("random");
    }

    public static boolean evaporatesWater(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.isHellWorld : environment.waterEvaporates;
    }

    public static boolean fastLava(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.isHellWorld : environment.lavaFlow.equals("nether");
    }

    public static boolean weatherDisabled(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        if (environment != null && environment.weather.equals("disabled") && provider.worldObj != null) {
            prepareWeather(provider.worldObj);
        }
        return environment == null ? provider.hasNoSky : environment.weather.equals("disabled");
    }

    public static boolean sleepDenied(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.isNether : !environment.beds.equals("sleep");
    }

    public static boolean bedDoesNotExplode(WorldProvider provider) {
        DimensionEnvironment environment = environment(provider);
        return environment == null ? provider.canRespawnHere() : !environment.beds.equals("explode");
    }

    public static void prepareWeather(World world) {
        DimensionEnvironment environment = environment(world.worldProvider);
        if (environment != null && environment.weather.equals("disabled")) {
            world.getWorldInfo().setRaining(false);
            world.getWorldInfo().setThundering(false);
            world.getWorldInfo().setRainTime(0);
            world.getWorldInfo().setThunderTime(0);
        }
    }
}
