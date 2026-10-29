package betamoon.interop.live;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rendered C2 gate. The disposable native screen exists only in the test
 * loader, never in the mod.
 */
public final class NativeX3LiveClientChecks {
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static final String SCREEN = "net/minecraft/src/GuiScreen";
    private static final String SELF = "betamoon/interop/live/NativeX3LiveClientChecks";
    private static TinyMappingResolver mappings;
    private static RuntimeNamespace namespace;
    private static Object client;
    private static Object screen;
    private static Object host;
    private static Object session;
    private static Object image;
    private static Object heldContext;
    private static Class<?> screenType;
    private static String owner;
    private static int texture;
    private static int draws;
    private static int phase;
    private static int mode;

    private NativeX3LiveClientChecks() {
    }

    static boolean beforeWorld(Object currentClient) throws Exception {
        if (phase == 5) {
            return true;
        }
        if (mappings == null) {
            mappings = TinyMappingResolver.readStrict(NativeX3LiveClientChecks.class
                    .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny"));
            namespace = RuntimeNamespace.valueOf(System.getProperty("betamoon.live.namespace", "NAMED"));
            client = currentClient;
            screenType = screenClass(client.getClass().getClassLoader());
        }
        require(NativeLiveClientDriver.field(client, CLIENT, "theWorld", "Lnet/minecraft/src/World;") == null,
                "C2 must execute before a world exists");
        if (phase == 0) {
            show(0, "native-client-live-input");
            phase = 1;
            return false;
        }
        if (phase == 1) {
            if (draws == 0) {
                return false;
            }
            require(GL11.glIsTexture(texture), "prepared image was not allocated");
            NativeLiveClientDriver.call(screen, SCREEN, "mouseClicked", "(III)V", 10, 10, 0);
            phase = 2;
            return false;
        }
        if (phase == 2) {
            managerObserve();
            if (NativeLiveClientDriver.field(client, CLIENT, "currentScreen",
                    "Lnet/minecraft/src/GuiScreen;") == screen) {
                return false;
            }
            require(!GL11.glIsTexture(texture), "close did not delete the owned texture");
            show(1, "native-client-live-reload");
            phase = 3;
            return false;
        }
        if (phase == 3) {
            if (draws == 0) {
                return false;
            }
            Class<?> tracker = Class.forName("betamoon.luamodloader.ScriptResourceTracker", false,
                    host.getClass().getClassLoader());
            tracker.getMethod("unload", String.class).invoke(null, owner);
            require(Boolean.FALSE.equals(invoke(session, "active")), "owner unload retained the active screen session");
            NativeLiveClientDriver.call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V",
                    new Object[]{null});
            managerObserve();
            require(!GL11.glIsTexture(texture), "reload did not delete the owned texture");
            show(2, "native-client-live-initialization-failure");
            NativeLiveClientDriver.call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V",
                    new Object[]{null});
            managerObserve();
            require(Boolean.FALSE.equals(invoke(session, "active")), "failed initialization left its host active");
            phase = 4;
            return false;
        }
        managerObserve();
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "C2 resource/display lifecycle produced a GL error");
        System.setProperty("betamoon.x3.passed", "true");
        System.out.println(
                "[X3-LIVE] no-world font/image/item preparation and drawing, authorized close, reload and failed-init cleanup passed");
        phase = 5;
        return true;
    }

    private static void show(int selectedMode, String selectedOwner) throws Exception {
        mode = selectedMode;
        owner = selectedOwner;
        draws = 0;
        screen = screenType.newInstance();
        NativeLiveClientDriver.call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", screen);
    }

    public static void initialize(Object target) {
        try {
            require(target == screen, "unexpected native screen initialized");
            Class<?> type = Class.forName("betamoon.gui.interop.NativeClientScreenHost", true,
                    client.getClass().getClassLoader());
            host = type.getConstructors()[0].newInstance(client, target, owner);
            invoke(host, "initialize");
            session = invoke(host, "session");
            Runnable preparation = () -> {
                try {
                    Object context = currentContext();
                    heldContext = context;
                    Map<?, ?> policy = (Map<?, ?>) invoke(context, "describe");
                    require(Boolean.TRUE.equals(policy.get("bindingPreparation")),
                            "client preparation capability missing");
                    require(Boolean.TRUE.equals(policy.get("clientResources")), "client resource capability missing");
                    require(Boolean.FALSE.equals(policy.get("gameplayWrites")),
                            "client preparation gained gameplay writes");
                    require(Boolean.FALSE.equals(policy.get("directUnknownCalls")),
                            "client preparation gained unknown calls");
                    require(Boolean.FALSE.equals(policy.get("directInitialization")),
                            "client preparation gained arbitrary initialization");
                    if (mode == 2) {
                        invoke(host, "prepareImage", new Class<?>[]{byte[].class, boolean.class}, new byte[8], true);
                    } else {
                        image = invoke(host, "prepareImage", new Class<?>[]{byte[].class, boolean.class}, png(), true);
                        Object prepared = invoke(image, "use");
                        texture = ((Number) invoke(prepared, "textureId")).intValue();
                        require(((Number) invoke(host, "textWidth", new Class<?>[]{String.class}, "Native client"))
                                .intValue() > 0, "prepared native font metrics unavailable");
                    }
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            };
            try {
                invoke(session, "prepare", new Class<?>[]{Runnable.class}, preparation);
                require(mode != 2, "invalid image initialization succeeded");
            } catch (RuntimeException failure) {
                if (mode != 2) {
                    throw failure;
                }
                require(Boolean.FALSE.equals(invoke(session, "active")),
                        "failed resource initializer retained its host");
            }
        } catch (Exception failure) {
            throw new IllegalStateException("C2 native screen initialization failed", failure);
        }
    }

    public static void draw(Object target, int mouseX, int mouseY, float partialTicks) {
        if (mode == 2 || !Boolean.TRUE.equals(invoke(session, "active"))) {
            return;
        }
        require(target == screen, "unexpected native screen drew");
        invoke(host, "paint", new Class<?>[]{Runnable.class}, (Runnable) () -> {
            Object context = currentContext();
            Map<?, ?> policy = (Map<?, ?>) invoke(context, "describe");
            require(Boolean.FALSE.equals(policy.get("bindingPreparation")), "paint acquired first-time preparation");
            require(Boolean.FALSE.equals(policy.get("clientActions")), "paint acquired a client action");
            expect("expired_scope", () -> invoke(heldContext, "prepare"));
            invoke(host, "drawText", new Class<?>[]{String.class, int.class, int.class, int.class}, "Native client", 8,
                    8, 0xFFFFFF);
            Method drawImage = method(host.getClass(), "drawImage", 5);
            call(drawImage, host, image, 8, 28, 24, 24);
            try {
                Class<?> stackType = Class.forName(
                        mappings.resolveClass(new ClassRef("net/minecraft/src/ItemStack"), namespace).replace('/', '.'),
                        false, client.getClass().getClassLoader());
                Object stack = stackType.getConstructor(int.class, int.class, int.class).newInstance(1, 1, 0);
                call(method(host.getClass(), "drawItem", 3), host, stack, 48, 8);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        });
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "prepared resource drawing produced a GL error");
        draws++;
    }

    public static void click(Object target, int x, int y, int button) {
        require(target == screen, "unexpected native screen input");
        Consumer<Object> input = permit -> {
            Map<?, ?> policy = (Map<?, ?>) invoke(currentContext(), "describe");
            require(Boolean.TRUE.equals(policy.get("clientActions")), "authorized input lacks its client capability");
            require(Boolean.FALSE.equals(policy.get("gameplayWrites")), "client input gained gameplay writes");
            Object action = invoke(host, "closeAction");
            call(method(session.getClass(), "submit", 2), session, action, permit);
        };
        invoke(host, "input", new Class<?>[]{Consumer.class}, input);
    }

    public static void closed(Object target) {
        if (target == screen && host != null) {
            invoke(host, "close");
        }
    }

    private static Object currentContext() {
        try {
            Class<?> type = Class.forName("betamoon.interop.runtime.NativeContext", false,
                    host.getClass().getClassLoader());
            return type.getMethod("current").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void managerObserve() throws Exception {
        Class<?> runtime = Class.forName("betamoon.interop.discovery.NativeDiscoveryRuntime", false,
                host.getClass().getClassLoader());
        Object bindings = runtime.getMethod("bindingsIfAvailable").invoke(null);
        Object manager = bindings.getClass().getField("clients").get(bindings);
        invoke(manager, "observe");
        Map<?, ?> state = (Map<?, ?>) invoke(manager, "diagnostics");
        if (!Boolean.TRUE.equals(invoke(session, "active"))) {
            require(((Number) state.get("resources")).intValue() == 0, "closed screen retained a resource");
        }
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                image.setRGB(x, y, 0xFF44FF44);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static Class<?> screenClass(ClassLoader parent) throws Exception {
        String name = "fixture/client/ClientBoundaryScreen";
        String base = mappings.resolveClass(new ClassRef(SCREEN), namespace);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, base, null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, base, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        forward(writer, "initGui", "()V", "initialize", "(Ljava/lang/Object;)V");
        forward(writer, "drawScreen", "(IIF)V", "draw", "(Ljava/lang/Object;IIF)V");
        forward(writer, "mouseClicked", "(III)V", "click", "(Ljava/lang/Object;III)V");
        forward(writer, "onGuiClosed", "()V", "closed", "(Ljava/lang/Object;)V");
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        class ScreenLoader extends ClassLoader {
            private ScreenLoader() {
                super(parent);
            }

            @Override
            protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException {
                return requested.equals(NativeX3LiveClientChecks.class.getName())
                        ? NativeX3LiveClientChecks.class
                        : super.loadClass(requested, resolve);
            }

            private Class<?> define() {
                return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
            }
        }
        return new ScreenLoader().define();
    }

    private static void forward(ClassWriter writer, String name, String descriptor, String callback, String target) {
        ResolvedMethod mapped = mappings.resolveMethod(new MethodRef(new ClassRef(SCREEN), name, descriptor),
                namespace);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, mapped.getName(), mapped.getDescriptor(), null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        if (!descriptor.equals("()V")) {
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.ILOAD, 2);
            method.visitVarInsn(descriptor.equals("(IIF)V") ? Opcodes.FLOAD : Opcodes.ILOAD, 3);
        }
        method.visitMethodInsn(Opcodes.INVOKESTATIC, SELF, callback, target, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static Method method(Class<?> type, String name, int parameters) {
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterTypes().length == parameters) {
                return candidate;
            }
        }
        throw new AssertionError("Missing C2 adapter method " + name);
    }

    private static Object invoke(Object receiver, String name) {
        return invoke(receiver, name, new Class<?>[0]);
    }

    private static Object invoke(Object receiver, String name, Class<?>[] types, Object... arguments) {
        try {
            return call(receiver.getClass().getMethod(name, types), receiver, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Object call(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void expect(String reason, Runnable callback) {
        try {
            callback.run();
            throw new AssertionError("Expected C2 refusal " + reason);
        } catch (RuntimeException failure) {
            try {
                require(reason.equals(failure.getClass().getField("reason").get(failure)), "wrong C2 refusal");
            } catch (ReflectiveOperationException unexpected) {
                throw new AssertionError(unexpected);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
