package betamoon.luaapi.material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.MapColor;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Immutable native properties, parsed before registration changes global state. */
final class BlockMaterialDefinition {
    private static final Map<String, MapColor> COLORS = colors();

    final String kind;
    final String colorName;
    final MapColor color;
    final boolean burning;
    final boolean harvestable;
    final boolean groundCover;
    final boolean blocksGrass;
    final boolean translucent;
    final int mobility;

    BlockMaterialDefinition(LuaValue value) {
        fields(value, "material", "key", "kind", "mapColor", "burning", "harvestable", "groundCover",
                "blocksGrass", "translucent", "mobility");
        kind = string(required(value, "kind"), "material.kind");
        if (!"solid".equals(kind) && !"transparent".equals(kind) && !"logic".equals(kind)
                && !"liquid".equals(kind)) {
            throw new LuaError("material.kind: expected solid, transparent, logic, or liquid");
        }
        colorName = string(required(value, "mapColor"), "material.mapColor");
        color = COLORS.get(colorName);
        if (color == null) {
            throw new LuaError("material.mapColor: unknown native map color '" + colorName + "'");
        }
        burning = bool(value.get("burning"), "material.burning", false);
        harvestable = bool(value.get("harvestable"), "material.harvestable", true);
        groundCover = bool(value.get("groundCover"), "material.groundCover",
                "transparent".equals(kind) || "liquid".equals(kind));
        if (!"solid".equals(kind) && (!value.get("blocksGrass").isnil()
                || !value.get("translucent").isnil())) {
            throw new LuaError("material: blocksGrass and translucent are only valid for solid materials");
        }
        blocksGrass = bool(value.get("blocksGrass"), "material.blocksGrass", "solid".equals(kind));
        translucent = bool(value.get("translucent"), "material.translucent", false);
        String mobilityName = value.get("mobility").isnil()
                ? ("liquid".equals(kind) || "logic".equals(kind) ? "noPush" : "normal")
                : string(value.get("mobility"), "material.mobility");
        if ("normal".equals(mobilityName)) {
            mobility = 0;
        } else if ("noPush".equals(mobilityName)) {
            mobility = 1;
        } else if ("immovable".equals(mobilityName)) {
            mobility = 2;
        } else {
            throw new LuaError("material.mobility: expected normal, noPush, or immovable");
        }
        if ("liquid".equals(kind) && mobility == 0) {
            throw new LuaError("material.mobility: liquid materials cannot use normal piston mobility");
        }
    }

    String signature() {
        return kind + "|" + colorName + "|" + burning + "|" + harvestable + "|" + groundCover + "|"
                + blocksGrass + "|" + translucent + "|" + mobility;
    }

    private static Map<String, MapColor> colors() {
        Map<String, MapColor> result = new LinkedHashMap<String, MapColor>();
        result.put("air", MapColor.airColor);
        result.put("grass", MapColor.grassColor);
        result.put("sand", MapColor.sandColor);
        result.put("cloth", MapColor.clothColor);
        result.put("tnt", MapColor.tntColor);
        result.put("ice", MapColor.iceColor);
        result.put("iron", MapColor.ironColor);
        result.put("foliage", MapColor.foliageColor);
        result.put("snow", MapColor.snowColor);
        result.put("clay", MapColor.clayColor);
        result.put("dirt", MapColor.dirtColor);
        result.put("stone", MapColor.stoneColor);
        result.put("water", MapColor.waterColor);
        result.put("wood", MapColor.woodColor);
        return Collections.unmodifiableMap(result);
    }
}
