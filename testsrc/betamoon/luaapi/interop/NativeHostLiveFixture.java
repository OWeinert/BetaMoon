package betamoon.luaapi.interop;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.mapping.ResolvedField;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.hosts.NativeHostType;
import betamoon.interop.hosts.NativeHostShape;
import betamoon.interop.interfaces.NativeImplementation;
import betamoon.interop.interfaces.NativeInterfaces;
import betamoon.interop.interfaces.NativeInterfaceContract;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.interop.client.NativeClientAction;
import betamoon.interop.client.NativeClientSession;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.runtime.NativeContext;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.UpValue;
import org.lwjgl.opengl.GL11;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.objectweb.asm.Type;

/**
 * Real class consumers; no Minecraft class name is linked into this
 * non-reobfuscated test fixture.
 */
public final class NativeHostLiveFixture {
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static final String SCREEN = "net/minecraft/src/GuiScreen";
    private static final String OWNER = "native-x10-menu";
    private static TinyMappingResolver mappings;
    private static RuntimeNamespace namespace;
    private static NativeBindings bindings;
    private static Object client;
    private static Object screen;
    private static Object oldAlias;
    private static NativeLuaHost handle;
    private static Globals globals;
    private static int phase;
    private static final String TILE_RENDERER = "net/minecraft/src/TileEntityRenderer";
    private static final String RENDER_BASE = "net/minecraft/src/TileEntitySpecialRenderer";
    private static final String DRAW = "(Lnet/minecraft/src/TileEntity;DDDF)V";
    private static Object renderer;
    private static Object previousRenderer;
    private static Object renderTile;
    private static Object rendererManager;
    private static Object nativeDelegate;
    private static NativeImplementation renderToken;
    private static int delegatedDraws;

    private NativeHostLiveFixture() {
    }

    public static boolean beforeWorld(Object actualClient) throws Exception {
        if (phase == 5) {
            return true;
        }
        if (mappings == null) {
            mappings = TinyMappingResolver.readStrict(
                    NativeHostLiveFixture.class.getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny"));
            namespace = RuntimeNamespace.valueOf(System.getProperty("betamoon.live.namespace", "NAMED"));
            client = actualClient;
            bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
        }
        require(field(client, CLIENT, "theWorld", "Lnet/minecraft/src/World;") == null,
                "generated screen must prepare and open without a world");
        if (phase == 0) {
            openMenu();
            phase = 1;
            return false;
        }
        if (phase == 1) {
            if (globals.get("draws").toint() == 0) {
                return false;
            }
            require(Boolean.FALSE.equals(call(screen, SCREEN, "doesGuiPauseGame", "()Z")),
                    "native screen pause query did not enter Lua");
            int initial = globals.get("initialized").toint();
            call(screen, SCREEN, "setWorldAndResolution", "(Lnet/minecraft/client/Minecraft;II)V", client, 320, 240);
            require(globals.get("initialized").toint() == initial + 1,
                    "native resize did not initialize generated override");
            closeMenu();
            phase = 2;
            return false;
        }
        if (phase == 2) {
            bindings.clients.observe();
            bindings.interfaces.observe(null, null);
            require(globals.get("closed").toint() == 1, "native close callback did not run once");
            require(!handle.value.describe().get("state").equals("active"),
                    "close action retained owned screen dispatch");
            int paused = globals.get("pauses").toint();
            oldAlias = screen;
            require(Boolean.TRUE.equals(call(oldAlias, SCREEN, "doesGuiPauseGame", "()Z"))
                    && globals.get("pauses").toint() == paused, "closed foreign screen alias called Lua");
            globals.load("assert(not pcall(function() saved:describe() end))").call();
            ScriptResourceTracker.unload(OWNER);
            Class<?> oldClass = screen.getClass();
            openMenu();
            require(screen.getClass() == oldClass, "owner reload generated another Java class for the same shape");
            require(Boolean.FALSE.equals(call(screen, SCREEN, "doesGuiPauseGame", "()Z"))
                    && Boolean.TRUE.equals(call(oldAlias, SCREEN, "doesGuiPauseGame", "()Z")),
                    "old alias adopted replacement owner callbacks");
            phase = 3;
            return false;
        }
        if (phase == 3) {
            if (globals.get("draws").toint() == 0) {
                return false;
            }
            int callbacks = globals.get("closed").toint();
            ScriptResourceTracker.unload(OWNER);
            require(Boolean.TRUE.equals(call(screen, SCREEN, "doesGuiPauseGame", "()Z")),
                    "owner fault left native class callbacks active");
            Object foreign = bindings.names.curatedType(SCREEN).newInstance();
            call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", foreign);
            if (field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;") == screen) {
                call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", new Object[]{null});
            }
            require(field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;") == foreign
                    && globals.get("closed").toint() == callbacks,
                    "closed consumer cleanup replaced another native screen or entered old Lua");
            call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", new Object[]{null});
            phase = 4;
            return false;
        }
        bindings.clients.observe();
        bindings.interfaces.observe(null, null);
        require(((Number) bindings.interfaces.diagnostics().get("classHosts")).intValue() == 0,
                "menu owner cleanup retained class callback entries");
        System.setProperty("betamoon.x10.menu", "true");
        System.out.println(
                "[X10-LIVE] worldless generated GuiScreen open/resize/pause/close, owned cleanup and closed alias/reload passed");
        phase = 5;
        globals = null;
        screen = null;
        oldAlias = null;
        handle = null;
        return true;
    }

    public static boolean rendererReady() throws Exception {
        if (bindings == null || client == null) {
            return false;
        }
        Class<?> manager = runtimeType(TILE_RENDERER);
        Object instance = staticField(manager, TILE_RENDERER, "instance", "Lnet/minecraft/src/TileEntityRenderer;");
        return call(instance, TILE_RENDERER, "getFontRenderer", "()Lnet/minecraft/src/FontRenderer;") != null
                && field(instance, TILE_RENDERER, "worldObj", "Lnet/minecraft/src/World;") == field(client, CLIENT,
                        "theWorld", "Lnet/minecraft/src/World;");
    }

    /**
     * Prepared Java drawing stays in the acceptance adapter; Lua receives no
     * generic GL/trusted-call permission.
     */
    public static Object prepareRenderer() throws Exception {
        NativeContext context = NativeContext.current();
        context.require(NativeContext.Effect.UNKNOWN, true);
        String owner = context.owner();
        Globals environment = ownerGlobals(owner);
        LuaTable state = environment.get("require").call("state").checktable();
        NativeAccessApi api = new NativeAccessApi(NativeDiscoveryRuntime::snapshot);
        Class<?> base = runtimeType(RENDER_BASE);
        NativeTypeRef baseRef = bindings.reference(base);
        NativeMemberRef draw = new NativeMemberRef(NativeMemberRef.Kind.METHOD, baseRef, null, "renderTileEntityAt",
                DRAW, "mapped", "mapped");
        NativeMemberRef constructor = new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, baseRef, null, "new", "()V",
                null, null);
        NativeHostShape shape = bindings.hostShapes
                .resolve(new NativeHostType(baseRef, constructor, Collections.singletonList(draw)), context);
        Class<?> managerType = runtimeType(TILE_RENDERER);
        rendererManager = staticField(managerType, TILE_RENDERER, "instance", "Lnet/minecraft/src/TileEntityRenderer;");
        Class<?> signType = runtimeType("net/minecraft/src/TileEntitySign");
        renderTile = signType.newInstance();
        setField(renderTile, "net/minecraft/src/TileEntity", "worldObj", "Lnet/minecraft/src/World;",
                context.root("world"));
        Object player = context.root("player");
        int tileX = (int) Math.floor((Double) field(player, "net/minecraft/src/Entity", "posX", "D"));
        int tileY = Math.max(1,
                Math.min(126, (int) Math.floor((Double) field(player, "net/minecraft/src/Entity", "posY", "D"))));
        int tileZ = (int) Math.floor((Double) field(player, "net/minecraft/src/Entity", "posZ", "D"));
        setField(renderTile, "net/minecraft/src/TileEntity", "xCoord", "I", tileX);
        setField(renderTile, "net/minecraft/src/TileEntity", "yCoord", "I", tileY);
        setField(renderTile, "net/minecraft/src/TileEntity", "zCoord", "I", tileZ);
        nativeDelegate = call(rendererManager, TILE_RENDERER, "getSpecialRendererForClass",
                "(Ljava/lang/Class;)Lnet/minecraft/src/TileEntitySpecialRenderer;", signType);
        require(nativeDelegate != null, "native sign renderer delegate missing");
        Object preparedDelegate = nativeDelegate;
        NativeImplementation[] token = new NativeImplementation[1];
        LuaValue observation = environment.load("return function(ctx) local state=require('state'); "
                + "assert(not ctx.native:describe().gameplayWrites); "
                + "state.draws=(state.draws or 0)+1; state.saved=ctx.native end").call();
        Map<String, NativeInterfaces.MethodCallback> callbacks = new LinkedHashMap<>();
        Method method = shape.methods.get(0);
        callbacks.put(NativeInterfaceContract.key(method),
                new NativeInterfaces.MethodCallback((selected, arguments, scope) -> {
                    NativeCallbackTable callback = new NativeCallbackTable(scope);
                    callback.set("method", new NativeLuaReference(api, draw));
                    ScriptExecution.invoke(owner, "native.host.prepared-renderer", observation, callback);
                    NativeContext.current().check();
                    bindings.interfaces.requireLive(token[0], NativeContext.current());
                    try {
                        call(preparedDelegate, RENDER_BASE, "renderTileEntityAt", DRAW, arguments);
                        delegatedDraws++;
                    } catch (Exception failure) {
                        throw new IllegalStateException("prepared native sign drawing failed", failure);
                    }
                    return null;
                }, "query"));
        NativeMemberPlan setup = bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.METHOD, baseRef, null,
                "setTileEntityRenderer", "(Lnet/minecraft/src/TileEntityRenderer;)V", "mapped", "mapped"), context,
                true);
        token[0] = bindings.interfaces.createClass(context, shape, callbacks,
                Collections.singletonMap(NativeInterfaceContract.key(method), "noop"), new Object[0], "world", null,
                (implementation, instance) -> setup.call(context, true, instance, new Object[]{rendererManager}));
        previousRenderer = renderer;
        renderer = token[0].value.use(context);
        renderToken = token[0];
        if (previousRenderer != null) {
            require(previousRenderer.getClass() == renderer.getClass(),
                    "renderer owner reload generated another shape");
        }
        state.set("renderer", new NativeLuaHost(api, token[0], baseRef, shape.fingerprint));
        state.set("active", LuaValue.FALSE);
        return renderTile;
    }

    public static void drawRenderer() throws Exception {
        NativeContext.current().require(NativeContext.Effect.UNKNOWN, true);
        Object selected = call(rendererManager, TILE_RENDERER, "getSpecialRendererForEntity",
                "(Lnet/minecraft/src/TileEntity;)Lnet/minecraft/src/TileEntitySpecialRenderer;", renderTile);
        require(selected == renderer, "native getter hook did not select the owned generated renderer");
        int before = delegatedDraws;
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            call(rendererManager, TILE_RENDERER, "renderTileEntityAt", DRAW, renderTile, 0.0, 0.0, 0.0, 0.0f);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL11.glPopAttrib();
        }
        require(delegatedDraws == before + 1 && GL11.glGetError() == GL11.GL_NO_ERROR,
                "actual native renderer did not dispatch one Lua observation and one prepared Java draw");
        require(call(rendererManager, TILE_RENDERER, "getSpecialRendererForClass",
                "(Ljava/lang/Class;)Lnet/minecraft/src/TileEntitySpecialRenderer;",
                renderTile.getClass()) == nativeDelegate,
                "getter hook replaced native renderer registry/cache identity");
        System.setProperty("betamoon.x10.renderer", "true");
    }

    public static boolean afterWorld() throws Exception {
        if (renderer == null || renderToken == null) {
            return false;
        }
        bindings.owned.observe(null, null);
        bindings.interfaces.observe(null, null);
        require(!renderToken.value.describe().get("state").equals("active"),
                "world replacement retained renderer callbacks");
        int before = delegatedDraws;
        call(renderer, RENDER_BASE, "renderTileEntityAt", DRAW, renderTile, 0.0, 0.0, 0.0, 0.0f);
        if (previousRenderer != null) {
            call(previousRenderer, RENDER_BASE, "renderTileEntityAt", DRAW, renderTile, 0.0, 0.0, 0.0, 0.0f);
        }
        require(delegatedDraws == before, "retained closed renderer aliases called Lua or prepared drawing");
        require(((Number) bindings.interfaces.diagnostics().get("classHosts")).intValue() == 0,
                "world cleanup retained class hosts");
        System.setProperty("betamoon.x10.cleanup", "true");
        return true;
    }

    private static Globals ownerGlobals(String owner) throws Exception {
        Method lookup = LuaScriptRegistry.class.getDeclaredMethod("findByFile", String.class);
        lookup.setAccessible(true);
        Object script = lookup.invoke(null, owner);
        require(script != null, "renderer acceptance owner is not loaded");
        Field initializer = script.getClass().getDeclaredField("modInit");
        initializer.setAccessible(true);
        LuaClosure closure = ((LuaValue) initializer.get(script)).checkclosure();
        for (UpValue value : closure.upValues) {
            if (value != null && value.getValue() instanceof Globals) {
                return (Globals) value.getValue();
            }
        }
        throw new AssertionError("acceptance modInit has no Lua environment");
    }

    private static Class<?> runtimeType(String canonical) throws ClassNotFoundException {
        return Class.forName(bindings.names.runtimeClass(canonical, "mapped").replace('/', '.'), false,
                client.getClass().getClassLoader());
    }

    private static Object staticField(Class<?> type, String owner, String name, String descriptor) throws Exception {
        ResolvedField reference = mappings.resolveField(new FieldRef(new ClassRef(owner), name, descriptor), namespace);
        Field field = type.getDeclaredField(reference.getName());
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setField(Object target, String owner, String name, String descriptor, Object value)
            throws Exception {
        ResolvedField reference = mappings.resolveField(new FieldRef(new ClassRef(owner), name, descriptor), namespace);
        Class<?> type = Class.forName(reference.getOwner().replace('/', '.'), false,
                target.getClass().getClassLoader());
        Field field = type.getDeclaredField(reference.getName());
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void openMenu() {
        ScriptExecution.withOwner(OWNER, () -> {
            globals = JsePlatform.standardGlobals();
            NativeAccessApi api = new NativeAccessApi(NativeDiscoveryRuntime::snapshot);
            LuaTable module = new LuaTable();
            LuaTable nativeApi = new LuaTable();
            api.attach(nativeApi);
            module.set("native", nativeApi);
            globals.set("betamoon", module);
            globals.set("Screen", new NativeLuaReference(api, bindings.reference(bindings.names.curatedType(SCREEN))));
            globals.load("Pause=Screen:method('doesGuiPauseGame','()Z'); Init=Screen:method('initGui','()V'); "
                    + "Close=Screen:method('onGuiClosed','()V'); Draw=Screen:method('drawScreen','(IIF)V'); "
                    + "shape=betamoon.native:hostType{base=Screen,constructor=Screen:constructor('()V'),overrides={Pause,Init,Close,Draw}}; "
                    + "pauses=0; initialized=0; closed=0; draws=0").call();
            NativeClientSession session = bindings.clients.create(OWNER, NativeHostLiveFixture::roots);
            NativeClientAction[] action = new NativeClientAction[1];
            session.prepare(() -> {
                NativeContext context = NativeContext.current();
                globals.set("access", new NativeLuaAccess(api, context, false));
                globals.load("host=access:direct():newHost(shape,{arguments={},setup=function(ctx) "
                        + "assert(ctx.native:describe().clientResources and not ctx.native:describe().gameplayWrites); "
                        + "assert(ctx.receiver:describe().bound) end,methods={ "
                        + "{method=Pause,callback=function(ctx) pauses=pauses+1; saved=ctx.native; return false end}, "
                        + "{method=Init,callback=function(ctx) initialized=initialized+1; assert(not ctx.native:describe().gameplayWrites) end}, "
                        + "{method=Close,callback=function() closed=closed+1 end}, "
                        + "{method=Draw,callback=function() draws=draws+1 end}}})").call();
                handle = (NativeLuaHost) globals.get("host");
                screen = handle.value.use(context);
                action[0] = session.action("open-generated-screen", () -> {
                    try {
                        call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", screen);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }, true);
            });
            session.input(permit -> session.submit(action[0], permit));
            return null;
        });
        bindings.clients.observe();
        try {
            require(field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;") == screen,
                    "prepared native open command did not install generated screen");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void closeMenu() {
        ScriptExecution.withOwner(OWNER, () -> {
            NativeClientSession session = bindings.clients.create(OWNER, NativeHostLiveFixture::roots);
            NativeClientAction[] action = new NativeClientAction[1];
            session.prepare(() -> {
                session.resource(() -> handle.value, value -> bindings.owned.invalidate(value, "released"));
                action[0] = session.action("close-generated-screen", () -> {
                    try {
                        if (field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;") == screen) {
                            call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V",
                                    new Object[]{null});
                        }
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }, true);
            });
            session.input(permit -> session.submit(action[0], permit));
            return null;
        });
        bindings.clients.observe();
    }

    private static Map<String, Object> roots() {
        try {
            Map<String, Object> roots = new LinkedHashMap<>();
            roots.put("client", client);
            roots.put("world", field(client, CLIENT, "theWorld", "Lnet/minecraft/src/World;"));
            roots.put("player", field(client, CLIENT, "thePlayer", "Lnet/minecraft/src/EntityPlayerSP;"));
            roots.put("screen", field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;"));
            return roots;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Object field(Object target, String owner, String name, String descriptor) throws Exception {
        ResolvedField reference = mappings.resolveField(new FieldRef(new ClassRef(owner), name, descriptor), namespace);
        Class<?> type = Class.forName(reference.getOwner().replace('/', '.'), false,
                target.getClass().getClassLoader());
        Field field = type.getDeclaredField(reference.getName());
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String owner, String name, String descriptor, Object... arguments)
            throws Exception {
        ResolvedMethod reference = mappings.resolveMethod(new MethodRef(new ClassRef(owner), name, descriptor),
                namespace);
        Class<?> type = Class.forName(reference.getOwner().replace('/', '.'), false,
                target.getClass().getClassLoader());
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(reference.getName())
                    && Type.getMethodDescriptor(method).equals(reference.getDescriptor())) {
                method.setAccessible(true);
                try {
                    return method.invoke(target, arguments);
                } catch (InvocationTargetException failure) {
                    throw new IllegalStateException("native consumer failed: " + owner + "." + name,
                            failure.getCause());
                }
            }
        }
        throw new NoSuchMethodException(owner + "." + name + descriptor);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
