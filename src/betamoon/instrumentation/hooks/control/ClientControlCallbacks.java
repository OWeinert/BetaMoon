package betamoon.instrumentation.hooks.control;

import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.EntityRenderer;
import net.minecraft.src.MovementInputFromOptions;

/**
 * Native-pass-through callbacks for verified controller seams. Runtime behavior
 * is attached incrementally without changing the default Minecraft path.
 */
public final class ClientControlCallbacks {
    private ClientControlCallbacks() {
    }

    public static int beginInputTick(Minecraft minecraft) {
        return 0;
    }

    public static void finishInputTick(Minecraft minecraft) {
    }

    public static int beforeMovementKey(EntityPlayerSP player, int keyCode, boolean pressed) {
        return 0;
    }

    public static void afterMovementKey(EntityPlayerSP player, int keyCode, boolean pressed, int decision) {
    }

    public static int enterMovementIntent() {
        return 0;
    }

    public static void finishMovementIntent(MovementInputFromOptions input, EntityPlayer player) {
    }

    public static void applyLook(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        player.func_346_d(yawDelta, pitchDelta);
    }

    public static int beforeAction(Minecraft minecraft, int button) {
        return 0;
    }

    public static void afterAction(Minecraft minecraft, int button, int decision) {
    }

    public static int beforeHeldBreaking(Minecraft minecraft, int button, boolean held) {
        return 0;
    }

    public static void afterHeldBreaking(Minecraft minecraft, int button, boolean held, int decision) {
    }

    public static int beforeTargeting(EntityRenderer renderer, float partialTick) {
        return 0;
    }

    public static void afterTargeting(EntityRenderer renderer, float partialTick, int decision) {
    }

    public static int beforeCamera(EntityRenderer renderer, float partialTick) {
        return 0;
    }

    public static void afterCamera(EntityRenderer renderer, float partialTick, int decision) {
    }

    public static int beforeProjection(EntityRenderer renderer, float partialTick, int eye) {
        return 0;
    }

    public static void afterProjection(EntityRenderer renderer, float partialTick, int eye, int decision) {
    }
}
