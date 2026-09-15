package betamoon.luaapi.asset;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import java.util.Locale;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class AssetDeclaration {
    private AssetDeclaration() {
    }

    static AssetDefinition read(AssetKind kind, LuaValue value) {
        LuaTable table = value.checktable();
        for (LuaValue key : table.keys()) {
            if (!key.eq_b(LuaValue.valueOf("key")) && !key.eq_b(LuaValue.valueOf("path"))) {
                throw new LuaError("Asset declaration has unknown field: " + key.tojstring());
            }
        }
        try {
            AssetId id = new AssetId(kind, AssetKey.parse(table.get("key").checkjstring()));
            AssetPath path = AssetPath.parse(table.get("path").checkjstring());
            String name = path.toString().toLowerCase(Locale.ROOT);
            String extension = name.substring(name.lastIndexOf('.') + 1);
            if (kind == AssetKind.TEXTURE && !extension.equals("png")) {
                throw new LuaError("Texture assets require a .png file");
            }
            if (kind == AssetKind.SOUND && !extension.equals("ogg") && !extension.equals("wav")) {
                throw new LuaError("Sound assets require an .ogg or .wav file");
            }
            return new AssetDefinition(id, path, extension);
        } catch (IllegalArgumentException error) {
            throw new LuaError("Asset: " + error.getMessage());
        }
    }
}
