package betamoon.worldgen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.BiomeGenBase;

/**
 * Registry for Lua-defined biomes and lookup table customization.
 *
 * <p>The vanilla biome system uses a 64x64 lookup table keyed by
 * temperature/humidity. Lua adds biomes by specifying a range on that table
 * and we patch the lookup table after scripts load.</p>
 */
public final class BiomeGenRegistry {
    private static final String[] FIELD_BIOME_LOOKUP_TABLE = new String[] { "biomeLookupTable", "x" };
    private static final List ENTRIES = new ArrayList();

    /**
     * Immutable registration entry describing where a biome should appear in the lookup table.
     */
    private static final class BiomeGenEntry {
        private final BiomeGenBase biome;
        private final double minTemperature;
        private final double maxTemperature;
        private final double minHumidity;
        private final double maxHumidity;

        private BiomeGenEntry(BiomeGenBase biome, double minTemperature, double maxTemperature,
            double minHumidity, double maxHumidity) {
            this.biome = biome;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.minHumidity = minHumidity;
            this.maxHumidity = maxHumidity;
        }
    }

    private BiomeGenRegistry() {
    }

    /** Removes Lua biome overlays and restores the vanilla lookup table. */
    public static synchronized void clear() {
        ENTRIES.clear();
        getBiomeLookupTable();
    }

    /**
     * Registers a new biome generator entry.
     *
     * @param biome biome instance created by Lua
     * @param minTemperature minimum temperature (0..1)
     * @param maxTemperature maximum temperature (0..1)
     * @param minHumidity minimum humidity (0..1)
     * @param maxHumidity maximum humidity (0..1)
     */
    public static synchronized void registerBiomeGenerator(BiomeGenBase biome, double minTemperature, double maxTemperature,
        double minHumidity, double maxHumidity) {
        if (biome == null) {
            return;
        }
        ENTRIES.add(new BiomeGenEntry(biome, minTemperature, maxTemperature, minHumidity, maxHumidity));
    }

    /**
     * Applies registered biome generators to the biome lookup table.
     *
     * <p>This should run after all Lua mods are loaded so custom biomes are
     * available during world generation.</p>
     */
    public static void applyBiomeGenerators() {
        if (ENTRIES.isEmpty()) {
            return;
        }
        // Refresh the table once, then layer custom biomes on top.
        BiomeGenBase[] table = getBiomeLookupTable();
        if (table == null || table.length == 0) {
            return;
        }
        // Apply in registration order so later entries can override earlier ones.
        for (int i = 0; i < ENTRIES.size(); i++) {
            BiomeGenEntry entry = (BiomeGenEntry) ENTRIES.get(i);
            applyBiomeEntry(table, entry);
        }
    }

    private static void applyBiomeEntry(BiomeGenBase[] table, BiomeGenEntry entry) {
        // The vanilla table is indexed by temperature (x) and humidity (y).
        for (int tempIndex = 0; tempIndex < 64; tempIndex++) {
            double temperature = tempIndex / 63.0;
            if (temperature < entry.minTemperature || temperature > entry.maxTemperature) {
                continue;
            }
            for (int humidityIndex = 0; humidityIndex < 64; humidityIndex++) {
                double humidity = humidityIndex / 63.0;
                if (humidity < entry.minHumidity || humidity > entry.maxHumidity) {
                    continue;
                }
                int index = tempIndex + humidityIndex * 64;
                table[index] = entry.biome;
            }
        }
    }

    /**
     * Accesses the private biome lookup table and refreshes it first.
     *
     * <p>Reflection is required because the table is private in BiomeGenBase.</p>
     */
    private static BiomeGenBase[] getBiomeLookupTable() {
        try {
            // Rebuild the vanilla lookup table before patching it.
            BiomeGenBase.generateBiomeLookup();
            Field field = resolveField(BiomeGenBase.class, FIELD_BIOME_LOOKUP_TABLE);
            return (BiomeGenBase[]) field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field resolveField(Class owner, String[] fieldNames) throws Exception {
        Exception last = null;
        for (int i = 0; i < fieldNames.length; i++) {
            try {
                Field field = owner.getDeclaredField(fieldNames[i]);
                field.setAccessible(true);
                return field;
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new NoSuchFieldException("No matching field.");
    }
}
