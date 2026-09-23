package betamoon.luaapi.tileentity;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.tileentity.ContainerControlDefinition;
import betamoon.tileentity.ContainerControlRuntime;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.ContainerSession;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Contract checks for typed container controls without requiring an OpenGL screen. */
public final class InteractiveContainerControlsTest {
    private InteractiveContainerControlsTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Field registered = TileEntityRegistry.class.getDeclaredField("minecraftTypeRegistered");
        registered.setAccessible(true);
        registered.setBoolean(null, true);
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "interactive_controls_test.lua");

        Globals globals = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        TileEntityApi.attach(api);
        globals.set("betamoon", api);
        LuaValue result = globals.load("return function() "
                + "local tile=betamoon.tileEntities:add{name='controls',inventory={slots={input={index=0}}},"
                + "data={enabled={type='boolean',default=false},speed={type='integer',default=0},"
                + "mode={type='string',default='idle'},label={type='string',default='old'}}};"
                + "local container=betamoon.containers:add{name='controls',tileEntity=tile,"
                + "slots={{slot='input',x=8,y=18}},playerInventory={x=8,y=84},"
                + "session={tab={type='integer',default=1},search={type='string',default='',maxLength=20},"
                + "ratio={type='number',default=1}},"
                + "controls={start={type='action',onActivate=function(ctx) activations=activations+1 end},"
                + "enabled={type='toggle',bind={data='enabled'}},"
                + "speed={type='number',bind={data='speed'},minimum=0,maximum=10,step=2},"
                + "mode={type='choice',bind={data='mode'},values={'idle','repeat'}},"
                + "label={type='text',bind={data='label'},maxLength=5},"
                + "tab={type='number',bind={session='tab'},minimum=1,maximum=3,step=1," 
                + "beforeChange=function(ctx,value) if value==3 then return 'deny' end return 'pass' end},"
                + "ratio={type='choice',bind={session='ratio'},values={1,2}},"
                + "dial={type='custom',onInput=function(ctx) ctx.session:set('tab',1) return 'handled' end}}};"
                + "local gui=betamoon.containerGuis:add{name='controls',container=container,elements={"
                + "{type='button',control='start',x=8,y=8,width=40,text='Start'},"
                + "{type='toggle',control='enabled',x=8,y=32},"
                + "{type='slider',control='speed',x=34,y=32,width=80},"
                + "{type='choice',control='mode',x=8,y=56,width=70},"
                + "{type='text_box',control='label',x=82,y=56,width=80},"
                + "{type='slider',control='tab',x=8,y=80,width=80,visibleWhen={session='tab',greaterOrEqual=1}},"
                + "{type='interactive',control='dial',x=100,y=80,width=32,height=14},"
                + "{type='choice',control='ratio',x=136,y=80,width=32,height=14}}};"
                + "return container,gui end")
                .call();
        globals.set("activations", 0);
        Varargs values = result.invoke();
        ContainerDefinition definition = ((TileEntityApi.ContainerHandle) values.arg(1)).definition;
        ContainerGuiDefinition gui = ((TileEntityApi.GuiHandle) values.arg(2)).definition;
        owner.invoke(null, new Object[]{null});

        require(definition.controls.size() == 8 && definition.session.size() == 3,
                "Control or session declarations were lost");
        require(gui.elements.size() == 8 && gui.elements.get(0) instanceof ContainerGuiDefinition.ControlElement,
                "Interactive GUI elements were not compiled");
        LuaTileEntity tile = new LuaTileEntity(definition.tileEntity.name);
        tile.readFromNBT(saved(definition.tileEntity.name));
        ContainerControlRuntime runtime = new ContainerControlRuntime(definition, tile, null);
        ContainerSession session = runtime.session();
        require(Integer.valueOf(1).equals(session.get("tab")), "Session default changed");

        LuaTable input = ContainerControlRuntime.input("script");
        require(runtime.activate(control(definition, "start"), input)
                && globals.get("activations").checkint() == 1, "Action callback did not run");
        require(runtime.activate(control(definition, "enabled"), input)
                && Boolean.TRUE.equals(tile.getDataValue("enabled")), "Toggle did not update tile data");
        require(runtime.changeNumber(control(definition, "speed"), 9.1D, input)
                && Integer.valueOf(10).equals(tile.getDataValue("speed")), "Number control did not clamp/snap");
        require(runtime.cycle(control(definition, "mode"), 1, input)
                && "repeat".equals(tile.getDataValue("mode")), "Choice control did not cycle");
        require(runtime.editText(control(definition, "label"), "toolong", input, true)
                && "toolo".equals(tile.getDataValue("label")), "Text control did not enforce maxLength");
        require(runtime.changeNumber(control(definition, "tab"), 2, input)
                && Integer.valueOf(2).equals(session.get("tab")), "Session binding did not update");
        require(!runtime.changeNumber(control(definition, "tab"), 3, input)
                && Integer.valueOf(2).equals(session.get("tab")), "beforeChange did not deny mutation");
        require(runtime.custom(control(definition, "dial"), input)
                && Integer.valueOf(1).equals(session.get("tab")), "Custom control input did not run");
        require(runtime.cycle(control(definition, "ratio"), 1, input)
                && Double.valueOf(2.0D).equals(session.get("ratio")), "Number choice did not preserve its type");
        runtime.close();
        expectLuaError(() -> session.get("tab"));
        expectLuaError(() -> runtime.activate(control(definition, "enabled"), input));
        expectLuaError(() -> runtime.custom(control(definition, "dial"), input));
        System.out.println("Interactive container controls passed: parsing, bindings, validation, callbacks, and expiry.");
    }

    private static NBTTagCompound saved(String type) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("BetaMoonType", type);
        return tag;
    }

    private static ContainerControlDefinition control(ContainerDefinition definition, String name) {
        return definition.controls.get(name);
    }

    private static void expectLuaError(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected a LuaError");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
