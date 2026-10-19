package betamoon.luaapi.utils;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Strict bounded namespaced tag arrays for keyed generation declarations. */
public final class LuaNamespacedTags {
    private LuaNamespacedTags() {
    }

    public static Set<String> read(LuaValue value, String path) {
        Set<String> result = new TreeSet<String>();
        if (value.isnil()) {
            return Collections.emptySet();
        }
        value.checktable();
        if (value.length() > 64 || value.checktable().keys().length != value.length()) {
            throw new LuaError(path + ": expected an array of at most 64 tags");
        }
        for (int i = 1; i <= value.length(); i++) {
            String tag = value.get(i).checkjstring();
            if (!tag.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || tag.length() > 256 || !result.add(tag)) {
                throw new LuaError(path + ": expected unique namespaced tags");
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
