package betamoon.dimension;

import betamoon.utils.WeakIdentityMap;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MapData;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/**
 * Canonical map ownership; the native byte remains a compatibility field, never
 * a custom identity.
 */
public final class DimensionMapState {
    private static final String KEY = "BetaMoonDimension";
    private static final WeakIdentityMap<MapData, Ownership> MAPS = new WeakIdentityMap<MapData, Ownership>();
    private static final ThreadLocal<MapData> CURRENT = new ThreadLocal<MapData>();

    private DimensionMapState() {
    }

    public static void read(MapData map, NBTTagCompound tag) {
        DimensionKey key;
        if (tag.hasKey(KEY)) {
            key = DimensionKey.parse(tag.getString(KEY));
            int expected = key.isNative() ? key.equals(DimensionKey.NETHER) ? -1 : 0 : 1;
            if (tag.getByte("dimension") != expected) {
                throw new IllegalStateException("Map dimension byte disagrees with its canonical identity");
            }
        } else {
            key = nativeKey(tag.getByte("dimension"));
        }
        MAPS.put(map, new Ownership(key));
    }

    public static int validateRead(NBTTagCompound tag) {
        int width = tag.getShort("width");
        int height = tag.getShort("height");
        if (width < 1 || width > 128 || height < 1 || height > 128
                || tag.getByteArray("colors").length != width * height) {
            throw new IllegalStateException("Invalid saved map dimensions or color count");
        }
        return 0;
    }

    public static void write(MapData map, NBTTagCompound tag) {
        Ownership ownership = ownership(map);
        if (ownership.key != null) {
            tag.setString(KEY, ownership.key.toString());
            tag.setByte("dimension", compatibilityByte(ownership.key));
        }
    }

    public static MapData bindReturned(MapData map, World world) {
        if (map != null && MAPS.get(map) == null) {
            bind(map, world);
        }
        return map;
    }

    public static void created(ItemStack stack, World world) {
        MapData map = (MapData) world.loadItemData(MapData.class, "map_" + stack.getItemDamage());
        if (map == null) {
            throw new IllegalStateException("New native map is absent from the global map cache");
        }
        bind(map, world);
    }

    private static void bind(MapData map, World world) {
        DimensionKey key = DimensionIdentity.key(world);
        MAPS.put(map, new Ownership(key));
        map.field_28178_d = compatibilityByte(key);
        map.markDirty();
    }

    public static MapData enter(MapData map) {
        MapData previous = CURRENT.get();
        CURRENT.set(map);
        return previous;
    }

    public static void exit(MapData previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static int worldComparison(WorldProvider provider) {
        MapData map = CURRENT.get();
        if (map == null) {
            return provider.worldType;
        }
        return comparison(map, provider.worldObj);
    }

    public static int playerComparison(EntityPlayer player) {
        MapData map = CURRENT.get();
        return map == null ? player.dimension : comparison(map, player.worldObj);
    }

    private static int comparison(MapData map, World world) {
        DimensionKey key = ownership(map).key;
        return key != null && world != null && key.equals(DimensionIdentity.key(world))
                ? map.field_28178_d
                : Integer.MIN_VALUE;
    }

    public static DimensionKey key(MapData map) {
        return ownership(map).key;
    }

    private static Ownership ownership(MapData map) {
        Ownership ownership = MAPS.get(map);
        if (ownership == null) {
            ownership = new Ownership(nativeKey(map.field_28178_d));
            MAPS.put(map, ownership);
        }
        return ownership;
    }

    private static byte compatibilityByte(DimensionKey key) {
        return (byte) (key.isNative() ? key.equals(DimensionKey.NETHER) ? -1 : 0 : 1);
    }

    private static DimensionKey nativeKey(int value) {
        return value == 0 ? DimensionKey.OVERWORLD : value == -1 ? DimensionKey.NETHER : null;
    }

    private static final class Ownership {
        private final DimensionKey key;

        private Ownership(DimensionKey key) {
            this.key = key;
        }
    }
}
