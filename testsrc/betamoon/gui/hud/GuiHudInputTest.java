package betamoon.gui.hud;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.InputDeviceEvent;
import betamoon.client.control.input.InputFamily;
import betamoon.client.control.input.InputRouter;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GameSettings;
import net.minecraft.src.MovementInputFromOptions;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.World;
import net.minecraft.src.EntityPlayerSP;
import org.lwjgl.input.Keyboard;
import sun.misc.Unsafe;

/**
 * Displayed regional input and screenless pointer ownership without a game
 * process.
 */
final class GuiHudInputTest {
    static void run() throws Exception {
        regions();
        cursor();
        movementCapture();
    }

    private static void regions() {
        GuiScene scene = new GuiScene();
        Probe allowed = new Probe();
        Probe excluded = new Probe();
        GuiContainer root = new GuiContainer() {
            protected void arrangeChildren(GuiContext context) {
                for (GuiElement child : getChildren()) {
                    child.measure(context, Constraints.tight(20, 20));
                    child.arrange(context, Rect.fromPositionAndSize(child == allowed ? 0 : 50, 0, 20, 20));
                }
            }
        };
        root.add(allowed);
        root.add(excluded);
        allowed.setVisualTransform(new GraphicsTransform(10, 0, 1, 1));
        scene.updateEnvironment(null, null, 100, 60, 100, 60);
        scene.setContent(root);
        HeadlessGuiFrames.layout(scene);
        scene.runRegionalInput(Collections.singletonList(allowed), () -> {
            require(!scene.mousePressed(1, 2, 0), "Region input used unsampled bounds before a visual transform");
            require(!scene.mousePressed(51, 2, 0), "An undeclared region captured a pointer press");
            require(scene.mousePressed(11, 2, 0), "Displayed transformed region did not receive its press");
            require(scene.mouseReleased(11, 2, 0), "Declared region lost its matching release");
            require(scene.keyTyped('\t', Keyboard.KEY_TAB, false), "Regional keyboard focus was unavailable");
            require(scene.getFocusedElement() == allowed && excluded.presses == 0,
                    "Tab traversal escaped the declared region");
            require(scene.mousePressed(11, 2, 0), "Region could not begin a second gesture");
        });
        scene.runRegionalInput(Collections.emptyList(), () -> {
            require(scene.mouseReleased(11, 2, 0), "Revoked regional gesture leaked its release");
            require(scene.getFocusedElement() == null && allowed.releases == 1,
                    "Revoked region retained focus or dispatched an activation release");
        });
        require(scene.mousePressed(51, 2, 0) && excluded.presses == 1,
                "Regional filtering persisted outside its input scope");
        scene.cancelInput();
        require(scene.getFocusedElement() == null && excluded.cancelled == 1,
                "Host input cleanup retained a gesture or keyboard focus");
    }

    private static void cursor() throws Exception {
        Minecraft client = allocate(Client.class);
        client.inGameHasFocus = true;
        InputRouter router = new InputRouter((context, action, error) -> {
            throw new AssertionError(error);
        });
        router.beginCycle(null, null, null, true);
        Pointer pointer = new Pointer();
        boolean[] eligible = {true};
        GuiHudActivation claim = new GuiHudActivation("hud-cursor-test", "test:cursor", 1, Collections.emptySet(),
                () -> {
                });
        router.accept(InputDeviceEvent.mouseButton(0, true));
        GuiHudCursor cursor = new GuiHudCursor(client, claim, router, pointer, () -> eligible[0]);
        claim.interactionCleanup(cursor::close);
        require(cursor.ownsGuiInput() && !pointer.grabbed && router.captureCount() == 1
                && router.captures(InputFamily.LOOK) && router.captures(InputFamily.WORLD_ACTIONS)
                && router.captures(InputFamily.MOVEMENT) && router.captures(InputFamily.INVENTORY),
                "Cursor entry did not acquire native families or release the pointer");
        claim.close();
        require(!cursor.ownsGuiInput() && pointer.grabbed && router.captureCount() == 0,
                "Closed claim retained cursor ownership or failed to restore the original grab");
        require(router.accept(InputDeviceEvent.mouseButton(0, false)),
                "Cursor closure resumed a pointer held before interaction");
        for (int loss = 0; loss < 6; loss++) {
            GuiHudActivation next = new GuiHudActivation("hud-cursor-test", "test:cursor", loss + 2,
                    Collections.emptySet(), () -> {
                    });
            pointer.grabbed = true;
            pointer.focused = true;
            client.inGameHasFocus = true;
            client.currentScreen = null;
            client.theWorld = null;
            client.thePlayer = null;
            GuiHudCursor active = new GuiHudCursor(client, next, router, pointer, () -> eligible[0]);
            if (loss == 0) {
                pointer.focused = false;
            } else if (loss == 1) {
                client.inGameHasFocus = false;
            } else if (loss == 2) {
                ScriptResourceTracker.unload("hud-cursor-test");
            } else if (loss == 3) {
                client.currentScreen = new GuiScreen();
            } else if (loss == 4) {
                client.theWorld = allocate(World.class);
            } else {
                client.thePlayer = allocate(EntityPlayerSP.class);
            }
            require(!active.ownsGuiInput(), "Focus/unload loss retained native cursor ownership");
            active.close();
            require(router.captureCount() == 0 && (loss == 2 || !pointer.grabbed),
                    "Cursor cleanup retained captures or regrabbed an unfocused client");
            next.close();
        }
        router.clear();
        ScriptResourceTracker.unload("hud-cursor-test");
    }

    private static void movementCapture() {
        MovementInputFromOptions input = new MovementInputFromOptions(new GameSettings());
        InputRouter router = ClientInputRuntime.router();
        router.clear();
        try {
            router.captureNativeFamilies("hud-movement-test", EnumSet.of(InputFamily.MOVEMENT));
            input.moveForward = 1;
            input.moveStrafe = 1;
            input.jump = true;
            input.sneak = true;
            ClientInputRuntime.applyControllerMovement(input, null);
            require(input.moveForward == 0 && input.moveStrafe == 0 && !input.jump && !input.sneak,
                    "Native movement capture left existing/controller movement active");
        } finally {
            router.clear();
            ScriptResourceTracker.unload("hud-movement-test");
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class Pointer implements GuiHudCursor.Pointer {
        boolean grabbed = true;
        boolean focused = true;
        public boolean available() {
            return true;
        }

        public boolean focused() {
            return focused;
        }

        public boolean grabbed() {
            return grabbed;
        }

        public void grabbed(boolean value) {
            grabbed = value;
        }
    }

    private static final class Client extends Minecraft {
        private Client() {
            super(null, null, null, 320, 240, false);
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }

    private static final class Probe extends GuiElement {
        int presses;
        int releases;
        int cancelled;
        Probe() {
            setFocusable(true);
        }

        protected Size measureElement(GuiContext context, Constraints constraints) {
            return new Size(20, 20);
        }

        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                presses++;
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
                releases++;
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                cancelled++;
                return true;
            }
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
