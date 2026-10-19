package betamoon.teleport;

import betamoon.dimension.DimensionRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;

/**
 * Logical-client integration. Rendering never advances preparation, and
 * ordinary native calls stay unchanged.
 */
public final class TeleportRuntime {
    private static Minecraft minecraft;
    private static TeleportService service;
    private static int nativeSwitchDepth;

    private TeleportRuntime() {
    }

    public static synchronized TeleportService service() {
        Minecraft current = ModLoader.getMinecraftInstance();
        if (current == null) {
            throw new IllegalStateException("unsupported_environment: no local Minecraft client");
        }
        return forClient(current);
    }

    private static synchronized TeleportService forClient(Minecraft current) {
        if (service == null || minecraft != current) {
            minecraft = current;
            service = new TeleportService(new NativeTeleportClient(current));
        }
        return service;
    }

    public static int respawn(Minecraft current, boolean ignoreBed) {
        if (current.theWorld == null || current.theWorld.multiplayerWorld
                || DimensionRuntime.binding(current.theWorld) == null) {
            return 0;
        }
        TeleportService.Acceptance accepted = forClient(current).respawn(current.thePlayer,
                new TeleportOwner("betamoon:native_respawn"), ignoreBed);
        if (accepted.ticket == null && !accepted.reason.equals("busy")) {
            new NativeTeleportClient(current).recovery("Respawn could not be prepared: " + accepted.reason);
        }
        return 1;
    }

    public static void tick(Minecraft current) {
        SavedDimensionLoad.tick(current);
        if (service != null && minecraft == current) {
            service.tick();
        }
    }

    public static boolean busy() {
        return SavedDimensionLoad.active() || service != null && service.busy();
    }

    public static void recover(Minecraft current, String detail) {
        forClient(current).enterRecovery();
        new NativeTeleportClient(current).recovery(detail);
    }

    public static boolean frozen() {
        return SavedDimensionLoad.active() || service != null && service.frozen();
    }

    public static boolean nativeSwitching() {
        return nativeSwitchDepth != 0;
    }

    public static NativeSwitch beginNativeSwitch() {
        nativeSwitchDepth++;
        return new NativeSwitch();
    }

    public static boolean startingWorldLoad() {
        if (SavedDimensionLoad.active() || service != null && service.busy() && !service.recovering()) {
            return false;
        }
        service = null;
        minecraft = null;
        return true;
    }

    public static final class NativeSwitch implements AutoCloseable {
        private boolean closed;

        private NativeSwitch() {
        }

        public void close() {
            if (!closed) {
                closed = true;
                nativeSwitchDepth--;
            }
        }
    }
}
