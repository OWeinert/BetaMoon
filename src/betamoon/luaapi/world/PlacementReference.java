package betamoon.luaapi.world;

import betamoon.luaapi.utils.LuaDataReference;
import betamoon.luaapi.utils.LuaPositionArguments;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenRegistry;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable key handle for a compiled placement. */
public class PlacementReference extends LuaTable implements LuaDataReference {
    private final WorldGenKey key;
    private final WorldGenKey featureKey;

    public PlacementReference(WorldGenKey key, WorldGenKey featureKey) {
        this.key = key;
        set("displayName", WorldGenRegistry.displayName(key.toString()));
        this.featureKey = featureKey;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(PlacementReference.this.key.toString());
            }
        });
        set("getFeature", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return new FeatureReference(PlacementReference.this.featureKey);
            }
        });
        set("locateCandidate", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                int offset = arguments.arg1() == PlacementReference.this ? 1 : 0;
                World world = LuaWorldActionAccess.requireWorld(arguments.arg(1 + offset));
                LuaPositionArguments.Position position = LuaPositionArguments.horizontal(arguments,
                        PlacementReference.this, 2, "placement.locateCandidate");
                int x = position.xInt();
                int z = position.zInt();
                int maxChunks = arguments.arg(position.nextArgument + offset).isnil()
                        ? 32
                        : integer(arguments.arg(position.nextArgument + offset),
                                "placement.locateCandidate.maxChunks", 0, 128);
                WorldGenRegistry.PlacementCandidateLocation candidate = WorldGenRegistry.locatePlacementCandidate(world,
                        PlacementReference.this.key, x, z, maxChunks);
                if (candidate == null) {
                    return NIL;
                }
                LuaTable result = new LuaTable();
                result.set("x", candidate.x);
                result.set("z", candidate.z);
                result.set("chunkX", candidate.chunkX);
                result.set("chunkZ", candidate.chunkZ);
                result.set("loaded", valueOf(candidate.loaded));
                return result;
            }
        });
    }

    public WorldGenKey key() {
        return key;
    }

    private static int integer(LuaValue value, String path, int min, int max) {
        if (!value.isint()) {
            throw new LuaError(path + ": expected an integer");
        }
        int result = value.toint();
        if (result < min || result > max) {
            throw new LuaError(path + ": expected " + min + ".." + max);
        }
        return result;
    }

    @Override
    public Object declarationValue() {
        return key.toString();
    }

}
