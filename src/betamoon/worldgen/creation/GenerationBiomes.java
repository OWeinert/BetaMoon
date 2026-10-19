package betamoon.worldgen.creation;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import net.minecraft.src.BiomeGenBase;

/**
 * Stable native and scripted biome identities, independent of display names.
 */
public final class GenerationBiomes {
    private static final Map<String, BiomeGenBase> NATIVE = Collections.unmodifiableMap(createNativeBiomes());
    private static final Map<BiomeGenBase, String> NATIVE_KEYS = nativeKeys();
    private GenerationBiomes() {
    }

    public static Map<String, BiomeGenBase> nativeBiomes() {
        return NATIVE;
    }

    private static Map<BiomeGenBase, String> nativeKeys() {
        Map<BiomeGenBase, String> result = new IdentityHashMap<BiomeGenBase, String>();
        for (Map.Entry<String, BiomeGenBase> entry : NATIVE.entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }
        return result;
    }

    private static Map<String, BiomeGenBase> createNativeBiomes() {
        Map<String, BiomeGenBase> result = new LinkedHashMap<String, BiomeGenBase>();
        String[] names = {"rainforest", "swampland", "seasonal_forest", "forest", "savanna", "shrubland", "taiga",
                "desert", "plains", "ice_desert", "tundra", "hell", "sky"};
        BiomeGenBase[] biomes = {BiomeGenBase.rainforest, BiomeGenBase.swampland, BiomeGenBase.seasonalForest,
                BiomeGenBase.forest, BiomeGenBase.savanna, BiomeGenBase.shrubland, BiomeGenBase.taiga,
                BiomeGenBase.desert, BiomeGenBase.plains, BiomeGenBase.iceDesert, BiomeGenBase.tundra,
                BiomeGenBase.hell, BiomeGenBase.sky};
        for (int i = 0; i < names.length; i++) {
            result.put("minecraft:biome/" + names[i], biomes[i]);
        }
        return result;
    }

    public static String key(BiomeGenBase biome) {
        WorldGenKey custom = BiomeGenRegistry.keyFor(biome);
        if (custom != null) {
            return custom.toString();
        }
        String nativeKey = NATIVE_KEYS.get(biome);
        if (nativeKey != null) {
            return nativeKey;
        }
        throw new IllegalStateException("World generation contains an unregistered biome");
    }

    public static BiomeGenBase resolve(String key) {
        if (key.startsWith("minecraft:biome/")) {
            return nativeBiomes().get(key);
        }
        return BiomeGenRegistry.biomeFor(WorldGenKey.parse(key, WorldGenKind.BIOME));
    }
}
