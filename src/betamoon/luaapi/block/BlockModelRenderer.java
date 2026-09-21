package betamoon.luaapi.block;

import betamoon.client.render.ModelAppearance;
import betamoon.client.render.ModelRenderer;
import net.minecraft.src.Block;
import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;
import net.minecraft.src.World;
import net.minecraft.src.ModLoader;
import org.lwjgl.opengl.GL11;

/**
 * Draws one client-only block appearance without a world tile entity.
 */
public final class BlockModelRenderer {
    private Tessellator fallbackTessellator = new Tessellator();

    public void render(World world, int blockX, int blockY, int blockZ, double x, double y, double z,
            float partialTick) {
        if (world == null) {
            return;
        }
        int id = world.getBlockId(blockX, blockY, blockZ);
        if (!BlockModelRegistry.isDynamic(id)) {
            return;
        }
        // Selection and callback state use the same current metadata.
        int metadata = world.getBlockMetadata(blockX, blockY, blockZ);
        ModelAppearance appearance = BlockModelRegistry.get(id, metadata);
        GL11.glPushMatrix();
        try {
            if (appearance == null) {
                GL11.glTranslated(x - blockX, y - blockY, z - blockZ);
                renderOrdinary(world, blockX, blockY, blockZ, Block.blocksList[id]);
            } else {
                GL11.glTranslated(x + 0.5, y, z + 0.5);
                ModelRenderer.render(appearance, "block", world.getWorldTime() + partialTick, metadata, blockX, blockY,
                        blockZ, world.getLightBrightness(blockX, blockY, blockZ));
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void renderOrdinary(World world, int blockX, int blockY, int blockZ, Block block) {
        Tessellator previous = Tessellator.instance;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    ModLoader.getMinecraftInstance().renderEngine.getTexture("/terrain.png"));
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glEnable(GL11.GL_CULL_FACE);
            boolean translucent = block.getRenderBlockPass() == 1;
            GL11.glDepthMask(!translucent);
            if (translucent) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            Tessellator.instance = fallbackTessellator;
            fallbackTessellator.startDrawingQuads();
            RenderBlocks renderer = new RenderBlocks(world);
            renderer.renderAllFaces = true;
            BlockModelRegistry.renderOrdinaryWorld(renderer, block, blockX, blockY, blockZ);
            fallbackTessellator.draw();
        } catch (RuntimeException error) {
            // A failed tessellation must not poison the next invocation's drawing state.
            fallbackTessellator = new Tessellator();
            throw error;
        } finally {
            Tessellator.instance = previous;
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
        }
    }
}
