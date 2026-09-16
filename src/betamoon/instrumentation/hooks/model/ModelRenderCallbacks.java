package betamoon.instrumentation.hooks.model;

import betamoon.luaapi.item.ItemModelRenderer;
import betamoon.luaapi.block.BlockModelScene;
import net.minecraft.src.RenderGlobal;
import net.minecraft.src.ModLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Vec3D;
import net.minecraft.src.Chunk;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.ItemStack;

/**
 * Narrow visual hooks; no gameplay mutation or instance state is introduced
 * here.
 */
public final class ModelRenderCallbacks {
    private static float partialTick;

    private ModelRenderCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static void chunkLoaded(Chunk chunk) {
        BlockModelScene.loaded(chunk);
    }

    public static void chunkUnloaded(Chunk chunk) {
        BlockModelScene.unloaded(chunk);
    }

    public static void chunkChanged(Chunk chunk, int x, int y, int z, boolean changed) {
        BlockModelScene.changed(chunk, x, y, z, changed);
    }

    public static void worldModels(RenderGlobal renderer, Vec3D camera, float partial) {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft == null || minecraft.renderViewEntity == null) {
            return;
        }
        EntityLiving view = minecraft.renderViewEntity;
        Vec3D origin = Vec3D.createVector(view.lastTickPosX + (view.posX - view.lastTickPosX) * partial,
                view.lastTickPosY + (view.posY - view.lastTickPosY) * partial,
                view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial);
        BlockModelScene.render(renderer.worldObj, origin, partial);
    }

    public static int frame(float partial) {
        partialTick = partial;
        return 0;
    }

    public static float partialTick() {
        return partialTick;
    }

    public static int held(EntityLiving holder, ItemStack stack) {
        return ItemModelRenderer.held(holder, stack);
    }

    public static int gui(int id, int metadata, int x, int y) {
        return ItemModelRenderer.gui(id, metadata, x, y);
    }

    public static int ground(EntityItem entity, double x, double y, double z, float yaw, float partial) {
        return ItemModelRenderer.ground(entity, x, y, z, partial);
    }

    public static void complete() {
    }
}
