package betamoon.dimension;

import betamoon.utils.WeakIdentityMap;
import net.minecraft.src.World;

/**
 * Detached cause of the next observed dimension change; no player or previous
 * world is retained.
 */
public final class DimensionTransition {
    private static final WeakIdentityMap<World, DimensionTransition> ARRIVALS = new WeakIdentityMap<World, DimensionTransition>();
    public final String reason;
    public final String transferId;

    private DimensionTransition(String reason, String transferId) {
        this.reason = reason;
        this.transferId = transferId;
    }

    public static void mark(World destination, String reason, String transferId) {
        ARRIVALS.put(destination, new DimensionTransition(reason, transferId));
    }

    public static DimensionTransition arrival(World destination) {
        DimensionTransition notice = ARRIVALS.get(destination);
        return notice == null ? new DimensionTransition("native", null) : notice;
    }
}
