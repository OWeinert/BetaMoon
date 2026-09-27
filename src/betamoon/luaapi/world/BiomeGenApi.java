package betamoon.luaapi.world;

import betamoon.luaapi.resource.LuaResultList;
import betamoon.minecraft.MinecraftBuiltins;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.BiomeRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.BiomeGenBase;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs biome registration, lookup, queries, and safe definition layers. */
public final class BiomeGenApi {
    private static final Map<BiomeGenBase, BiomeReference> REFERENCES =
            new IdentityHashMap<BiomeGenBase, BiomeReference>();

    private BiomeGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable biomes = new LuaTable();
        biomes.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                BiomeGenRegistry.BiomeGenEntry entry = BiomeRegistration.register(
                        new BiomeDeclaration(argument(args, biomes)));
                return reference(entry.biome, entry);
            }
        });
        biomes.set("get", lookup(biomes, false));
        biomes.set("getRequired", lookup(biomes, true));
        biomes.set("find", query(biomes, 0));
        biomes.set("first", query(biomes, 1));
        biomes.set("one", query(biomes, 2));
        worldgen.set("biomes", biomes);
    }

    private static VarArgFunction lookup(final LuaTable registry, final boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String name = argument(args, registry).checkjstring();
                BiomeGenBase biome = find(name);
                if (biome == null && required) {
                    throw new LuaError("Biome was not found: " + name);
                }
                return biome == null ? NIL : reference(biome, BiomeGenRegistry.find(biome));
            }
        };
    }

    private static VarArgFunction query(final LuaTable registry, final int mode) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue criteria = argument(args, registry);
                if (criteria.isnil()) {
                    criteria = new LuaTable();
                }
                if (!criteria.istable()) {
                    throw new LuaError("Biome query must be a table.");
                }
                List<LuaValue> matches = new ArrayList<LuaValue>();
                for (BiomeReference reference : all()) {
                    if (reference.matches(criteria)) {
                        matches.add(reference);
                    }
                }
                if (mode == 1) {
                    return matches.isEmpty() ? NIL : matches.get(0);
                }
                if (mode == 2) {
                    if (matches.isEmpty()) {
                        return NIL;
                    }
                    if (matches.size() != 1) {
                        throw new LuaError("Expected exactly one biome, found " + matches.size() + ".");
                    }
                    return matches.get(0);
                }
                return new LuaResultList(matches, (reference, definition, index) ->
                        ((BiomeReference) reference).override(definition));
            }
        };
    }

    private static synchronized BiomeReference reference(BiomeGenBase biome,
            BiomeGenRegistry.BiomeGenEntry entry) {
        BiomeReference reference = REFERENCES.get(biome);
        if (reference == null) {
            reference = new BiomeReference(biome, entry);
            REFERENCES.put(biome, reference);
        }
        return reference;
    }

    private static BiomeGenBase find(String name) {
        for (BiomeGenRegistry.BiomeGenEntry entry : BiomeGenRegistry.entries()) {
            if (entry.biome.biomeName.equalsIgnoreCase(name)) {
                return entry.biome;
            }
        }
        return MinecraftBuiltins.resolveBiome(name);
    }

    private static List<BiomeReference> all() {
        Set<BiomeGenBase> biomes = new LinkedHashSet<BiomeGenBase>();
        for (String name : MinecraftBuiltins.biomes().keySet()) {
            BiomeGenBase biome = MinecraftBuiltins.resolveBiome(name);
            if (biome != null) {
                biomes.add(biome);
            }
        }
        for (BiomeGenRegistry.BiomeGenEntry entry : BiomeGenRegistry.entries()) {
            biomes.add(entry.biome);
        }
        List<BiomeReference> result = new ArrayList<BiomeReference>();
        for (BiomeGenBase biome : biomes) {
            result.add(reference(biome, BiomeGenRegistry.find(biome)));
        }
        Collections.sort(result, new Comparator<BiomeReference>() {
            public int compare(BiomeReference left, BiomeReference right) {
                return left.get("name").tojstring().compareToIgnoreCase(right.get("name").tojstring());
            }
        });
        return result;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver) {
        return args.arg(args.arg1() == receiver ? 2 : 1);
    }
}
