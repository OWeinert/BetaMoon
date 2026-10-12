package betamoon.luaapi.utils;

import betamoon.content.ContentKey;
import betamoon.content.ContentNamespaces;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.regex.Pattern;

/** Applies the reserved-key policy to declarations made by executing Lua lessons. */
public final class LuaContentNamespaces {
    private static final Pattern BUNDLED_LESSON = Pattern.compile(
            "^(?:(?:01_beg|02_int|03_adv)_\\d{2}[a-z]?_[a-z0-9_]+)(?:\\.lua|/main\\.lua)$");

    private LuaContentNamespaces() {
    }

    public static void requireDeclarationNamespace(ContentKey key) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (ContentNamespaces.EXAMPLE.equals(key.namespace()) && owner != null
                && BUNDLED_LESSON.matcher(owner.replace('\\', '/')).matches()) {
            return;
        }
        ContentNamespaces.requireUserNamespace(key);
    }
}
