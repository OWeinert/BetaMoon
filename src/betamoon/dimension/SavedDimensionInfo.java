package betamoon.dimension;

import betamoon.dimension.storage.DimensionIndexStore;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionPaths;
import betamoon.teleport.TeleportJournal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.CompressedStreamTools;
import net.minecraft.src.NBTTagDouble;
import net.minecraft.src.NBTTagFloat;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.WorldInfo;

/**
 * Read-only join preflight, before a save handler or a chunk loader can change
 * the save.
 */
public final class SavedDimensionInfo {
    public final DimensionPaths paths;
    public final WorldInfo worldInfo;
    public final NBTTagCompound player;
    public final DimensionKey key;
    public final boolean deferred;

    private SavedDimensionInfo(DimensionPaths paths, NBTTagCompound root) throws IOException {
        this.paths = paths;
        if (!root.hasKey("Data")) {
            throw new IOException("Saved world has no root metadata");
        }
        NBTTagCompound data = root.getCompoundTag("Data");
        worldInfo = new WorldInfo(data);
        player = data.hasKey("Player")
                ? DimensionNbt.copy(data.getCompoundTag("Player"), TeleportJournal.PLAYER_BYTES)
                : null;
        DimensionIndexStore index = new DimensionIndexStore(paths);
        key = DimensionSession.savedDimension(index, player);
        boolean pending = false;
        if (Files.exists(paths.resolve("betamoon/player_teleport.dat"))) {
            TeleportJournal navigation = new TeleportJournal(paths);
            navigation.recoverySide(player);
            pending = navigation.pending() || navigation.preparation() != null || navigation.hasCleanup(key.toString());
        } else if (player != null && player.hasKey("BetaMoonPlayer")) {
            throw new IOException("recovery_required: saved player has no matching navigation journal");
        }
        deferred = !key.isNative() || pending;
        if (deferred) {
            validatePlayer(player);
        }
    }

    public static SavedDimensionInfo inspect(Path directory) throws IOException {
        DimensionPaths paths = new DimensionPaths(directory);
        if (!ownsSave(paths)) {
            return null;
        }
        Path level = paths.resolve("level.dat");
        if (!Files.exists(level)) {
            throw new IOException("recovery_required: dimension records exist but root player metadata is missing");
        }
        return new SavedDimensionInfo(paths, DimensionNbt.readCompressed(level, 1024 * 1024));
    }

    private static boolean ownsSave(DimensionPaths paths) throws IOException {
        if (Files.exists(paths.resolve("betamoon/dimensions.dat"))
                || Files.exists(paths.resolve("betamoon/player_teleport.dat"))
                || Files.exists(paths.resolve("betamoon/dimensions"))) {
            return true;
        }
        // Native saves retain their backup recovery, foreign IDs and metadata limits.
        return hasPlayerMarker(paths.root().resolve("level.dat"))
                || hasPlayerMarker(paths.root().resolve("level.dat_old"));
    }

    private static boolean hasPlayerMarker(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (InputStream input = Files.newInputStream(file)) {
            NBTTagCompound player = CompressedStreamTools.func_1138_a(input).getCompoundTag("Data")
                    .getCompoundTag("Player");
            return player.hasKey("BetaMoonDimension") || player.hasKey("BetaMoonPlayer");
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static void validatePlayer(NBTTagCompound player) throws IOException {
        if (player == null) {
            throw new IOException("Saved dimension has no player snapshot");
        }
        NBTTagList positions = player.getTagList("Pos");
        NBTTagList rotation = player.getTagList("Rotation");
        NBTTagList motion = player.getTagList("Motion");
        if (positions.tagCount() != 3 || rotation.tagCount() != 2 || motion.tagCount() != 3) {
            throw new IOException("Saved player has invalid position, rotation or motion");
        }
        for (int index = 0; index < 3; index++) {
            if (!(positions.tagAt(index) instanceof NBTTagDouble) || !(motion.tagAt(index) instanceof NBTTagDouble)
                    || !Double.isFinite(((NBTTagDouble) positions.tagAt(index)).doubleValue)
                    || !Double.isFinite(((NBTTagDouble) motion.tagAt(index)).doubleValue)) {
                throw new IOException("Saved player coordinates/motion must be finite native doubles");
            }
        }
        for (int index = 0; index < 2; index++) {
            if (!(rotation.tagAt(index) instanceof NBTTagFloat)
                    || !Float.isFinite(((NBTTagFloat) rotation.tagAt(index)).floatValue)) {
                throw new IOException("Saved player rotation must be finite native floats");
            }
        }
        double x = ((NBTTagDouble) positions.tagAt(0)).doubleValue;
        double y = ((NBTTagDouble) positions.tagAt(1)).doubleValue;
        double z = ((NBTTagDouble) positions.tagAt(2)).doubleValue;
        if (Math.abs(x) >= 30000000 || Math.abs(z) >= 30000000 || y < -64 || y > 4098) {
            throw new IOException("Saved dimension player position exceeds supported world bounds");
        }
    }
}
