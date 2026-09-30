package betamoon.luaapi.world;

import betamoon.luaapi.utils.LuaDataSnapshot;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/** Adds structure-document defaults to the shared JSON-compatible Lua snapshot. */
final class LuaStructureDocument {
    private LuaStructureDocument() {
    }

    static Map<String, Object> snapshot(LuaValue value, String path) {
        Map<String, Object> document = snapshotObject(value, path);
        if (!document.containsKey("format")) {
            document.put("format", "betamoon_structure");
        }
        return document;
    }

    static Map<String, Object> snapshotObject(LuaValue value, String path) {
        return LuaDataSnapshot.object(value, path);
    }
}
