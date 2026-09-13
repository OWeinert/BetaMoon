package betamoon.luaapi;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luaapi.module.ModuleRegistry;
import betamoon.minecraft.MinecraftBuiltins;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.GenerationDimension;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.src.BiomeGenBase;
import java.lang.reflect.Method;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemFood;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Session;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises declarative domain APIs without a procedural Lua backend. */
public final class DeclarativeDomainTest {
    private DeclarativeDomainTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (Block.stone == null) {
            throw new AssertionError("Vanilla items must be initialized");
        }
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "quality_domains.lua");
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            lua.load("local tools=betamoon.materials.tools; "
                    + "local def={key='quality_material',harvestLevel=2,durability=123,efficiency=4.5,damage=3}; "
                    + "material=tools:add(def); assert(tools:getRequired(' QUALITY_MATERIAL ')==material); "
                    + "assert(tools.add(def)==material); assert(tools:get('wood')=='wood'); "
                    + "assert(not pcall(function() tools:getRequired('missing') end)); "
                    + "def.durability=124; assert(not pcall(function() tools:add(def) end)); "
                    + "assert(not pcall(function() tools:add({key='missing_fields'}) end)); "
                    + "assert(not pcall(function() betamoon.materials.armor:add({key='invalid',protection=-1}) end)); "
                    + "assert(betamoon.createToolMaterial==nil and betamoon.createArmorMaterial==nil)").call();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            lua.load("local tools=betamoon.materials.tools; "
                    + "assert(not pcall(function() tools:getRequired('quality_material') end)); "
                    + "assert(tools:add({key='quality_material',harvestLevel=2,durability=123,efficiency=4.5,damage=3})"
                    + "==material)").call();
            lua.load("local api=betamoon; local positions=api.positions; "
                    + "local p=positions:integer(1,2,3); assert(p.x==1 and p.y==2 and p.z==3); "
                    + "p=positions.float(1.5,2.5,3.5); assert(p.x==1.5 and p.y==2.5 and p.z==3.5); "
                    + "value={answer=42}; assert(select('#', api.modules:export('quality_module',value))==0); "
                    + "assert(not pcall(function() api.modules:export('missing_value') end)); "
                    + "assert(not pcall(function() api.modules:import('quality_module') end)); "
                    + "assert(type(api.chat.send)=='function' and type(api.chat.broadcast)=='function'); "
                    + "assert(api.createBlock==nil and api.createItem==nil and api.createTool==nil "
                    + "and api.createArmor==nil and api.exportModule==nil and api.PositionI==nil)").call();
            ModuleRegistry.publish("quality_domains.lua");
            lua.load("assert(betamoon.modules:import('quality_module')==value); "
                    + "assert(require('quality_module')==value)").call();
            verifyMinecraftConstants(lua);
            verifyItems(lua);
            verifyBlocks(lua);
            verifyWorldGeneration(lua);
            System.out.println(
                    "Declarative domains passed: constants, materials, world generation, validation and retained identity.");
        } finally {
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void verifyMinecraftConstants(Globals lua) {
        lua.load("local mc=betamoon.mc; " + "assert(mc.world.biomes.desert=='Desert'); "
                + "assert(mc.world.dimensions.both=='both'); " + "assert(mc.world.spawnGroups.creatures=='creatures'); "
                + "assert(mc.blockMaterials.rock=='rock' and mc.stepSounds.stone=='stone'); "
                + "assert(mc.entities.sheep=='Sheep' and mc.projectiles.snowball=='snowball'); "
                + "assert(mc.particles.redstone_dust=='reddust'); " + "assert(mc.sounds.random.click=='random.click'); "
                + "assert(mc.gui.backgrounds.container=='minecraft:container'); "
                + "assert(mc.gui.sprites.furnace_arrow=='minecraft:furnace_arrow'); "
                + "assert(betamoon.worldgen.treeModes.none=='none'); "
                + "local found=false; for key,value in pairs(mc.blockMaterials) do "
                + "if key=='rock' and value=='rock' then found=true end end; assert(found); "
                + "assert(not pcall(function() mc.blockMaterials.rock='stone' end)); "
                + "assert(not pcall(function() mc.world={} end)); "
                + "assert(not pcall(function() rawset(mc.blockMaterials,'rock','stone') end)); "
                + "assert(not pcall(function() table.insert(mc.blockMaterials,'stone') end)); "
                + "assert(not pcall(function() setmetatable(mc.blockMaterials,{}) end)); "
                + "assert(not pcall(function() return mc.world.biomes.dessert end))").call();

        for (String material : MinecraftBuiltins.blockMaterials().values()) {
            require(MinecraftBuiltins.resolveBlockMaterial(material) != null,
                    "Exposed block material resolves: " + material);
        }
        for (String sound : MinecraftBuiltins.stepSounds().values()) {
            require(MinecraftBuiltins.resolveStepSound(sound) != null, "Exposed step sound resolves: " + sound);
        }
        for (String biome : MinecraftBuiltins.biomes().values()) {
            require(MinecraftBuiltins.resolveBiome(biome) != null, "Exposed biome resolves: " + biome);
        }
        for (String entity : MinecraftBuiltins.entities().values()) {
            require(MinecraftBuiltins.resolveEntity(entity) != null, "Exposed entity resolves: " + entity);
        }
    }

    private static void verifyItems(Globals lua) {
        lua.load("local items=betamoon.items; "
                + "foodDef={id=28100,name='quality_food',type='food',food={healing=3,wolfFood=true}, "
                + "maxStackSize=16,icon={x=2,y=3},hasSubtypes=false}; items:add(foodDef); "
                + "armorDef={id=28101,name='quality_armor',material='iron',slot='head',renderIndex='gold'}; "
                + "betamoon.armor:add(armorDef); "
                + "assert(not pcall(function() items:add({id=28102,name='bad_armor',type='armor', "
                + "material='iron',slot='head',modelTexture='unused.png',renderIndex=0}) end)); "
                + "assert(not pcall(function() items:add({id=28102,name='bad_tool',type='hoe', "
                + "material='wood',efficiency=7}) end))").call();
        Item food = Item.itemsList[28100];
        Item armor = Item.itemsList[28101];
        require(food instanceof ItemFood && food.getItemStackLimit() == 16, "Food conversion preserves stack size");
        require(food.getIconFromDamage(0) == 50 && !food.getHasSubtypes(),
                "Food conversion preserves icon and subtypes");
        require(((ItemFood) food).getHealAmount() == 3, "Food healing");
        require(armor instanceof ItemArmor && ((ItemArmor) armor).armorType == 0, "Armor slot alias");
        require(((ItemArmor) armor).renderIndex == 4, "Armor render alias");
        require(Item.itemsList[28102] == null, "Unsupported declarations must not allocate native items");
        lua.load("foodDef.food.healing=5; betamoon.items:add(foodDef); betamoon.armor:add(armorDef); "
                + "assert(not pcall(function() betamoon.items:add({id=28100,name='changed_type'}) end))").call();
        require(Item.itemsList[28100] == food && Item.itemsList[28101] == armor, "Retained food and armor identities");
        require(((ItemFood) food).getHealAmount() == 5, "Retained food values update in place");
    }

    private static void verifyBlocks(Globals lua) throws Exception {
        Field blockList = Session.class.getDeclaredFields()[0];
        blockList.setAccessible(true);
        Field modLoaderList = ModLoader.class.getDeclaredField("field_blockList");
        modLoaderList.setAccessible(true);
        modLoaderList.set(null, blockList);
        lua.load("local blocks=betamoon.blocks; " + "local def={id=247,name='quality_block',material='rock',texture=9, "
                + "textures={all=10,sides=11,north=12,back=13,top=14},drops={{item=247}}}; "
                + "blocks:add(def); blocks.add(def); "
                + "assert(not pcall(function() blocks:add({id=246,name='bad_drop',material='rock', "
                + "drops={{item=999999}}}) end)); "
                + "assert(not pcall(function() blocks:add({id=246,name='bad_sound',material='rock', "
                + "stepSound='missing'}) end))").call();
        Block block = Block.blocksList[247];
        require(block.getBlockTextureFromSide(0) == 10, "All-face texture default");
        require(block.getBlockTextureFromSide(1) == 14, "Specific top texture");
        require(block.getBlockTextureFromSide(2) == 13, "Back alias overrides north");
        require(block.getBlockTextureFromSide(3) == 11, "Side texture overrides all");
        require(Block.blocksList[246] == null, "Invalid declarations must not allocate native blocks");
    }

    private static void verifyWorldGeneration(Globals lua) throws Exception {
        WorldGenRegistry.clear();
        BiomeGenRegistry.clear();
        try {
            lua.load("local ores=betamoon.worldgen.ores; "
                    + "local def={block=1,veinsPerChunk=3,veinSize=7,height={min=4,max=12}}; "
                    + "assert(ores:add(def)==nil); def.dimension='hell'; ores.add(def); "
                    + "def.dimension='both'; ores:add(def); def.height.min=13; "
                    + "assert(not pcall(function() ores:add(def) end)); "
                    + "assert(not pcall(function() ores:add({block=1,veinsPerChunk=1,veinSize=1,"
                    + "height={min=1,max=2},biomes={'missing'}}) end)); " + "local biomes=betamoon.worldgen.biomes; "
                    + "biomes:add({name='quality_biome',basedOn='desert',surface={top=1},weather={rain=true},"
                    + "spawns={creatures={{entity='sheep',weight=1}}}}); "
                    + "assert(not pcall(function() biomes:add({name='bad_range',range={humidity={1,0}}}) end)); "
                    + "assert(not pcall(function() biomes:add({name='bad_tree',trees={type='missing'}}) end)); "
                    + "assert(not pcall(function() biomes:add({name='bad_spawn',spawns={missing={}}}) end)); "
                    + "assert(not pcall(function() biomes:add({name='bad_entity',"
                    + "spawns={creatures={{entity='Item',weight=1}}}}) end))").call();
            List<?> ores = entries(WorldGenRegistry.class, "ORE_ENTRIES");
            require(ores.size() == 3, "Invalid ore declarations must not install generators");
            require(GenerationDimension.OVERWORLD.equals(field(ores.get(0), "dimension")), "Default ore dimension");
            require(GenerationDimension.NETHER.equals(field(ores.get(1), "dimension")), "Nether alias");
            require(GenerationDimension.BOTH.equals(field(ores.get(2), "dimension")), "Both-dimensions mode");
            require(field(ores.get(0), "targetBlockId") == null && field(ores.get(1), "targetBlockId") == null
                    && field(ores.get(2), "targetBlockId") == null,
                    "Default replacement is selected for the active dimension");
            require(GenerationDimension.BOTH.includes(false) && GenerationDimension.BOTH.includes(true),
                    "Both-dimensions entries run in the overworld and nether");
            List<?> biomes = entries(BiomeGenRegistry.class, "ENTRIES");
            require(biomes.size() == 1, "Invalid biome declarations must not install overlays");
            BiomeGenBase biome = (BiomeGenBase) field(biomes.get(0), "biome");
            require("quality_biome".equals(biome.biomeName), "Custom biome name");
            require(biome.topBlock == Block.stone.blockID, "Surface override");
            require(biome.fillerBlock == BiomeGenBase.desert.fillerBlock, "Inherited surface");
            require(BiomeGenBase.desert.topBlock != Block.stone.blockID, "Source biome must remain unchanged");
        } finally {
            WorldGenRegistry.clear();
            BiomeGenRegistry.clear();
        }
    }

    private static List<?> entries(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (List<?>) field.get(null);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
