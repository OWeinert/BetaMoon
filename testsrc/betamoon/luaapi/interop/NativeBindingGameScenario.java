package betamoon.luaapi.interop;

import betamoon.event.Events;
import betamoon.event.api.EventChannel;
import betamoon.event.context.EventContext;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.NativeDiscoveryTest;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.event.EventsApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Runs inside the artifact/game loader. Generated third-party types use the
 * actual runtime descriptors.
 */
public final class NativeBindingGameScenario {
    private static final String OWNER = "native_game_fixture.lua";

    private NativeBindingGameScenario() {
    }

    public static void run(String namespace, Path work) throws Exception {
        ClassLoader gameLoader = NativeBindingGameScenario.class.getClassLoader();
        TinyMappingResolver mappings;
        try (InputStream input = gameLoader.getResourceAsStream("betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            mappings = TinyMappingResolver.readStrict(input);
        }
        NativeNames symbolic = new NativeNames(mappings.snapshot(), namespace);
        Class<?> worldType = Class.forName(symbolic.runtimeClass("net/minecraft/src/World", "mapped").replace('/', '.'),
                false, gameLoader);
        Class<?> clientType = Class.forName(
                symbolic.runtimeClass("net/minecraft/client/Minecraft", "mapped").replace('/', '.'), false, gameLoader);
        Class<?> stackType = Class.forName(
                symbolic.runtimeClass("net/minecraft/src/ItemStack", "mapped").replace('/', '.'), false, gameLoader);
        Map<String, Class<?>> curated = new LinkedHashMap<>();
        curated.put("net/minecraft/src/World", worldType);
        curated.put("net/minecraft/src/ItemStack", stackType);
        curated.put("net/minecraft/src/IWorldAccess", Class.forName(
                symbolic.runtimeClass("net/minecraft/src/IWorldAccess", "mapped").replace('/', '.'), false, gameLoader));
        NativeNames names = new NativeNames(mappings.snapshot(), namespace, curated);
        if (namespace.equals("client")) {
            try {
                gameLoader.loadClass("net.minecraft.src.World");
                throw new AssertionError("Packaged fixture inherited named World");
            } catch (ClassNotFoundException expected) {
            }
        }
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.capture(Collections.singletonList(worldType), Collections.emptyList(), Collections.emptyList(), false,
                null, false);
        NativeBindings provisional = new NativeBindings(names, loaders);
        String runtimeMethod = ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(provisional, OWNER, callback, true,
                            Collections.emptyMap(), () -> true)) {
                NativeMemberRef method = new NativeMemberRef(NativeMemberRef.Kind.METHOD,
                        NativeTypeRef.named("net.minecraft.src.World", "mapped", null, null), null, "getBlockId",
                        "(III)I", null, null);
                return provisional.exact(method, context, false).member.getName();
            }
        });
        Path mod = work.resolve("native-fixture-mod.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(mod))) {
            write(output, "fixture/nativeaccess/WorldMod.class",
                    worldMod(Type.getInternalName(worldType), runtimeMethod));
            write(output, "fixture/nativeaccess/ModApi.class", modApi(Type.getInternalName(worldType), runtimeMethod));
            write(output, "fixture/nativeaccess/ClientMod.class", clientMod(clientType));
        }
        try (URLClassLoader mods = new URLClassLoader(new URL[]{mod.toUri().toURL()}, gameLoader)) {
            Class<?> worldMod = Class.forName("fixture.nativeaccess.WorldMod", false, mods);
            Class<?> apiMod = Class.forName("fixture.nativeaccess.ModApi", false, mods);
            NativeLoaderRegistry.Capture capture = loaders.capture(Collections.singletonList(worldType),
                    Arrays.asList(worldMod, apiMod), Arrays.asList(worldType, worldMod, apiMod), false, null, false);
            try (NativeDiscoveryService discovery = new NativeDiscoveryService(work.resolve("cache"))) {
                NativeDiscoveryTest.await(discovery, discovery.request(capture, namespace, Collections.emptyList()));
                NativeBindings bindings = new NativeBindings(names, loaders, discovery::snapshot);
                Object world = allocate(worldMod);
                Map<String, Object> roots = new LinkedHashMap<>();
                roots.put("world", world);
                Globals lua = JsePlatform.standardGlobals();
                LuaTable module = new LuaTable();
                NativeDiscoveryApi.attach(module, OWNER, new NativeDiscoveryApi.Backend() {
                    public NativeIndexSnapshot snapshot() {
                        return discovery.snapshot();
                    }

                    public Map<String, Object> status() {
                        return discovery.status();
                    }

                    public NativeDiscoveryService.Ticket refresh() {
                        return discovery.request(capture, namespace, Collections.emptyList());
                    }

                    public void requireClient() {
                        bindings.requireThread();
                    }
                });
                EventsApi.attach(module);
                lua.set("betamoon", module);
                ScriptExecution.withOwner(OWNER, () -> {
                    try (LuaCallbackScope callback = new LuaCallbackScope(true);
                            NativeContext context = NativeContext.open(bindings, OWNER, callback, true, roots,
                                    () -> true)) {
                        lua.set("access", NativeAccessApi.contextAccess(context));
                        lua.load("local n=betamoon.native; local a=access:direct(); "
                                + "local world=a:world(); local World=n:type('net.minecraft.src.World'); "
                                + "assert(world:getBlockId(10,64,10)==84); "
                                + "assert(a:call(World,'getBlockId',world,10,64,10)==84); "
                                + "local Get=World:method('getBlockId','(III)I'); "
                                + "assert(assert(a:bind(Get)):call(world,10,64,10)==84); "
                                + "local ok,err=pcall(function() Get:call(world,10,64,10) end); "
                                + "assert(not ok and tostring(err):find('direct_required')); "
                                + "local api=assert(a:bind('fixture.nativeaccess.ModApi',{names='runtime'})); "
                                + "local inspect=api:method('inspect','(Lnet/minecraft/src/World;III)I'); "
                                + "assert(inspect:call(nil,world,1,2,3)==6); "
                                + "local info=Get:describe(); assert(info.returnType.name=='int'); "
                                + "local own=n:typeOf(world):method('getBlockId','(III)I'):describe(); "
                                + "assert(own.declaringTypeRef.name=='fixture.nativeaccess.WorldMod'); "
                                + "assert(assert(a:bind(info)):call(world,2,3,4)==9); "
                                + "local stack=assert(a:bind('net.minecraft.src.ItemStack')):constructor('(III)V'):new(1,5,2); "
                                + "local copy=stack:copy(); assert(copy.stackSize==5 and not copy:sameObject(stack)); "
                                + "copy.stackSize=2; assert(stack.stackSize==5); "
                                + "ownedStack=access:copy(stack); local owned=access:use(ownedStack); "
                                + "owned.stackSize=8; assert(stack.stackSize==5); "
                                + "ownedCells=a:copy(a:array('int',{4,5}),{lifetime='world'}); "
                                + "assert(not pcall(function() a:retain(world,{lifetime='owner'}) end)); "
                                + "local field=stack:field('itemDamage','I'); assert(field:get()==2); "
                                + "field:set(3); assert(field:get()==3);", "native game binding").call();
                    }
                    try (LuaCallbackScope callback = new LuaCallbackScope(true);
                            NativeContext context = NativeContext.open(bindings, OWNER, callback, true, roots,
                                    () -> true)) {
                        lua.set("access", NativeAccessApi.contextAccess(context));
                        lua.load("assert(access:use(ownedStack).stackSize==8); "
                                + "assert(access:use(ownedCells)[1]==5); access:direct():release(ownedStack); "
                                + "assert(ownedStack:describe().state=='released')", "owned game copies").call();
                    }
                    bindings.owned.observe(null, null);
                    lua.load("assert(ownedCells:describe().state=='stale_world')", "world copy expiry").call();
                    return null;
                });
                verifyEventEntry(lua, bindings, names, worldType, world);
                verifyCuratedIdentity(lua, bindings, names, loaders, stackType);
                NativeWorldObserverScenario.verify(lua, bindings, worldType, world);
            }
        } finally {
            loaders.close();
        }
    }

    private static void verifyCuratedIdentity(Globals lua, NativeBindings bindings, NativeNames names,
            NativeLoaderRegistry loaders, Class<?> stackType) throws Exception {
        Method copy = Arrays
                .stream(stackType.getDeclaredMethods()).filter(method -> method.getParameterTypes().length == 0
                        && method.getReturnType() == stackType && names.methodMatches(method, "copy", "mapped"))
                .findFirst().get();
        String internal = Type.getInternalName(stackType);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, copy.getName(), "()L" + internal + ";", null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        ClassLoader duplicate = new ClassLoader(stackType.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(stackType.getName())) {
                    Class<?> loaded = findLoadedClass(name);
                    return loaded == null ? defineClass(name, bytes, 0, bytes.length) : loaded;
                }
                return super.loadClass(name, resolve);
            }
        };
        Class<?> fake = Class.forName(stackType.getName(), false, duplicate);
        Object object = allocate(fake);
        loaders.capture(Collections.singletonList(stackType), Collections.singletonList(fake),
                Collections.singletonList(fake), false, null, false);
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, callback, true,
                            Collections.singletonMap("subject", object), () -> true)) {
                NativeTypeRef reference = bindings.reference(fake);
                NativeMemberPlan selected = bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.METHOD, reference,
                        reference, copy.getName(), "()L" + internal + ";", "runtime", "runtime"), context, true);
                if (selected.effect != NativeContext.Effect.UNKNOWN) {
                    throw new AssertionError("A duplicate Minecraft-named class acquired curated effects");
                }
                lua.set("duplicateAccess", NativeAccessApi.contextAccess(context));
                lua.load(
                        "local a=duplicateAccess; local ok,e=pcall(function() a:copy(a:subject()) end); "
                                + "assert(not ok and tostring(e):find('unsupported_native_copy'))",
                        "duplicate copy refusal").call();
            }
            return null;
        });
    }

    private static void verifyEventEntry(Globals lua, NativeBindings bindings, NativeNames names, Class<?> worldType,
            Object world) throws Exception {
        ClassLoader loader = NativeBindingGameScenario.class.getClassLoader();
        Class<?> clientType = Class.forName(
                names.runtimeClass("net/minecraft/client/Minecraft", "mapped").replace('/', '.'), false, loader);
        Class<?> clientMod = Class.forName("fixture.nativeaccess.ClientMod", false, world.getClass().getClassLoader());
        Object client = allocate(clientMod);
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, callback, true, Collections.emptyMap(),
                            () -> true)) {
                NativeMemberPlan field = bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.FIELD,
                        NativeTypeRef.named("net.minecraft.client.Minecraft", "mapped", null, null), null, "theWorld",
                        "Lnet/minecraft/src/World;", null, null), context, true);
                field.set(context, true, client, world);
            }
            return null;
        });
        Field installed = NativeDiscoveryRuntime.class.getDeclaredField("bindings");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        try {
            installed.set(null, bindings);
            verifyEnvironment(lua, bindings, names, worldType, world, clientType, client);
            ScriptExecution.invoke(OWNER, "register event",
                    lua.load("betamoon.events:on('world_join',function(ctx) "
                            + "assert(ctx.native:direct():world():getBlockId(1,2,3)==6); eventSeen=true; "
                            + "retainedEventWorld=ctx.native:world(); end)", "native event fixture"),
                    LuaValue.NONE);
            Class<?> contextType = loader.loadClass("betamoon.event.context.WorldEventCtx");
            EventContext context = (EventContext) contextType.getConstructor(clientType, worldType).newInstance(client,
                    world);
            publish(Events.WORLD_JOIN, context);
            if (!lua.get("eventSeen").toboolean()) {
                throw new AssertionError("Existing event boundary did not supply native access");
            }
            lua.load("local ok,e=pcall(function() return retainedEventWorld:describe() end); "
                    + "assert(not ok and tostring(e):find('expired_scope'))", "event expiry").call();
        } finally {
            installed.set(null, previous);
            ScriptResourceTracker.unload(OWNER);
        }
    }

    private static void verifyEnvironment(Globals lua, NativeBindings bindings, NativeNames names, Class<?> worldType,
            Object world, Class<?> clientType, Object client) throws Exception {
        ClassLoader loader = worldType.getClassLoader();
        Class<?> playerType = Class.forName(
                names.runtimeClass("net/minecraft/src/EntityPlayerSP", "mapped").replace('/', '.'), false, loader);
        Class<?> screenType = Class
                .forName(names.runtimeClass("net/minecraft/src/GuiScreen", "mapped").replace('/', '.'), false, loader);
        Object player = allocate(playerType);
        Object replacement = allocate(playerType);
        Object replacementScreen = allocate(screenType);
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, callback, true, Collections.emptyMap(),
                            () -> true)) {
                NativeTypeRef entity = NativeTypeRef.named("net.minecraft.src.Entity", "mapped", null, null);
                Field entityWorld = (Field) bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.FIELD, entity, null,
                        "worldObj", "Lnet/minecraft/src/World;", null, null), context, true).member;
                entityWorld.set(player, world);
                entityWorld.set(replacement, world);
                NativeTypeRef minecraft = NativeTypeRef.named("net.minecraft.client.Minecraft", "mapped", null, null);
                Field clientPlayer = (Field) bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.FIELD, minecraft,
                        null, "thePlayer", "Lnet/minecraft/src/EntityPlayerSP;", null, null), context, true).member;
                Field clientScreen = (Field) bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.FIELD, minecraft,
                        null, "currentScreen", "Lnet/minecraft/src/GuiScreen;", null, null), context, true).member;
                clientPlayer.set(client, player);
                Method entry = NativeEventAccess.class.getMethod("entry", LuaCallbackScope.class, clientType, worldType,
                        Object.class);
                try (LuaCallbackScope event = new LuaCallbackScope(true);
                        NativeContext scope = ((NativeEntry) entry.invoke(null, event, client, world, player))
                                .open(OWNER)) {
                    lua.set("changingAccess", NativeAccessApi.contextAccess(scope));
                    lua.load(
                            "assert(changingAccess:player()); " + "assert(changingAccess:describe().phase=='gameplay')",
                            "current player root").call();
                    clientPlayer.set(client, replacement);
                    lua.load("local ok,e=pcall(function() changingAccess:world() end); "
                            + "assert(not ok and tostring(e):find('stale_environment'))", "player replacement").call();
                }
                try (LuaCallbackScope event = new LuaCallbackScope(false);
                        NativeContext scope = ((NativeEntry) entry.invoke(null, event, client, world, replacement))
                                .open(OWNER)) {
                    lua.set("changingAccess", NativeAccessApi.contextAccess(scope));
                    lua.load("assert(changingAccess:describe().phase=='query')", "query phase").call();
                    clientScreen.set(client, replacementScreen);
                    lua.load("local ok,e=pcall(function() changingAccess:client() end); "
                            + "assert(not ok and tostring(e):find('stale_environment'))", "screen replacement").call();
                }
                clientScreen.set(client, null);
                clientPlayer.set(client, null);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
            return null;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void publish(EventChannel channel, EventContext context) {
        channel.publish(context);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
    }

    private static byte[] worldMod(String parent, String blockMethod) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/nativeaccess/WorldMod", null, parent, null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, blockMethod, "(III)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.IADD);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(2, 4);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] modApi(String world, String blockMethod) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/nativeaccess/ModApi", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "inspect",
                "(L" + world + ";III)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, world, blockMethod, "(III)I", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(4, 4);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] clientMod(Class<?> parent) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/nativeaccess/ClientMod", null,
                Type.getInternalName(parent), null);
        for (Method declared : parent.getMethods()) {
            if (!Modifier.isAbstract(declared.getModifiers())) {
                continue;
            }
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, declared.getName(),
                    Type.getMethodDescriptor(declared), null, null);
            method.visitCode();
            method.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError");
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
            method.visitInsn(Opcodes.ATHROW);
            int locals = 1;
            for (Type argument : Type.getArgumentTypes(declared)) {
                locals += argument.getSize();
            }
            method.visitMaxs(2, locals);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void write(ZipOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
}
