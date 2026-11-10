package betamoon.luaapi.client.ui;

import betamoon.assets.AssetKind;
import betamoon.luaapi.asset.AssetReference;
import betamoon.luaapi.utils.LuaDataSnapshot;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Normalizes only the loading image boundary; unrelated declaration values
 * retain strict snapshot rules.
 */
public final class GuiLoadingParser {
    public static Map<String, Object> snapshot(LuaValue value, String path) {
        LuaTable declaration = value.checktable();
        LuaValue loading = declaration.rawget("loading");
        if (loading.isnil()) {
            return LuaDataSnapshot.object(declaration, path, Double.MAX_VALUE);
        }
        LuaTable appearance = copy(loading.checktable());
        LuaValue background = appearance.rawget("background");
        if (background instanceof AssetReference) {
            AssetReference reference = (AssetReference) background;
            if (reference.getId().getKind() != AssetKind.TEXTURE) {
                throw new LuaError(path + ".loading.background: expected a registered texture key or reference");
            }
            appearance.rawset("background", reference.getId().getKey().toString());
        }
        LuaTable normalized = copy(declaration);
        normalized.rawset("loading", appearance);
        return LuaDataSnapshot.object(normalized, path, Double.MAX_VALUE);
    }

    private static LuaTable copy(LuaTable value) {
        LuaTable copy = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = value.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return copy;
            }
            copy.rawset(key, next.arg(2));
        }
    }

    private GuiLoadingParser() {
    }
}
