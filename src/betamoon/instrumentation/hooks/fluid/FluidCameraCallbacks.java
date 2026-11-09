package betamoon.instrumentation.hooks.fluid;

import betamoon.client.atmosphere.AtmosphereRuntime;
import betamoon.client.render.FluidCameraRuntime;
import betamoon.gui.hud.GuiHudNativePart;
import betamoon.gui.hud.GuiHudRuntime;
import betamoon.instrumentation.hooks.gui.GuiHudCallbacks;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.Material;
import net.minecraft.src.RenderEngine;

/** Mapped client callbacks; ordinary fluid rendering remains Java-only. */
public final class FluidCameraCallbacks {
    private FluidCameraCallbacks() {
    }

    public static int pass() {
        return 0;
    }

    public static boolean nativeCameraMaterial(EntityLiving view, Material material) {
        return FluidCameraRuntime.nativeCameraMaterial(view, material);
    }

    public static boolean overlayVisible(EntityPlayerSP player, Material material) {
        return FluidCameraRuntime.overlayVisible(player, material);
    }

    public static boolean showAirHud(EntityPlayerSP player, Material material) {
        boolean visible = FluidCameraRuntime.showAirHud(player, material);
        if (visible && GuiHudCallbacks.atlasAvailable()) {
            GuiHudRuntime.nativePart(GuiHudNativePart.BREATH);
        }
        return visible;
    }

    public static int overlayTexture(RenderEngine engine, String path) {
        return FluidCameraRuntime.overlayTexture(engine, path);
    }

    public static void fogColor(boolean cloud) {
        AtmosphereRuntime.fogColor(cloud);
        FluidCameraRuntime.applyFogColor();
    }

    public static void fog(boolean cloud) {
        AtmosphereRuntime.fog(cloud);
        FluidCameraRuntime.applyFog();
    }
}
