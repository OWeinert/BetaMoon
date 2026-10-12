package betamoon.instrumentation.hooks.control;

import betamoon.client.control.input.ClientInputRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.EntityRenderer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.MovementInputFromOptions;

/**
 * Native-pass-through callbacks for verified controller seams. Runtime behavior
 * is attached incrementally without changing the default Minecraft path.
 */
public final class ClientControlCallbacks {
    private ClientControlCallbacks() {
    }

    public static int beginInputTick(Minecraft minecraft) {
        ClientInputRuntime.beginCycle(minecraft);
        return 0;
    }

    public static void finishInputTick(Minecraft minecraft) {
    }

    public static int beforeMovementKey(EntityPlayerSP player, int keyCode, boolean pressed) {
        return ClientInputRuntime.consumesMovementKey(player, keyCode) ? 1 : 0;
    }

    public static int beforeGuiKeyboardInput(GuiScreen screen) {
        return ClientInputRuntime.consumesCurrentKeyboardEvent() ? 1 : 0;
    }

    public static void afterGuiKeyboardInput(GuiScreen screen, int decision) {
    }

    public static int beforeGuiMouseInput(GuiScreen screen) {
        return ClientInputRuntime.consumesCurrentMouseEvent() ? 1 : 0;
    }

    public static void afterGuiMouseInput(GuiScreen screen, int decision) {
    }

    public static void afterMovementKey(EntityPlayerSP player, int keyCode, boolean pressed, int decision) {
    }

    public static int enterMovementIntent() {
        return 0;
    }

    public static void finishMovementIntent(MovementInputFromOptions input, EntityPlayer player) {
    }

    public static void applyLook(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        if (!ClientInputRuntime.consumesLook()) {
            player.func_346_d(yawDelta, pitchDelta);
        }
    }

    public static int beforeAction(Minecraft minecraft, int button) {
        return ClientInputRuntime.consumesWorldAction(button) ? 1 : 0;
    }

    public static void afterAction(Minecraft minecraft, int button, int decision) {
    }

    public static int beforeHeldBreaking(Minecraft minecraft, int button, boolean held) {
        return ClientInputRuntime.consumesWorldAction(button) ? 1 : 0;
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

    public static boolean nextKeyboardEvent() {
        return ClientInputRuntime.nextKeyboardEvent();
    }

    public static boolean keyboardEventState() {
        return ClientInputRuntime.keyboardEventState();
    }

    public static boolean nextMouseEvent() {
        return ClientInputRuntime.nextMouseEvent();
    }

    public static boolean mouseEventButtonState() {
        return ClientInputRuntime.mouseEventButtonState();
    }

    public static int mouseEventWheel() {
        return ClientInputRuntime.mouseEventWheel();
    }
}
