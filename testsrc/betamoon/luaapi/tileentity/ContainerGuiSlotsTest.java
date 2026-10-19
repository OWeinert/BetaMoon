package betamoon.luaapi.tileentity;

import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiAnchor;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.luamodloader.LuaScriptRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Purely visual slot frames use inner dimensions and share normal image
 * layout/tooltip rules.
 */
public final class ContainerGuiSlotsTest {
    public static void main(String[] arguments) throws Exception {
        Field registered = TileEntityRegistry.class.getDeclaredField("minecraftTypeRegistered");
        registered.setAccessible(true);
        registered.setBoolean(null, true);
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "visual_slots_test.lua");
        try {
            Globals lua = JsePlatform.standardGlobals();
            LuaTable api = new LuaTable();
            TileEntityApi.attach(api);
            lua.set("betamoon", api);
            LuaValue handle = lua.load("local tile=betamoon.tileEntities:add{name='visual_slots',"
                    + "inventory={slots={input={index=0}}},data={active={type='boolean',default=true,sync=true}}};"
                    + "return betamoon.containers:add{name='visual_slots',tileEntity=tile,"
                    + "slots={{slot='input',x=80,y=30}},playerInventory={x=8,y=84}}").call();
            ContainerDefinition container = ((TileEntityApi.ContainerHandle) handle).definition;
            verifyPanels(lua, container);
            List<ContainerGuiDefinition.Element> frames = parse(lua, container,
                    "{type='slot'}, {type='slot',width=32,height=10}, {type='slot',width=1,height=1},"
                            + "{type='slot',width=254,height=254}, {type='slot',width=24},"
                            + "{type='image',builtin='minecraft:slot'},"
                            + "{type='image',builtin='minecraft:slot',width=40,height=8},"
                            + "{type='group',x=7,y=9,visibleWhen={field='active',equals=true},elements={"
                            + "{type='slot',x=3,y=4,anchor='center',layer='foreground',tooltip={'Detail'}}}}");
            int[][] expected = {{18, 18}, {34, 12}, {3, 3}, {256, 256}, {26, 18}, {18, 18}, {40, 8}, {18, 18}};
            for (int index = 0; index < frames.size(); index++) {
                ContainerGuiDefinition.ImageElement frame = (ContainerGuiDefinition.ImageElement) frames.get(index);
                require(frame.width == expected[index][0] && frame.height == expected[index][1],
                        "Unexpected visual dimensions for element " + index);
                require(frame.image.border == (index == 5 || index == 6 ? 0 : 1),
                        "Only dedicated slot elements should preserve their border");
            }
            ContainerGuiDefinition.ImageElement frame = (ContainerGuiDefinition.ImageElement) frames.get(0);
            ContainerGuiDefinition.ImageElement legacy = (ContainerGuiDefinition.ImageElement) frames.get(5);
            require(frame.image.resource.equals(legacy.image.resource) && frame.image.u == legacy.image.u
                    && frame.image.v == legacy.image.v && frame.image.width == legacy.image.width
                    && frame.image.height == legacy.image.height, "Default slot must use the native slot sprite");
            ContainerGuiDefinition.ImageElement grouped = (ContainerGuiDefinition.ImageElement) frames.get(7);
            require(grouped.x == 10 && grouped.y == 13 && grouped.anchor == GuiAnchor.CENTER
                    && grouped.visibleWhen != null && grouped.tooltip.get(0).equals("Detail"),
                    "Slot frames must retain group offsets, anchoring, visibility and tooltips");
            require(container.slots.size() == 1 && container.slots.get(0).x == 80,
                    "Visual frames must not create or reposition container slots");
            ContainerGuiParser.validateBounds(parse(lua, container, "{type='slot',x=158,y=148}"), 176, 166);
            ContainerGuiParser.validateBounds(parse(lua, container, "{type='slot',width=254,height=254}"), 256, 256);
            reject(lua, container, "{type='slot',x=159}", true);
            reject(lua, container, "{type='slot',y=149}", true);
            for (String invalid : new String[]{"0", "-1", "0.5", "'16'", "true", "255", "2147483647", "math.huge",
                    "0/0"}) {
                reject(lua, container, "{type='slot',width=" + invalid + "}", false);
                reject(lua, container, "{type='slot',height=" + invalid + "}", false);
            }
            System.out.println("Visual slot GUI passed: defaults, inner sizing, nine-slice source, legacy images, "
                    + "grouping, bounds, strict dimensions and independent container slots.");
        } finally {
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void verifyPanels(Globals lua, ContainerDefinition container) {
        List<ContainerGuiDefinition.Element> panels = parse(lua, container,
                "{type='panel',x=176,y=0,width=64,height=80},"
                        + "{type='panel',x=-40,y=0,width=40,height=50,color='green'},"
                        + "{type='panel',x=0,y=-24,width=40,height=24,color=0x123456},"
                        + "{type='panel',x=0,y=166,width=40,height=24,layer='foreground'},"
                        + "{type='slot',x=180,y=8}, {type='slot',x=168,y=24}, {type='slot',x=-20,y=8}");
        ContainerGuiParser.validateBounds(panels, 176, 166);
        ContainerGuiDefinition.PanelElement first = (ContainerGuiDefinition.PanelElement) panels.get(0);
        ContainerGuiDefinition.PanelElement green = (ContainerGuiDefinition.PanelElement) panels.get(1);
        require(first.width == 64 && first.height == 80 && first.layer == 0 && first.color == 0xFFFFFF
                && green.color == 0x55FF55, "Panels need outer dimensions, a background layer and optional tint");
        require(container.slots.size() == 1, "Expansion panels must not create container slots");
        ContainerGuiParser.validateBounds(
                parse(lua, container, "{type='slot',x=190,y=6}, {type='panel',x=176,y=0,width=80,height=80}"), 176,
                166);
        reject(lua, container, "{type='panel',x=176,y=20,width=40,height=40}, {type='slot',x=180,y=0}", true);
        reject(lua, container, "{type='panel',x=176,y=0,width=20,height=40}, {type='slot',x=190,y=8}", true);
        reject(lua, container, "{type='panel',x=176,y=0,width=20,height=20},"
                + "{type='panel',x=196,y=20,width=20,height=20}, {type='slot',x=190,y=8}", true);
        ContainerGuiParser
                .validateBounds(
                        parse(lua, container,
                                "{type='panel',x=176,y=0,width=12,height=40},"
                                        + "{type='panel',x=188,y=0,width=12,height=40}, {type='slot',x=180,y=8}"),
                        176, 166);
        for (String invalid : new String[]{"nil", "8", "0", "-1", "9.5", "257", "'16'", "math.huge", "0/0"}) {
            reject(lua, container, "{type='panel',width=" + invalid + ",height=16}", false);
            reject(lua, container, "{type='panel',width=16,height=" + invalid + "}", false);
        }
        reject(lua, container, "{type='panel',width=16,height=16,color=-1}", false);
        reject(lua, container, "{type='panel',width=16,height=16,color=0x1000000}", false);
    }

    private static List<ContainerGuiDefinition.Element> parse(Globals lua, ContainerDefinition container,
            String entries) {
        List<ContainerGuiDefinition.Element> elements = new ArrayList<ContainerGuiDefinition.Element>();
        ContainerGuiParser.parseElements(lua.load("return {" + entries + "}").call(), container, elements, 0, 0, null);
        return elements;
    }

    private static void reject(Globals lua, ContainerDefinition container, String entry, boolean bounds) {
        try {
            List<ContainerGuiDefinition.Element> elements = parse(lua, container, entry);
            if (bounds) {
                ContainerGuiParser.validateBounds(elements, 176, 166);
            }
            throw new AssertionError("Invalid slot declaration accepted: " + entry);
        } catch (LuaError expected) {
            // Reject before any GUI or inventory mutation.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
