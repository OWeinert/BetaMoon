package betamoon.luaapi.item;

import betamoon.client.render.ModelAppearance;
import betamoon.client.render.ModelRenderer;
import betamoon.instrumentation.hooks.model.ModelRenderCallbacks;
import betamoon.luaapi.block.BlockModelRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import org.lwjgl.opengl.GL11;

/**
 * Display adapters for native item drawing, including block items with model
 * appearances.
 */
public final class ItemModelRenderer {
    private ItemModelRenderer() {
    }

    static ModelAppearance appearance(int id, int metadata) {
        return id < 256 ? BlockModelRegistry.get(id, metadata) : ItemModelRegistry.get(id, metadata);
    }

    public static int held(EntityLiving holder, ItemStack stack) {
        ModelAppearance appearance = stack == null ? null : appearance(stack.itemID, stack.getItemDamage());
        if (appearance == null) {
            return 0;
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(0, -0.5, 0);
            float partial = ModelRenderCallbacks.partialTick();
            ModelRenderer.render(appearance, "held", holder.ticksExisted + partial, stack.getItemDamage(), holder.posX,
                    holder.posY, holder.posZ, holder.getEntityBrightness(partial));
        } finally {
            GL11.glPopMatrix();
        }
        return 1;
    }

    public static int gui(int id, int metadata, int x, int y) {
        ModelAppearance appearance = appearance(id, metadata);
        if (appearance == null) {
            return 0;
        }
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        double age = minecraft.theWorld == null
                ? 0
                : minecraft.theWorld.getWorldTime() + ModelRenderCallbacks.partialTick();
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + 8, y + 12, 0);
            GL11.glScaled(12, -12, 12);
            GL11.glRotated(25, 1, 0, 0);
            GL11.glRotated(-45, 0, 1, 0);
            ModelRenderer.render(appearance, "gui", age, metadata, 0, 0, 0, 1);
        } finally {
            GL11.glPopMatrix();
        }
        return 1;
    }

    public static int ground(EntityItem entity, double x, double y, double z, float partialTick) {
        ModelAppearance appearance = entity.item == null
                ? null
                : appearance(entity.item.itemID, entity.item.getItemDamage());
        if (appearance == null) {
            return 0;
        }
        double age = entity.age + partialTick;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y + 0.1 + Math.sin(age / 10 + entity.field_804_d) * 0.1, z);
            GL11.glRotated((age / 20 + entity.field_804_d) * 180 / Math.PI, 0, 1, 0);
            GL11.glScaled(0.5, 0.5, 0.5);
            ModelRenderer.render(appearance, "ground", age, entity.item.getItemDamage(), entity.posX, entity.posY,
                    entity.posZ, entity.getEntityBrightness(partialTick));
        } finally {
            GL11.glPopMatrix();
        }
        return 1;
    }
}
