package betamoon.luaapi.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Precompiled metadata labels using Minecraft's normal translated item-name
 * path.
 */
public final class ItemDisplayNames {
    private final Map<Integer, String> names;

    public ItemDisplayNames(LuaValue declaration, int maximum) {
        Map<Integer, String> values = new LinkedHashMap<Integer, String>();
        if (!declaration.isnil()) {
            if (!declaration.istable()) {
                throw error("displayNames", "expected a table indexed by metadata");
            }
            LuaValue key = LuaValue.NIL;
            while (!(key = declaration.next(key).arg1()).isnil()) {
                int metadata = integer(key, "displayNames metadata", 0, maximum);
                String label = string(declaration.get(key), "displayNames[" + metadata + "]");
                if (label.trim().isEmpty()) {
                    throw error("displayNames[" + metadata + "]", "expected a non-empty name");
                }
                values.put(metadata, label);
            }
        }
        names = Collections.unmodifiableMap(values);
    }

    public String key(String base, int metadata) {
        return names.containsKey(metadata) ? variantKey(base, metadata) : base;
    }

    public void register(String base) {
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            ModLoader.AddLocalization(variantKey(base, entry.getKey()) + ".name", entry.getValue());
        }
    }

    public Set<Integer> metadataValues() {
        return names.keySet();
    }

    public void validateMetadata(IntPredicate valid) {
        for (int metadata : names.keySet()) {
            if (!valid.test(metadata)) {
                throw error("displayNames[" + metadata + "]", "not valid subtype metadata");
            }
        }
    }

    private static String variantKey(String base, int metadata) {
        return base + ".betamoon.metadata." + metadata;
    }
}
