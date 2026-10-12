package betamoon.client.render;

import betamoon.fluid.FluidCameraPolicy;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.contact.FluidContactRuntime;
import betamoon.resources.LuaTextureResources;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderEngine;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Render-thread consumption of immutable eye-fluid policies. */
public final class FluidCameraRuntime {
    private static final Map<FluidRegistration, OverlayBinding> OVERLAYS = new IdentityHashMap<>();
    private static final ThreadLocal<FloatBuffer> FOG_COLOR = ThreadLocal.withInitial(
            () -> BufferUtils.createFloatBuffer(4));

    private FluidCameraRuntime() {
    }

    public static FluidRegistration eyeFluid() {
        releaseStaleOverlays();
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        EntityLiving view = minecraft == null ? null : minecraft.renderViewEntity;
        return view == null || view.worldObj == null ? null : FluidContactRuntime.snapshot(view).eyeFluid();
    }

    public static boolean nativeCameraMaterial(EntityLiving view, Material material) {
        FluidRegistration fluid = eyeFluid();
        return fluid != null && !fluid.definition().isVanilla() ? false : view.isInsideOfMaterial(material);
    }

    public static boolean overlayVisible(EntityLiving player, Material material) {
        FluidRegistration fluid = eyeFluid();
        return fluid != null && !fluid.definition().isVanilla()
                ? fluid.definition().camera().hasOverlay() : player.isInsideOfMaterial(material);
    }

    public static boolean showAirHud(EntityLiving player, Material material) {
        FluidRegistration fluid = FluidContactRuntime.snapshot(player).eyeFluid();
        return fluid != null && !fluid.definition().isVanilla()
                ? fluid.definition().entity().drowning() : player.isInsideOfMaterial(material);
    }

    public static int overlayTexture(RenderEngine engine, String original) {
        if (!"/misc/water.png".equals(original)) {
            return engine.getTexture(original);
        }
        FluidRegistration fluid = eyeFluid();
        if (fluid == null || fluid.definition().isVanilla()) {
            return engine.getTexture(original);
        }
        FluidCameraPolicy policy = fluid.definition().camera();
        if (policy.nativeOverlay() != null) {
            return engine.getTexture(policy.nativeOverlay());
        }
        if (policy.overlayAsset() == null) {
            return engine.getTexture(original);
        }
        OverlayBinding current = OVERLAYS.get(fluid);
        if (current == null || current.policy != policy) {
            if (current != null) {
                LuaTextureResources.release(current.resource);
            }
            current = new OverlayBinding(policy, LuaTextureResources.register(policy.overlayAsset()));
            OVERLAYS.put(fluid, current);
        }
        return engine.getTexture(current.resource);
    }

    public static void applyFogColor() {
        FluidCameraPolicy policy = customPolicy();
        if (policy == null || policy.fogColor() == null) {
            return;
        }
        int color = policy.fogColor().intValue();
        GL11.glClearColor(((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F, 0.0F);
    }

    public static void applyFog() {
        FluidCameraPolicy policy = customPolicy();
        if (policy == null) {
            return;
        }
        if (policy.fogColor() != null) {
            int color = policy.fogColor().intValue();
            FloatBuffer rgba = FOG_COLOR.get();
            rgba.clear();
            rgba.put(((color >> 16) & 255) / 255.0F);
            rgba.put(((color >> 8) & 255) / 255.0F);
            rgba.put((color & 255) / 255.0F);
            rgba.put(1.0F);
            rgba.flip();
            GL11.glFog(GL11.GL_FOG_COLOR, rgba);
        }
        if (policy.fogDensity() != null) {
            GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
            GL11.glFogf(GL11.GL_FOG_DENSITY, policy.fogDensity().floatValue());
        }
    }

    private static FluidCameraPolicy customPolicy() {
        FluidRegistration fluid = eyeFluid();
        return fluid == null || fluid.definition().isVanilla() ? null : fluid.definition().camera();
    }

    private static void releaseStaleOverlays() {
        Iterator<Map.Entry<FluidRegistration, OverlayBinding>> iterator = OVERLAYS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<FluidRegistration, OverlayBinding> entry = iterator.next();
            if (entry.getKey().definition().camera() != entry.getValue().policy) {
                LuaTextureResources.release(entry.getValue().resource);
                iterator.remove();
            }
        }
    }

    private static final class OverlayBinding {
        private final FluidCameraPolicy policy;
        private final String resource;

        private OverlayBinding(FluidCameraPolicy policy, String resource) {
            this.policy = policy;
            this.resource = resource;
        }
    }
}
