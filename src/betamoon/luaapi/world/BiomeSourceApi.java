package betamoon.luaapi.world;

import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import betamoon.worldgen.biome.BiomeSourceReader;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Installs fixed and vanilla-climate biome source declarations. */
public final class BiomeSourceApi {
    private BiomeSourceApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable sources = new LuaTable();
        sources.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue definition = argument(arguments, sources, 1);
                FeaturePlacementApi.table(definition, "worldgen.biomeSources:add");
                final Map<String, Object> declaration = LuaDataSnapshot.object(definition, "BiomeSource");
                String key = required(definition, "key").checkjstring();
                WorldGenKey typed;
                try {
                    typed = BiomeGenRegistry.registerSource(key, new BiomeGenRegistry.SourceFactory() {
                        @Override
                        public BiomeSourceDefinition create(WorldGenKey typedKey, String resourceOwner, String owner,
                                String source) {
                            return BiomeSourceReader.read(typedKey, resourceOwner, owner, source, declaration,
                                    SpatialRegistry.pendingSnapshot(), true);
                        }
                    });
                } catch (IllegalArgumentException exception) {
                    throw new LuaError("BiomeSource: " + exception.getMessage());
                }
                return new BiomeSourceReference(typed);
            }
        });
        sources.set("get", lookup(sources, false));
        sources.set("getRequired", lookup(sources, true));
        worldgen.set("biomeSources", sources);
    }

    private static WorldGenKey key(LuaValue value, String path) {
        if (value instanceof BiomeSourceReference) {
            return ((BiomeSourceReference) value).key();
        }
        if (!value.isstring()) {
            throw new LuaError(path + ": expected a biome-source reference or key");
        }
        try {
            return WorldGenKey.parse(value.tojstring(), WorldGenKind.BIOME_SOURCE);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static VarArgFunction lookup(final LuaTable receiver, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                WorldGenKey key = key(argument(arguments, receiver, 1), "BiomeSource.get");
                if (!BiomeGenRegistry.hasSource(key)) {
                    if (required) {
                        throw new LuaError("Biome source is not registered: " + key);
                    }
                    return NIL;
                }
                return new BiomeSourceReference(key);
            }
        };
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }
}
