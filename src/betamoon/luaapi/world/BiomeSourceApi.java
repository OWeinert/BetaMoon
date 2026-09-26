package betamoon.luaapi.world;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                String key = required(definition, "key").checkjstring();
                String type = required(definition, "type").checkjstring().toLowerCase(Locale.ROOT);
                final WorldGenKey fixed;
                final List<BiomeSourceDefinition.ClimateEntry> entries = new ArrayList<BiomeSourceDefinition.ClimateEntry>();
                if (type.equals("fixed")) {
                    fixed = BiomeGenApi.key(required(definition, "biome"), "BiomeSource.biome");
                } else if (type.equals("vanilla_climate")) {
                    fixed = null;
                    LuaValue values = required(definition, "entries");
                    FeaturePlacementApi.table(values, "BiomeSource.entries");
                    if (values.length() < 1 || values.length() > 256) {
                        throw new LuaError("BiomeSource.entries: expected 1..256 entries");
                    }
                    for (int index = 1; index <= values.length(); index++) {
                        String path = "BiomeSource.entries[" + index + "]";
                        LuaValue entry = values.get(index);
                        FeaturePlacementApi.table(entry, path);
                        WorldGenKey biome = BiomeGenApi.key(required(entry, "biome"), path + ".biome");
                        double[] temperature = range(required(entry, "temperature"), path + ".temperature");
                        double[] humidity = range(required(entry, "humidity"), path + ".humidity");
                        int priority = entry.get("priority").optint(0);
                        entries.add(new BiomeSourceDefinition.ClimateEntry(biome, temperature[0], temperature[1],
                                humidity[0], humidity[1], priority));
                    }
                } else {
                    throw new LuaError("BiomeSource.type: expected 'fixed' or 'vanilla_climate'");
                }
                final boolean active = definition.get("active").optboolean(false);
                final int priority = definition.get("priority").optint(0);
                final String compiledType = type;
                WorldGenKey typed = BiomeGenRegistry.registerSource(key, new BiomeGenRegistry.SourceFactory() {
                    @Override
                    public BiomeSourceDefinition create(WorldGenKey typedKey, String resourceOwner, String owner,
                            String source) {
                        return new BiomeSourceDefinition(typedKey, resourceOwner, owner, source, compiledType, fixed,
                                entries, active, priority);
                    }
                });
                return new BiomeSourceReference(typed);
            }
        });
        sources.set("get", lookup(sources, false));
        sources.set("getRequired", lookup(sources, true));
        worldgen.set("biomeSources", sources);
    }

    private static double[] range(LuaValue value, String path) {
        FeaturePlacementApi.table(value, path);
        double min = (value.get("min").isnil() ? value.get(1) : value.get("min")).checkdouble();
        double max = (value.get("max").isnil() ? value.get(2) : value.get("max")).checkdouble();
        if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0.0D || max > 1.0D || min > max) {
            throw new LuaError(path + ": expected 0 <= min <= max <= 1");
        }
        return new double[] { min, max };
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
