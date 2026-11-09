package betamoon.gui.hud;

import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.LuaApiUtils;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.instrumentation.agent.AgentRuntime;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Logical HUD lifecycle and native render seams; metadata alone changes
 * nothing.
 */
public final class GuiHudRuntime {
    private static final GuiHudRegistry REGISTRY = new GuiHudRegistry();
    private static GuiHudHost current;
    private static Refusal rejected;

    public static GuiHudRegistry registry() {
        return REGISTRY;
    }

    public static GuiScreenActivation instance(GuiHudActivation claim, GuiHudNativePart part) {
        return current == null || !current.valid() ? null : current.instance(claim, part);
    }

    public static String enterCursorMode(GuiHudActivation claim) {
        return current != null && current.valid() ? current.enterCursorMode(claim) : "not_ready";
    }

    public static boolean inputAvailable(GuiHudActivation claim) {
        return current != null && current.valid() && current.inputAvailable(claim);
    }

    public static boolean cursorMode(GuiHudActivation claim) {
        return current != null && current.valid() && current.cursorMode(claim);
    }

    public static void stopInteraction(GuiHudActivation claim) {
        if (current != null) {
            current.stopInteraction(claim);
        }
    }

    public static Map<String, Object> capabilities() {
        Map<String, Object> parts = new LinkedHashMap<>();
        for (GuiHudNativePart part : GuiHudNativePart.values()) {
            parts.put(part.name, nativeAvailable(part));
        }
        return Collections.unmodifiableMap(parts);
    }

    private static boolean nativeAvailable(GuiHudNativePart part) {
        if (!AgentRuntime.getReport().isApplied("betamoon:gui_hud:native_frame")) {
            return false;
        }
        if (part == GuiHudNativePart.CHAT) {
            return AgentRuntime.getReport().isApplied("betamoon:gui_hud:chat");
        }
        if (!AgentRuntime.getReport().isApplied("betamoon:gui_hud:textures")) {
            return false;
        }
        switch (part) {
            case HOTBAR:
                return AgentRuntime.getReport().isApplied("betamoon:gui_hud:hotbar_items");
            case ARMOR:
                return AgentRuntime.getReport().isApplied("betamoon:gui_hud:armor");
            case BREATH:
                return AgentRuntime.getReport().isApplied("betamoon:fluid_camera:air_hud");
            default:
                return true;
        }
    }

    public static Map<String, Object> parts(GuiHudActivation claim) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (GuiHudNativePart part : claim.requestedParts()) {
            String status = !claim.requested()
                    ? "closed"
                    : current != null && current.valid() ? current.partStatus(claim, part) : "queued";
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("status", status);
            if (status.equals("unavailable")) {
                value.put("reason", nativeAvailable(part) ? "not_rendered" : "native_hook_unavailable");
            }
            result.put(part.name, Collections.unmodifiableMap(value));
        }
        return Collections.unmodifiableMap(result);
    }

    public static boolean keyboard() {
        return keyboard(Keyboard.getEventCharacter(), Keyboard.getEventKey(), Keyboard.getEventKeyState(), shift(),
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
    }

    public static boolean keyboard(char character, int key, boolean down, boolean shift, boolean control) {
        if (current == null || !current.valid()) {
            return false;
        }
        GuiHudHost receiving = current;
        try {
            return receiving.keyboard(character, key, down, shift, control);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            receiving.failed(failure);
            return false;
        }
    }

    public static boolean mouse() {
        if (current == null || current.client.displayWidth <= 0 || current.client.displayHeight <= 0) {
            return false;
        }
        return mouse(Mouse.getEventX(), Mouse.getEventY(), Mouse.getEventButton(), Mouse.getEventButtonState(),
                Mouse.getEventDWheel(), shift());
    }

    /**
     * Native framebuffer coordinates, translated by the same displayed viewport as
     * painting.
     */
    public static boolean mouse(int x, int y, int button, boolean down, int wheel, boolean shift) {
        if (current == null || !current.valid()) {
            return false;
        }
        GuiHudHost receiving = current;
        try {
            return receiving.mouseEvent(x, y, button, down, wheel, shift);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            receiving.failed(failure);
            return false;
        }
    }

    private static boolean shift() {
        return Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
    }

    public static void tick(Minecraft client) {
        if (current != null && (current.client != client || !current.valid() || !current.available())) {
            close();
        }
        if (client == null || REGISTRY.activations().isEmpty()) {
            close();
            rejected = null;
            return;
        }
        if (rejected != null && rejected.matches(client, REGISTRY.revision())) {
            return;
        }
        rejected = null;
        if (client.fontRenderer == null || client.renderEngine == null || client.displayWidth <= 0
                || client.displayHeight <= 0) {
            return;
        }
        if (current == null && NativeDiscoveryRuntime.bindingsIfAvailable() != null) {
            try {
                current = new GuiHudHost(client, REGISTRY);
            } catch (RuntimeException failure) {
                rejected = new Refusal(client, REGISTRY.revision());
                for (GuiHudActivation activation : REGISTRY.activations()) {
                    activation.publish("refused", "client_resources_unavailable", null);
                }
                LuaApiUtils.warnForScript("<engine-hud>", "UI HUD", "HUD resources unavailable", failure);
                return;
            }
        }
        if (current != null) {
            GuiHudHost updating = current;
            try {
                updating.tick();
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                updating.failed(failure);
            }
            if (current == updating && !updating.hasLiveClaims()) {
                rejected = new Refusal(client, REGISTRY.revision());
                close();
            }
        }
    }

    public static void beginNative(Minecraft client, int mouseX, int mouseY, float partialTicks) {
        if (current != null && current.client == client && current.valid()) {
            GuiHudHost rendering = current;
            try {
                rendering.beginNative(mouseX, mouseY, partialTicks);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                rendering.nativeDrawing = false;
                rendering.failed(failure);
            }
        }
    }

    public static void endNative() {
        if (current != null) {
            current.nativeDrawing = false;
        }
    }

    public static boolean nativePart(GuiHudNativePart part) {
        if (current == null || !current.valid()) {
            return false;
        }
        GuiHudHost rendering = current;
        try {
            return rendering.nativePart(part);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            rendering.failed(failure);
            return false;
        }
    }

    public static boolean nativeSuppressed(GuiHudNativePart part) {
        return current != null && current.valid() && current.nativeSuppressed(part);
    }

    public static void clientOverlay(Minecraft client, float partialTicks) {
        if (current != null && current.client == client && current.valid()) {
            GuiHudHost rendering = current;
            try {
                rendering.clientOverlay(partialTicks);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                rendering.failed(failure);
            }
        }
    }

    public static void clientOverlay(Minecraft client, int mouseX, int mouseY, float partialTicks) {
        if (current != null && current.client == client && current.valid()) {
            GuiHudHost rendering = current;
            try {
                rendering.clientOverlay(mouseX, mouseY, partialTicks);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                rendering.failed(failure);
            }
        }
    }

    public static void worldTick(Minecraft client, World world) {
        if (current != null && current.client == client && current.valid() && client.theWorld == world) {
            current.worldTick();
        }
    }

    public static void close() {
        GuiHudHost previous = current;
        current = null;
        if (previous != null) {
            previous.close();
        }
    }

    /** Refused environments never pin a closed native screen, world or player. */
    private static final class Refusal {
        private final WeakReference<Minecraft> client;
        private final WeakReference<Object> screen;
        private final WeakReference<Object> world;
        private final WeakReference<Object> player;
        private final boolean hadScreen;
        private final boolean hadWorld;
        private final boolean hadPlayer;
        private final long revision;

        Refusal(Minecraft client, long revision) {
            this.client = new WeakReference<>(client);
            screen = new WeakReference<>(client.currentScreen);
            world = new WeakReference<>(client.theWorld);
            player = new WeakReference<>(client.thePlayer);
            hadScreen = client.currentScreen != null;
            hadWorld = client.theWorld != null;
            hadPlayer = client.thePlayer != null;
            this.revision = revision;
        }

        boolean matches(Minecraft value, long revision) {
            return client.get() == value && this.revision == revision && matches(screen, hadScreen, value.currentScreen)
                    && matches(world, hadWorld, value.theWorld) && matches(player, hadPlayer, value.thePlayer);
        }

        private boolean matches(WeakReference<Object> captured, boolean hadValue, Object current) {
            return hadValue ? current != null && captured.get() == current : current == null;
        }
    }

    private GuiHudRuntime() {
    }
}
