package betamoon.worldgen;

import betamoon.luaapi.world.BiomeDeclaration;
import betamoon.wrappers.BiomeGenWrapper;
import java.util.List;
import java.util.Map;

/**
 * Applies a parsed biome declaration and installs it in the runtime registry.
 */
public final class BiomeRegistration {
    private BiomeRegistration() {
    }

    public static BiomeGenWrapper register(BiomeDeclaration definition) {
        BiomeGenWrapper biome;
        if (definition.basedOn == null) {
            biome = new BiomeGenWrapper(definition.name);
            biome.applyDefaultSurface();
        } else {
            biome = new BiomeGenWrapper(definition.basedOn.biomeName);
            biome.applyDefaultsFrom(definition.basedOn);
            biome.applyName(definition.name);
        }
        if (definition.color != null) {
            biome.applyColor(definition.color);
        }
        if (definition.foliageColor != null) {
            biome.applyFoliageColor(definition.foliageColor);
        }
        if (definition.topBlock != null) {
            biome.applyTopBlock(definition.topBlock);
        }
        if (definition.fillerBlock != null) {
            biome.applyFillerBlock(definition.fillerBlock);
        }
        if (definition.treeMode != null) {
            biome.applyTreeMode(definition.treeMode);
        }
        if (definition.bigTreeChance != null) {
            biome.applyBigTreeChance(definition.bigTreeChance);
        }
        switch (definition.weather) {
            case SNOW:
                biome.applyRainEnabled(false);
                biome.applySnowEnabled(true);
                break;
            case RAIN:
                biome.applySnowEnabled(false);
                biome.applyRainEnabled(true);
                break;
            case DRY:
                biome.applyRainEnabled(false);
                break;
            default:
                break;
        }
        for (Map.Entry<BiomeSpawnGroup, List<BiomeDeclaration.Spawn>> group : definition.spawns.entrySet()) {
            biome.clearSpawns(group.getKey());
            for (BiomeDeclaration.Spawn spawn : group.getValue()) {
                biome.addSpawn(group.getKey(), spawn.entity, spawn.weight);
            }
        }
        BiomeGenRegistry.registerBiomeGenerator(biome, definition.temperature.min, definition.temperature.max,
                definition.humidity.min, definition.humidity.max);
        return biome;
    }
}
