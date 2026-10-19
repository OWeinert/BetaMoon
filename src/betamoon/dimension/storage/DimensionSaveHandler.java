package betamoon.dimension.storage;

import betamoon.dimension.DimensionWorldProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkLoader;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldProvider;

/**
 * Child storage never creates a second session lock or writes root world
 * metadata.
 */
public final class DimensionSaveHandler implements ISaveHandler {
    public final ISaveHandler root;
    public final DimensionMetadataStore metadata;
    public final DimensionPaths paths;
    private final RootPlayerWriter players;
    private DimensionChunkLoader loader;

    public DimensionSaveHandler(ISaveHandler root, DimensionMetadataStore metadata, DimensionPaths paths,
            RootPlayerWriter players) {
        this.root = root;
        this.metadata = metadata;
        this.paths = paths;
        this.players = players;
    }

    @Override
    public WorldInfo loadWorldInfo() {
        try {
            return metadata.worldInfo();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load dimension metadata", exception);
        }
    }

    @Override
    public void func_22150_b() {
        root.func_22150_b();
    }

    @Override
    public IChunkLoader getChunkLoader(WorldProvider provider) {
        if (!(provider instanceof DimensionWorldProvider) || provider.worldType != metadata.numericId
                || !((DimensionWorldProvider) provider).binding.definition.key.equals(metadata.key)) {
            throw new IllegalStateException("Dimension chunk loader/provider identity mismatch");
        }
        try {
            Path directory = paths.dimension(metadata.key);
            Files.createDirectories(directory);
            paths.dimension(metadata.key);
            if (loader == null) {
                loader = new DimensionChunkLoader(paths, "betamoon/dimensions/" + metadata.key.storagePath(),
                        root instanceof SaveOldDir);
            }
            return loader;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot open dimension chunk storage", exception);
        }
    }

    @Override
    public void saveWorldInfoAndPlayer(WorldInfo info, List players) {
        saveWorldInfo(info);
        if (!players.isEmpty()) {
            if (players.size() != 1 || !(players.get(0) instanceof EntityPlayer)) {
                throw new IllegalStateException("Custom dimensions currently support one local player");
            }
            this.players.save((EntityPlayer) players.get(0));
        }
    }

    @Override
    public void saveWorldInfo(WorldInfo info) {
        root.func_22150_b();
        try {
            metadata.save(info);
        } catch (IOException exception) {
            throw new IllegalStateException("save_failed: cannot save dimension metadata", exception);
        }
    }

    @Override
    public File func_28113_a(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,128}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid dimension data name");
        }
        try {
            Path path = paths.resolve("betamoon/dimensions/" + metadata.key.storagePath() + "/data/" + name + ".dat");
            Files.createDirectories(path.getParent());
            return path.toFile();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot route dimension-local data", exception);
        }
    }

    public void requireChunksSaved() throws IOException {
        if (loader != null) {
            loader.requireSaved();
        }
    }

    public interface RootPlayerWriter {
        void save(EntityPlayer player);
    }
}
