package betamoon.teleport;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.NativeFieldAccess;
import betamoon.worldgen.terrain.BetaMoonChunkProvider;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkCoordIntPair;
import net.minecraft.src.ChunkProvider;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.ChunkProviderHell;
import net.minecraft.src.ChunkProviderSky;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.World;

/**
 * Sliced native-cache preparation with recursive-load accounting and deferred
 * automatic population.
 */
public final class ChunkPreparation {
    private static final ThreadLocal<ChunkPreparation> ACTIVE = new ThreadLocal<ChunkPreparation>();
    private static final Field CHUNK_MAP = NativeFieldAccess.field(ChunkProvider.class,
            new FieldRef(new ClassRef("net/minecraft/src/ChunkProvider"), "chunkMap", "Ljava/util/Map;"));
    private final World world;
    private final IChunkProvider cache;
    private final int maximum;
    private final Set<Long> acquired = new LinkedHashSet<Long>();
    private final List<Long> required = new ArrayList<Long>();
    private final List<Long> populations = new ArrayList<Long>();
    private int cursor;
    private int populationCursor;
    private int remainingThisTick;
    private boolean populating;
    private String violation;
    private final Runnable checkpoint;
    private boolean checkpointed;

    public ChunkPreparation(World world, int maximum) {
        this(world, maximum, null);
    }

    public ChunkPreparation(World world, int maximum, Runnable checkpoint) {
        this.world = world;
        cache = world.getIChunkProvider();
        this.maximum = maximum;
        this.checkpoint = checkpoint;
        requireSupported(cache);
    }

    private static void requireSupported(IChunkProvider cache) {
        if (cache.getClass() != ChunkProvider.class) {
            throw new IllegalArgumentException(
                    "unsupported_environment: replacement chunk cache has no preparation contract");
        }
        try {
            Field factory = null;
            for (Field field : ChunkProvider.class.getDeclaredFields()) {
                if (field.getType() == IChunkProvider.class) {
                    if (factory != null) {
                        throw new IllegalArgumentException("unsupported_environment: ambiguous chunk factory");
                    }
                    factory = field;
                }
            }
            if (factory == null) {
                throw new IllegalArgumentException("unsupported_environment: missing chunk factory");
            }
            factory.setAccessible(true);
            IChunkProvider provider = (IChunkProvider) factory.get(cache);
            if (provider instanceof BetaMoonChunkProvider) {
                provider = ((BetaMoonChunkProvider) provider).nativeDelegate();
            }
            if (provider == null || provider.getClass() != ChunkProviderGenerate.class
                    && provider.getClass() != ChunkProviderHell.class
                    && provider.getClass() != ChunkProviderSky.class) {
                throw new IllegalArgumentException(
                        "unsupported_environment: replacement generator has no bounded population contract");
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("unsupported_environment: cannot inspect chunk factory", exception);
        }
    }

    /**
     * Reserve the complete footprint before any population writes; loaded chunks do
     * not consume acquisition budget.
     */
    public void reserve(int chunkX, int chunkZ, boolean loadChunks) {
        if (ACTIVE.get() != null || cursor < required.size() || populationCursor < populations.size()) {
            throw new IllegalStateException("Nested or unfinished chunk preparation");
        }
        List<Long> planned = new ArrayList<Long>();
        int missing = 0;
        for (int x = chunkX - 2; x <= chunkX + 2; x++) {
            for (int z = chunkZ - 2; z <= chunkZ + 2; z++) {
                if (!loaded(cache, x, z)) {
                    if (!loadChunks) {
                        throw new WorkFailure("chunks_unavailable");
                    }
                    missing++;
                }
                planned.add(key(x, z));
            }
        }
        if (missing > maximum - acquired.size()) {
            throw new WorkFailure("budget_exhausted");
        }
        required.clear();
        required.addAll(planned);
        populations.clear();
        cursor = 0;
        populationCursor = 0;
        for (int x = chunkX - 1; x <= chunkX; x++) {
            for (int z = chunkZ - 1; z <= chunkZ; z++) {
                populations.add(key(x, z));
            }
        }
    }

    /**
     * At most two new chunks and one non-preemptible population unit per logical
     * task tick.
     */
    public boolean step() {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Nested chunk preparation");
        }
        remainingThisTick = 2;
        ACTIVE.set(this);
        try {
            while (cursor < required.size()) {
                long position = required.get(cursor);
                int x = x(position);
                int z = z(position);
                if (!loaded(cache, x, z) && remainingThisTick == 0) {
                    return false;
                }
                Chunk chunk = cache.provideChunk(x, z);
                if (chunk == null || chunk.xPosition != x || chunk.zPosition != z) {
                    throw new WorkFailure("unsupported_environment");
                }
                cursor++;
            }
            if (populationCursor < populations.size()) {
                long position = populations.get(populationCursor);
                Chunk chunk = cache.provideChunk(x(position), z(position));
                if (!chunk.isTerrainPopulated) {
                    if (!checkpointed && checkpoint != null) {
                        checkpoint.run();
                        checkpointed = true;
                    }
                    populating = true;
                    try {
                        cache.populate(cache, x(position), z(position));
                        if (violation != null) {
                            throw new WorkFailure(violation);
                        }
                    } catch (Throwable failure) {
                        chunk.isTerrainPopulated = false;
                        chunk.setChunkModified();
                        String reason = failure instanceof WorkFailure
                                ? ((WorkFailure) failure).reason
                                : "generation_failed";
                        throw new WorkFailure(reason, failure, true);
                    } finally {
                        populating = false;
                    }
                }
                populationCursor++;
            }
            return populationCursor >= populations.size();
        } finally {
            ACTIVE.remove();
        }
    }

    public int acquiredCount() {
        return acquired.size();
    }

    public int progressPercent() {
        int total = required.size() + populations.size();
        return total == 0 ? -1 : (cursor + populationCursor) * 100 / total;
    }

    public static int beforeLoad(IChunkProvider provider, int x, int z) {
        ChunkPreparation active = ACTIVE.get();
        if (active != null && !loaded(provider, x, z)) {
            if (active.cache != provider || active.populating || active.remainingThisTick == 0
                    || active.acquired.size() >= active.maximum || !active.required.contains(key(x, z))) {
                active.violation = "budget_exhausted";
                throw new WorkFailure(active.violation);
            }
            active.remainingThisTick--;
            active.acquired.add(key(x, z));
        }
        return 0;
    }

    public static int beforeAccess(ChunkProvider provider, int x, int z) {
        ChunkPreparation active = ACTIVE.get();
        if (active == null) {
            return 0;
        }
        if (active != null && active.populating && (active.cache != provider || !active.required.contains(key(x, z)))) {
            active.violation = "budget_exhausted";
            throw new WorkFailure(active.violation);
        }
        loaded(provider, x, z);
        return 0;
    }

    /**
     * Native chunk keys alias every 32768 coordinates. Refuse an alias before
     * native code can return it.
     */
    @SuppressWarnings("unchecked")
    public static boolean loaded(IChunkProvider provider, int x, int z) {
        if (provider.getClass() != ChunkProvider.class) {
            return provider.chunkExists(x, z);
        }
        Map<Integer, Chunk> chunks = (Map<Integer, Chunk>) NativeFieldAccess.get(CHUNK_MAP, provider);
        Chunk chunk = chunks.get(ChunkCoordIntPair.chunkXZ2Int(x, z));
        if (chunk != null && (chunk.xPosition != x || chunk.zPosition != z)) {
            throw new WorkFailure("unsupported_environment");
        }
        return chunk != null;
    }

    public static int deferPopulation() {
        ChunkPreparation active = ACTIVE.get();
        return active != null && !active.populating ? 1 : 0;
    }

    /**
     * A domain engine may contain an exception; preparation still cannot commit its
     * partially applied population.
     */
    public static void reportPopulationFailure(Throwable failure) {
        ChunkPreparation active = ACTIVE.get();
        if (active != null && active.populating) {
            active.violation = failure instanceof WorkFailure ? ((WorkFailure) failure).reason : "generation_failed";
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }

    /**
     * Escapes native loaders which catch Exception and otherwise treat a failed
     * read as absent terrain.
     */
    public static final class WorkFailure extends Error {
        public final String reason;
        public final boolean populationChanged;

        public WorkFailure(String reason) {
            this(reason, null, false);
        }

        private WorkFailure(String reason, Throwable cause, boolean populationChanged) {
            super(reason, cause);
            this.reason = reason;
            this.populationChanged = populationChanged;
        }
    }
}
