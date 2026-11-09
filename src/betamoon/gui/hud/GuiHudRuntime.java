package betamoon.gui.hud;

import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.LuaApiUtils;
import betamoon.gui.screen.GuiScreenActivation;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.src.World;

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
            current.beginNative(mouseX, mouseY, partialTicks);
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
