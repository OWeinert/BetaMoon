package betamoon.interop.live;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Real managed screen acceptance without compile-time Minecraft names in the
 * CLIENT test loader.
 */
final class UiLiveClientChecks {
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static final String SCREEN = "net/minecraft/src/GuiScreen";
    private static final String OWNER = "ui-live-acceptance.lua";
    private static Globals lua;
    private static Object activation;
    private static Object screen;
    private static int phase;
    private static int wait;
    private static int worldPhase;
    private static int example;
    private static String exampleOwner;
    private static Object exampleMenu;
    private static final String[] EXAMPLES = {"01_beg_33_first_screen.lua", "02_int_36_reactive_screen.lua",
            "03_adv_26_prepared_ui_components.lua", "03_adv_27_raw_ui_graphics.lua"};
    private static final String[] EXAMPLE_SCREENS = {"example:first_screen", "example:reactive_form",
            "example:prepared_ui", "example:raw_ui"};
    private static ClassLoader loader;

    static boolean beforeWorld(Object client) throws Exception {
        if (phase == 8) {
            return true;
        }
        if (phase == 0) {
            loader = client.getClass().getClassLoader();
            lua = JsePlatform.standardGlobals();
            LuaTable module = new LuaTable();
            Class.forName("betamoon.luaapi.client.ui.GuiUiApi", true, loader)
                    .getMethod("attach", LuaTable.class, String.class).invoke(null, module, OWNER);
            Class.forName("betamoon.luaapi.event.EventsApi", true, loader).getMethod("attach", LuaTable.class)
                    .invoke(null, module);
            lua.set("betamoon", module);
            Class.forName("betamoon.luamodloader.ScriptExecution", false, loader)
                    .getMethod("withOwner", String.class, Supplier.class)
                    .invoke(null, OWNER, (Supplier<Object>) () -> lua
                            .load("ui=betamoon.client.ui; rawCalls=0; paintCalls=0; bad=false; "
                                    + "ui.components:add{key='uitest:wheel',size={width=80,height=80},paint=function(v)paintCalls=paintCalls+1;"
                                    + "return {{op='ring',x=40,y=40,inner=20,outer=32,sweep=300,color='#FF55AAFF'},{op='hitCircle',name='wheel',x=40,y=40,radius=32}}end};"
                                    + "ui.components:add{key='uitest:raw',size={width=80,height=32},rawDraw=function(f)rawCalls=rawCalls+1;"
                                    + "assert(not pcall(function()form:close()end));f.gl:enable('texture',false);f.gl:program('tintProgram');"
                                    + "f.gl:uniform('tint',0.25,0.75,0.5,1);f.gl:buffer('quad');f.gl:program(nil);"
                                    + "f.gl:target('scratch');f.gl:clear(0xFF112233);f.gl:target(nil);end};"
                                    + "templateCalls=0;ui.themes:add{key='uitest:theme',base='minimal',variants={default={values={foreground='#FFEEEEEE'}}},templates={button={parts={'content','focus'},paint=function(v)templateCalls=templateCalls+1;"
                                    + "local function commands(c)return {{op='rect',width={source='width'},height={source='height'},color=c},{op='text',text=v.props.text,x=6,y=5,color=v.styles.default.foreground}}end;"
                                    + "return {default=commands('#FF263B50'),hovered=commands('#FF456B90'),pressed=commands('#FF152839'),focused=commands('#FF385169')}end}}};"
                                    + "definition={key='uitest:form',theme='uitest:theme',state={count={type='integer',default=0,min=0,max=100}},"
                                    + "actions={apply={scope='client',payload={},handler=function(ctx)ctx.state:set('count',ctx.state:get('count')+1);return {applied=true}end},"
                                    + "world={scope='world',payload={},handler=function()error('worldless action ran')end}},"
                                    + "resources={buffers={quad={mode='quads',vertices={0,0,80,0,80,32,0,32}}},"
                                    + "programs={tintProgram={vertex='void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;}',"
                                    + "fragment='uniform vec4 tint;void main(){gl_FragColor=tint;}',uniforms={tint=4}}},targets={scratch={width=16,height=16}}},"
                                    + "root={type='column',width=200,height=190,gap=3,children={{type='text',id='readout',bind=function(v)return {text='Count '..v.state:get('count')}end},"
                                    + "{component='uitest:wheel'},{component='uitest:raw'},{type='button',id='apply',text='Apply',on={activate=function(ctx)ticket=assert(ctx.screen:request('apply',{}))end}},"
                                    + "{type='button',id='close',text='Close',on={activate=function(ctx)ctx.screen:close()end}}}}};"
                                    + "dialogOpens=0;dialogRefreshes=0;dialogDefinition={key='uitest:dialog',state={count={type='integer',default=0}},"
                                    + "on={open=function()dialogOpens=dialogOpens+1 end,refresh=function()dialogRefreshes=dialogRefreshes+1 end},"
                                    + "actions={accept={scope='client',payload={},handler=function(ctx)ctx.state:set('count',4);return true end}},"
                                    + "root={type='column',width=160,height=60,children={{type='text',text='Confirm'},"
                                    + "{type='button',id='accept',text='Accept',on={activate=function(ctx)dialogTicket=ctx.screen:request('accept',{})end}}}}};ui.screens:add(dialogDefinition);"
                                    + "ui.screens:add(definition);wrongGuiTick=false;betamoon.events:on('gui_tick',function(ctx)"
                                    + "if form and form:status()=='active' and ctx.name~='GuiManagedScreen' then wrongGuiTick=true end end);"
                                    + "opening=betamoon.events:on('game_tick',function(ctx)if not form then "
                                    + "assert(not pcall(function()luajava.bindClass('betamoon.luaapi.utils.LuaCallbackScope').requireGameplayWrite()end));"
                                    + "form=assert(ui.screens:open('uitest:form',{state={count=7}}));assert(form:status()=='queued')end end)")
                            .call());
            phase = 1;
            return false;
        }
        if (phase == 1) {
            if (!lua.load("return form and form:status()=='active' and rawCalls>1").call().toboolean()) {
                if (++wait > 60) {
                    throw new AssertionError("Managed screen did not activate/render: "
                            + lua.load("return form:status(),form:reason()").invoke());
                }
                return false;
            }
            activation = Class.forName("betamoon.gui.screen.GuiClientUiRuntime", false, loader).getMethod("current")
                    .invoke(null);
            screen = NativeLiveClientDriver.field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;");
            require(screen != null && screen.getClass().getName().equals("betamoon.gui.screen.GuiManagedScreen"),
                    "Public UI did not use its built-in native adapter");
            require(lua.load("return paintCalls==1 and form.state:get('count')==7").call().toboolean(),
                    "Prepared paint polled Lua or lost initial state");
            lua.load(
                    "oldElement=form:element('apply'); local t,r=form:request('world',{});assert(t==nil and r=='world_unavailable')")
                    .call();
            click(client, "apply");
            phase = 2;
            return false;
        }
        if (phase == 2) {
            if (!lua.load("return ticket and ticket:status()=='succeeded'").call().toboolean()) {
                return false;
            }
            require(lua.load("return form.state:get('count')==8 and ticket:result().applied").call().toboolean(),
                    "Client action did not publish its detached completion/state");
            require(!lua.get("wrongGuiTick").toboolean(),
                    "gui_tick described the screen replaced by queued UI activation");
            capture(client);
            require(lua.load("return templateCalls==2").call().toboolean(),
                    "Template builder ran during repeated painting");
            lua.load("popup=assert(form:dialog('uitest:dialog',{state={count=2}}));assert(popup:status()=='queued')")
                    .call();
            wait = 0;
            phase = 7;
            return false;
        }
        if (phase == 7) {
            if (++wait < 3) {
                return false;
            }
            if (!lua.load("return popup:status()=='active'").call().toboolean()) {
                if (wait > 30) {
                    throw new AssertionError(
                            "Dialog did not activate: " + lua.load("return popup:status(),popup:reason()").invoke());
                }
                return false;
            }
            require(NativeLiveClientDriver.field(client, CLIENT, "currentScreen",
                    "Lnet/minecraft/src/GuiScreen;") == screen, "Dialog replaced its parent's native screen");
            require(lua.load("return popup.state:get('count')==2 and form.state:get('count')==8").call().toboolean(),
                    "Dialog did not isolate local state");
            java.lang.reflect.Field controllerField = activation.getClass().getDeclaredField("controller");
            controllerField.setAccessible(true);
            Object controller = controllerField.get(activation);
            java.lang.reflect.Field dialogsField = controller.getClass().getDeclaredField("dialogs");
            dialogsField.setAccessible(true);
            Object parent = activation;
            activation = ((java.util.Map<?, ?>) dialogsField.get(controller)).values().iterator().next();
            click(client, "accept");
            activation = parent;
            wait = 0;
            phase = 9;
            return false;
        }
        if (phase == 9) {
            if (!lua.load("return dialogTicket and dialogTicket:status()=='succeeded'").call().toboolean()) {
                if (++wait > 30) {
                    throw new AssertionError("Dialog control action did not complete");
                }
                return false;
            }
            require(lua.load("return popup.state:get('count')==4 and form.state:get('count')==8").call().toboolean(),
                    "Dialog action used its parent's model");
            lua.load("dialogDefinition.root.children[2].text='Accepted';ui.screens:add(dialogDefinition)").call();
            phase = 10;
            wait = 0;
            return false;
        }
        if (phase == 10) {
            if (++wait < 4) {
                return false;
            }
            require(lua.load("return popup.state:get('count')==4 and popup:element('accept'):get('text')=='Accepted' "
                    + "and dialogOpens==1 and dialogRefreshes==1").call().toboolean(),
                    "Dialog visual refresh lost state or reopened its lifecycle");
            NativeLiveClientDriver.call(screen, SCREEN, "keyTyped", "(CI)V", '\0', 1);
            require(lua.load("return popup:status()=='closed' and form:status()=='active'").call().toboolean(),
                    "Escape did not close only the modal document");
            lua.load("definition.root.children[4].text='Updated';ui.screens:add(definition)").call();
            wait = 0;
            phase = 3;
            return false;
        }
        if (phase == 3) {
            if (++wait < 4) {
                return false;
            }
            if (lua.load("return pcall(function()oldElement:get('text')end)").call().toboolean()) {
                return false;
            }
            require(lua.load("return form.state:get('count')==8 and form:element('apply'):get('text')=='Updated'")
                    .call().toboolean(), "Visual refresh reopened/reset the page");
            require(lua.load("return paintCalls==2").call().toboolean(),
                    "Compatible visual refresh rebuilt paint repeatedly");
            lua.load(
                    "ui.components:add{key='uitest:wheel',size={width=80,height=80},paint=function()return {{op='rect',width=-1,height=10}}end}")
                    .call();
            wait = 0;
            phase = 4;
            return false;
        }
        if (phase == 4) {
            if (++wait < 4) {
                return false;
            }
            require(NativeLiveClientDriver.field(client, CLIENT, "currentScreen",
                    "Lnet/minecraft/src/GuiScreen;") == screen, "Rejected custom paint replaced the native page");
            require(lua.load("return form:status()=='active' and form.state:get('count')==8").call().toboolean(),
                    "Rejected paint refresh discarded the usable model");
            click(client, "close");
            phase = 5;
            return false;
        }
        if (phase == 5) {
            if (!lua.load("return form:status()=='closed'").call().toboolean()) {
                return false;
            }
            require(NativeLiveClientDriver.field(client, CLIENT, "currentScreen",
                    "Lnet/minecraft/src/GuiScreen;") != screen, "Close retained the owned native screen");
            Class.forName("betamoon.luamodloader.ScriptResourceTracker", false, loader)
                    .getMethod("unload", String.class).invoke(null, OWNER);
            wait = 0;
            phase = 6;
            return false;
        }
        if (phase == 6) {
            if (++wait < 3) {
                return false;
            }
            long bytes = ((Number) Class.forName("betamoon.gui.interop.GuiResourceBudget", false, loader)
                    .getMethod("allocated").invoke(null)).longValue();
            require(bytes == 0, "Owned UI resource bytes survived close/native disposal: " + bytes);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Managed UI rendering leaked a GL error");
            phase = 11;
            System.out.println(
                    "[UI-LIVE] worldless public screen, shared control/action, prepared/raw rendering, retained refresh, rejected paint and resource disposal passed");
            activation = null;
            screen = null;
            return false;
        }
        if (phase == 11) {
            if (example == EXAMPLES.length) {
                lua = null;
                phase = 8;
                System.setProperty("betamoon.ui.passed", "true");
                return true;
            }
            Object nativeMenu = NativeLiveClientDriver.field(client, CLIENT, "currentScreen",
                    "Lnet/minecraft/src/GuiScreen;");
            if (exampleMenu != nativeMenu) {
                exampleMenu = nativeMenu;
                wait = 0;
                return false;
            }
            if (++wait < 3) {
                return false;
            }
            exampleOwner = EXAMPLES[example];
            lua = JsePlatform.standardGlobals();
            Object module = Class.forName("betamoon.luaapi.BetaMoonModule", true, loader).getConstructor(String.class)
                    .newInstance(exampleOwner);
            module.getClass().getMethod("call", LuaValue.class, LuaValue.class).invoke(module, LuaValue.NIL, lua);
            String source = new String(
                    Files.readAllBytes(Paths.get(System.getProperty("betamoon.ui.examples"), exampleOwner)),
                    StandardCharsets.UTF_8);
            Class.forName("betamoon.luamodloader.ScriptExecution", false, loader)
                    .getMethod("withOwner", String.class, Supplier.class)
                    .invoke(null, exampleOwner, (Supplier<Object>) () -> lua.load(source, exampleOwner).call());
            Class.forName("betamoon.luamodloader.ScriptExecution", false, loader)
                    .getMethod("withOwner", String.class, Supplier.class).invoke(null, exampleOwner,
                            (Supplier<Object>) () -> lua
                                    .load("modInit();exampleForm=assert(betamoon.client.ui.screens:open('"
                                            + EXAMPLE_SCREENS[example] + "'))")
                                    .call());
            phase = 12;
            wait = 0;
            return false;
        }
        if (phase == 12) {
            if (++wait < 7) {
                return false;
            }
            require(lua.load("return exampleForm:status()=='active'").call().toboolean(),
                    "Distributed UI example failed its prepared/rendered opening: " + exampleOwner + " "
                            + lua.load("return exampleForm:status(),exampleForm:reason()").invoke());
            require(GL11.glGetError() == GL11.GL_NO_ERROR,
                    "Distributed UI example leaked graphics state: " + exampleOwner);
            lua.load("exampleForm:close()").call();
            phase = 13;
            return false;
        }
        if (phase == 13) {
            if (!lua.load("return exampleForm:status()=='closed'").call().toboolean()) {
                return false;
            }
            Class.forName("betamoon.luamodloader.ScriptResourceTracker", false, loader)
                    .getMethod("unload", String.class).invoke(null, exampleOwner);
            System.out.println("[UI-LIVE] rendered distributed example passed: " + exampleOwner);
            example++;
            exampleMenu = null;
            phase = 11;
            return false;
        }
        return false;
    }

    static boolean inWorld(Object client) throws Exception {
        if (worldPhase == 0) {
            lua = JsePlatform.standardGlobals();
            LuaTable module = new LuaTable();
            Class.forName("betamoon.luaapi.client.ui.GuiUiApi", true, loader)
                    .getMethod("attach", LuaTable.class, String.class).invoke(null, module, OWNER);
            lua.set("betamoon", module);
            Class.forName("betamoon.luamodloader.ScriptExecution", false, loader)
                    .getMethod("withOwner", String.class, Supplier.class)
                    .invoke(null, OWNER, (Supplier<Object>) () -> lua
                            .load("local ui=betamoon.client.ui;ui.screens:add{key='uitest:world',context='world',"
                                    + "data={sample={type='string',default='Waiting'}},actions={sample={scope='world',payload={},handler=function(ctx)"
                                    + "assert(ctx.world and ctx.player and ctx.native);ctx.data:set('sample','World sampled');return true end}},"
                                    + "root={type='text',bind=function(v)return {text=v.data:get('sample')}end}};worldForm=assert(ui.screens:open('uitest:world'))")
                            .call());
            worldPhase = 1;
            wait = 0;
            return false;
        }
        if (worldPhase == 1) {
            if (!lua.load("return worldForm:status()=='active'").call().toboolean()) {
                if (++wait > 40) {
                    throw new AssertionError("World screen did not activate: "
                            + lua.load("return worldForm:status(),worldForm:reason()").invoke());
                }
                return false;
            }
            lua.load("worldTicket=assert(worldForm:request('sample',{}))").call();
            worldPhase = 2;
            wait = 0;
            return false;
        }
        if (worldPhase == 2) {
            if (!lua.load("return worldTicket:status()=='succeeded'").call().toboolean()) {
                if (++wait > 40) {
                    throw new AssertionError("World action did not execute at its world boundary");
                }
                return false;
            }
            require(lua.load("return worldForm.data:get('sample')=='World sampled'").call().toboolean(),
                    "World action did not publish observed data");
            worldPhase = 3;
        }
        return true;
    }

    static void afterWorld() throws Exception {
        require(worldPhase == 3 && lua.load("return worldForm:status()=='closed'").call().toboolean(),
                "World replacement retained the managed world activation");
        Class.forName("betamoon.luamodloader.ScriptResourceTracker", false, loader).getMethod("unload", String.class)
                .invoke(null, OWNER);
        lua = null;
        System.out
                .println("[UI-LIVE] world screen, world-boundary action, observed data and environment cleanup passed");
    }

    private static void capture(Object client) throws Exception {
        int width = ((Number) NativeLiveClientDriver.field(client, CLIENT, "displayWidth", "I")).intValue();
        int height = ((Number) NativeLiveClientDriver.field(client, CLIENT, "displayHeight", "I")).intValue();
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int shaderPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 4;
                int red = pixels.get(offset) & 255;
                int green = pixels.get(offset + 1) & 255;
                int blue = pixels.get(offset + 2) & 255;
                image.setRGB(x, height - 1 - y, 0xFF000000 | red << 16 | green << 8 | blue);
                if (Math.abs(red - 64) <= 2 && Math.abs(green - 191) <= 2 && Math.abs(blue - 128) <= 2) {
                    shaderPixels++;
                }
            }
        }
        Object element = element("apply");
        int logicalWidth = ((Number) NativeLiveClientDriver.field(screen, SCREEN, "width", "I")).intValue();
        int logicalHeight = ((Number) NativeLiveClientDriver.field(screen, SCREEN, "height", "I")).intValue();
        int left = ((Number) invoke(element, "getLeft")).intValue() + 6;
        int top = ((Number) invoke(element, "getTop")).intValue() + 5;
        int white = 0;
        int area = 0;
        for (int y = top * height / logicalHeight; y < (top + 8) * height / logicalHeight; y++) {
            for (int x = left * width / logicalWidth; x < (left + 29) * width / logicalWidth; x++) {
                int pixel = image.getRGB(x, y);
                area++;
                if ((pixel >> 16 & 255) > 210 && (pixel >> 8 & 255) > 210 && (pixel & 255) > 210) {
                    white++;
                }
            }
        }
        require(white > 8 && white < area * 0.7,
                "Prepared button text lost its glyph alpha texture: " + white + "/" + area);
        require(shaderPixels > 32, "Raw shader did not paint actual screen pixels");
        ImageIO.write(image, "png",
                Paths.get(System.getProperty("betamoon.live.result")).resolveSibling("ui-managed-form.png").toFile());
    }

    private static void click(Object client, String id) throws Exception {
        Object element = element(id);
        int x = ((Number) invoke(element, "getLeft")).intValue() + 4;
        int y = ((Number) invoke(element, "getTop")).intValue() + 4;
        NativeLiveClientDriver.call(screen, SCREEN, "mouseClicked", "(III)V", x, y, 0);
        NativeLiveClientDriver.call(screen, SCREEN, "mouseMovedOrUp", "(III)V", x, y, 0);
    }

    private static Object element(String id) throws Exception {
        Object plan = invoke(activation, "plan");
        String resolved = (String) invoke(plan, "elementId", String.class, id);
        java.lang.reflect.Field pageField = activation.getClass().getDeclaredField("page");
        pageField.setAccessible(true);
        Object page = pageField.get(activation);
        Method treeMethod = page.getClass().getDeclaredMethod("tree");
        treeMethod.setAccessible(true);
        return invoke(treeMethod.invoke(page), "element", String.class, resolved);
    }

    private static Object invoke(Object target, String name) throws Exception {
        return invoke(target, name, null, null);
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object argument) throws Exception {
        try {
            return parameter == null
                    ? target.getClass().getMethod(name).invoke(target)
                    : target.getClass().getMethod(name, parameter).invoke(target, argument);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(name + " failed", failure.getCause());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
