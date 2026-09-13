package betamoon.luaapi.minecraft;

import betamoon.luaapi.block.BlockFace;
import betamoon.luaapi.item.ProjectileType;
import betamoon.minecraft.MinecraftBuiltins;
import betamoon.worldgen.BiomeSpawnGroup;
import betamoon.worldgen.BiomeTreeMode;
import betamoon.worldgen.GenerationDimension;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
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
        module.set("mc", table("betamoon.mc", minecraftValues));

        LuaValue worldgen = module.get("worldgen");
        if (worldgen.istable()) {
            worldgen.set("treeModes", treeModes());
        }
    }

    private static StrictReadOnlyTable dimensions() {
        Map<String, String> values = new LinkedHashMap<>();
        for (GenerationDimension dimension : GenerationDimension.values()) {
            values.put(dimension.getLuaName(), dimension.getLuaName());
        }
        return constants("betamoon.mc.world.dimensions", values);
    }

    private static StrictReadOnlyTable spawnGroups() {
        Map<String, String> values = new LinkedHashMap<>();
        for (BiomeSpawnGroup group : BiomeSpawnGroup.values()) {
            values.put(group.getLuaName(), group.getLuaName());
        }
        return constants("betamoon.mc.world.spawnGroups", values);
    }

    private static StrictReadOnlyTable blockFaces() {
        Map<String, String> values = new LinkedHashMap<>();
        for (BlockFace face : BlockFace.values()) {
            values.put(face.luaName, face.luaName);
        }
        return constants("betamoon.mc.blockFaces", values);
    }

    private static StrictReadOnlyTable projectiles() {
        Map<String, String> values = new LinkedHashMap<>();
        for (ProjectileType projectile : ProjectileType.values()) {
            values.put(projectile.getLuaName(), projectile.getLuaName());
        }
        return constants("betamoon.mc.projectiles", values);
    }

    private static StrictReadOnlyTable treeModes() {
        Map<String, String> values = new LinkedHashMap<>();
        for (BiomeTreeMode mode : BiomeTreeMode.values()) {
            values.put(mode.getLuaName(), mode.getLuaName());
        }
        return constants("betamoon.worldgen.treeModes", values);
    }

    private static StrictReadOnlyTable constants(String path, Map<String, String> values) {
        Map<String, LuaValue> luaValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            luaValues.put(entry.getKey(), LuaValue.valueOf(entry.getValue()));
        }
        return table(path, luaValues);
    }

    private static StrictReadOnlyTable table(String path, Map<String, LuaValue> values) {
        return new StrictReadOnlyTable(path, values);
    }

    /** LuaTable with real enumerable entries and no writable access path. */
    private static final class StrictReadOnlyTable extends LuaTable {
        private final String path;

        private StrictReadOnlyTable(String path, Map<String, LuaValue> values) {
            this.path = path;
            for (Map.Entry<String, LuaValue> entry : values.entrySet()) {
                super.rawset(LuaValue.valueOf(entry.getKey()), entry.getValue());
            }
        }

        @Override
        public LuaValue get(int key) {
            return requireKnown(LuaValue.valueOf(key), super.rawget(key));
        }

        @Override
        public LuaValue get(LuaValue key) {
            return requireKnown(key, super.rawget(key));
        }

        @Override
        public LuaValue rawget(int key) {
            return requireKnown(LuaValue.valueOf(key), super.rawget(key));
        }

        @Override
        public LuaValue rawget(LuaValue key) {
            return requireKnown(key, super.rawget(key));
        }

        @Override
        public void set(int key, LuaValue value) {
            readOnly();
        }

        @Override
        public void set(LuaValue key, LuaValue value) {
            readOnly();
        }

        @Override
        public void rawset(int key, LuaValue value) {
            readOnly();
        }

        @Override
        public void rawset(LuaValue key, LuaValue value) {
            readOnly();
        }

        @Override
        public LuaValue remove(int position) {
            return readOnly();
        }

        @Override
        public void insert(int position, LuaValue value) {
            readOnly();
        }

        @Override
        public void sort(LuaValue comparator) {
            readOnly();
        }

        @Override
        public LuaValue setmetatable(LuaValue metatable) {
            return readOnly();
        }

        private LuaValue requireKnown(LuaValue key, LuaValue value) {
            if (value.isnil()) {
                throw new LuaError(path + " has no constant named '" + key.tojstring() + "'.");
            }
            return value;
        }

        private LuaValue readOnly() {
            throw new LuaError(path + " is read-only.");
        }
    }
}
