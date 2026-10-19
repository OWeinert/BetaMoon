package betamoon.dimension;

import betamoon.dimension.storage.DimensionMetadataStore;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.World;
import net.minecraft.src.WorldChunkManager;
import net.minecraft.src.WorldChunkManagerHell;

/**
 * Detached queries never allocate an ID, write metadata, generate chunks or
 * activate another world.
 */
public final class DimensionQueries {
    private DimensionQueries() {
    }

    public static Map<String, Object> describe(DimensionKey key) {
        return describe(key, null);
    }

    public static Map<String, Object> describe(DimensionKey key, DimensionDefinition pinned) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        DimensionDefinition definition = pinned == null ? DimensionRegistry.get(key) : pinned;
        if (definition != null) {
            result.putAll(definition.declaration);
            result.put("displayName", definition.displayName);
            result.put("coordinateScale", definition.coordinateScale);
            result.put("initialTime", Long.toString(definition.initialTime));
            result.put("spawn", definition.spawn);
        } else if (key.isNative()) {
            result.put("displayName", key.equals(DimensionKey.NETHER) ? "Nether" : "Overworld");
            result.put("coordinateScale", key.equals(DimensionKey.NETHER) ? 8 : 1);
        } else {
            return null;
        }
        result.put("key", key.toString());
        result.put("native", key.isNative());
        return result;
    }

    public static DimensionSession session(World world) throws IOException {
        DimensionRuntime.WorldBinding active = DimensionRuntime.binding(world);
        return active == null ? DimensionSession.get(world, DimensionWorldAccess.save(world)) : active.session;
    }

    public static Map<String, Object> state(World world, DimensionKey key) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", key.toString());
        result.put("state", "uninitialized");
        if (key.isNative()) {
            result.put("state", "ready");
            return result;
        }
        try {
            DimensionSession session = session(world);
            DimensionGenerationBinding binding = session.availableBinding(key);
            if (binding == null) {
                throw new IOException("dimension_unavailable");
            }
            Integer id = session.index.id(key);
            if (id != null) {
                DimensionMetadataStore metadata = new DimensionMetadataStore(session.paths, binding, id,
                        session.rootSeed);
                result.put("state", metadata.initialized() ? "ready" : "uninitialized");
                result.put("signature", metadata.signature);
            }
        } catch (IOException exception) {
            result.put("state", "unavailable");
            result.put("reason", exception.getMessage());
        }
        return result;
    }

    public static FieldContext previewContext(World world, DimensionKey key) throws IOException {
        if (DimensionIdentity.key(world).equals(key)) {
            return WorldGenerationRuntime.fieldContext(world);
        }
        DimensionSession session = session(world);
        DimensionGenerationBinding binding = session.availableBinding(key);
        if (binding == null) {
            throw new IOException("dimension_unavailable");
        }
        Integer id = session.index.id(key);
        DimensionMetadataStore metadata = id == null
                ? null
                : new DimensionMetadataStore(session.paths, binding, id, session.rootSeed);
        boolean anchor = metadata != null && metadata.hasAnchor();
        int anchorX = anchor ? metadata.anchorX() : 0;
        int anchorZ = anchor ? metadata.anchorZ() : 0;
        if (!anchor && "fixed".equals(binding.definition.spawn.get("type"))) {
            anchor = true;
            anchorX = (int) Math.floor(((Number) binding.definition.spawn.get("x")).doubleValue());
            anchorZ = (int) Math.floor(((Number) binding.definition.spawn.get("z")).doubleValue());
        }
        return detachedContext(binding, binding.definition.seed(session.rootSeed), id == null ? 2 : id, anchor, anchorX,
                anchorZ);
    }

    public static FieldContext detachedContext(DimensionGenerationBinding binding, long seed, int numericId,
            boolean anchor, int anchorX, int anchorZ) {
        WorldChunkManager climate = binding.nativeBase.equals("nether")
                ? new WorldChunkManagerHell(BiomeGenBase.hell, 1, 0)
                : binding.nativeBase.equals("sky")
                        ? new WorldChunkManagerHell(BiomeGenBase.sky, 0.5, 0)
                        : new DimensionPreviewClimate(seed);
        ProfileWorldChunkManager manager = new ProfileWorldChunkManager(climate,
                binding.profile == null ? null : binding.profile.biomeSource, null, binding.biomes);
        FieldContext context = new FieldContext(seed, numericId, binding.definition.key.toString(), anchor, anchorX,
                anchorZ, manager, binding.spatial);
        manager.setFieldContext(context);
        return context;
    }
}
