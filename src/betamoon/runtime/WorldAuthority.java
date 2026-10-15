package betamoon.runtime;

import net.minecraft.src.World;

/** Central authority predicate for gameplay mutations and callbacks. */
public final class WorldAuthority {
    private WorldAuthority() {
    }

    public static boolean isAuthoritative(World world) {
        return world != null && !world.multiplayerWorld;
    }

    public static boolean isRemoteView(World world) {
        return world != null && world.multiplayerWorld;
    }
}
