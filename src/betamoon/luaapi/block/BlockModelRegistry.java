package betamoon.luaapi.block;

import betamoon.assets.model.ModelGeometry;
import betamoon.assets.model.ModelPose;
import betamoon.assets.model.ModelVector;
import betamoon.client.render.ModelAppearance;
import betamoon.client.render.ModelAppearanceSet;
import betamoon.client.render.ModelRenderer;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import forge.MinecraftForgeClient;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.BaseMod;
import net.minecraft.src.Block;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;
import org.luaj.vm2.LuaError;

/**
 * Block-specific chunk rendering, model selection, and visual invalidation.
 */
public final class BlockModelRegistry {
    private static final Map<Integer, ModelAppearanceSet> MODELS = new LinkedHashMap<>();
    private static final ThreadLocal<Integer> ORDINARY_RENDERING = new ThreadLocal<>();
    private static int renderType = -1;
    private static boolean chunksDirty;
    private static boolean sceneDirty;

    private BlockModelRegistry() {
    }

    public static void initialize(BaseMod mod) {
        if (renderType < 0) {
            renderType = ModLoader.getUniqueBlockModelID(mod, true);
        }
    }

    public static ModelAppearance get(int id, int metadata) {
        ModelAppearanceSet appearances = MODELS.get(id);
        return appearances == null ? null : appearances.select(metadata);
    }

    public static boolean hasModels(int id) {
        return MODELS.containsKey(id);
    }

    public static boolean isDynamic(int id) {
        ModelAppearanceSet appearances = MODELS.get(id);
        return appearances != null && appearances.isDynamic();
    }

    public static int renderType() {
        return renderType;
    }

    public static void validate(ModelAppearanceDeclaration appearance) {
        if (appearance == null || appearance.isDynamic()) {
            return;
        }
        for (ModelAppearanceDeclaration.Material material : appearance.materials.values()) {
            if ((!material.blend.equals("opaque") && !material.blend.equals("cutout")) || material.opacity != 1) {
                throw new LuaError(
                        "Static block models require opaque/cutout materials with opacity 1; use appearance.mode = \"dynamic\" for transparency");
            }
        }
        for (ModelAppearanceDeclaration layer : appearance.layers) {
            validate(layer);
        }
    }

    static void install(int id, ModelAppearanceSet appearance) {
        if (appearance != null && renderType < 0) {
            throw new LuaError("Block model rendering has not been initialized by ModLoader");
        }
        boolean wasDynamic = isDynamic(id);
        ModelAppearanceSet previous = appearance == null ? MODELS.remove(id) : MODELS.put(id, appearance);
        if (wasDynamic != isDynamic(id)) {
            sceneDirty = true;
        }
        if (previous != null) {
            previous.close();
        }
        if (appearance != null) {
            appearance.onChange(() -> chunksDirty = true);
        }
        chunksDirty |= appearance != null || previous != null;
    }

    public static void flushInvalidation() {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (chunksDirty && minecraft != null && minecraft.renderGlobal != null && minecraft.theWorld != null) {
            if (sceneDirty) {
                BlockModelScene.rebuildLoaded();
                sceneDirty = false;
            }
            minecraft.renderGlobal.loadRenderers();
            chunksDirty = false;
        }
    }

    public static boolean renderWorld(RenderBlocks renderer, IBlockAccess world, int x, int y, int z, Block block) {
        if (!hasModels(block.blockID) || isDynamic(block.blockID)) {
            return false;
        }
        ModelAppearance appearance = get(block.blockID, world.getBlockMetadata(x, y, z));
        if (appearance == null) {
            return renderOrdinaryWorld(renderer, block, x, y, z);
        }
        renderStatic(appearance, renderer, world, x, y, z, block);
        for (ModelAppearance layer : appearance.layers) {
            renderStatic(layer, renderer, world, x, y, z, block);
        }
        return true;
    }

    public static boolean isOrdinaryRendering(int id) {
        return Integer.valueOf(id).equals(ORDINARY_RENDERING.get());
    }

    static boolean renderOrdinaryWorld(RenderBlocks renderer, Block block, int x, int y, int z) {
        Integer previous = ORDINARY_RENDERING.get();
        ORDINARY_RENDERING.set(block.blockID);
        try {
            return renderer.renderBlockByRenderType(block, x, y, z);
        } finally {
            restoreOrdinaryRendering(previous);
        }
    }

    public static void renderOrdinaryInventory(RenderBlocks renderer, Block block, int metadata) {
        Integer previous = ORDINARY_RENDERING.get();
        ORDINARY_RENDERING.set(block.blockID);
        try {
            renderer.renderBlockOnInventory(block, metadata, 1);
        } finally {
            restoreOrdinaryRendering(previous);
        }
    }

    private static void restoreOrdinaryRendering(Integer previous) {
        if (previous == null) {
            ORDINARY_RENDERING.remove();
        } else {
            ORDINARY_RENDERING.set(previous);
        }
    }

    private static void renderStatic(ModelAppearance appearance, RenderBlocks renderer, IBlockAccess world, int x,
            int y, int z, Block block) {
        ModelPose pose = new ModelPose(appearance.geometry());
        Tessellator previous = Tessellator.instance;
        try {
            for (Map.Entry<String, ModelAppearanceDeclaration.Material> entry : appearance.definition.materials
                    .entrySet()) {
                boolean breaking = renderer.overrideBlockTexture >= 0;
                if (!breaking) {
                    MinecraftForgeClient.bindTexture(appearance.textures.get(entry.getKey()));
                }
                Tessellator tessellator = Tessellator.instance;
                ModelAppearanceDeclaration.Material material = entry.getValue();
                float light = material.unlit ? 1 : block.getBlockBrightness(world, x, y, z);
                for (int bone = 0; bone < pose.getGeometry().bones.size(); bone++) {
                    for (ModelGeometry.Quad face : pose.getGeometry().bones.get(bone).faces) {
                        if (!face.material.equals(entry.getKey())) {
                            continue;
                        }
                        ModelVector[] vertices = new ModelVector[4];
                        for (int i = 0; i < 4; i++) {
                            vertices[i] = appearance.transform(pose.transform(bone, face.vertices.get(i)), "block")
                                    .times(1.0 / 16);
                        }
                        ModelVector normal = ModelRenderer.normal(vertices);
                        double shade = material.unlit ? 1 : 0.6 + 0.4 * Math.max(0, normal.y);
                        tessellator.setColorOpaque_F((float) (((material.tint >> 16) & 255) / 255.0 * light * shade),
                                (float) (((material.tint >> 8) & 255) / 255.0 * light * shade),
                                (float) ((material.tint & 255) / 255.0 * light * shade));
                        for (int side = 0; side < (material.cull || face.plane ? 1 : 2); side++) {
                            for (int index = 0; index < 4; index++) {
                                int i = side == 0 ? index : 3 - index;
                                ModelVector uv = face.uv.get(i);
                                double u = uv.x * material.scaleU + material.u;
                                double v = uv.y * material.scaleV + material.v;
                                if (breaking) {
                                    u = (renderer.overrideBlockTexture % 16 + (i == 1 || i == 2 ? 1 : 0)) / 16.0;
                                    v = (renderer.overrideBlockTexture / 16 + (i >= 2 ? 1 : 0)) / 16.0;
                                }
                                tessellator.addVertexWithUV(x + 0.5 + vertices[i].x, y + vertices[i].y,
                                        z + 0.5 + vertices[i].z, u, v);
                            }
                        }
                    }
                }
                if (!breaking) {
                    MinecraftForgeClient.unbindTexture();
                }
            }
        } finally {
            Tessellator.instance = previous;
        }
    }
}
