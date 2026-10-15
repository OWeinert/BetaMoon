package betamoon.luaapi.item;

import betamoon.luamodloader.LuaContentRegistry;
import forge.MinecraftForge;
import java.util.Locale;
import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.Item;
import org.luaj.vm2.LuaError;

/** Native tool creation used by the declarative item API. */
final class ItemToolApi {
    private ItemToolApi() {
    }

    static Item createOrRetain(ItemDeclaration definition) {
        EnumToolMaterial material = definition.tool.material;
        String type = definition.kind.name().toLowerCase(Locale.ROOT);
        String signature = "tool:" + type + ":" + material.name();
        LuaContentRegistry.Entry entry = LuaContentRegistry.find("item", definition.id);
        if (entry != null) {
            LuaContentRegistry.remember("item", definition.id, entry.value, signature);
            if (!definition.kind.toolClass.isInstance(entry.value)) {
                throw new LuaError("Tool: changing the type of id " + definition.id + " requires a restart.");
            }
            return (Item) entry.value;
        }
        Item tool = create(definition);
        MinecraftForge.setToolClass(tool, type, material.getHarvestLevel());
        LuaContentRegistry.remember("item", definition.id, tool, signature);
        return tool;
    }

    private static Item create(ItemDeclaration definition) {
        int id = definition.id - 256;
        String name = definition.name;
        EnumToolMaterial material = definition.tool.material;
        switch (definition.kind) {
            case PICKAXE:
                return new LuaPickaxeItem(id, material, name);
            case AXE:
                return new LuaAxeItem(id, material, name);
            case SHOVEL:
                return new LuaSpadeItem(id, material, name);
            case HOE:
                return new LuaHoeItem(id, material, name);
            case SWORD:
                return new LuaSwordItem(id, material, material.getDamageVsEntity(), name);
            default:
                throw new IllegalArgumentException("Expected a tool kind: " + definition.kind);
        }
    }
}
