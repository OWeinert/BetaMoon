package betamoon.worldgen.creation;

import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.ISaveHandler;

/**
 * Explicit consent scoped to one world load and its shared dimension save
 * handler.
 */
public final class WorldGeneratorFallback implements AutoCloseable {
    private static final ThreadLocal<WorldGeneratorFallback> CURRENT = new ThreadLocal<WorldGeneratorFallback>();
    private static final Map<ISaveHandler, WorldGeneratorFallback> SESSIONS = new WeakHashMap<ISaveHandler, WorldGeneratorFallback>();
    private final String path;
    private final String profile;
    private final long seed;

    private WorldGeneratorFallback(MissingWorldGenerator missing) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested fallback loads are not supported");
        }
        path = missing.settingsFile.toPath().toAbsolutePath().normalize().toString();
        profile = missing.profileKey;
        seed = missing.seed;
        CURRENT.set(this);
    }

    public static WorldGeneratorFallback begin(MissingWorldGenerator missing) {
        return new WorldGeneratorFallback(missing);
    }

    public static synchronized boolean activeSession(ISaveHandler save) {
        return SESSIONS.containsKey(save);
    }

    public static synchronized boolean approved(ISaveHandler save, File file, String profile, long seed) {
        WorldGeneratorFallback permission = SESSIONS.get(save);
        if (permission == null) {
            permission = CURRENT.get();
        }
        if (permission == null || permission.seed != seed || !permission.profile.equals(profile)
                || !permission.path.equals(file.toPath().toAbsolutePath().normalize().toString())) {
            return false;
        }
        SESSIONS.put(save, permission);
        return true;
    }

    /** A new startWorld call must never reuse consent from an earlier session. */
    public static synchronized void startingWorld() {
        SESSIONS.clear();
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
