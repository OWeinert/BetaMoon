package betamoon.worldgen;

import betamoon.loot.LootTableRegistry;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import net.minecraft.src.Block;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Verifies the plain-table and table-backed-builder structure frontends. */
public final class StructureLuaFrontendTest {
    private StructureLuaFrontendTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks are initialized");
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "structure_lua_frontend_test.lua");
        LootTableRegistry.clear();
        WorldGenRegistry.clear();
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            try (LootTableRegistry.PublicationBatch lootBatch = LootTableRegistry.beginPublication(
                    "structure_lua_frontend_test.lua", "Structure Lua frontend test");
                    WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication(
                            "structure_lua_frontend_test.lua", "Structure Lua frontend test")) {
                lua.load(script()).call();
                String example = new String(Files.readAllBytes(Paths.get(
                        "examples/03_adv_20_complete_worldgen_pack/structures.lua")), StandardCharsets.UTF_8);
                lua.load(example, "@examples/03_adv_20_complete_worldgen_pack/structures.lua").call().call();
                lootBatch.validate();
                batch.validate();
                lootBatch.publish();
                batch.publish();
            }

            List<WorldGenRegistry.StructureDescription> structures = WorldGenRegistry.structureSnapshot();
            require(structures.size() == 6,
                    "Compiled, builder, clone, loot, plain-table, and bundled builder structures publish");
            require(description(structures, "test:structure/compiled").blocks == 2,
                    "Compiled handles retain their pre-mutation snapshot");
            require(description(structures, "test:structure/mutated").blocks == 3,
                    "Passing a builder directly snapshots its current visible data");
            require(description(structures, "test:structure/clone").blocks == 4,
                    "Clones can diverge without changing their source builder");
            require(description(structures, "test:structure/plain").blocks == 2,
                    "Plain Lua tables compile through the shared structure schema");
            require(description(structures, "test:structure/plain").dimensions.equals("2x1x1"),
                    "Lua geometry produces the same bounds as JSON geometry");
            require(description(structures, "test:structure/loot").blocks == 1,
                    "Builder loot helpers accept typed reusable table handles");
            require(description(structures, "example:structure/amber_stone_circle").blocks > 0,
                    "The bundled builder example compiles and publishes");
            System.out.println("Structure Lua frontend checks passed.");
        } finally {
            LootTableRegistry.clear();
            WorldGenRegistry.clear();
            owner.invoke(null, new Object[]{null});
        }
    }

    private static String script() {
        return "local structures=betamoon.worldgen.structures; "
                + "local builder=structures:builder(); "
                + "assert(builder.format=='betamoon_structure' and type(builder.palette)=='table' "
                + "and type(builder.elements)=='table' and type(builder.fill)=='function' "
                + "and type(builder.loot)=='function' and type(builder.repeatElement)=='function'); "
                + "for key in pairs(builder) do assert(key~='fill' and key~='compile' and key~='clone') end; "
                + "builder:state('stone',{block='minecraft:stone'}); "
                + "local supplied={from={0,0,0},to={1,0,0},state='stone'}; builder:fill(supplied); "
                + "supplied.to[1]=12; assert(builder.elements[1].to[1]==1 and builder.elements[1].type=='fill'); "
                + "local compiled=builder:compile(); "
                + "builder:block{pos={2,0,0},state='stone'}; "
                + "local clone=builder:clone(); clone:block{pos={3,0,0},state='stone'}; "
                + "assert(#builder.elements==2 and #clone.elements==3); "
                + "structures:add{key='test:compiled',template=compiled}; "
                + "structures:add{key='test:mutated',template=builder}; "
                + "structures:add{key='test:clone',template=clone}; "
                + "local lootTable=betamoon.lootTables:add{key='test:dungeon',pools={{key='main',rolls=1,"
                + "entries={{type='item',stack={item=265,count=2,damage=1}}}}}}; "
                + "assert(lootTable.key=='test:dungeon' and betamoon.lootTables:getRequired('test:dungeon').key"
                + "=='test:dungeon' and betamoon.lootTables:get('test:missing')==nil); "
                + "local nestedLoot=betamoon.lootTables:add{key='test:nested',pools={{key='nested',rolls=1,"
                + "entries={{type='table',table=lootTable}}}}}; "
                + "local lootBuilder=structures:builder(); lootBuilder:state('chest',{block=54}); "
                + "lootBuilder:block{pos={0,0,0},state='chest'}; "
                + "lootBuilder:loot{key='main_chest',pos={0,0,0},table=nestedLoot}; "
                + "structures:add{key='test:loot',template=lootBuilder}; "
                + "structures:add{key='test:plain',template={palette={stone={block='minecraft:stone'}},"
                + "elements={{type='fill',from={0,0,0},to={1,0,0},state='stone'}}}}; "
                + "assert(not pcall(function() structures:add{key='test:both',path='asset.json',"
                + "template={}} end)); "
                + "assert(not pcall(function() local cyclic={}; cyclic.self=cyclic; "
                + "structures:add{key='test:cyclic',template=cyclic} end)); "
                + "assert(not pcall(function() structures:add{key='test:sparse',template={"
                + "palette={stone={block='minecraft:stone'}},elements={[2]={type='block',pos={0,0,0},"
                + "state='stone'}}}} end)); "
                + "assert(not pcall(function() structures:add{key='test:function',template={"
                + "palette={},elements={},extra=function() end}} end))";
    }

    private static WorldGenRegistry.StructureDescription description(
            List<WorldGenRegistry.StructureDescription> structures, String key) {
        for (WorldGenRegistry.StructureDescription structure : structures) {
            if (structure.key.equals(key)) {
                return structure;
            }
        }
        throw new AssertionError("Missing structure description " + key);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
