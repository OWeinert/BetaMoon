package betamoon.interop.live;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.LinkedHashMap;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real native menus and mapped actions; avoids compile-time Minecraft names in
 * the CLIENT loader.
 */
final class UiMenuLiveChecks {
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static final String SCREEN = "net/minecraft/src/GuiScreen";
    private static final String OWNER = "ui-menu-live.lua";
    private static final String OTHER = "ui-menu-partner.lua";
    private static Globals lua;
    private static ClassLoader loader;
    private static int phase;
    private static int wait;
    private static Object parent;
    private static long phaseFrame;

    static boolean beforeWorld(Object client) throws Exception {
        if (phase == 9) {
            return true;
        }
        if (++wait > 180) {
            throw new AssertionError("Native menu acceptance timed out at phase " + phase + ": "
                    + (lua == null
                            ? "initializing"
                            : lua.load("return a and a:status(),a and a:reason(),"
                                    + "b and b:status(),b and b:reason(),hi and hi:status(),lo and lo:status()")
                                    .invoke()));
        }
        if (phase == 0) {
            loader = client.getClass().getClassLoader();
            lua = JsePlatform.standardGlobals();
            attach("bm", OWNER);
            attach("partner", OTHER);
            lua.load("ui=bm.client.ui;u2=partner.client.ui;clicks=0;nativeMode=false;bad=false;"
                    + "ui.screens:add{key='menutest:detail',root={type='text',text='Menu detail'}};"
                    + "ui.screens:add{key='menutest:first',root={type='button',id='open',height=20,text='Open',on={activate=function(ctx)"
                    + "clicks=clicks+1;if nativeMode then ticket=assert(ctx.menu:action('options')) "
                    + "else page=assert(ui.screens:open('menutest:detail'))end end}}};"
                    + "u2.screens:add{key='menutest:second',root={type='button',id='second',height=20,text='Partner'}};"
                    + "ui.screens:add{key='menutest:options',root={type='button',id='back',height=20,text='Back',"
                    + "on={activate=function(ctx)backTicket=assert(ctx.menu:action('back'))end}}};"
                    + "ui.menus:add{key='menutest:a',target='minecraft:main',screen='menutest:first'};"
                    + "u2.menus:add{key='menutest:b',target='minecraft:main',screen='menutest:second',after={'menutest:a'}};"
                    + "ui.menus:add{key='menutest:options_menu',target='minecraft:options',screen='menutest:options',region='footer'};"
                    + "a=ui.menus:activate('menutest:a');b=u2.menus:activate('menutest:b');"
                    + "opts=ui.menus:activate('menutest:options_menu');").call();
            parent = Class.forName("betamoon.gui.GuiBetaMoonMainMenu", true, loader).getConstructor().newInstance();
            NativeLiveClientDriver.call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", parent);
            next(1);
            return false;
        }
        if (phase == 1 && ready("a:status()=='active' and b:status()=='active'")) {
            Object first = entry("menutest:a");
            Object second = entry("menutest:b");
            Object firstMount = field(first, "mount");
            Object secondMount = field(second, "mount");
            int bottom = ((Number) invoke(firstMount, "getBottom")).intValue();
            int top = ((Number) invoke(secondMount, "getTop")).intValue();
            require(bottom < top, "Two owners overlapped or lost their declared ordering");
            capture(client, "ui-native-menu.png");
            click("menutest:a", "open");
            require(lua.get("clicks").toint() == 1, "Contribution native click count: " + lua.get("clicks"));
            next(2);
        } else if (phase == 2 && ready("page and page:status()=='active'")) {
            require(currentScreen(client).getClass().getName().equals("betamoon.gui.screen.GuiManagedScreen"),
                    "A contributed action did not open a normal registered screen");
            lua.load("page:close()").call();
            next(3);
        } else if (phase == 3 && currentScreen(client) == parent
                && ready("a:status()=='active' and b:status()=='active'")) {
            lua.set("nativeMode", LuaValue.TRUE);
            click("menutest:a", "open");
            next(4);
        } else if (phase == 4 && ready("ticket and ticket:status()=='succeeded' and opts:status()=='active'")) {
            require(currentScreen(client) != parent, "Typed options action did not use native navigation");
            click("menutest:options_menu", "back");
            next(5);
        } else if (phase == 5 && currentScreen(client) == parent
                && ready("backTicket:status()=='succeeded' and a:status()=='active'")) {
            lua.load("ui.components:add{key='menutest:raw',size={width=20,height=20},rawDraw=function(f)"
                    + "if bad then error('intentional menu paint fault')end end};"
                    + "ui.screens:add{key='menutest:low',root={type='button',id='low',height=20,text='Low'}};"
                    + "ui.screens:add{key='menutest:high',root={type='column',children={{type='button',text='High',height=20},"
                    + "{component='menutest:raw'}}}};"
                    + "ui.menus:add{key='menutest:low_menu',target='minecraft:main',screen='menutest:low',replace=true};"
                    + "ui.menus:add{key='menutest:high_menu',target='minecraft:main',screen='menutest:high',replace=true,priority=10};"
                    + "lo=ui.menus:activate('menutest:low_menu');hi=ui.menus:activate('menutest:high_menu')").call();
            next(6);
        } else if (phase == 6 && ready("hi:status()=='active' and lo:status()=='suspended'")) {
            require(suppressed(client), "A usable replacement did not suppress native buttons");
            capture(client, "ui-native-menu-replacement.png");
            for (Object button : (java.util.List<?>) NativeLiveClientDriver.field(parent, SCREEN, "controlList",
                    "Ljava/util/List;")) {
                if (((Number) NativeLiveClientDriver.field(button, "net/minecraft/src/GuiButton", "id", "I"))
                        .intValue() == 0) {
                    int x = ((Number) NativeLiveClientDriver.field(button, "net/minecraft/src/GuiButton", "xPosition",
                            "I")).intValue();
                    int y = ((Number) NativeLiveClientDriver.field(button, "net/minecraft/src/GuiButton", "yPosition",
                            "I")).intValue();
                    nativeClick(client, parent, x + 4, y + 4);
                }
            }
            require(currentScreen(client) == parent, "A replacement click activated a covered native button");
            lua.set("bad", LuaValue.TRUE);
            next(7);
        } else if (phase == 7 && ready("hi:status()=='refused' and lo:status()=='active'")) {
            require(suppressed(client), "Faulted replacement did not yield to the next usable owner");
            lua.load("lo:close();hi:close()").call();
            require(!suppressed(client), "Closed claims continued suppressing native input/paint before the next tick");
            next(8);
        } else if (phase == 8 && ready("a:status()=='active' and b:status()=='active'")) {
            Class<?> tracker = Class.forName("betamoon.luamodloader.ScriptResourceTracker", false, loader);
            tracker.getMethod("unload", String.class).invoke(null, OWNER);
            tracker.getMethod("unload", String.class).invoke(null, OTHER);
            require(!suppressed(client), "Owner unload retained replacement authority");
            NativeLiveClientDriver.call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V",
                    new Object[]{null});
            System.setProperty("betamoon.ui.menus.passed", "true");
            System.out
                    .println("[UI-MENU-LIVE] passed: two owners, shared input, full page/return, mapped options/back, "
                            + "replacement readiness, priority/fault fallback and immediate claim/unload release.");
            next(9);
        }
        return phase == 9;
    }

    private static boolean ready(String expression) {
        return wait >= 4 && NativeLiveClientDriver.presentedFrames() > phaseFrame + 1
                && lua.load("return " + expression).call().toboolean();
    }

    private static void next(int value) {
        phase = value;
        wait = 0;
        phaseFrame = NativeLiveClientDriver.presentedFrames();
    }

    private static void attach(String name, String owner) throws Exception {
        LuaTable module = new LuaTable();
        Class.forName("betamoon.luaapi.client.ui.GuiUiApi", true, loader)
                .getMethod("attach", LuaTable.class, String.class).invoke(null, module, owner);
        lua.set(name, module);
    }

    private static Object currentScreen(Object client) throws Exception {
        return NativeLiveClientDriver.field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;");
    }

    private static boolean suppressed(Object client) throws Exception {
        return (Boolean) Class.forName("betamoon.gui.menu.GuiMenuRuntime", false, loader)
                .getMethod("suppressNative", parent.getClass().getSuperclass().getSuperclass())
                .invoke(null, currentScreen(client));
    }

    private static Object context() throws Exception {
        return field(Class.forName("betamoon.gui.menu.GuiMenuRuntime", false, loader), "current");
    }

    private static Object entry(String key) throws Exception {
        return ((Map<?, ?>) field(context(), "entries")).get(key);
    }

    private static void click(String key, String id) throws Exception {
        Object entry = entry(key);
        Object activation = invoke(field(entry, "controller"), "current");
        Object plan = invoke(activation, "plan");
        String resolved = (String) plan.getClass().getMethod("elementId", String.class).invoke(plan, id);
        Object page = field(entry, "page");
        Method tree = page.getClass().getDeclaredMethod("tree");
        tree.setAccessible(true);
        Object element = tree.invoke(page).getClass().getMethod("element", String.class).invoke(tree.invoke(page),
                resolved);
        int x = ((Number) invoke(element, "getLeft")).intValue() + 4;
        int y = ((Number) invoke(element, "getTop")).intValue() + 4;
        Object context = context();
        nativeClick(field(context, "client"), field(context, "screen"), x, y);
    }

    private static void nativeClick(Object client, Object screen, int x, int y) throws Exception {
        int displayWidth = ((Number) NativeLiveClientDriver.field(client, CLIENT, "displayWidth", "I")).intValue();
        int displayHeight = ((Number) NativeLiveClientDriver.field(client, CLIENT, "displayHeight", "I")).intValue();
        int width = ((Number) NativeLiveClientDriver.field(screen, SCREEN, "width", "I")).intValue();
        int height = ((Number) NativeLiveClientDriver.field(screen, SCREEN, "height", "I")).intValue();
        Map<Field, Object> saved = new LinkedHashMap<>();
        for (String name : new String[]{"eventButton", "eventState", "event_dwheel", "event_x", "event_y"}) {
            Field field = Mouse.class.getDeclaredField(name);
            field.setAccessible(true);
            saved.put(field, field.get(null));
        }
        Class<?> input = Class.forName("betamoon.client.control.input.ClientInputRuntime", false, loader);
        Class<?> device = Class.forName("betamoon.client.control.input.InputDeviceEvent", false, loader);
        Field consumed = input.getDeclaredField("mouseConsumed");
        consumed.setAccessible(true);
        boolean previous = consumed.getBoolean(null);
        Object router = input.getMethod("router").invoke(null);
        boolean protectedMenu = "minecraft:options".equals(field(context(), "target"));
        try {
            mouse("eventButton", 0);
            mouse("event_dwheel", 0);
            mouse("event_x", (x * displayWidth + displayWidth / 2) / width);
            mouse("event_y", ((height - y - 1) * displayHeight + displayHeight / 2) / height);
            for (boolean pressed : new boolean[]{true, false}) {
                mouse("eventState", pressed);
                router.getClass().getMethod("observeScreen", Object.class).invoke(router, screen);
                Object event = device.getMethod("mouseButton", int.class, boolean.class).invoke(null, 0, pressed);
                boolean handled = (Boolean) router.getClass()
                        .getMethod(protectedMenu ? "acceptProtected" : "accept", device, boolean.class)
                        .invoke(router, event, false);
                consumed.setBoolean(null, handled);
                NativeLiveClientDriver.call(screen, SCREEN, "handleMouseInput", "()V");
            }
        } finally {
            for (Map.Entry<Field, Object> value : saved.entrySet()) {
                value.getKey().set(null, value.getValue());
            }
            consumed.setBoolean(null, previous);
        }
    }

    private static void mouse(String name, Object value) throws Exception {
        Field field = Mouse.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void capture(Object client, String name) throws Exception {
        int width = ((Number) NativeLiveClientDriver.field(client, CLIENT, "displayWidth", "I")).intValue();
        int height = ((Number) NativeLiveClientDriver.field(client, CLIENT, "displayHeight", "I")).intValue();
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 4;
                image.setRGB(x, height - y - 1, 0xFF000000 | (pixels.get(offset) & 255) << 16
                        | (pixels.get(offset + 1) & 255) << 8 | pixels.get(offset + 2) & 255);
            }
        }
        ImageIO.write(image, "png",
                Paths.get(System.getProperty("betamoon.live.result")).resolveSibling(name).toFile());
    }

    private static Object field(Object object, String name) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>) object : object.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object instanceof Class ? null : object);
    }

    private static Object invoke(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
