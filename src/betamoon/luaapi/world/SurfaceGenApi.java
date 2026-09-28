package betamoon.luaapi.world;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Installs compiled raw-buffer surface rule declarations. */
public final class SurfaceGenApi {
    private SurfaceGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable surfaces = new LuaTable();
        surfaces.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue definition = argument(arguments, surfaces, 1);
                FeaturePlacementApi.table(definition, "worldgen.surfaces:add");
                String key = required(definition, "key").checkjstring();
                BlockSet replace = definition.get("replace").isnil()
                        ? new BlockSet(Block.stone.blockID, Block.dirt.blockID, Block.grass.blockID,
                                Block.sand.blockID, Block.gravel.blockID)
                        : FeaturePlacementApi.blocks(definition.get("replace"), "Surface.replace");
                LuaValue layerValues = required(definition, "layers");
                FeaturePlacementApi.table(layerValues, "Surface.layers");
                if (layerValues.length() < 1 || layerValues.length() > 16) {
                    throw new LuaError("Surface.layers: expected 1..16 layers");
                }
                List<SurfaceRuleSet.Layer> layers = new ArrayList<SurfaceRuleSet.Layer>();
                int maximumDepth = 0;
                for (int index = 1; index <= layerValues.length(); index++) {
                    LuaValue layer = layerValues.get(index);
                    FeaturePlacementApi.table(layer, "Surface.layers[" + index + "]");
                    int block = FeaturePlacementApi.blockId(required(layer, "block"),
                            "Surface.layers[" + index + "].block");
                    IntRange depth = FeaturePlacementApi.range(required(layer, "depth"),
                            "Surface.layers[" + index + "].depth", 1, 32);
                    maximumDepth += depth.max;
                    layers.add(new SurfaceRuleSet.Layer(block, depth));
                }
                if (maximumDepth > 64) {
                    throw new LuaError("Surface.layers: combined maximum depth may not exceed 64");
                }
                Integer underwater = definition.get("underwaterBlock").isnil() ? null
                        : Integer.valueOf(FeaturePlacementApi.blockId(definition.get("underwaterBlock"),
                                "Surface.underwaterBlock"));
                int seaLevel = FeaturePlacementApi.optionalInteger(definition.get("seaLevel"), 64,
                        "Surface.seaLevel", 0, 127);
                WorldGenKey typed = BiomeGenRegistry.registerSurface(key, new BiomeGenRegistry.SurfaceFactory() {
                    @Override
                    public SurfaceRuleSet create(WorldGenKey typedKey, String resourceOwner, String owner,
                            String source) {
                        return new SurfaceRuleSet(typedKey, resourceOwner, owner, source, replace, layers,
                                underwater, seaLevel);
                    }
                });
                return new SurfaceReference(typed);
            }
        });
        surfaces.set("get", lookup(surfaces, false));
        surfaces.set("getRequired", lookup(surfaces, true));
        worldgen.set("surfaces", surfaces);
    }

    static WorldGenKey key(LuaValue value, String path) {
        if (value instanceof SurfaceReference) {
            return ((SurfaceReference) value).key();
        }
        if (!value.isstring()) {
            throw new LuaError(path + ": expected a surface reference or key");
        }
        try {
            return WorldGenKey.parse(value.tojstring(), WorldGenKind.SURFACE);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static VarArgFunction lookup(final LuaTable receiver, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                WorldGenKey key = key(argument(arguments, receiver, 1), "Surface.get");
                if (!BiomeGenRegistry.hasSurface(key)) {
                    if (required) {
                        throw new LuaError("Surface is not registered: " + key);
                    }
                    return NIL;
                }
                return new SurfaceReference(key);
            }
        };
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }
}
