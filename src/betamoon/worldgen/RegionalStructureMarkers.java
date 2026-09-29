package betamoon.worldgen;

import betamoon.assets.AssetKey;
import betamoon.entity.EntitySpawner;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureTransform;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.src.IInventory;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

/** Optional post-block passes for authored entity and inventory loot markers. */
final class RegionalStructureMarkers {
    private RegionalStructureMarkers() {
    }

    static int apply(World world, RegionalStructureDefinition definition, RegionalStructurePlan plan,
            int chunkX, int chunkZ) {
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
                    piece.conformOffsets)) {
                if (Math.floorDiv(marker.position.x, 16) != chunkX
                        || Math.floorDiv(marker.position.z, 16) != chunkZ) {
                    continue;
                }
                try {
                    if (definition.entityMarkers && marker.name.equals("entity")) {
                        if (!spawnEntity(world, marker, piece.transform)) {
                            failures++;
                        }
                    } else if (definition.lootMarkers && marker.name.equals("loot")) {
                        if (!placeLoot(world, marker)) {
                            failures++;
                        }
                    }
                } catch (RuntimeException error) {
                    failures++;
                }
            }
        }
        return failures;
    }

    static void validate(StructureFeature feature, boolean entityMarkers, boolean lootMarkers) {
        StructureTransform identity = new StructureTransform(StructureTransform.Rotation.NONE,
                StructureTransform.Mirror.NONE);
        for (StructureFeature.PositionedMarker marker
                : feature.markers(new BlockPosition(0, 0, 0), identity)) {
            if (entityMarkers && marker.name.equals("entity")) {
                entity(marker);
            } else if (lootMarkers && marker.name.equals("loot")) {
                lootEntries(marker, 255);
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

    private static boolean placeLoot(World world, StructureFeature.PositionedMarker marker) {
        TileEntity tile = world.getBlockTileEntity(marker.position.x, marker.position.y, marker.position.z);
        if (!(tile instanceof IInventory) || !(marker.value instanceof Map)) {
            return false;
        }
        IInventory inventory = (IInventory) tile;
        List<?> items = lootEntries(marker, inventory.getSizeInventory() - 1);
        boolean applied = false;
        for (Object entryValue : items) {
            if (!(entryValue instanceof Map)) {
                return false;
            }
            Map<?, ?> entry = (Map<?, ?>) entryValue;
            int slot = integer(entry.get("slot"), 0, inventory.getSizeInventory() - 1, "loot marker slot");
            if (inventory.getStackInSlot(slot) != null) {
                continue;
            }
            int item = item(entry.get("item"));
            int count = integer(entry.get("count"), 1, 64, 1, "loot marker count");
            int damage = integer(entry.get("damage"), 0, 32767, 0, "loot marker damage");
            inventory.setInventorySlotContents(slot, new ItemStack(item, count, damage));
            applied = true;
        }
        if (applied) {
            inventory.onInventoryChanged();
        }
        return true;
    }

    private static List<?> lootEntries(StructureFeature.PositionedMarker marker, int maxSlot) {
        if (!(marker.value instanceof Map)) {
            throw new IllegalArgumentException("loot marker value must be an item object or items list");
        }
        Map<?, ?> value = (Map<?, ?>) marker.value;
        Object entries = value.get("items");
        List<?> items = entries instanceof List ? (List<?>) entries : Collections.singletonList(value);
        if (items.size() > 256) {
            throw new IllegalArgumentException("loot marker items must contain at most 256 entries");
        }
        for (Object entryValue : items) {
            if (!(entryValue instanceof Map)) {
                throw new IllegalArgumentException("loot marker items must contain item objects");
            }
            Map<?, ?> entry = (Map<?, ?>) entryValue;
            integer(entry.get("slot"), 0, maxSlot, "loot marker slot");
            item(entry.get("item"));
            integer(entry.get("count"), 1, 64, 1, "loot marker count");
            integer(entry.get("damage"), 0, 32767, 0, "loot marker damage");
        }
        return items;
    }

    private static int item(Object value) {
        if (value instanceof Number) {
            int id = integer(value, 1, Item.itemsList.length - 1, "loot marker item");
            if (Item.itemsList[id] == null) {
                throw new IllegalArgumentException("loot marker item is not registered: " + id);
            }
            return id;
        }
        String path = string(value, "loot marker item");
        int separator = path.indexOf(':');
        if (separator >= 0) {
            path = AssetKey.parse(path).getPath();
        }
        String shortPath = path.startsWith("item/") ? path.substring("item/".length()) : path;
        for (int id = 1; id < Item.itemsList.length; id++) {
            Item item = Item.itemsList[id];
            if (item == null) {
                continue;
            }
            String name = item.getItemName();
            String bare = name != null && name.startsWith("item.") ? name.substring("item.".length()) : name;
            if (path.equals(name) || path.equals(bare) || shortPath.equals(name) || shortPath.equals(bare)) {
                return id;
            }
        }
        throw new IllegalArgumentException("loot marker item is not registered: " + value);
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

    private static int integer(Object value, int min, int max, String path) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        double number = ((Number) value).doubleValue();
        if (number != Math.rint(number) || number < min || number > max) {
            throw new IllegalArgumentException(path + " must be " + min + ".." + max);
        }
        return (int) number;
    }

    private static int integer(Object value, int min, int max, int fallback, String path) {
        return value == null ? fallback : integer(value, min, max, path);
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
