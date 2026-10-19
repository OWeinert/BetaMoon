package betamoon.dimension.storage;

import betamoon.dimension.DimensionSession;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.IChunkLoader;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldProvider;

/**
 * Native terrain layout with root-owned player persistence, including empty
 * preparing worlds.
 */
public final class DimensionRootSaveHandler implements ISaveHandler {
    public final DimensionSession session;
    private final World world;
    private DimensionChunkLoader loader;

    public DimensionRootSaveHandler(DimensionSession session, World world) {
        this.session = session;
        this.world = world;
    }

    public WorldInfo loadWorldInfo() {
        return session.root.loadWorldInfo();
    }

    public void func_22150_b() {
        session.root.func_22150_b();
    }

    public IChunkLoader getChunkLoader(WorldProvider provider) {
        if (provider.worldType != 0 && provider.worldType != -1) {
            throw new IllegalStateException("Native storage cannot select a custom dimension by number");
        }
        if (loader == null) {
            loader = new DimensionChunkLoader(session.paths, provider.worldType == -1 ? "DIM-1" : ".",
                    session.root instanceof SaveOldDir);
        }
        return loader;
    }

    public void saveWorldInfoAndPlayer(WorldInfo info, List players) {
        try {
            session.saveNativeMetadata(world, info, players);
        } catch (IOException exception) {
            throw new IllegalStateException("save_failed: cannot persist native root metadata/player", exception);
        }
    }

    public void saveWorldInfo(WorldInfo info) {
        saveWorldInfoAndPlayer(info, Collections.emptyList());
    }

    public File func_28113_a(String name) {
        return session.root.func_28113_a(name);
    }

    public void requireChunksSaved() throws IOException {
        if (loader != null) {
            loader.requireSaved();
        }
    }
}
