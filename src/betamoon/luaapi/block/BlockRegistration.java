package betamoon.luaapi.block;

import betamoon.BetaMoonMain;
import betamoon.client.render.ModelAppearanceSet;
import betamoon.client.render.ModelRenderingSupport;
import java.io.IOException;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.resources.EnumTexAtlas;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.wrappers.BlockWrapper;
import forge.MinecraftForge;
import java.util.Map;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;

/**
 * Applies a parsed block declaration while preserving retained native
 * identities.
 */
final class BlockRegistration {
    private BlockRegistration() {
    }

    static BlockWrapper register(BlockDeclaration definition) {
        if (definition.callbacks.visual.hasModels()) {
            ModelRenderingSupport.requireAvailable();
        }
        ModelAppearanceSet appearance;
        try {
            appearance = definition.callbacks.visual.hasModels()
                    ? new ModelAppearanceSet(definition.appearance, definition.callbacks.visual.appearances())
                    : null;
        } catch (IOException error) {
            throw new LuaError("Block appearance: " + error.getMessage());
        }
        try {
            return registerPrepared(definition, appearance);
        } catch (RuntimeException error) {
            if (appearance != null) {
                appearance.close();
            }
            throw error;
        }
    }

    private static BlockWrapper registerPrepared(BlockDeclaration definition, ModelAppearanceSet appearance) {
        BlockWrapper block = createOrRetain(definition);
        applyProperties(block, definition);
        applyTextures(block, definition);
        for (Map.Entry<String, Integer> entry : definition.harvest.entrySet()) {
            MinecraftForge.setBlockHarvestLevel(block, entry.getKey(), entry.getValue());
        }
        for (BlockDeclaration.Drop drop : definition.drops) {
            block.addCustomDrop(drop.itemId, drop.min, drop.max);
        }

        LuaContentRegistry.Entry entry = LuaContentRegistry.find("block", definition.id);
        if (entry.registered) {
            BetaMoonMain.LOGGER.warning("Ignored duplicate block register: id=" + definition.id);
        } else {
            ModLoader.RegisterBlock(block);
            ModLoader.AddName(block, definition.displayName);
            entry.registered = true;
        }

        BlockTickRegistry.register(block, definition.ticks);
        BlockComponents components = definition.components;
        if (components.tile != null) {
            TileEntityRegistry.attachBlock(definition.id, components.tile, components.container, components.gui,
                    components.redstone);
            block.enableTileEntity();
        }
        BlockModelRegistry.install(definition.id, appearance);
        BlockCallbackRegistry.install(definition.id, definition.callbacks);
        return block;
    }

    private static BlockWrapper createOrRetain(BlockDeclaration definition) {
        String signature = "block:" + definition.materialName.toLowerCase();
        LuaContentRegistry.Entry existing = LuaContentRegistry.find("block", definition.id);
        if (existing != null) {
            LuaContentRegistry.remember("block", definition.id, existing.value, signature);
            if (!(existing.value instanceof BlockWrapper)) {
                throw new LuaError("Block: incompatible existing id: " + definition.id);
            }
            return (BlockWrapper) existing.value;
        }
        try {
            BlockWrapper block = new BlockWrapper(definition.id, 0, definition.material, definition.name);
            LuaContentRegistry.remember("block", definition.id, block, signature);
            return block;
        } catch (RuntimeException exception) {
            throw new LuaError("Block: " + String.valueOf(exception.getMessage()));
        }
    }

    private static void applyProperties(BlockWrapper block, BlockDeclaration definition) {
        if (definition.hardness != null) {
            block.setHardness(definition.hardness);
        }
        if (definition.resistance != null) {
            block.setResistance(definition.resistance);
        }
        if (definition.light != null) {
            block.setLightValue(definition.light);
        }
        if (definition.lightOpacity != null) {
            block.setLightOpacity(definition.lightOpacity);
        }
        if (definition.stepSound != null) {
            block.setStepSound(definition.stepSound);
        }
        if (definition.unbreakable) {
            block.setBlockUnbreakable();
        }
    }

    private static void applyTextures(BlockWrapper block, BlockDeclaration definition) {
        if (definition.texture != null) {
            block.blockIndexInTexture = resolveTexture(definition.texture);
        }
        for (BlockDeclaration.FaceTexture entry : definition.textures) {
            int texture = resolveTexture(entry.texture);
            if (entry.side == -1) {
                block.setAllSideTextures(texture);
            } else if (entry.side == -2) {
                for (int side = 2; side <= 5; side++) {
                    block.setSideTextureIndex(side, texture);
                }
            } else {
                block.setSideTextureIndex(entry.side, texture);
            }
        }
    }

    private static int resolveTexture(BlockDeclaration.Texture texture) {
        return texture.index != null ? texture.index : LuaApiUtils.registerTexture(EnumTexAtlas.BLOCKS, texture.path);
    }
}
