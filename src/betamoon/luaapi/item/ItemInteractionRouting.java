package betamoon.luaapi.item;

import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

/**
 * Bridges Lua's denied result across vanilla's block-use and fallback-use
 * calls.
 */
public final class ItemInteractionRouting {
    private static final ThreadLocal<Attempt> CURRENT = new ThreadLocal<Attempt>();

    private ItemInteractionRouting() {
    }

    public static void begin(EntityPlayer player, World world) {
        CURRENT.set(new Attempt(player, world));
    }

    public static void deny() {
        Attempt attempt = CURRENT.get();
        if (attempt != null) {
            attempt.state = AttemptState.DENIED;
        }
    }

    public static boolean complete(boolean originalResult) {
        Attempt attempt = CURRENT.get();
        if (attempt != null && attempt.state == AttemptState.DENIED) {
            attempt.state = AttemptState.AWAITING_FALLBACK;
            return false;
        }
        CURRENT.remove();
        return originalResult;
    }

    public static boolean denyFallback(EntityPlayer player, World world) {
        Attempt attempt = CURRENT.get();
        CURRENT.remove();
        return attempt != null && attempt.state == AttemptState.AWAITING_FALLBACK && attempt.player == player
                && attempt.world == world && attempt.tick == world.getWorldTime();
    }

    private enum AttemptState {
        ACTIVE, DENIED, AWAITING_FALLBACK
    }

    private static final class Attempt {
        private final EntityPlayer player;
        private final World world;
        private final long tick;
        private AttemptState state = AttemptState.ACTIVE;

        private Attempt(EntityPlayer player, World world) {
            this.player = player;
            this.world = world;
            tick = world.getWorldTime();
        }
    }
}
