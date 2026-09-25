package betamoon.luaapi.world;

import betamoon.worldgen.BiomeRegistration;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs declarative biome generation. */
public final class BiomeGenApi {
    private BiomeGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable biomes = new LuaTable();
        biomes.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                BiomeDeclaration declaration = new BiomeDeclaration(argument(args, biomes, 1));
                int decoratorCount = decoratorCount(declaration.decorators);
                WorldGenKey surface = declaration.surfaceRule.isnil() ? null
                        : SurfaceGenApi.key(declaration.surfaceRule, "Biome.surfaceRule");
                WorldGenKey key = BiomeRegistration.register(declaration, surface, decoratorCount);
                FeaturePlacementApi.addBiomeDecorators(key, declaration.decorators);
                return new BiomeReference(key);
            }
        });
        biomes.set("get", lookup(biomes, false));
        biomes.set("getRequired", lookup(biomes, true));
        worldgen.set("biomes", biomes);
    }

    static WorldGenKey key(LuaValue value, String path) {
        if (value instanceof BiomeReference) {
            return ((BiomeReference) value).key();
        }
        if (!value.isstring()) {
            throw new LuaError(path + ": expected a biome reference or key");
        }
        try {
            return WorldGenKey.parse(value.tojstring(), WorldGenKind.BIOME);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static int decoratorCount(LuaValue decorators) {
        if (decorators.isnil()) {
            return 0;
        }
        FeaturePlacementApi.table(decorators, "Biome.decorators");
        int count = decorators.length();
        if (count > 64) {
            throw new LuaError("Biome.decorators: expected at most 64 placements");
        }
        return count;
    }

    private static VarArgFunction lookup(final LuaTable receiver, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                WorldGenKey key = key(argument(arguments, receiver, 1), "Biome.get");
                if (!BiomeGenRegistry.hasBiome(key)) {
                    if (required) {
                        throw new LuaError("Biome is not registered: " + key);
                    }
                    return NIL;
                }
                return new BiomeReference(key);
            }
        };
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }
}
