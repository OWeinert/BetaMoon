package betamoon.luaapi.asset;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.assets.TextureAnimation;
import betamoon.assets.io.AssetDefaultPaths;
import betamoon.assets.io.AssetResolver;
import java.io.IOException;
import java.util.Locale;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class AssetDeclaration {
    private final AssetId id;
    private final AssetPath path;
    private final String extension;
    private final TextureAnimation textureAnimation;

    private AssetDeclaration(AssetId id, AssetPath path, String extension, TextureAnimation textureAnimation) {
        this.id = id;
        this.path = path;
        this.extension = extension;
        this.textureAnimation = textureAnimation;
    }

    static AssetDeclaration read(AssetKind kind, LuaValue value) {
        LuaTable table = value.checktable();
        for (LuaValue key : table.keys()) {
            if (!key.eq_b(LuaValue.valueOf("key")) && !key.eq_b(LuaValue.valueOf("path"))
                    && !(kind == AssetKind.TEXTURE && key.eq_b(LuaValue.valueOf("animation")))) {
                throw new LuaError("Asset declaration has unknown field: " + key.tojstring());
            }
        }
        try {
            AssetId id = new AssetId(kind, AssetKey.parse(table.get("key").checkjstring()));
            if (kind == AssetKind.TEXTURE && "minecraft".equals(id.getKey().getNamespace())) {
                throw new LuaError("The minecraft texture namespace is reserved for built-in references");
            }
            LuaValue playback = table.get("animation");
            if (playback == LuaValue.FALSE) {
                throw new LuaError("Texture asset animation must be a playback table");
            }
            TextureAnimation animation = playback.isnil() ? null : TextureInputs.animation(playback);
            LuaValue suppliedPath = table.get("path");
            if (suppliedPath.isnil()) {
                return new AssetDeclaration(id, null, null, animation);
            }
            AssetPath path = AssetPath.parse(suppliedPath.checkjstring());
            String name = path.toString().toLowerCase(Locale.ROOT);
            String extension = name.substring(name.lastIndexOf('.') + 1);
            if (kind == AssetKind.TEXTURE && !extension.equals("png")) {
                throw new LuaError("Texture assets require a .png file");
            }
            if (kind == AssetKind.SOUND && !extension.equals("ogg") && !extension.equals("wav")) {
                throw new LuaError("Sound assets require an .ogg or .wav file");
            }
            if (kind == AssetKind.MODEL || kind == AssetKind.ANIMATION) {
                extension = kind == AssetKind.MODEL ? "json" : "animation.json";
            }
            return new AssetDeclaration(id, path, extension, animation);
        } catch (IllegalArgumentException error) {
            throw new LuaError("Asset: " + error.getMessage());
        }
    }

    AssetDefinition resolve(AssetResolver resolver) throws IOException {
        AssetDefinition definition = path == null ? AssetDefaultPaths.resolve(id, resolver)
                : new AssetDefinition(id, path, extension);
        return textureAnimation == null ? definition : definition.withTextureAnimation(textureAnimation);
    }
}
