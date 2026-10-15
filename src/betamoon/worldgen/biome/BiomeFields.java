package betamoon.worldgen.biome;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.BiomeGenBase;
import org.luaj.vm2.LuaError;

/**
 * Mapped/obfuscated native biome fields, resolved lazily and cached per field.
 */
enum BiomeFields {
    SNOW("enableSnow", "v"), RAIN("enableRain", "w"), MONSTERS("spawnableMonsterList",
            "s"), CREATURES("spawnableCreatureList", "t"), WATER_CREATURES("spawnableWaterCreatureList", "u");

    private final String mappedName;
    private final String obfuscatedName;
    private Field resolved;

    BiomeFields(String mappedName, String obfuscatedName) {
        this.mappedName = mappedName;
        this.obfuscatedName = obfuscatedName;
    }

    boolean readFlag(BiomeGenBase biome) {
        try {
            return resolve().getBoolean(biome);
        } catch (Exception exception) {
            throw failure("read biome flag", exception);
        }
    }

    void writeFlag(BiomeGenBase biome, boolean value) {
        try {
            resolve().setBoolean(biome, value);
        } catch (Exception exception) {
            throw failure("set biome flag", exception);
        }
    }

    List<?> readSpawns(BiomeGenBase biome) {
        try {
            List<?> entries = (List<?>) resolve().get(biome);
            return entries == null ? Collections.emptyList() : entries;
        } catch (Exception exception) {
            throw failure("read biome spawn list", exception);
        }
    }

    private synchronized Field resolve() throws ReflectiveOperationException {
        if (resolved == null) {
            Field field;
            try {
                field = BiomeGenBase.class.getDeclaredField(mappedName);
            } catch (NoSuchFieldException exception) {
                field = BiomeGenBase.class.getDeclaredField(obfuscatedName);
            }
            field.setAccessible(true);
            resolved = field;
        }
        return resolved;
    }

    private LuaError failure(String operation, Exception exception) {
        return new LuaError("Biome: unable to " + operation + " '" + mappedName + "': " + exception.getMessage());
    }
}
