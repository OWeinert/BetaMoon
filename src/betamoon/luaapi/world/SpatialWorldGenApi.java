package betamoon.luaapi.world;

import betamoon.debug.DebugExports;
import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionRuntime;
import betamoon.luaapi.utils.LuaDataReference;
import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.runtime.RuntimeEnvironment;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.FieldTrace;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.terrain.GenerationDiagnosticExport;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import betamoon.worldgen.terrain.WorldGeneratorManifest;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Structural spatial declarations and detached world-scoped read access. */
public final class SpatialWorldGenApi {
    private SpatialWorldGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        LuaTable spatial = new LuaTable();
        spatial.set("noises", registry(WorldGenKind.NOISE));
        spatial.set("fields", registry(WorldGenKind.FIELD));
        LuaTable anchors = registry(WorldGenKind.ANCHOR);
        anchors.set("getOriginalSpawn", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                World world = LuaWorldActionAccess.requireWorld(argument(arguments, anchors, 1));
                DimensionRuntime.WorldBinding dimension = DimensionRuntime.binding(world);
                if (dimension != null && dimension.save != null && dimension.save.metadata.hasAnchor()) {
                    LuaTable result = new LuaTable();
                    result.set("x", dimension.save.metadata.anchorX());
                    result.set("y", dimension.save.metadata.anchorY());
                    result.set("z", dimension.save.metadata.anchorZ());
                    result.set("dimension", world.worldProvider.worldType);
                    result.set("dimensionKey", DimensionIdentity.key(world).toString());
                    result.set("source", "original_spawn");
                    return result;
                }
                WorldGeneratorManifest manifest = WorldGenerationRuntime.manifest(world);
                if (manifest == null || manifest.originalSpawn == null) {
                    return varargsOf(new LuaValue[]{NIL, valueOf("anchor_unavailable")});
                }
                LuaTable result = new LuaTable();
                result.set("x", manifest.originalSpawn.x);
                result.set("y", manifest.originalSpawn.y);
                result.set("z", manifest.originalSpawn.z);
                result.set("dimension", manifest.dimension);
                result.set("dimensionKey", DimensionIdentity.key(world).toString());
                result.set("source", "original_spawn");
                return result;
            }
        });
        spatial.set("anchors", anchors);
        worldgen.set("spatial", spatial);
    }

    private static LuaTable registry(WorldGenKind kind) {
        LuaTable registry = new LuaTable();
        registry.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                try {
                    String key = SpatialRegistry.add(kind, LuaDataSnapshot.object(argument(arguments, registry, 1),
                            "worldgen.spatial." + kind.getPath(), 30000000));
                    return new Reference(kind, key);
                } catch (IllegalArgumentException exception) {
                    throw new LuaError(exception.getMessage());
                }
            }
        });
        for (String operation : new String[]{"get", "getRequired"}) {
            registry.set(operation, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    try {
                        String key = WorldGenKey.parse(argument(arguments, registry, 1).checkjstring(), kind)
                                .toString();
                        if (!SpatialRegistry.exists(kind, key)) {
                            if (operation.equals("getRequired")) {
                                throw new LuaError("Unknown spatial " + kind.getPath() + ": " + key);
                            }
                            return NIL;
                        }
                        return new Reference(kind, key);
                    } catch (IllegalArgumentException exception) {
                        throw new LuaError(exception.getMessage());
                    }
                }
            });
        }
        return registry;
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }

    private static final class Reference extends LuaTable implements LuaDataReference {
        private final String key;

        private Reference(WorldGenKind kind, String key) {
            this.key = key;
            set("key", key);
            set("getKey", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    return valueOf(key);
                }
            });
            if (kind == WorldGenKind.FIELD) {
                set("sample", query(false));
                set("inspect", query(true));
                set("exportSlice", new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        World world = LuaWorldActionAccess.requireWorld(argument(args, Reference.this, 1));
                        if (world.worldProvider.worldType != 0 || RuntimeEnvironment.isDedicatedServer()) {
                            return varargsOf(new LuaValue[]{NIL, valueOf("dimension")});
                        }
                        try {
                            Map<String, Object> options = argument(args, Reference.this, 2).isnil()
                                    ? Collections.<String, Object>emptyMap()
                                    : LuaDataSnapshot.object(argument(args, Reference.this, 2), "field.exportSlice",
                                            30000000);
                            String root = DebugExports.getDebugDirPath();
                            if (root.isEmpty()) {
                                return varargsOf(new LuaValue[]{NIL, valueOf("debug_directory_unavailable")});
                            }
                            ScalarField field = WorldGenerationRuntime.spatial(world).field(key);
                            return valueOf(GenerationDiagnosticExport.field(Paths.get(root), field,
                                    WorldGenerationRuntime.fieldContext(world), options));
                        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                            throw new LuaError(exception.getMessage());
                        }
                    }
                });
            }
        }

        private VarArgFunction query(boolean inspect) {
            return new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    World world = LuaWorldActionAccess.requireWorld(argument(arguments, Reference.this, 1));
                    if (world.worldProvider.worldType != 0) {
                        return varargsOf(new LuaValue[]{NIL, valueOf("dimension")});
                    }
                    ScalarField field;
                    SpatialRegistry.Snapshot snapshot = WorldGenerationRuntime.spatial(world);
                    try {
                        field = snapshot.field(key);
                    } catch (IllegalArgumentException exception) {
                        return varargsOf(new LuaValue[]{NIL, valueOf("definition_unavailable")});
                    }
                    int offset = arguments.arg1() == Reference.this ? 1 : 0;
                    boolean three = arguments.narg() - offset == 4;
                    if (arguments.narg() - offset != 3 && !three) {
                        throw new LuaError("field:sample expects world,x,z or world,x,y,z");
                    }
                    double x = coordinate(argument(arguments, Reference.this, 2), "field.x");
                    double y = three ? coordinate(argument(arguments, Reference.this, 3), "field.y") : 0;
                    double z = coordinate(argument(arguments, Reference.this, three ? 4 : 3), "field.z");
                    if ((field.dependencies & 2) != 0 && !three) {
                        return varargsOf(new LuaValue[]{NIL, valueOf("requires_y")});
                    }
                    FieldContext context = WorldGenerationRuntime.fieldContext(world);
                    if ((field.dependencies & 16) != 0 && !context.hasOriginalSpawn) {
                        return varargsOf(new LuaValue[]{NIL, valueOf("anchor_unavailable")});
                    }
                    FieldTrace trace = inspect ? new FieldTrace() : null;
                    if (trace != null) {
                        context = context.withTrace(trace);
                    }
                    LuaTable result = new LuaTable();
                    try {
                        result.set("value", field.sample(context, x, y, z));
                    } catch (IllegalStateException exception) {
                        throw new LuaError(exception.getMessage());
                    }
                    result.set("signature", snapshot.signature(key, WorldGenKind.FIELD));
                    if (inspect) {
                        result.set("min", field.min);
                        result.set("max", field.max);
                        result.set("dependencies", field.dependencies);
                        result.set("nodes", field.nodeCount);
                        result.set("sampleWork", (double) field.sampleWork);
                        result.set("trace", LuaDetachedData.copy(trace.samples()));
                        result.set("traceTruncated", valueOf(trace.truncated()));
                    }
                    return result;
                }
            };
        }

        @Override
        public Object declarationValue() {
            return key;
        }
    }

    private static double coordinate(LuaValue value, String path) {
        double number = LuaDeclarationValues.number(value, path);
        if (Math.abs(number) > 30000000) {
            throw new LuaError(path + ": coordinate outside +/-30000000");
        }
        return number;
    }
}
