package betamoon.luaapi;

import betamoon.luaapi.item.ConfigurableAttackDamage;
import betamoon.luaapi.item.ConfigurableItemDurability;
import betamoon.luaapi.item.ConfigurableItemSubtypes;
import betamoon.luaapi.item.ConfigurableMiningEfficiency;
import betamoon.luamodloader.LuaScriptRegistry;
import java.lang.reflect.Method;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises declarative item properties through Java item capabilities. */
public final class ItemCapabilitiesTest {
    private ItemCapabilitiesTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "quality_capabilities.lua");
        try {
            verifyCapabilities();
        } finally {
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void verifyCapabilities() {
        require(Block.stone != null, "Vanilla items must be initialized");
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, lua);
        lua.load("local api=betamoon.items; local types={'pickaxe','axe','shovel','hoe','sword'}; "
                + "for i,kind in ipairs(types) do "
                + "local def={id=28000+i,type=kind,material='WOOD',name='quality_'..kind,maxDamage=91}; "
                + "local tool=api:add(def); def.efficiency=7; " + "local ok,err=pcall(function() api:add(def) end); "
                + "assert(ok==(i<=3)); if not ok then assert(tostring(err):find('does not support efficiency')) end; "
                + "def.efficiency=nil; def.damageVsEntity=11; " + "ok,err=pcall(function() api:add(def) end); "
                + "assert(ok==(kind~='hoe')); if not ok then assert(tostring(err):find('does not support damage')) end; "
                + "api:add({id=28000+i,name='retained',maxDamage=12}); "
                + "end; api:add({id=28010,name='quality_item',maxDamage=55,hasSubtypes=true}); "
                + "api:add({id=28011,name='quality_no_subtypes',hasSubtypes=false})").call();

        for (int index = 1; index <= 5; index++) {
            Item item = Item.itemsList[28000 + index];
            require(item instanceof ConfigurableItemDurability && item instanceof ConfigurableItemSubtypes,
                    "Every existing tool wrapper must expose its durability and subtype setters");
            require(item.getMaxDamage() == 91,
                    "Basic item declarations must preserve their existing type restrictions");
            require((item instanceof ConfigurableMiningEfficiency) == (index <= 3),
                    "Mining capability must be limited to pickaxe, axe and shovel");
            require((item instanceof ConfigurableAttackDamage) == (index != 4),
                    "Hoe must not expose an unsupported attack damage capability");
            if (item instanceof ConfigurableAttackDamage) {
                require(item.getDamageVsEntity(null) == 11, "Damage setter must update the actual Minecraft item");
            }
        }
        Item pickaxe = Item.itemsList[28001];
        require(pickaxe.getStrVsBlock(new ItemStack(pickaxe), Block.stone) == 7,
                "Efficiency must reach native mining behavior");
        Item basic = Item.itemsList[28010];
        require(basic.getMaxDamage() == 55 && basic.getHasSubtypes(), "Basic item setters must still work");
        require(!Item.itemsList[28011].getHasSubtypes(), "False subtype declarations must remain false");
        require(((ConfigurableItemDurability) basic).setMaxDamageValue(60) == basic,
                "Capability setters must retain fluent item identity");
        System.out.println("Item capabilities passed: support matrix, Lua errors, chaining and native item behavior.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
