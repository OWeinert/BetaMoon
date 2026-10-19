package betamoon.worldgen.creation;

import betamoon.dimension.storage.DimensionRootSaveHandler;
import betamoon.dimension.storage.DimensionSaveHandler;
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
        return SESSIONS.containsKey(root(save));
    }

    /**
     * Carries explicit consent across a bounded deferred load, without extending it
     * to another save.
     */
    public static synchronized void retainFor(ISaveHandler save) {
        save = root(save);
        WorldGeneratorFallback permission = CURRENT.get();
        File settings = save.func_28113_a("betamoon_generation_settings");
        if (permission != null && settings != null
                && permission.path.equals(settings.toPath().toAbsolutePath().normalize().toString())) {
            SESSIONS.put(save, permission);
        }
    }

    /**
     * Explicit recovery buttons may continue the same approved load; another save
     * still fails path/seed checks.
     */
    public static synchronized Runnable continueFor(ISaveHandler save, Runnable action) {
        save = root(save);
        WorldGeneratorFallback captured = save == null ? null : SESSIONS.get(save);
        if (captured == null) {
            captured = CURRENT.get();
        }
        if (captured == null) {
            return action;
        }
        final WorldGeneratorFallback permission = captured;
        return () -> {
            WorldGeneratorFallback previous = CURRENT.get();
            if (previous != null && previous != permission) {
                throw new IllegalStateException("Cannot combine world generator fallback permissions");
            }
            CURRENT.set(permission);
            try {
                action.run();
            } finally {
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
        };
    }

    public static synchronized boolean approved(ISaveHandler save, File file, String profile, long seed) {
        save = root(save);
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

    private static ISaveHandler root(ISaveHandler save) {
        return save instanceof DimensionRootSaveHandler
                ? ((DimensionRootSaveHandler) save).session.root
                : save instanceof DimensionSaveHandler ? ((DimensionSaveHandler) save).root : save;
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
