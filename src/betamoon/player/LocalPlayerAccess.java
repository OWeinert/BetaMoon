package betamoon.player;

import betamoon.entity.motion.EntitySimulationThread;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

/** Native-client supplied resolver, with no cached player/world Java object. */
public final class LocalPlayerAccess {
    public interface Provider {
        EntityPlayer player();

        World world();
    }

    public static final class Resolution {
        public final EntityPlayer player;
        public final String reason;

        private Resolution(EntityPlayer player, String reason) {
            this.player = player;
            this.reason = reason;
        }
    }

    private static volatile Provider provider;

    private LocalPlayerAccess() {
    }

    public static void initialize(Provider value) {
        EntitySimulationThread.requireCurrent();
        if (value == null) {
            throw new IllegalArgumentException("Local player provider is required");
        }
        provider = value;
    }

    public static EntityPlayer current() {
        return resolve().player;
    }

    public static Resolution resolve() {
        if (provider == null) {
            return new Resolution(null, "no_player");
        }
        EntitySimulationThread.requireCurrent();
        EntityPlayer player = provider.player();
        World world = provider.world();
        if (player == null) {
            return new Resolution(null, "no_player");
        }
        if (world == null || player.worldObj != world || player.isDead || player.health <= 0) {
            return new Resolution(null, "player_not_ready");
        }
        if (world.multiplayerWorld) {
            return new Resolution(null, "unsupported_environment");
        }
        return new Resolution(player, null);
    }
}
