package betamoon.instrumentation.hooks.gui;

import betamoon.gui.hud.GuiHudNativePart;
import betamoon.gui.hud.GuiHudRuntime;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.instrumentation.agent.AgentRuntime;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiIngame;
import org.lwjgl.opengl.GL11;

/**
 * Typed presentation seams preserve original native receivers, values and
 * gameplay state.
 */
public final class GuiHudCallbacks {
    public static int beginClientFrame() {
        return 0;
    }

    public static int beforeNative(GuiIngame hud, Minecraft client, float partialTicks, int x, int y, int counter) {
        if (hud.getClass() == GuiIngame.class) {
            GuiHudRuntime.beginNative(client, x, y, partialTicks, counter);
        } else {
            GuiHudRuntime.endNative();
        }
        return 0;
    }

    public static void afterNative() {
        GuiHudRuntime.endNative();
        GuiHudRuntime.worldOverlay();
    }

    public static void nativeFailed() {
        GuiHudRuntime.endNative();
    }

    public static void clientFrame(Minecraft client, float partialTicks) {
        GuiHudRuntime.clientOverlay(client, partialTicks);
    }

    public static void texture(GuiIngame hud, int x, int y, int u, int v, int width, int height) {
        GuiHudNativePart part = GuiHudNativePart.texture(u, v, width, height);
        if (part == null || !GuiHudRuntime.nativePart(part)) {
            int transformed = part == null ? 0 : beginTransform(part);
            try {
                hud.drawTexturedModalRect(x, y, u, v, width, height);
            } finally {
                endTransform(transformed);
            }
        }
    }

    public static boolean atlasAvailable() {
        return AgentRuntime.getReport().isApplied(GuiHudHook.TEXTURES);
    }

    public static int armor(EntityPlayerSP player) {
        if (atlasAvailable()) {
            GuiHudRuntime.nativePart(GuiHudNativePart.ARMOR);
        }
        return player.getPlayerArmorValue();
    }

    public static int beforeHotbar() {
        return GuiHudRuntime.nativeSuppressed(GuiHudNativePart.HOTBAR) ? 1 : 0;
    }

    public static void afterHotbar() {
    }

    public static int beginHotbarTransform() {
        return beginTransform(GuiHudNativePart.HOTBAR);
    }

    private static int beginTransform(GuiHudNativePart part) {
        GraphicsTransform transform = GuiHudRuntime.nativeTransform(part);
        if (transform == null) {
            return 0;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(transform.x, transform.y, 0);
        GL11.glScaled(transform.scaleX, transform.scaleY, 1);
        return 1;
    }

    public static void endTransform(int transformed) {
        if (transformed != 0) {
            GL11.glPopMatrix();
        }
    }

    public static List<?> chat(List<?> original) {
        return GuiHudRuntime.nativePart(GuiHudNativePart.CHAT) ? Collections.emptyList() : original;
    }

    private GuiHudCallbacks() {
    }
}
