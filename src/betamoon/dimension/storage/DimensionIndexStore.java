package betamoon.dimension.storage;

import betamoon.dimension.DimensionKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Save-local numeric allocation. Retired/absent keys keep their IDs
 * permanently.
 */
public final class DimensionIndexStore {
    private static final int MAX_ENTRIES = 65536;
    private final CheckedDimensionRecord record;
    private final Map<DimensionKey, Integer> ids = new TreeMap<DimensionKey, Integer>();
    private int nextId = 2;

    public DimensionIndexStore(DimensionPaths paths) throws IOException {
        record = new CheckedDimensionRecord(paths, "betamoon/dimensions.dat", 0x424d4449, 4 * 1024 * 1024);
        byte[] payload = record.read();
        if (payload == null) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            nextId = input.readInt();
            int count = input.readInt();
            if (nextId < 2 || count < 0 || count > MAX_ENTRIES) {
                throw new IOException("Invalid dimension allocation index");
            }
            Set<Integer> used = new HashSet<Integer>();
            for (int index = 0; index < count; index++) {
                DimensionKey key = DimensionKey.parse(input.readUTF());
                int id = input.readInt();
                if (key.isNative() || id < 2 || id >= nextId || !used.add(id) || ids.put(key, id) != null) {
                    throw new IOException("Conflicting dimension allocation index");
                }
            }
            if (input.available() != 0) {
                throw new IOException("Trailing dimension allocation data");
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid dimension allocation values", exception);
        }
    }

    public synchronized int allocate(DimensionKey key) throws IOException {
        if (key.isNative()) {
            return key.equals(DimensionKey.NETHER) ? -1 : 0;
        }
        Integer existing = ids.get(key);
        if (existing != null) {
            return existing;
        }
        if (nextId == Integer.MAX_VALUE || ids.size() >= MAX_ENTRIES) {
            throw new IOException("Dimension allocation budget exhausted");
        }
        int allocated = nextId;
        ids.put(key, allocated);
        nextId++;
        try {
            save();
        } catch (IOException exception) {
            ids.remove(key);
            nextId = allocated;
            throw exception;
        }
        return allocated;
    }

    public synchronized DimensionKey resolve(int id) {
        if (id == 0 || id == -1) {
            return DimensionKey.nativeKey(id);
        }
        for (Map.Entry<DimensionKey, Integer> entry : ids.entrySet()) {
            if (entry.getValue() == id) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized Integer id(DimensionKey key) {
        return key.isNative() ? Integer.valueOf(key.equals(DimensionKey.NETHER) ? -1 : 0) : ids.get(key);
    }

    public synchronized Map<DimensionKey, Integer> entries() {
        return Collections.unmodifiableMap(new TreeMap<DimensionKey, Integer>(ids));
    }

    private void save() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(nextId);
            output.writeInt(ids.size());
            for (Map.Entry<DimensionKey, Integer> entry : ids.entrySet()) {
                output.writeUTF(entry.getKey().toString());
                output.writeInt(entry.getValue());
            }
        }
        record.write(bytes.toByteArray());
    }
}
