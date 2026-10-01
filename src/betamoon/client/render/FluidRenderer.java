package betamoon.client.render;

import betamoon.fluid.FluidRegistry;
import betamoon.fluid.FluidRenderType;
import betamoon.fluid.FluidState;
import betamoon.fluid.block.FluidBlock;
import net.minecraft.src.BaseMod;
import net.minecraft.src.Block;
import net.minecraft.src.EntityRenderer;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.MathHelper;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;

/** Identity-aware chunk renderer for custom fluid surfaces. */
public final class FluidRenderer {
    private FluidRenderer() {
    }

    public static void initialize(BaseMod mod) {
        if (FluidRenderType.get() < 0) {
            FluidRenderType.install(ModLoader.getUniqueBlockModelID(mod, true));
        }
    }

    public static boolean handles(Block block, int renderType) {
        return block instanceof FluidBlock && renderType == FluidRenderType.get();
    }

    public static boolean render(RenderBlocks renderer, IBlockAccess world, int x, int y, int z,
            FluidBlock block) {
        boolean top = block.shouldSideBeRendered(world, x, y + 1, z, 1);
        boolean bottom = block.shouldSideBeRendered(world, x, y - 1, z, 0);
        boolean[] sides = {
            block.shouldSideBeRendered(world, x, y, z - 1, 2),
            block.shouldSideBeRendered(world, x, y, z + 1, 3),
            block.shouldSideBeRendered(world, x - 1, y, z, 4),
            block.shouldSideBeRendered(world, x + 1, y, z, 5)
        };
        if (!top && !bottom && !sides[0] && !sides[1] && !sides[2] && !sides[3]) {
            return false;
        }

        int tint = block.colorMultiplier(world, x, y, z);
        float red = (tint >> 16 & 255) / 255.0F;
        float green = (tint >> 8 & 255) / 255.0F;
        float blue = (tint & 255) / 255.0F;
        if (EntityRenderer.field_28135_a) {
            float grayscale = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float adjustedGreen = (red * 30.0F + green * 70.0F) / 100.0F;
            float adjustedBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = grayscale;
            green = adjustedGreen;
            blue = adjustedBlue;
        }

        float northWest = cornerHeight(world, block.blockID, x, y, z);
        float southWest = cornerHeight(world, block.blockID, x, y, z + 1);
        float southEast = cornerHeight(world, block.blockID, x + 1, y, z + 1);
        float northEast = cornerHeight(world, block.blockID, x + 1, y, z);
        Tessellator tessellator = Tessellator.instance;
        boolean rendered = false;
        int metadata = world.getBlockMetadata(x, y, z);

        if (renderer.renderAllFaces || top) {
            int texture = block.getBlockTextureFromSideAndMetadata(1, metadata);
            float angle = (float) block.flowAngle(world, x, y, z);
            if (angle > -999.0F) {
                texture = block.getBlockTextureFromSideAndMetadata(2, metadata);
            }
            int textureX = (texture & 15) << 4;
            int textureY = texture & 240;
            double centerU = (textureX + 8.0D) / 256.0D;
            double centerV = (textureY + 8.0D) / 256.0D;
            if (angle < -999.0F) {
                angle = 0.0F;
            } else {
                centerU = (textureX + 16.0F) / 256.0F;
                centerV = (textureY + 16.0F) / 256.0F;
            }
            float sin = MathHelper.sin(angle) * 8.0F / 256.0F;
            float cos = MathHelper.cos(angle) * 8.0F / 256.0F;
            float brightness = block.getBlockBrightness(world, x, y, z);
            tessellator.setColorOpaque_F(brightness * red, brightness * green, brightness * blue);
            tessellator.addVertexWithUV(x, y + northWest, z, centerU - cos - sin, centerV - cos + sin);
            tessellator.addVertexWithUV(x, y + southWest, z + 1, centerU - cos + sin, centerV + cos + sin);
            tessellator.addVertexWithUV(x + 1, y + southEast, z + 1, centerU + cos + sin, centerV + cos - sin);
            tessellator.addVertexWithUV(x + 1, y + northEast, z, centerU + cos - sin, centerV - cos - sin);
            rendered = true;
        }

        if (renderer.renderAllFaces || bottom) {
            float brightness = block.getBlockBrightness(world, x, y - 1, z);
            tessellator.setColorOpaque_F(0.5F * brightness, 0.5F * brightness, 0.5F * brightness);
            renderer.renderBottomFace(block, x, y, z, block.getBlockTextureFromSide(0));
            rendered = true;
        }

        for (int side = 0; side < 4; side++) {
            if (!renderer.renderAllFaces && !sides[side]) {
                continue;
            }
            float firstHeight;
            float secondHeight;
            float firstX;
            float secondX;
            float firstZ;
            float secondZ;
            int neighborX = x;
            int neighborZ = z;
            if (side == 0) {
                firstHeight = northWest;
                secondHeight = northEast;
                firstX = x;
                secondX = x + 1;
                firstZ = secondZ = z;
                neighborZ--;
            } else if (side == 1) {
                firstHeight = southEast;
                secondHeight = southWest;
                firstX = x + 1;
                secondX = x;
                firstZ = secondZ = z + 1;
                neighborZ++;
            } else if (side == 2) {
                firstHeight = southWest;
                secondHeight = northWest;
                firstX = secondX = x;
                firstZ = z + 1;
                secondZ = z;
                neighborX--;
            } else {
                firstHeight = northEast;
                secondHeight = southEast;
                firstX = secondX = x + 1;
                firstZ = z;
                secondZ = z + 1;
                neighborX++;
            }

            int texture = block.getBlockTextureFromSideAndMetadata(side + 2, metadata);
            int textureX = (texture & 15) << 4;
            int textureY = texture & 240;
            double minU = textureX / 256.0D;
            double maxU = (textureX + 16.0D - 0.01D) / 256.0D;
            double firstV = (textureY + (1.0F - firstHeight) * 16.0F) / 256.0D;
            double secondV = (textureY + (1.0F - secondHeight) * 16.0F) / 256.0D;
            double bottomV = (textureY + 16.0D - 0.01D) / 256.0D;
            float brightness = block.getBlockBrightness(world, neighborX, y, neighborZ);
            brightness *= side < 2 ? 0.8F : 0.6F;
            tessellator.setColorOpaque_F(brightness * red, brightness * green, brightness * blue);
            tessellator.addVertexWithUV(firstX, y + firstHeight, firstZ, minU, firstV);
            tessellator.addVertexWithUV(secondX, y + secondHeight, secondZ, maxU, secondV);
            tessellator.addVertexWithUV(secondX, y, secondZ, maxU, bottomV);
            tessellator.addVertexWithUV(firstX, y, firstZ, minU, bottomV);
            rendered = true;
        }
        block.minY = 0.0D;
        block.maxY = 1.0D;
        return rendered;
    }

    static float cornerHeight(IBlockAccess world, int fluidBlockId, int x, int y, int z) {
        FluidRegistry fluids = FluidRegistry.instance();
        int samples = 0;
        float air = 0.0F;
        for (int corner = 0; corner < 4; corner++) {
            int sampleX = x - (corner & 1);
            int sampleZ = z - (corner >> 1 & 1);
            if (fluids.isSameFluid(fluidBlockId, world.getBlockId(sampleX, y + 1, sampleZ))) {
                return 1.0F;
            }
            int sampleId = world.getBlockId(sampleX, y, sampleZ);
            if (!fluids.isSameFluid(fluidBlockId, sampleId)) {
                if (!world.getBlockMaterial(sampleX, y, sampleZ).isSolid()) {
                    air += 1.0F;
                    samples++;
                }
                continue;
            }
            FluidState state = FluidState.of(world.getBlockMetadata(sampleX, y, sampleZ));
            if (state.isFalling() || state.isSource()) {
                air += state.airFraction() * 10.0F;
                samples += 10;
            }
            air += state.airFraction();
            samples++;
        }
        return samples == 0 ? 0.0F : 1.0F - air / samples;
    }
}
