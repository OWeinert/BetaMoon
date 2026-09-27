package betamoon.luaapi.world;

import betamoon.assets.AssetKey;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.GenerationDimension;
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
    public final AssetKey key;
    public final int blockId;
    public final int veinsPerChunk;
    public final int veinSize;
    public final int minY;
    public final int maxY;
    public final GenerationDimension dimension;
    public final Integer targetBlockId;
    private final BiomeGenBase[] allowedBiomes;

    public OreDeclaration(LuaValue definition) {
        if (!definition.istable()) {
            throw new LuaError("worldgen.ores:add expects a definition table.");
        }
        LuaValue keyValue = definition.get("key");
        try {
            key = keyValue.isnil() ? null : AssetKey.parse(keyValue.checkjstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError("OreGen key: " + error.getMessage());
        }
        LuaValue height = required(definition, "height");
        if (!height.istable()) {
            throw new LuaError("Ore height must be { min=..., max=... }.");
        }
        blockId = resolveBlockId(required(definition, "block"));
        veinsPerChunk = required(definition, "veinsPerChunk").checkint();
        veinSize = required(definition, "veinSize").checkint();
        minY = required(height, "min").checkint();
        maxY = required(height, "max").checkint();
        if (minY < 0 || maxY < 0 || minY > maxY) {
            throw new LuaError("OreGen: invalid ore Y range: " + minY + " to " + maxY);
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
    }

    public BiomeGenBase[] getAllowedBiomes() {
        return allowedBiomes == null ? null : allowedBiomes.clone();
    }

    public static String[] biomeNames(LuaValue value) {
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

    public static int resolveBlockId(LuaValue value) {
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
