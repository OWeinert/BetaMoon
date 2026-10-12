package betamoon.luaapi.world;

import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.FeatureOptions;
import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenRegistry;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable key handle for a reusable compiled world feature. */
public final class FeatureReference extends LuaTable {
    private final WorldGenKey key;

    public FeatureReference(WorldGenKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(FeatureReference.this.key.toString());
            }
        });
        set("place", action(false));
        set("preview", action(true));
    }

    private VarArgFunction action(final boolean preview) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                int offset = arguments.arg1() == FeatureReference.this ? 1 : 0;
                World world = LuaWorldActionAccess.requireMutableWorld(arguments.arg(1 + offset));
                int x = coordinate(arguments.arg(2 + offset), "feature.place.x");
                int y = integer(arguments.arg(3 + offset), "feature.place.y", 0, 127);
                int z = coordinate(arguments.arg(4 + offset), "feature.place.z");
                LuaValue options = arguments.arg(5 + offset);
                long salt = options.istable() && !options.get("seed").isnil() ? options.get("seed").checklong() : 0L;
                FeatureOptions featureOptions = parseOptions(options, preview ? "feature.preview" : "feature.place");
                String dimension = world.worldProvider.worldType == -1 ? "minecraft:nether" : "minecraft:overworld";
                long seed = SeedMixer.generationSeed(world.getRandomSeed(), dimension, "direct", x >> 4, z >> 4,
                        FeatureReference.this.key, salt ^ (((long) x) << 32) ^ z ^ y);
                FeatureResult outcome = preview
                        ? WorldGenRegistry.previewFeature(FeatureReference.this.key, world,
                                new BlockPosition(x, y, z), seed, featureOptions)
                        : WorldGenRegistry.placeFeature(FeatureReference.this.key, world,
                                new BlockPosition(x, y, z), seed, featureOptions);
                return result(outcome);
            }
        };
    }

    public WorldGenKey key() {
        return key;
    }

    private static FeatureOptions parseOptions(LuaValue options, String path) {
        if (options.isnil()) {
            return FeatureOptions.DEFAULT;
        }
        if (!options.istable()) {
            throw new LuaError(path + ".options: expected a table");
        }
        String rotation = options.get("rotation").isnil() ? null : options.get("rotation").checkjstring();
        String mirror = options.get("mirror").isnil() ? null : options.get("mirror").checkjstring();
        return new FeatureOptions(rotation, mirror);
    }

    private static LuaTable result(FeatureResult result) {
        LuaTable value = new LuaTable();
        value.set("placed", valueOf(result.placed));
        value.set("reason", result.reason == null ? NIL : valueOf(result.reason));
        value.set("blocksChanged", result.blocksChanged);
        if (result.min != null && result.max != null) {
            LuaTable bounds = new LuaTable();
            bounds.set("min", position(result.min));
            bounds.set("max", position(result.max));
            value.set("bounds", bounds);
        }
        if (!result.details.isEmpty()) {
            LuaTable details = new LuaTable();
            for (java.util.Map.Entry<String, Object> entry : result.details.entrySet()) {
                Object detail = entry.getValue();
                if (detail instanceof Number) {
                    details.set(entry.getKey(), valueOf(((Number) detail).doubleValue()));
                } else if (detail instanceof Boolean) {
                    details.set(entry.getKey(), valueOf(((Boolean) detail).booleanValue()));
                } else {
                    details.set(entry.getKey(), valueOf(String.valueOf(detail)));
                }
            }
            value.set("details", details);
        }
        return value;
    }

    private static LuaTable position(BlockPosition position) {
        LuaTable value = new LuaTable();
        value.set("x", position.x);
        value.set("y", position.y);
        value.set("z", position.z);
        return value;
    }

    private static int coordinate(LuaValue value, String path) {
        return integer(value, path, -30000000, 30000000);
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
}
