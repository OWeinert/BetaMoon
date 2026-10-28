package betamoon.interop.live;

import betamoon.BetaMoonClient;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.mapping.ResolvedField;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.NativeAuthoringCatalog;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Type;

/**
 * Real rendered client/world acceptance, using normal construction, events,
 * reload and shutdown. No Unsafe.
 */
public final class NativeLiveClientDriver {
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static TinyMappingResolver mappings;
    private static RuntimeNamespace namespace;
    private static int stage;
    private static int ticks;
    private static int reportedStage = -1;
    private static boolean busy;
    private static Object observedWorld;
    private static List<?> baselineListeners;

    private NativeLiveClientDriver() {
    }

    public static void beforeTick(Object client) {
        if (stage > 0 && stage < 5) {
            try {
                // A test host may lose focus to the editor gate. Keep the disposable world
                // ticking normally.
                setField(client, CLIENT, "isGamePaused", "Z", false);
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot unpause the isolated acceptance client", failure);
            }
        }
    }

    public static void tick(Object client) {
        if (busy || stage == 99) {
            return;
        }
        busy = true;
        try {
            if (mappings == null) {
                mappings = TinyMappingResolver.readStrict(NativeLiveClientDriver.class
                        .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny"));
                namespace = RuntimeNamespace.valueOf(System.getProperty("betamoon.live.namespace", "NAMED"));
            }
            if (NativeDiscoveryRuntime.bindingsIfAvailable() == null || ++ticks < 5) {
                return;
            }
            Object world = field(client, CLIENT, "theWorld", "Lnet/minecraft/src/World;");
            if (reportedStage != stage) {
                System.out.println("[N6-LIVE] stage=" + stage + " ticks=" + ticks);
                reportedStage = stage;
            }
            if (world != null) {
                call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", new Object[]{null});
            }
            if (stage == 0) {
                if (Boolean.getBoolean("betamoon.native.x3") && !NativeX3LiveClientChecks.beforeWorld(client)) {
                    return;
                }
                if (Boolean.getBoolean("betamoon.native.n7") && !NativeN7LiveClientChecks.beforeWorld(client)) {
                    return;
                }
                Class<?> controllerType = Class
                        .forName(mappings.resolveClass(new ClassRef("net/minecraft/src/PlayerControllerSP"), namespace)
                                .replace('/', '.'), false, client.getClass().getClassLoader());
                Class<?> clientType = Class.forName(CLIENT.replace('/', '.'), false,
                        client.getClass().getClassLoader());
                Constructor<?> constructor = controllerType.getConstructor(clientType);
                setField(client, CLIENT, "playerController", "Lnet/minecraft/src/PlayerController;",
                        constructor.newInstance(client));
                call(client, CLIENT, "startWorld", "(Ljava/lang/String;Ljava/lang/String;J)V", "N6-Native-Acceptance",
                        "N6 Native Acceptance", 19860425L);
                call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", new Object[]{null});
                stage = 1;
            } else if (stage == 1 && world != null) {
                observedWorld = world;
                baselineListeners = new ArrayList<>(
                        (List<?>) field(world, "net/minecraft/src/World", "worldAccesses", "Ljava/util/List;"));
                System.setProperty("betamoon.live.ready", "true");
                stage = 2;
            } else if (stage == 2 && "closed".equals(System.getProperty("betamoon.live.lua")) && detached()) {
                verifyPreservedListeners();
                if (Boolean.getBoolean("betamoon.hooks.live")) {
                    betamoon.hooks.live.HookLiveClientChecks.reloads();
                }
                System.clearProperty("betamoon.live.lua");
                BetaMoonClient.getInstance().reloadLuaScripts();
                stage = 3;
            } else if (stage == 3 && "closed".equals(System.getProperty("betamoon.live.lua")) && detached()) {
                verifyPreservedListeners();
                System.setProperty("betamoon.live.mode", "replacement");
                System.clearProperty("betamoon.live.lua");
                BetaMoonClient.getInstance().reloadLuaScripts();
                stage = 4;
            } else if (stage == 4 && "attached".equals(System.getProperty("betamoon.live.lua"))) {
                if (Boolean.getBoolean("betamoon.native.n7") && !NativeN7LiveClientChecks.inWorld(client, world)) {
                    return;
                }
                call(client, CLIENT, "changeWorld1", "(Lnet/minecraft/src/World;)V", new Object[]{null});
                stage = 5;
            } else if (stage == 5 && world == null && detached()
                    && Boolean.TRUE.equals(NativeDiscoveryRuntime.snapshot().coverage.get("ready"))) {
                Path directory = Paths.get(System.getProperty("betamoon.live.result")).getParent();
                NativeAuthoringCatalog.Payload payload;
                try (Writer writer = Files.newBufferedWriter(directory.resolve("native_catalog.jsonl"),
                        StandardCharsets.UTF_8)) {
                    payload = NativeAuthoringCatalog.write(writer, NativeDiscoveryRuntime.snapshot());
                }
                Files.write(directory.resolve("native_catalog.json"),
                        CatalogJson.encode(payload.manifest()).getBytes(StandardCharsets.UTF_8));
                write("passed", null);
                stage = 99;
                call(client, CLIENT, "shutdownMinecraftApplet", "()V");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                write("failed", failure.toString());
                stage = 99;
                call(client, CLIENT, "shutdownMinecraftApplet", "()V");
            } catch (Throwable ignored) {
                System.exit(3);
            }
        } finally {
            busy = false;
        }
    }

    private static boolean detached() {
        return ((Number) NativeDiscoveryRuntime.bindingsIfAvailable().interfaces.diagnostics().get("attachments"))
                .intValue() == 0;
    }

    private static void verifyPreservedListeners() throws Exception {
        List<?> current = (List<?>) field(observedWorld, "net/minecraft/src/World", "worldAccesses",
                "Ljava/util/List;");
        for (Object listener : baselineListeners) {
            boolean found = false;
            for (Object value : current) {
                found |= value == listener;
            }
            if (!found) {
                throw new AssertionError("Native cleanup removed another real world listener");
            }
        }
    }

    static Object field(Object object, String owner, String name, String descriptor) throws Exception {
        ResolvedField target = mappings.resolveField(new FieldRef(new ClassRef(owner), name, descriptor), namespace);
        Class<?> type = Class.forName(target.getOwner().replace('/', '.'), false, object.getClass().getClassLoader());
        Field field = type.getDeclaredField(target.getName());
        field.setAccessible(true);
        return field.get(object);
    }

    private static void setField(Object object, String owner, String name, String descriptor, Object value)
            throws Exception {
        ResolvedField target = mappings.resolveField(new FieldRef(new ClassRef(owner), name, descriptor), namespace);
        Class<?> type = Class.forName(target.getOwner().replace('/', '.'), false, object.getClass().getClassLoader());
        Field field = type.getDeclaredField(target.getName());
        field.setAccessible(true);
        field.set(object, value);
    }

    static Object call(Object object, String owner, String name, String descriptor, Object... arguments)
            throws Exception {
        ResolvedMethod target = mappings.resolveMethod(new MethodRef(new ClassRef(owner), name, descriptor), namespace);
        Class<?> type = Class.forName(target.getOwner().replace('/', '.'), false, object.getClass().getClassLoader());
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(target.getName())
                    && Type.getMethodDescriptor(method).equals(target.getDescriptor())) {
                method.setAccessible(true);
                return method.invoke(object, arguments);
            }
        }
        throw new NoSuchMethodException(target.toString());
    }

    private static void write(String status, String error) throws Exception {
        String result = CatalogJson.encode(CatalogValues.record("status", status, "namespace",
                namespace.getMappingName(), "ticks", ticks, "stage", stage, "error", error, "renderedClient", true,
                "worldCreatedNormally", true, "observerReloadAndWorldCleanup", status.equals("passed"), "x3Acceptance",
                "true".equals(System.getProperty("betamoon.x3.passed")), "x5Acceptance",
                "true".equals(System.getProperty("betamoon.x5.passed")), "x8Acceptance",
                "true".equals(System.getProperty("betamoon.x8.passed")), "n7Acceptance",
                Boolean.getBoolean("betamoon.native.n7") && "passed".equals(System.getProperty("betamoon.n7.phase"))));
        Files.write(Paths.get(System.getProperty("betamoon.live.result")), result.getBytes(StandardCharsets.UTF_8));
        System.out.println("[N6-LIVE] " + result);
    }
}
