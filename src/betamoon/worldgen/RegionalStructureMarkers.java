package betamoon.worldgen;

import betamoon.assets.AssetKey;
import betamoon.entity.EntitySpawner;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureTransform;
import java.util.Map;
import net.minecraft.src.World;

/** Optional regional post-block pass for authored entity markers. */
final class RegionalStructureMarkers {
    private RegionalStructureMarkers() {
    }

    static int apply(World world, RegionalStructureDefinition definition, RegionalStructurePlan plan,
            int chunkX, int chunkZ) {
        if (!definition.entityMarkers) {
            return 0;
        }
        int failures = 0;
        for (RegionalStructurePlan.Piece piece : plan.pieces) {
            if (!piece.bounds.intersectsChunk(chunkX, chunkZ)) {
                continue;
            }
            StructureFeature feature = feature(piece.featureKey);
            if (feature == null) {
                failures++;
                continue;
            }
            for (StructureFeature.PositionedMarker marker : feature.markers(piece.origin, piece.transform,
                    piece.conformOffsets, piece.seed)) {
                if (!marker.name.equals("entity") || Math.floorDiv(marker.position.x, 16) != chunkX
                        || Math.floorDiv(marker.position.z, 16) != chunkZ) {
                    continue;
                }
                try {
                    if (!spawnEntity(world, marker, piece.transform)) {
                        failures++;
                    }
                } catch (RuntimeException error) {
                    failures++;
                }
            }
        }
        return failures;
    }

    static void validate(StructureFeature feature, boolean entityMarkers) {
        if (!entityMarkers) {
            return;
        }
        StructureTransform identity = new StructureTransform(StructureTransform.Rotation.NONE,
                StructureTransform.Mirror.NONE);
        for (StructureFeature.PositionedMarker marker
                : feature.markers(new BlockPosition(0, 0, 0), identity)) {
            if (marker.name.equals("entity")) {
                entity(marker);
            }
        }
    }

    private static boolean spawnEntity(World world, StructureFeature.PositionedMarker marker,
            StructureTransform transform) {
        EntityMarker value = entity(marker);
        float yaw = transform.applyYaw(value.yaw);
        EntitySpawner.Result result = EntitySpawner.spawn(world, AssetKey.parse(value.type),
                marker.position.x + 0.5D, marker.position.y, marker.position.z + 0.5D, yaw, value.pitch, null,
                "structure");
        return result.entity != null;
    }

    private static EntityMarker entity(StructureFeature.PositionedMarker marker) {
        String type;
        float yaw = 0.0F;
        float pitch = 0.0F;
        if (marker.value instanceof String) {
            type = string(marker.value, "entity marker type");
        } else if (marker.value instanceof Map) {
            Map<?, ?> value = (Map<?, ?>) marker.value;
            type = string(value.get("type"), "entity marker type");
            yaw = (float) number(value.get("yaw"), 0.0D, -360.0D, 360.0D, "entity marker yaw");
            pitch = (float) number(value.get("pitch"), 0.0D, -90.0D, 90.0D, "entity marker pitch");
        } else {
            throw new IllegalArgumentException("entity marker value must be a type key or object");
        }
        AssetKey.parse(type);
        return new EntityMarker(type, yaw, pitch);
    }

    private static StructureFeature feature(WorldGenKey key) {
        FeatureDefinition definition = FeaturePlacementRegistry.findFeature(key);
        return definition != null && definition.feature instanceof StructureFeature
                ? (StructureFeature) definition.feature : null;
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException(path + " must be a non-empty string");
        }
        return (String) value;
    }

    private static double number(Object value, double fallback, double min, double max, String path) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(path + " must be a number");
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < min || number > max) {
            throw new IllegalArgumentException(path + " must be " + min + ".." + max);
        }
        return number;
    }

    private static final class EntityMarker {
        private final String type;
        private final float yaw;
        private final float pitch;

        private EntityMarker(String type, float yaw, float pitch) {
            this.type = type;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
