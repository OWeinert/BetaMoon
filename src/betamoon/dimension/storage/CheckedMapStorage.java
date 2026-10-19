package betamoon.dimension.storage;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.NativeFieldAccess;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.src.MapDataBase;
import net.minecraft.src.MapStorage;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagShort;

/**
 * Checked native MapStorage formats around the original cache, including the
 * original global ID allocator.
 */
public class CheckedMapStorage extends MapStorage {
    private static final int MAX_DATA_BYTES = 4 * 1024 * 1024;
    private static final ClassRef TYPE = new ClassRef("net/minecraft/src/MapStorage");
    private static final Field CACHE = NativeFieldAccess.field(MapStorage.class,
            new FieldRef(TYPE, "loadedDataMap", "Ljava/util/Map;"));
    private static final Field ENTRIES = NativeFieldAccess.field(MapStorage.class,
            new FieldRef(TYPE, "loadedDataList", "Ljava/util/List;"));
    private static final Field COUNTERS = NativeFieldAccess.field(MapStorage.class,
            new FieldRef(TYPE, "idCounts", "Ljava/util/Map;"));
    private final MapStorage delegate;
    protected final DimensionPaths paths;
    private final String directory;

    public CheckedMapStorage(DimensionPaths paths, String directory, MapStorage original) {
        super(null);
        this.paths = paths;
        this.directory = directory;
        delegate = original;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized MapDataBase loadData(Class type, String name) {
        Map<String, MapDataBase> cache = (Map<String, MapDataBase>) NativeFieldAccess.get(CACHE, delegate);
        MapDataBase existing = cache.get(name);
        if (existing != null) {
            return existing;
        }
        try {
            String file = dataPath(name);
            if (Files.notExists(paths.resolve(file))) {
                return null;
            }
            NBTTagCompound saved = DimensionNbt.readCompressed(paths.resolve(file), MAX_DATA_BYTES);
            if (!saved.hasKey("data")) {
                throw new IOException("MapStorage payload has no data compound");
            }
            MapDataBase loaded = (MapDataBase) type.getConstructor(String.class).newInstance(name);
            loaded.readFromNBT(saved.getCompoundTag("data"));
            delegate.setData(name, loaded);
            return loaded;
        } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("Cannot read saved world data: " + name, exception);
        }
    }

    @Override
    public synchronized void setData(String name, MapDataBase data) {
        dataPath(name);
        delegate.setData(name, data);
    }

    @Override
    public synchronized void saveAllData() {
        try {
            flush(false);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save checked world data", exception);
        }
    }

    public synchronized void flush(boolean all) throws IOException {
        List<?> entries = new ArrayList<Object>((List<?>) NativeFieldAccess.get(ENTRIES, delegate));
        for (Object value : entries) {
            MapDataBase data = (MapDataBase) value;
            if (all || data.isDirty()) {
                NBTTagCompound payload = new NBTTagCompound();
                data.writeToNBT(payload);
                NBTTagCompound root = new NBTTagCompound();
                root.setCompoundTag("data", payload);
                DimensionNbt.writeCompressed(paths, dataPath(data.field_28168_a), root, MAX_DATA_BYTES);
                data.setDirty(false);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized int getUniqueDataId(String name) {
        int allocated = delegate.getUniqueDataId(name);
        try {
            Map<String, Short> counts = (Map<String, Short>) NativeFieldAccess.get(COUNTERS, delegate);
            NBTTagCompound tag = new NBTTagCompound();
            for (Map.Entry<String, Short> entry : counts.entrySet()) {
                tag.setShort(entry.getKey(), entry.getValue());
            }
            DimensionNbt.writeRaw(paths, directory + "idcounts.dat", tag, 65536);
            return allocated;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist the global native data ID allocation", exception);
        }
    }

    public void validateCounterFile() throws IOException {
        String file = directory + "idcounts.dat";
        if (Files.exists(paths.resolve(file))) {
            NBTTagCompound tag = DimensionNbt.readRaw(paths.resolve(file), 65536);
            for (Object value : tag.func_28110_c()) {
                if (!(value instanceof NBTTagShort)) {
                    throw new IOException("Invalid native data ID allocation payload");
                }
            }
        }
    }

    private String dataPath(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,128}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid native world-data name");
        }
        return directory + name + ".dat";
    }
}
