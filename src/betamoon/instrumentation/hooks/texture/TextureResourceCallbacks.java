package betamoon.instrumentation.hooks.texture;

import betamoon.resources.LuaTextureResources;
import java.awt.image.BufferedImage;
import net.minecraft.src.RenderEngine;

/** Resolves BetaMoon's virtual texture paths while preserving normal texture-pack behavior. */
public final class TextureResourceCallbacks {
    private TextureResourceCallbacks() {
    }

    public static BufferedImage findLuaTexture(String resourcePath) {
        return LuaTextureResources.load(resourcePath);
    }

    /** Replaces the missing-texture pixels uploaded by Minecraft with the Lua image. */
    public static int uploadLuaTexture(RenderEngine renderEngine, int textureId, BufferedImage luaTexture) {
        if (luaTexture != null) {
            renderEngine.setupTexture(luaTexture, textureId);
        }
        return textureId;
    }
}
