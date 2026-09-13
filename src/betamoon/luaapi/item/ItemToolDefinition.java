package betamoon.luaapi.item;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.keys;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/**
 * Tool effectiveness, harvest levels and durability are intentionally separate
 * settings.
 */
public final class ItemToolDefinition {
    private static final Map<?, ?> HARVEST_LEVELS = (Map<?, ?>) forgeValue("toolHarvestLevels");
    private static final Set<?> EFFECTIVE_BLOCKS = (Set<?>) forgeValue("toolEffectiveness");
    public final Map<String, Integer> classes;
    public final Float efficiency;
    public final int mineCost;
    public final int hitCost;

    public ItemToolDefinition(LuaValue definition, LuaValue legacyEfficiency) {
        Map<String, Integer> classes = new LinkedHashMap<>();
        int mine = -1;
        int hit = -1;
        Float speed = null;
        if (!definition.isnil()) {
            fields(definition, "tool", "classes", "efficiency", "durabilityCost");
            LuaValue declaredClasses = definition.get("classes");
            if (!declaredClasses.isnil()) {
                for (String name : keys(declaredClasses, "tool.classes")) {
                    classes.put(name, integer(declaredClasses.get(name), "tool.classes." + name, 0, 255));
                }
            }
            if (!definition.get("efficiency").isnil()) {
                if (!legacyEfficiency.isnil()) {
                    throw error("tool.efficiency", "cannot also specify top-level efficiency");
                }
                speed = (float) number(definition.get("efficiency"), "tool.efficiency");
                if (speed <= 0 || speed > 100000) {
                    throw error("tool.efficiency", "expected 0 < efficiency <= 100000");
                }
            }
            LuaValue costs = definition.get("durabilityCost");
            if (!costs.isnil()) {
                fields(costs, "tool.durabilityCost", "mine", "hit");
                if (!costs.get("mine").isnil()) {
                    mine = integer(costs.get("mine"), "tool.durabilityCost.mine", 0, 32767);
                }
                if (!costs.get("hit").isnil()) {
                    hit = integer(costs.get("hit"), "tool.durabilityCost.hit", 0, 32767);
                }
            }
        }
        this.classes = Collections.unmodifiableMap(classes);
        efficiency = speed;
        mineCost = mine;
        hitCost = hit;
    }

    public Boolean harvest(Block block, int metadata) {
        if (classes.isEmpty()) {
            return null;
        }
        if (block.blockMaterial.getIsHarvestable()) {
            return Boolean.TRUE;
        }
        Map<?, ?> levels = HARVEST_LEVELS;
        boolean declared = false;
        for (Map.Entry<String, Integer> tool : classes.entrySet()) {
            Integer required = (Integer) levels.get(Arrays.asList(block.blockID, metadata, tool.getKey()));
            if (required != null) {
                declared = true;
                if (tool.getValue() >= required) {
                    return Boolean.TRUE;
                }
            }
        }
        return declared ? Boolean.FALSE : null;
    }

    public boolean effective(Block block, int metadata) {
        for (String tool : classes.keySet()) {
            if (EFFECTIVE_BLOCKS.contains(Arrays.asList(block.blockID, metadata, tool))) {
                return true;
            }
        }
        return false;
    }

    private static Object forgeValue(String name) {
        try {
            Field field = forge.ForgeHooks.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read Forge tool harvest levels", failure);
        }
    }
}
