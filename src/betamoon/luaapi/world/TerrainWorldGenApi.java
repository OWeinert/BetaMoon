package betamoon.luaapi.world;

import betamoon.debug.DebugExports;
import betamoon.luaapi.utils.LuaDataReference;
import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luaapi.utils.LuaPositionArguments;
import betamoon.runtime.RuntimeEnvironment;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.terrain.GenerationDiagnosticExport;
import betamoon.worldgen.terrain.GenerationHookAvailability;
import betamoon.worldgen.terrain.GeneratorPreview;
import betamoon.worldgen.terrain.HeightEnvelope;
import betamoon.worldgen.terrain.ProfileMapTerrainSource;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.TerrainSource;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Terrain declarations and non-loading previews; parser/runtime remain separate
 * from Lua.
 */
public final class TerrainWorldGenApi {
    private TerrainWorldGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        LuaTable terrain = new LuaTable();
        terrain.set("sources", registry(WorldGenKind.TERRAIN_SOURCE));
        terrain.set("profiles", registry(WorldGenKind.TERRAIN));
        worldgen.set("terrain", terrain);
        worldgen.set("materialRules", registry(WorldGenKind.MATERIAL));
        worldgen.set("populationPolicies", registry(WorldGenKind.POPULATION));
        worldgen.set("generatorProfiles", registry(WorldGenKind.GENERATOR));
        worldgen.set("carvers", registry(WorldGenKind.CARVER));
    }

    private static LuaTable registry(WorldGenKind kind) {
        LuaTable registry = new LuaTable();
        registry.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                try {
                    if (kind == WorldGenKind.GENERATOR) {
                        GenerationHookAvailability.require(true);
                    }
                    return new Reference(kind, TerrainRegistry.add(kind, LuaDataSnapshot
                            .object(argument(arguments, registry, 1), "worldgen." + kind.getPath(), 30000000)));
                } catch (IllegalArgumentException | IllegalStateException exception) {
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
                        if (!TerrainRegistry.exists(kind, key)) {
                            if (operation.equals("getRequired")) {
                                throw new LuaError("Unknown " + kind.getPath() + ": " + key);
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
        if (kind == WorldGenKind.GENERATOR) {
            registry.set("getSelected", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    World world = LuaWorldActionAccess.requireWorld(argument(arguments, registry, 1));
                    WorldGeneratorProfile profile = WorldGenerationRuntime.profile(world);
                    return profile == null ? NIL : new Reference(kind, profile.key, profile);
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
        private final WorldGeneratorProfile pinned;

        private Reference(WorldGenKind kind, String key) {
            this(kind, key, null);
        }

        private Reference(WorldGenKind kind, String key, WorldGeneratorProfile pinned) {
            this.key = key;
            this.pinned = pinned;
            set("key", key);
            set("getKey", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    return valueOf(key);
                }
            });
            if (kind == WorldGenKind.GENERATOR) {
                set("describe", new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs arguments) {
                        WorldGeneratorProfile profile = pinned == null
                                ? TerrainRegistry.snapshot().generator(key)
                                : pinned;
                        if (profile == null) {
                            return varargsOf(new LuaValue[]{NIL, valueOf("definition_unavailable")});
                        }
                        return LuaDetachedData.copy(GeneratorPreview.describe(profile));
                    }
                });
                set("previewChunk", new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs arguments) {
                        World world = LuaWorldActionAccess.requireWorld(argument(arguments, Reference.this, 1));
                        LuaPositionArguments.Position position = LuaPositionArguments.horizontal(arguments,
                                Reference.this, 2, "generator.previewChunk", -1875000, 1874999);
                        int x = position.xInt();
                        int z = position.zInt();
                        WorldGeneratorProfile profile = selected(world);
                        if (profile == null) {
                            return varargsOf(new LuaValue[]{NIL, valueOf("definition_unavailable")});
                        }
                        try {
                            return LuaDetachedData.copy(
                                    GeneratorPreview.chunk(profile, WorldGenerationRuntime.fieldContext(world), x, z));
                        } catch (IllegalArgumentException exception) {
                            return varargsOf(new LuaValue[]{NIL, valueOf(exception.getMessage())});
                        } catch (IllegalStateException exception) {
                            throw new LuaError(exception.getMessage());
                        }
                    }
                });
                set("exportChunk", new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        Varargs preview = Reference.this.get("previewChunk").invoke(args);
                        if (preview.arg1().isnil()) {
                            return preview;
                        }
                        if (RuntimeEnvironment.isDedicatedServer()) {
                            return varargsOf(new LuaValue[]{NIL, valueOf("no_client")});
                        }
                        String root = DebugExports.getDebugDirPath();
                        if (root.isEmpty()) {
                            return varargsOf(new LuaValue[]{NIL, valueOf("debug_directory_unavailable")});
                        }
                        try {
                            // The preview is detached; this copy retains only its bounded report values.
                            Map<String, Object> report = LuaDataSnapshot.object(preview.arg1(), "generator.preview",
                                    Double.MAX_VALUE);
                            return valueOf(GenerationDiagnosticExport.chunk(Paths.get(root), report));
                        } catch (IOException | IllegalArgumentException exception) {
                            throw new LuaError(exception.getMessage());
                        }
                    }
                });
                set("sampleHeightOutput", new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs arguments) {
                        World world = LuaWorldActionAccess.requireWorld(argument(arguments, Reference.this, 1));
                        String output = argument(arguments, Reference.this, 2).checkjstring();
                        LuaPositionArguments.Position position = LuaPositionArguments.horizontal(arguments,
                                Reference.this, 3, "generator.sampleHeightOutput");
                        int x = position.xInt();
                        int z = position.zInt();
                        WorldGeneratorProfile profile = selected(world);
                        if (profile == null || profile.settings == null
                                || !profile.settings.heightOutputs.containsKey(output)) {
                            return varargsOf(new LuaValue[]{NIL, valueOf("unknown_output")});
                        }
                        return valueOf(profile.settings.heightOutputs.get(output)
                                .sample(WorldGenerationRuntime.fieldContext(world), x, 0, z));
                    }
                });
            }
            if (kind == WorldGenKind.TERRAIN_SOURCE || kind == WorldGenKind.TERRAIN) {
                set("sample", new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs arguments) {
                        World world = LuaWorldActionAccess.requireWorld(argument(arguments, Reference.this, 1));
                        LuaPositionArguments.Position position = LuaPositionArguments.horizontal(arguments,
                                Reference.this, 2, "terrain.sample");
                        int x = position.xInt();
                        int z = position.zInt();
                        TerrainSource source = kind == WorldGenKind.TERRAIN_SOURCE
                                ? WorldGenerationRuntime.terrainDefinitions(world).source(key)
                                : null;
                        if (source != null && source.isNative()) {
                            return varargsOf(new LuaValue[]{NIL, valueOf("native_preview_unavailable")});
                        }
                        try {
                            LuaTable result = new LuaTable();
                            if (source == null) {
                                HeightEnvelope profile = WorldGenerationRuntime.terrainDefinitions(world).profile(key);
                                result.set("height", profile.height(WorldGenerationRuntime.fieldContext(world), x, z));
                                result.set("profileKey", key);
                                double slope = profile.slope(WorldGenerationRuntime.fieldContext(world), x, z);
                                result.set("slope", slope);
                                result.set("slopeLimitExceeded", valueOf(slope > profile.surfaceSlopeLimit));
                                result.set("signature", profile.signature);
                            } else {
                                result.set("height", source.height(WorldGenerationRuntime.fieldContext(world), x, z));
                                result.set("sourceKey", key);
                                result.set("signature", source.signature());
                                if (source instanceof ProfileMapTerrainSource) {
                                    LuaTable choices = new LuaTable();
                                    for (Map.Entry<HeightEnvelope, Double> choice : ((ProfileMapTerrainSource) source)
                                            .weights(WorldGenerationRuntime.fieldContext(world), x, z).entrySet()) {
                                        LuaTable entry = new LuaTable();
                                        entry.set("key", choice.getKey() == null ? "vanilla" : choice.getKey().key);
                                        entry.set("weight", choice.getValue());
                                        choices.set(choices.length() + 1, entry);
                                    }
                                    result.set("profiles", choices);
                                }
                            }
                            return result;
                        } catch (IllegalStateException exception) {
                            throw new LuaError(exception.getMessage());
                        }
                    }
                });
            }
        }

        private WorldGeneratorProfile selected(World world) {
            WorldGeneratorProfile selected = WorldGenerationRuntime.profile(world);
            return selected != null && selected.key.equals(key) ? selected : TerrainRegistry.snapshot().generator(key);
        }

        @Override
        public Object declarationValue() {
            return key;
        }
    }
}
