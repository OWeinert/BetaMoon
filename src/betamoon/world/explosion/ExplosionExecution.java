package betamoon.world.explosion;

import net.minecraft.src.World;

/** Tracks nested execution and exposes the active immutable request to callbacks. */
public final class ExplosionExecution {
    private static final int MAX_DEPTH = 8;
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<Integer>();
    private static final ThreadLocal<ExplosionRequest> CURRENT = new ThreadLocal<ExplosionRequest>();

    private ExplosionExecution() {
    }

    public static ExplosionResult execute(World world, ExplosionRequest request) {
        if (world == null || request == null) {
            throw new IllegalArgumentException("Explosion world and request are required");
        }
        if (world.multiplayerWorld) {
            throw new IllegalStateException("Explosions can only be created in an authoritative world");
        }
        if (request.source != null && (request.source.isDead || request.source.worldObj != world)) {
            throw new IllegalArgumentException("Explosion source must be a live entity in the same world");
        }
        int depth = DEPTH.get() == null ? 0 : DEPTH.get().intValue();
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("Explosion recursion limit of " + MAX_DEPTH + " was reached");
        }

        ExplosionRequest previous = CURRENT.get();
        DEPTH.set(Integer.valueOf(depth + 1));
        CURRENT.set(request);
        try {
            return new ExplosionExecutor(world, request).execute();
        } finally {
            if (depth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(Integer.valueOf(depth));
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static ExplosionRequest current() {
        return CURRENT.get();
    }
}
