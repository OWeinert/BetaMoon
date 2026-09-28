package betamoon.luaapi.world;

import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.GenerationDimension;
import betamoon.worldgen.WorldGenLimits;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/**
 * Parsed ore configuration, independent of registration and Lua builder
 * lifetimes.
 */
public final class OreDeclaration {
    public final String key;
    public final int blockId;
    public final int veinsPerChunk;
    public final int veinSize;
    public final int minY;
    public final int maxY;
    public final GenerationDimension dimension;
    public final Integer targetBlockId;
    public final long salt;
    private final BiomeGenBase[] allowedBiomes;

    public OreDeclaration(LuaValue definition) {
        if (!definition.istable()) {
            throw new LuaError("worldgen.ores:add expects a definition table.");
        }
        key = definition.get("key").isnil() ? null : definition.get("key").checkjstring().trim();
        LuaValue height = required(definition, "height");
        if (!height.istable()) {
            throw new LuaError("Ore height must be { min=..., max=... }.");
        }
        blockId = resolveBlockId(required(definition, "block"));
        veinsPerChunk = required(definition, "veinsPerChunk").checkint();
        veinSize = required(definition, "veinSize").checkint();
        minY = required(height, "min").checkint();
        maxY = required(height, "max").checkint();
        if (minY < WorldGenLimits.MIN_HEIGHT || maxY > WorldGenLimits.MAX_HEIGHT || minY > maxY) {
            throw new LuaError("OreGen: height must satisfy 0 <= min <= max <= 127.");
        }
        if (veinsPerChunk < 0 || veinsPerChunk > WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK) {
            throw new LuaError("OreGen: veinsPerChunk must be between 0 and "
                    + WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK + ".");
        }
        if (veinSize < 1 || veinSize > WorldGenLimits.MAX_ORE_VEIN_SIZE) {
            throw new LuaError("OreGen: veinSize must be between 1 and " + WorldGenLimits.MAX_ORE_VEIN_SIZE + ".");
        }
        dimension = GenerationDimension.parse(definition.get("dimension").optjstring("overworld"));
        LuaValue replace = definition.get("replace");
        if (replace.isnil()) {
            targetBlockId = null;
        } else {
            int replacementId = resolveBlockId(replace);
            if (Block.blocksList[replacementId] == null) {
                throw new LuaError("OreGen: unknown spawn block id: " + replacementId);
            }
            targetBlockId = Integer.valueOf(replacementId);
        }
        allowedBiomes = WorldGenRegistry.resolveBiomes(biomeNames(definition.get("biomes")));
        salt = definition.get("salt").isnil() ? 0L : definition.get("salt").checklong();
    }

    public BiomeGenBase[] getAllowedBiomes() {
        return allowedBiomes == null ? null : allowedBiomes.clone();
    }

    private static String[] biomeNames(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        if (!value.istable()) {
            throw new LuaError("OreGen: biomes must be provided as a table of names.");
        }
        if (value.length() == 0) {
            return null;
        }
        String[] names = new String[value.length()];
        for (int i = 0; i < names.length; i++) {
            names[i] = value.get(i + 1).checkjstring().trim();
        }
        return names;
    }

    private static int resolveBlockId(LuaValue value) {
        if (value.isnumber()) {
            int id = value.toint();
            if (id < 0 || id >= Block.blocksList.length) {
                throw new LuaError("OreGen: block id out of range: " + id);
            }
            return id;
        }
        if (value.istable()) {
            LuaValue idValue = value.get("id");
            if (!idValue.isnil()) {
                return resolveBlockId(idValue);
            }
            LuaValue getter = value.get("getId");
            if (!getter.isnil()) {
                return resolveBlockId(getter.call(value));
            }
        }
        throw new LuaError("OreGen: block must be a block id or block handle.");
    }
}
