package betamoon.luaapi.material;

import betamoon.luaapi.material.ArmorMaterialApi.ArmorMaterial;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;

/** Retains native armor material identities across script reloads. */
public final class ArmorMaterialRegistry {
    private static final Map<String, ArmorMaterial> MATERIALS = new HashMap<>();

    private ArmorMaterialRegistry() {
    }

    public static ArmorMaterial register(String name, int level) {
        if (level < 0) {
            throw new LuaError("ArmorMaterial: level must be 0 or higher.");
        }
        ArmorMaterial existing = MATERIALS.get(name.toLowerCase());
        if (existing != null) {
            if (existing.level != level) {
                throw new LuaError("ArmorMaterial: changing material '" + name + "' requires a restart.");
            }
            return existing;
        }
        int renderIndex = ModLoader.AddArmor(name);
        ArmorMaterial created = new ArmorMaterial(name, level, renderIndex);
        MATERIALS.put(name.toLowerCase(), created);
        return created;
    }
}
