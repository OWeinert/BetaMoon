package betamoon.dimension.storage;

import net.minecraft.src.MapData;
import net.minecraft.src.MapDataBase;
import net.minecraft.src.MapStorage;

/**
 * One shared cache/ID allocator for native maps, separate caches for
 * dimension-local service data.
 */
public final class DimensionMapStorage extends CheckedMapStorage {
    private final MapStorage global;

    public DimensionMapStorage(DimensionSaveHandler local, MapStorage global) {
        super(local.paths, "betamoon/dimensions/" + local.metadata.key.storagePath() + "/data/", new MapStorage(null));
        this.global = global;
    }

    @Override
    public MapDataBase loadData(Class type, String name) {
        return MapData.class.isAssignableFrom(type) || name.startsWith("map_")
                ? global.loadData(type, name)
                : super.loadData(type, name);
    }

    @Override
    public void setData(String name, MapDataBase data) {
        if (data instanceof MapData || name.startsWith("map_")) {
            global.setData(name, data);
        } else {
            super.setData(name, data);
        }
    }

    @Override
    public int getUniqueDataId(String name) {
        return global.getUniqueDataId(name);
    }

    @Override
    public void saveAllData() {
        super.saveAllData();
        global.saveAllData();
    }
}
