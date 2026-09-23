package betamoon.luaapi.minecraft;

import betamoon.luaapi.block.BlockFace;
import betamoon.luaapi.item.ProjectileType;
import betamoon.luaapi.utils.LuaConstantTable;
import betamoon.minecraft.MinecraftBuiltins;
import betamoon.worldgen.BiomeSpawnGroup;
import betamoon.worldgen.BiomeTreeMode;
import betamoon.worldgen.GenerationDimension;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Exposes named Minecraft values to Lua as strict read-only tables. */
public final class MinecraftApi {
    private MinecraftApi() {
    }

    public static void attach(LuaTable module) {
        Map<String, LuaValue> worldValues = new LinkedHashMap<>();
        worldValues.put("biomes", constants("betamoon.mc.world.biomes", MinecraftBuiltins.biomes()));
        worldValues.put("dimensions", dimensions());
        worldValues.put("spawnGroups", spawnGroups());

        Map<String, LuaValue> soundValues = new LinkedHashMap<>();
        soundValues.put("random", constants("betamoon.mc.sounds.random", MinecraftBuiltins.randomSounds()));

        Map<String, LuaValue> guiValues = new LinkedHashMap<>();
        guiValues.put("backgrounds", constants("betamoon.mc.gui.backgrounds", MinecraftBuiltins.guiBackgrounds()));
        guiValues.put("sprites", constants("betamoon.mc.gui.sprites", MinecraftBuiltins.guiSprites()));

        Map<String, LuaValue> fuelValues = new LinkedHashMap<>();
        fuelValues.put("furnace", LuaValue.valueOf("minecraft:fuel/furnace"));

        Map<String, LuaValue> minecraftValues = new LinkedHashMap<>();
        minecraftValues.put("world", table("betamoon.mc.world", worldValues));
        minecraftValues.put("blockMaterials",
                constants("betamoon.mc.blockMaterials", MinecraftBuiltins.blockMaterials()));
        minecraftValues.put("stepSounds", constants("betamoon.mc.stepSounds", MinecraftBuiltins.stepSounds()));
        minecraftValues.put("toolMaterials", constants("betamoon.mc.toolMaterials", MinecraftBuiltins.toolMaterials()));
        minecraftValues.put("armorMaterials",
                constants("betamoon.mc.armorMaterials", MinecraftBuiltins.armorMaterials()));
        minecraftValues.put("armorSlots", constants("betamoon.mc.armorSlots", MinecraftBuiltins.armorSlots()));
        minecraftValues.put("blockFaces", blockFaces());
        minecraftValues.put("entities", constants("betamoon.mc.entities", MinecraftBuiltins.entities()));
        minecraftValues.put("projectiles", projectiles());
        minecraftValues.put("particles", constants("betamoon.mc.particles", MinecraftBuiltins.particles()));
        minecraftValues.put("sounds", table("betamoon.mc.sounds", soundValues));
        minecraftValues.put("gui", table("betamoon.mc.gui", guiValues));
        minecraftValues.put("fuels", table("betamoon.mc.fuels", fuelValues));
        module.set("mc", table("betamoon.mc", minecraftValues));

        LuaValue worldgen = module.get("worldgen");
        if (worldgen.istable()) {
            worldgen.set("treeModes", treeModes());
        }
    }

    private static LuaConstantTable dimensions() {
        Map<String, String> values = new LinkedHashMap<>();
        for (GenerationDimension dimension : GenerationDimension.values()) {
            values.put(dimension.getLuaName(), dimension.getLuaName());
        }
        return constants("betamoon.mc.world.dimensions", values);
    }

    private static LuaConstantTable spawnGroups() {
        Map<String, String> values = new LinkedHashMap<>();
        for (BiomeSpawnGroup group : BiomeSpawnGroup.values()) {
            values.put(group.getLuaName(), group.getLuaName());
        }
        return constants("betamoon.mc.world.spawnGroups", values);
    }

    private static LuaConstantTable blockFaces() {
        Map<String, String> values = new LinkedHashMap<>();
        for (BlockFace face : BlockFace.values()) {
            values.put(face.luaName, face.luaName);
        }
        return constants("betamoon.mc.blockFaces", values);
    }

    private static LuaConstantTable projectiles() {
        Map<String, String> values = new LinkedHashMap<>();
        for (ProjectileType projectile : ProjectileType.values()) {
            values.put(projectile.getLuaName(), projectile.getLuaName());
        }
        return constants("betamoon.mc.projectiles", values);
    }

    private static LuaConstantTable treeModes() {
        Map<String, String> values = new LinkedHashMap<>();
        for (BiomeTreeMode mode : BiomeTreeMode.values()) {
            values.put(mode.getLuaName(), mode.getLuaName());
        }
        return constants("betamoon.worldgen.treeModes", values);
    }

    private static LuaConstantTable constants(String path, Map<String, String> values) {
        Map<String, LuaValue> luaValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            luaValues.put(entry.getKey(), LuaValue.valueOf(entry.getValue()));
        }
        return table(path, luaValues);
    }

    private static LuaConstantTable table(String path, Map<String, LuaValue> values) {
        return new LuaConstantTable(path, values);
    }
}
