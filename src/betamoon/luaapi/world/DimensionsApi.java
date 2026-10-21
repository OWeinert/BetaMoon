package betamoon.luaapi.world;

import betamoon.dimension.DimensionDefinition;
import betamoon.dimension.DimensionCapabilities;
import betamoon.dimension.DimensionGenerationBinding;
import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionKey;
import betamoon.dimension.DimensionQueries;
import betamoon.dimension.DimensionRegistry;
import betamoon.luaapi.utils.LuaDataReference;
import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luaapi.utils.LuaPositionArguments;
import betamoon.worldgen.terrain.GeneratorPreview;
import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Declaration and detached inspection. World activation is exclusively owned by
 * teleportation.
 */
public final class DimensionsApi {
    private DimensionsApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable registry = new LuaTable();
        module.set("dimensions", registry);
        registry.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    DimensionCapabilities.requireDeclaration();
                    return reference(DimensionRegistry
                            .add(LuaDataSnapshot.object(argument(args, registry, 1), "dimension", Double.MAX_VALUE)));
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw new LuaError(exception.getMessage());
                }
            }
        });
        for (String method : new String[]{"get", "getRequired"}) {
            registry.set(method, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    DimensionKey key = key(argument(args, registry, 1));
                    if (!key.isNative() && DimensionRegistry.get(key) == null) {
                        if (method.equals("getRequired")) {
                            throw new LuaError("Unknown dimension: " + key);
                        }
                        return NIL;
                    }
                    return reference(key);
                }
            });
        }
        registry.set("list", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                TreeSet<DimensionKey> keys = new TreeSet<DimensionKey>(DimensionRegistry.snapshot().keySet());
                keys.add(DimensionKey.OVERWORLD);
                keys.add(DimensionKey.NETHER);
                LuaTable result = new LuaTable();
                for (DimensionKey key : keys) {
                    result.set(result.length() + 1, LuaDetachedData.copy(DimensionQueries.describe(key)));
                }
                return result;
            }
        });
    }

    public static DimensionKey key(LuaValue value) {
        try {
            return DimensionKey
                    .parse(value instanceof Reference ? ((Reference) value).key.toString() : value.checkjstring());
        } catch (IllegalArgumentException exception) {
            throw new LuaError(exception.getMessage());
        }
    }

    public static LuaTable reference(DimensionKey key) {
        return reference(key, null);
    }

    public static LuaTable reference(DimensionKey key, DimensionDefinition pinned) {
        return new Reference(key, pinned);
    }

    public static double coordinateScale(LuaValue value) {
        if (value instanceof Reference && ((Reference) value).pinned != null) {
            return ((Reference) value).pinned.coordinateScale;
        }
        return DimensionIdentity.coordinateScale(key(value));
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static final class Reference extends LuaTable implements LuaDataReference {
        private final DimensionKey key;
        private final DimensionDefinition pinned;

        private Reference(DimensionKey key, DimensionDefinition pinned) {
            this.key = key;
            this.pinned = pinned;
            set("key", key.toString());
            for (String method : new String[]{"getKey", "describe", "getState", "previewChunk", "sampleHeightOutput"}) {
                set(method, new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        if (method.equals("getKey")) {
                            return valueOf(key.toString());
                        }
                        if (method.equals("describe")) {
                            Map<String, Object> description = DimensionQueries.describe(key, pinned);
                            return description == null
                                    ? failure("definition_unavailable")
                                    : LuaDetachedData.copy(description);
                        }
                        World world = LuaWorldActionAccess.requireWorld(argument(args, Reference.this, 1));
                        if (method.equals("getState")) {
                            return LuaDetachedData.copy(DimensionQueries.state(world, key));
                        }
                        try {
                            DimensionGenerationBinding binding = DimensionQueries.session(world).availableBinding(key);
                            if (binding == null || binding.profile == null) {
                                return failure("native_preview_unavailable");
                            }
                            if (method.equals("previewChunk")) {
                                LuaPositionArguments.Position position = LuaPositionArguments.horizontal(args,
                                        Reference.this, 2, "dimension.previewChunk", -1875000, 1874999);
                                int x = position.xInt();
                                int z = position.zInt();
                                return LuaDetachedData.copy(GeneratorPreview.chunk(binding.profile,
                                        DimensionQueries.previewContext(world, key), x, z));
                            }
                            String output = argument(args, Reference.this, 2).checkjstring();
                            LuaPositionArguments.Position position = LuaPositionArguments.horizontal(args,
                                    Reference.this, 3, "dimension.sampleHeightOutput", -30000000, 29999999);
                            int x = position.xInt();
                            int z = position.zInt();
                            if (binding.profile.settings == null
                                    || !binding.profile.settings.heightOutputs.containsKey(output)) {
                                return failure("unknown_output");
                            }
                            return valueOf(binding.profile.settings.heightOutputs.get(output)
                                    .sample(DimensionQueries.previewContext(world, key), x, 0, z));
                        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                            if (exception.getMessage() != null
                                    && exception.getMessage().contains("Original spawn anchor is unavailable")) {
                                return failure("anchor_unavailable");
                            }
                            return failure(exception.getMessage());
                        }
                    }
                });
            }
        }

        @Override
        public Object declarationValue() {
            return key.toString();
        }
    }

    private static Varargs failure(String reason) {
        return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(reason)});
    }
}
