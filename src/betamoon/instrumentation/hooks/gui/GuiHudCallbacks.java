package betamoon.instrumentation.hooks.gui;

import betamoon.gui.hud.GuiHudNativePart;
import betamoon.gui.hud.GuiHudRuntime;
import betamoon.instrumentation.agent.AgentRuntime;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiIngame;

/**
 * Typed presentation seams preserve original native receivers, values and
 * gameplay state.
 */
public final class GuiHudCallbacks {
    public static int beginClientFrame() {
        return 0;
    }

    public static int beforeNative(GuiIngame hud, Minecraft client, float partialTicks, int x, int y) {
        if (hud.getClass() == GuiIngame.class) {
            GuiHudRuntime.beginNative(client, x, y, partialTicks);
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
            hud.drawTexturedModalRect(x, y, u, v, width, height);
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

    public static List<?> chat(List<?> original) {
        return GuiHudRuntime.nativePart(GuiHudNativePart.CHAT) ? Collections.emptyList() : original;
    }

    private GuiHudCallbacks() {
    }
}
