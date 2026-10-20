package betamoon.luaapi.material;

import betamoon.content.ContentKey;
import betamoon.content.ContentKeyException;
import betamoon.content.ContentType;
import betamoon.luaapi.utils.LuaContentNamespaces;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.minecraft.MinecraftBuiltins;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.Material;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Stable native block-material identities shared across script environments. */
public final class BlockMaterialRegistry {
    private static final Map<String, Entry> CUSTOM = new LinkedHashMap<String, Entry>();
    private static final Map<Material, Entry> BY_MATERIAL = new IdentityHashMap<Material, Entry>();

    private BlockMaterialRegistry() {
    }

    static synchronized Entry register(LuaValue declaration) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Materials may only be declared while a Lua script is loading.");
        }
        BlockMaterialDefinition definition = new BlockMaterialDefinition(declaration);
        ContentKey key = key(declaration.get("key"), true);
        Entry existing = CUSTOM.get(key.toString());
        if (existing != null) {
            if (!owner.equals(existing.owner)) {
                throw new LuaError("Material '" + key + "' is already owned by another script.");
            }
            if (!definition.signature().equals(existing.signature)) {
                throw new LuaError("Material '" + key
                        + "' changed native properties. Restart Minecraft with worlds closed to change its definition.");
            }
            return existing;
        }
        Entry created = new Entry(key.toString(), owner, definition.signature(), new NativeBlockMaterial(definition));
        CUSTOM.put(created.key, created);
        BY_MATERIAL.put(created.material, created);
        return created;
    }

    public static synchronized Entry resolve(LuaValue input, String path) {
        if (input instanceof BlockMaterialReference) {
            return ((BlockMaterialReference) input).entry();
        }
        if (!input.isstring()) {
            throw new LuaError(path + ": expected a material name or material reference");
        }
        Entry entry = find(input.checkjstring());
        if (entry == null) {
            throw new LuaError(path + ": unknown material '" + input.checkjstring() + "'");
        }
        return entry;
    }

    public static synchronized Entry find(String name) {
        Entry custom = CUSTOM.get(name);
        if (custom != null) {
            return custom;
        }
        Material vanilla = MinecraftBuiltins.resolveBlockMaterial(name);
        if (vanilla == null) {
            return null;
        }
        Entry cached = BY_MATERIAL.get(vanilla);
        if (cached != null) {
            return cached;
        }
        for (Map.Entry<String, String> builtin : MinecraftBuiltins.blockMaterials().entrySet()) {
            if (MinecraftBuiltins.resolveBlockMaterial(builtin.getKey()) == vanilla) {
                Entry entry = new Entry(builtin.getKey(), null, "vanilla:" + builtin.getKey(), vanilla);
                BY_MATERIAL.put(vanilla, entry);
                return entry;
            }
        }
        return null;
    }

    public static synchronized Entry describe(Material material) {
        Entry custom = BY_MATERIAL.get(material);
        if (custom != null) {
            return custom;
        }
        for (String name : MinecraftBuiltins.blockMaterials().keySet()) {
            if (MinecraftBuiltins.resolveBlockMaterial(name) == material) {
                return find(name);
            }
        }
        return null;
    }

    private static ContentKey key(LuaValue value, boolean declaration) {
        try {
            ContentKey key = ContentKey.parseForType(value.checkjstring(), ContentType.BLOCK_MATERIAL);
            if (declaration) {
                LuaContentNamespaces.requireDeclarationNamespace(key);
            }
            return key;
        } catch (ContentKeyException error) {
            throw new LuaError("material.key: " + error.getMessage());
        }
    }

    public static final class Entry {
        public final String key;
        public final String owner;
        public final String signature;
        public final Material material;
        private BlockMaterialReference reference;

        private Entry(String key, String owner, String signature, Material material) {
            this.key = key;
            this.owner = owner;
            this.signature = signature;
            this.material = material;
        }

        public boolean custom() {
            return owner != null;
        }

        synchronized BlockMaterialReference reference() {
            if (reference == null) {
                reference = new BlockMaterialReference(this);
            }
            return reference;
        }
    }
}
