package betamoon.luaapi.asset;

import betamoon.assets.AssetKind;
import betamoon.assets.TextureAnimation;
import betamoon.client.assets.AssetLocation;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;

/** Common texture description for every BetaMoon-owned appearance consumer. */
public final class TextureInputs {
    private TextureInputs() {
    }

    public static AssetLocation read(LuaValue value) {
        if (value.istable() && !(value instanceof AssetReference)) {
            fields(value, "texture", "asset", "animation");
            LuaValue asset = value.get("asset");
            if (asset.isnil()) {
                throw new LuaError("texture.asset is required");
            }
            AssetLocation location = AssetInputs.read(asset, AssetKind.TEXTURE);
            LuaValue playback = value.get("animation");
            try {
                return playback.isnil() ? location : location.withTextureAnimation(animation(playback));
            } catch (IllegalArgumentException error) {
                throw new LuaError(error.getMessage());
            }
        }
        return AssetInputs.read(value, AssetKind.TEXTURE);
    }

    public static TextureAnimation animation(LuaValue value) {
        if (value == LuaValue.FALSE) {
            return TextureAnimation.FIRST_FRAME;
        }
        fields(value, "texture.animation", "layout", "frameTicks", "sequence", "clock");
        String layout = value.get("layout").optjstring("vertical");
        if (!"vertical".equals(layout)) {
            throw new LuaError("texture.animation.layout must be vertical");
        }
        int ticks = value.get("frameTicks").isnil() ? 1
                : integer(value.get("frameTicks"), "texture.animation.frameTicks", 1, 1200);
        String clock = value.get("clock").optjstring("client");
        if (!"client".equals(clock) && !"unpaused".equals(clock)) {
            throw new LuaError("texture.animation.clock must be client or unpaused");
        }
        LuaValue entries = value.get("sequence");
        int[] sequence = null;
        if (!entries.isnil()) {
            if (!entries.istable() || entries.length() == 0 || entries.checktable().keys().length != entries.length()
                    || entries.length() > 256) {
                throw new LuaError("texture.animation.sequence must be a dense array of 1..256 frame indices");
            }
            sequence = new int[entries.length()];
            for (int i = 1; i <= sequence.length; i++) {
                sequence[i - 1] = integer(entries.get(i), "texture.animation.sequence[" + i + "]", 1, 256);
            }
        }
        return new TextureAnimation(ticks, sequence, "unpaused".equals(clock));
    }
}
