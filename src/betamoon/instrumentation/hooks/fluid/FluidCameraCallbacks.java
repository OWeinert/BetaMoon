package betamoon.instrumentation.hooks.fluid;

import betamoon.client.render.FluidCameraRuntime;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.Material;
import net.minecraft.src.RenderEngine;

/** Mapped client callbacks; renderer work never invokes Lua. */
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
        return FluidCameraRuntime.showAirHud(player, material);
    }

    public static int overlayTexture(RenderEngine engine, String path) {
        return FluidCameraRuntime.overlayTexture(engine, path);
    }

    public static void fogColor() {
        FluidCameraRuntime.applyFogColor();
    }

    public static void fog() {
        FluidCameraRuntime.applyFog();
    }
}
