package betamoon.gui.hud;

import betamoon.client.control.input.GuiInputOwner;
import betamoon.client.control.input.InputFamily;
import betamoon.client.control.input.InputFamilyCapture;
import betamoon.client.control.input.InputRouter;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Owns a screenless pointer without changing Minecraft's screen or focus state.
 */
final class GuiHudCursor implements AutoCloseable, GuiInputOwner {
    interface Pointer {
        boolean available();

        boolean focused();

        boolean grabbed();

        void grabbed(boolean value);
    }

    static final Pointer NATIVE_POINTER = new Pointer() {
        public boolean available() {
            return Mouse.isCreated();
        }

        public boolean focused() {
            return !Display.isCreated() || Display.isActive();
        }

        public boolean grabbed() {
            return Mouse.isGrabbed();
        }

        public void grabbed(boolean value) {
            Mouse.setGrabbed(value);
            Mouse.getDX();
            Mouse.getDY();
        }
    };

    final GuiHudActivation claim;
    private final Minecraft client;
    private final Pointer pointer;
    private final BooleanSupplier eligible;
    private final InputFamilyCapture capture;
    private final boolean restoreGrab;
    private final Object world;
    private final Object player;
    private boolean closed;

    GuiHudCursor(Minecraft client, GuiHudActivation claim, InputRouter router, Pointer pointer,
            BooleanSupplier eligible) {
        if (client.currentScreen != null || !client.inGameHasFocus || !pointer.available() || !pointer.focused()
                || !eligible.getAsBoolean()) {
            throw new IllegalStateException("Cursor requires an eligible focused gameplay HUD");
        }
        this.client = client;
        this.claim = claim;
        this.pointer = pointer;
        this.eligible = eligible;
        world = client.theWorld;
        player = client.thePlayer;
        restoreGrab = pointer.grabbed();
        capture = router.captureNativeFamilies(claim.owner,
                EnumSet.of(InputFamily.LOOK, InputFamily.WORLD_ACTIONS, InputFamily.MOVEMENT, InputFamily.INVENTORY));
        try {
            if (client.thePlayer != null && client.thePlayer.movementInput != null) {
                client.thePlayer.resetPlayerKeyState();
            }
            pointer.grabbed(false);
        } catch (RuntimeException | Error failure) {
            capture.close();
            throw failure;
        }
    }

    public boolean ownsGuiInput() {
        return !closed && claim.requested() && capture.isActive() && client.currentScreen == null
                && client.theWorld == world && client.thePlayer == player && client.inGameHasFocus
                && pointer.available() && pointer.focused() && eligible.getAsBoolean();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        capture.close();
        if (restoreGrab && client.currentScreen == null && client.inGameHasFocus && pointer.available()
                && pointer.focused() && client.theWorld == world && client.thePlayer == player) {
            pointer.grabbed(true);
        }
    }
}
