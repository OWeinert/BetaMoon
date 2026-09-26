package betamoon.world.explosion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable outcome returned after every selected explosion effect completes. */
public final class ExplosionResult {
    public final double x;
    public final double y;
    public final double z;
    public final float strength;
    public final int blocksAffected;
    public final int blocksDestroyed;
    public final int entitiesDamaged;
    public final int entitiesKnockedBack;
    public final int firesPlaced;
    public final boolean includesAffectedBlocks;
    public final List<ExplosionPosition> affectedBlocks;
    public final boolean affectedBlocksTruncated;

    ExplosionResult(ExplosionRequest request, int blocksAffected, int blocksDestroyed, int entitiesDamaged,
            int entitiesKnockedBack, int firesPlaced, List<ExplosionPosition> affectedBlocks,
            boolean affectedBlocksTruncated) {
        x = request.x;
        y = request.y;
        z = request.z;
        strength = request.strength;
        this.blocksAffected = blocksAffected;
        this.blocksDestroyed = blocksDestroyed;
        this.entitiesDamaged = entitiesDamaged;
        this.entitiesKnockedBack = entitiesKnockedBack;
        this.firesPlaced = firesPlaced;
        includesAffectedBlocks = request.includeAffectedBlocks;
        this.affectedBlocks = Collections.unmodifiableList(new ArrayList<ExplosionPosition>(affectedBlocks));
        this.affectedBlocksTruncated = affectedBlocksTruncated;
    }
}
