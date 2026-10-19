package betamoon.dimension.storage;

import betamoon.dimension.DimensionGenerationBinding;
import betamoon.dimension.DimensionKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.WorldInfo;

/** Local mutable metadata and the pinned world-shaping/navigation contract. */
public final class DimensionMetadataStore {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final CheckedDimensionRecord record;
    public final DimensionKey key;
    public final int numericId;
    public final long seed;
    public final String signature;
    private final Map<String, String> dependencies;
    private WorldInfo info;
    private boolean initialized;
    private boolean hasAnchor;
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;

    public DimensionMetadataStore(DimensionPaths paths, DimensionGenerationBinding binding, int id, long rootSeed)
            throws IOException {
        key = binding.definition.key;
        numericId = id;
        seed = binding.definition.seed(rootSeed);
        signature = binding.signature;
        dependencies = binding.dependencies;
        record = new CheckedDimensionRecord(paths, "betamoon/dimensions/" + key.storagePath() + "/dimension.dat",
                0x424d444d, MAX_BYTES);
        byte[] saved = record.read();
        if (saved == null) {
            info = new WorldInfo(seed, binding.definition.displayName);
            info.setWorldTime(binding.definition.initialTime);
            info.setSaveVersion(19132);
            info.setSpawn(0, 64, 0);
        } else {
            read(saved);
        }
    }

    private void read(byte[] saved) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(saved))) {
            if (!input.readUTF().equals(key.toString()) || input.readInt() != numericId || input.readLong() != seed) {
                throw new IOException("generation_mismatch: " + key + " requires its saved definition/seed/ID");
            }
            String savedSignature = input.readUTF();
            initialized = input.readBoolean();
            hasAnchor = input.readBoolean();
            if (initialized && !hasAnchor) {
                throw new IOException("Initialized dimension has no generation anchor");
            }
            anchorX = input.readInt();
            anchorY = input.readInt();
            anchorZ = input.readInt();
            spawnX = input.readDouble();
            spawnY = input.readDouble();
            spawnZ = input.readDouble();
            spawnYaw = input.readFloat();
            spawnPitch = input.readFloat();
            validatePosition(spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
            if (anchorX < -30000000 || anchorX > 30000000 || anchorZ < -30000000 || anchorZ > 30000000 || anchorY < 0
                    || anchorY > 127) {
                throw new IOException("Invalid dimension original anchor");
            }
            int length = input.readInt();
            if (length < 0 || length != input.available() || length > MAX_BYTES) {
                throw new IOException("Invalid dimension metadata NBT length");
            }
            byte[] nbt = new byte[length];
            input.readFully(nbt);
            NBTTagCompound tag = DimensionNbt.decode(nbt, MAX_BYTES);
            if (!savedSignature.equals(signature)) {
                throw mismatch(tag.getCompoundTag("BetaMoonGenerationDependencies"));
            }
            if (tag.hasKey("Player")) {
                throw new IOException("Dimension-local metadata must not own root player state");
            }
            info = new WorldInfo(tag);
            if (info.getRandomSeed() != seed || info.getWorldTime() < 0) {
                throw new IOException("Invalid dimension metadata seed/time");
            }
        }
    }

    private IOException mismatch(NBTTagCompound saved) {
        TreeSet<String> names = new TreeSet<String>(dependencies.keySet());
        for (Object value : saved.func_28110_c()) {
            names.add(((NBTBase) value).getKey());
        }
        StringBuilder detail = new StringBuilder("generation_mismatch: ").append(key);
        int count = 0;
        for (String name : names) {
            if (!saved.hasKey(name) || !dependencies.containsKey(name)
                    || !saved.getString(name).equals(dependencies.get(name))) {
                detail.append(count == 0 ? " requires matching inputs: " : ", ").append(name);
                if (++count == 8) {
                    detail.append(" (additional inputs may differ)");
                    break;
                }
            }
        }
        return new IOException(detail.toString());
    }

    public synchronized WorldInfo worldInfo() throws IOException {
        return new WorldInfo(DimensionNbt.copy(info.getNBTTagCompound(), MAX_BYTES));
    }

    public synchronized void initialize(double x, double y, double z, float yaw, float pitch) throws IOException {
        validatePosition(x, y, z, yaw, pitch);
        if (initialized) {
            return;
        }
        if (!hasAnchor) {
            bootstrapAnchor((int) Math.floor(x), blockSpawnY(y), (int) Math.floor(z));
        }
        WorldInfo previousInfo = worldInfo();
        double[] previousEntry = entryPosition();
        spawnX = x;
        spawnY = y;
        spawnZ = z;
        spawnYaw = yaw;
        spawnPitch = pitch;
        info.setSpawn((int) Math.floor(x), blockSpawnY(y), (int) Math.floor(z));
        initialized = true;
        try {
            save(info);
        } catch (IOException exception) {
            initialized = false;
            info = previousInfo;
            spawnX = previousEntry[0];
            spawnY = previousEntry[1];
            spawnZ = previousEntry[2];
            spawnYaw = (float) previousEntry[3];
            spawnPitch = (float) previousEntry[4];
            throw exception;
        }
    }

    public synchronized void bootstrapAnchor(int x, int y, int z) throws IOException {
        validatePosition(x, y, z, 0, 0);
        if (y < 0 || y > 127) {
            throw new IOException("Original block anchor height must be within 0..127");
        }
        if (hasAnchor) {
            return;
        }
        int previousX = anchorX;
        int previousY = anchorY;
        int previousZ = anchorZ;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        hasAnchor = true;
        try {
            save(info);
        } catch (IOException exception) {
            hasAnchor = false;
            anchorX = previousX;
            anchorY = previousY;
            anchorZ = previousZ;
            throw exception;
        }
    }

    public synchronized void save(WorldInfo next) throws IOException {
        NBTTagCompound tag = next.getNBTTagCompoundWithPlayer(Collections.emptyList());
        if (next.getRandomSeed() != seed || tag.hasKey("Player")) {
            throw new IOException("Dimension metadata seed/player ownership mismatch");
        }
        NBTTagCompound inputs = new NBTTagCompound();
        for (Map.Entry<String, String> input : dependencies.entrySet()) {
            inputs.setString(input.getKey(), input.getValue());
        }
        tag.setCompoundTag("BetaMoonGenerationDependencies", inputs);
        byte[] nbt = DimensionNbt.encode(tag, MAX_BYTES);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(key.toString());
            output.writeInt(numericId);
            output.writeLong(seed);
            output.writeUTF(signature);
            output.writeBoolean(initialized);
            output.writeBoolean(hasAnchor);
            output.writeInt(anchorX);
            output.writeInt(anchorY);
            output.writeInt(anchorZ);
            output.writeDouble(spawnX);
            output.writeDouble(spawnY);
            output.writeDouble(spawnZ);
            output.writeFloat(spawnYaw);
            output.writeFloat(spawnPitch);
            output.writeInt(nbt.length);
            output.write(nbt);
        }
        record.write(bytes.toByteArray());
        info = new WorldInfo(DimensionNbt.decode(nbt, MAX_BYTES));
    }

    public synchronized boolean initialized() {
        return initialized;
    }

    public synchronized boolean hasAnchor() {
        return hasAnchor;
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    public double[] entryPosition() {
        return new double[]{spawnX, spawnY, spawnZ, spawnYaw, spawnPitch};
    }

    public static int blockSpawnY(double y) {
        return Math.max(0, Math.min(127, (int) Math.floor(y)));
    }

    public static void validatePosition(double x, double y, double z, double yaw, double pitch) throws IOException {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(yaw)
                || !Double.isFinite(pitch) || Math.abs(x) >= 30000000 || Math.abs(z) >= 30000000 || y < -64 || y > 4096
                || pitch < -90 || pitch > 90) {
            throw new IOException("Invalid dimension entry coordinates/orientation");
        }
    }
}
