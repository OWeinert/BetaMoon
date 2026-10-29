package betamoon.interop.hosts;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.attachments.NativeAttachment;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.interfaces.NativeImplementation;
import betamoon.interop.interfaces.NativeInterfaces;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.function.BooleanSupplier;
import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Failure, attachment, loader and bounded-cache contracts independent of
 * Minecraft's class loader.
 */
public final class NativeHostEdgeTest {
    private static int checks;

    static void run(NativeBindings bindings, NativeContext context) {
        BooleanSupplier readiness = LuaScriptRegistry.callbackOwnerReadiness("readiness-edge.lua");
        require(readiness.getAsBoolean(), "unregistered Java owner is ready");
        LuaScriptRegistry.registerFile("readiness-edge.lua");
        require(!readiness.getAsBoolean(), "registration invalidates cached unregistered readiness");
        LuaScriptRegistry.markLoadedByFile("readiness-edge.lua");
        require(readiness.getAsBoolean(), "loaded owner activates its existing readiness cell");
        LuaScriptRegistry.markFailedByFile("readiness-edge.lua", "fixture");
        require(!readiness.getAsBoolean(), "failure invalidates readiness immediately");
        NativeHostShape shape = bindings.hostShapes.resolve(type(bindings, ConsumerBase.class, "value", "()I"),
                context);
        AtomicInteger calls = new AtomicInteger();
        Map<String, NativeInterfaces.MethodCallback> callback = Collections.singletonMap("value()I",
                new NativeInterfaces.MethodCallback((method, arguments, scope) -> {
                    calls.incrementAndGet();
                    return 9;
                }, "query"));
        ConsumerBase[] escaped = new ConsumerBase[1];
        expect("setup_failed", () -> bindings.interfaces.createClass(context, shape, callback, Collections.emptyMap(),
                new Object[0], "owner", null, (token, instance) -> {
                    escaped[0] = (ConsumerBase) instance;
                    require(escaped[0].value() == 2 && calls.get() == 0, "setup cannot enter Lua before activation");
                    throw new NativeAccessFailure("setup_failed", "fixture");
                }));
        require(escaped[0].value() == 2 && calls.get() == 0, "escaped failed setup alias stays closed");
        NativeImplementation first = bindings.interfaces.createClass(context, shape, callback, Collections.emptyMap(),
                new Object[0], "owner", null);
        NativeImplementation second = bindings.interfaces.createClass(context, shape, callback, Collections.emptyMap(),
                new Object[0], "owner", null);
        ConsumerBase firstAlias = (ConsumerBase) first.value.use(context);
        ConsumerBase secondAlias = (ConsumerBase) second.value.use(context);
        Consumer consumer = new Consumer();
        NativeTypeRef consumerType = bindings.reference(Consumer.class);
        String descriptor = "(" + Type.getDescriptor(ConsumerBase.class) + ")V";
        NativeAttachment attachment = bindings.interfaces.attach(context, first, consumer,
                bindings.exact(member(consumerType, "add", descriptor), context, true),
                bindings.exact(member(consumerType, "remove", descriptor), context, true), "owner", false);
        consumer.add(secondAlias);
        require(firstAlias.value() == 9 && secondAlias.value() == 9,
                "class arguments support native attachment dispatch");
        bindings.interfaces.requestClose(first, context);
        require(firstAlias.value() == 2 && consumer.values.size() == 2 && attachment.state().equals("pending_removal"),
                "detach is deferred while callbacks are disabled immediately");
        bindings.interfaces.observe(null, null);
        require(consumer.values.size() == 2, "native collection is not mutated inside an active context");
        pendingConsumer = consumer;
        pendingSurvivor = secondAlias;
        pendingAttachment = attachment;
        bindings.owned.invalidate(second.value, "released");
        Consumer broken = new Consumer();
        broken.failAdd = true;
        NativeImplementation partial = bindings.interfaces.createClass(context, shape, callback, Collections.emptyMap(),
                new Object[0], "owner", null);
        ConsumerBase partialAlias = (ConsumerBase) partial.value.use(context);
        expect("native_exception",
                () -> bindings.interfaces.attach(context, partial, broken,
                        bindings.exact(member(consumerType, "add", descriptor), context, true),
                        bindings.exact(member(consumerType, "remove", descriptor), context, true), "owner", false));
        require(partialAlias.value() == 2 && broken.values.size() == 1,
                "partial install disables before deferred removal");
        pendingBroken = broken;
        NativeHostShape objectShape = bindings.hostShapes
                .resolve(type(bindings, ConsumerBase.class, "toString", "()Ljava/lang/String;"), context);
        NativeImplementation objectHost = bindings.interfaces.createClass(context, objectShape,
                Collections.singletonMap("toString()Ljava/lang/String;",
                        new NativeInterfaces.MethodCallback((method, arguments, scope) -> "scripted object", "query")),
                Collections.emptyMap(), new Object[0], "owner", null);
        require(objectHost.value.use(context).toString().equals("scripted object"),
                "explicit Object override enters class callback");
        bindings.owned.invalidate(objectHost.value, "released");
        ConsumerBase[] recursive = new ConsumerBase[1];
        NativeImplementation reentry = bindings.interfaces.createClass(context, shape,
                Collections.singletonMap("value()I", new NativeInterfaces.MethodCallback(
                        (method, arguments, scope) -> recursive[0].value(), "query")),
                Collections.emptyMap(), new Object[0], "owner", null);
        recursive[0] = (ConsumerBase) reentry.value.use(context);
        expect("native_host_reentry", recursive[0]::value);
        require(recursive[0].value() == 2, "reentry failure clears callbacks before subsequent native fallback");
        NativeImplementation threaded = bindings.interfaces.createClass(context, shape, callback,
                Collections.emptyMap(), new Object[0], "owner", null);
        ConsumerBase threadedAlias = (ConsumerBase) threaded.value.use(context);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            try {
                threadedAlias.value();
            } catch (Throwable caught) {
                failure.set(caught);
            }
        });
        foreign.start();
        try {
            foreign.join();
        } catch (InterruptedException interrupted) {
            throw new AssertionError(interrupted);
        }
        require(failure.get() instanceof NativeAccessFailure
                && ((NativeAccessFailure) failure.get()).reason.equals("wrong_thread"),
                "foreign thread callback denied");
        require(threadedAlias.value() == 2, "foreign thread failure closes only that host");
        ConsumerBase[] nested = new ConsumerBase[17];
        List<NativeImplementation> nestedTokens = new ArrayList<>();
        for (int index = 0; index < nested.length; index++) {
            int slot = index;
            NativeImplementation token = bindings.interfaces.createClass(context, shape,
                    Collections.singletonMap("value()I",
                            new NativeInterfaces.MethodCallback(
                                    (method, arguments, scope) -> slot == 16 ? 10 : nested[slot + 1].value(), "query")),
                    Collections.emptyMap(), new Object[0], "owner", null);
            nestedTokens.add(token);
            nested[index] = (ConsumerBase) token.value.use(context);
        }
        expect("native_depth_limit", nested[0]::value);
        for (NativeImplementation token : nestedTokens) {
            bindings.owned.invalidate(token.value, "released");
        }
        NativeImplementation independent = ScriptExecution.withOwner("other-host-owner", () -> {
            try (LuaCallbackScope otherScope = new LuaCallbackScope(true);
                    NativeContext other = NativeContext.open(bindings, "other-host-owner", otherScope, true,
                            Collections.emptyMap(), () -> true)) {
                return bindings.interfaces.createClass(other, shape, callback, Collections.emptyMap(), new Object[0],
                        "owner", null);
            }
        });
        expect("wrong_owner", () -> independent.value.use(context));
        ScriptExecution.withOwner("other-host-owner", () -> {
            try (LuaCallbackScope otherScope = new LuaCallbackScope(true);
                    NativeContext other = NativeContext.open(bindings, "other-host-owner", otherScope, true,
                            Collections.emptyMap(), () -> true)) {
                require(((ConsumerBase) independent.value.use(other)).value() == 9,
                        "independent owner keeps its exact class callbacks after another instance fails");
                bindings.owned.invalidate(independent.value, "released");
            }
            return null;
        });
        NativeHostDispatch delayed = new NativeHostDispatch(shape, new String[]{"super"},
                (instance, method, arguments) -> 99, () -> false);
        ConsumerBase delayedAlias = (ConsumerBase) shape.construct(delayed, new Object[0]);
        delayed.activate();
        require(delayedAlias.value() == 2, "owner initialization gate uses native fallback");
        delayed.close();
        expect("native_implementation_closed", delayed::activate);
        NativeHostShape signatures = bindings.hostShapes.resolve(
                type(bindings, Signatures.class, "mix", "(JD[Ljava/lang/String;Ljava/lang/Object;)D"), context);
        Signatures mixed = (Signatures) signatures.construct(input -> {
            Object[] arguments = (Object[]) input[2];
            require(arguments[0].equals(7L) && arguments[1].equals(2.5D) && arguments[2] instanceof String[]
                    && arguments[3] == null, "wide primitive/array/null parameters retain exact slots");
            return new Object[]{false, 12.5D};
        }, new Object[0]);
        require(mixed.mix(7, 2.5, new String[]{"x"}, null) == 12.5, "wide typed callback result verified");
        require(signatures.callSuper(0, mixed, new Object[]{7L, 2.5D, new String[]{"x"}, null}).equals(9.5D),
                "wide native super preserves arguments");
        NativeHostShape interfaceCollision = bindings.hostShapes
                .resolve(type(bindings, InterfaceCollision.class, "value", "()I"), context);
        InterfaceCollision interfaceAlias = (InterfaceCollision) interfaceCollision
                .construct(input -> new Object[]{true, null}, new Object[0]);
        require(interfaceAlias.betamoon$host$super$0() == -1 && interfaceAlias.value() == 6,
                "synthetic helper does not override unrelated inherited interface default");
        NativeHostShape collision = bindings.hostShapes.resolve(type(bindings, Collision.class, "value", "()I"),
                context);
        Object collisionAlias = collision.construct(input -> new Object[]{true, null}, new Object[0]);
        require(collision.callSuper(0, collisionAlias, new Object[0]).equals(6),
                "generated super helper avoids native name collision");
        expect("missing_native_host_method",
                () -> bindings.hostShapes.resolve(type(bindings, Opaque.class, "value", "()I"), context));
        NativeHostShape exposed = bindings.hostShapes.resolve(type(bindings, Exposed.class, "hidden", "()I"), context);
        require(((Exposed) exposed.construct(input -> new Object[]{false, 4}, new Object[0])).hidden() == 4,
                "publicly exposed runtime-package abstract slot can be implemented");
        NativeHostShape throwing = bindings.hostShapes
                .resolve(type(bindings, ThrowingConstructor.class, "value", "()I"), context);
        expect("native_host_construction_failed", () -> bindings.interfaces.createClass(context, throwing, callback,
                Collections.emptyMap(), new Object[0], "owner", null));
        require(ThrowingConstructor.escaped.value() == 3, "constructor-escaped failed instance never activates Lua");
        ThrowingConstructor.escaped = null;
        NativeHostShape disposal = bindings.hostShapes.resolve(type(bindings, FailingDisposer.class, "value", "()I"),
                context);
        NativeImplementation disposable = bindings.interfaces.createClass(context, disposal, callback,
                Collections.emptyMap(), new Object[0], "owner",
                bindings.exact(member(bindings.reference(FailingDisposer.class), "dispose", "()V"), context, true));
        bindings.owned.invalidate(disposable.value, "released");
        require(FailingDisposer.disposals == 0, "native disposal waits until outside callback/context");
        Object world = new Object();
        Object player = new Object();
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("world", world);
        roots.put("player", player);
        try (LuaCallbackScope worldScope = new LuaCallbackScope(true);
                NativeContext worldContext = NativeContext.open(bindings, context.owner(), worldScope, true, roots,
                        () -> true)) {
            NativeImplementation worldHost = bindings.interfaces.createClass(worldContext, shape, callback,
                    Collections.emptyMap(), new Object[0], "world", null);
            ConsumerBase worldAlias = (ConsumerBase) worldHost.value.use(worldContext);
            require(worldAlias.value() == 9, "world-host callback uses its admitted root generation");
            bindings.owned.observe(world, new Object());
            require(worldAlias.value() == 2, "player replacement invalidates before retained foreign alias enters Lua");
        }
        cacheLimit(bindings, context);
        System.out.println("Native host edge contracts passed: " + checks + " checks.");
    }

    private static Consumer pendingConsumer;
    private static ConsumerBase pendingSurvivor;
    private static NativeAttachment pendingAttachment;
    private static Consumer pendingBroken;

    static void verifyDeferred(NativeBindings bindings) {
        bindings.interfaces.observe(null, null);
        bindings.owned.observe(null, null);
        require(pendingConsumer.values.size() == 1 && pendingConsumer.values.get(0) == pendingSurvivor
                && pendingAttachment.state().equals("closed"),
                "identity-specific removal preserves another native entry");
        require(pendingBroken.values.isEmpty(), "partial add receives deferred identity-specific removal");
        require(((Number) bindings.interfaces.diagnostics().get("classHosts")).intValue() == 0,
                "failed and closed hosts leave zero callback registrations");
        require(FailingDisposer.disposals == 1, "throwing disposer runs once and does not resurrect callbacks");
        pendingConsumer = null;
        pendingSurvivor = null;
        pendingAttachment = null;
        pendingBroken = null;
    }

    private static void cacheLimit(NativeBindings bindings, NativeContext context) {
        for (int bits = 1; bits <= 64; bits++) {
            bindings.hostShapes.resolve(combination(bindings, bits), context);
        }
        expect("native_host_shape_limit", () -> bindings.hostShapes.resolve(combination(bindings, 65), context));
        require(bindings.hostShapes.resolve(combination(bindings, 1), context) != null,
                "generation limit still permits reuse of existing shapes");
    }

    private static NativeHostType combination(NativeBindings bindings, int bits) {
        List<String> methods = new ArrayList<>();
        for (int bit = 0; bit < 7; bit++) {
            if ((bits & 1 << bit) != 0) {
                methods.add("m" + bit);
                methods.add("()I");
            }
        }
        return type(bindings, Many.class, methods.toArray(new String[0]));
    }

    static void loaderCollection() throws Exception {
        WeakReference<?>[] references = isolatedLoaders();
        for (int attempt = 0; attempt < 40 && references[0].get() != null; attempt++) {
            System.gc();
            Thread.sleep(25);
        }
        require(references[0].get() == null && references[1].get() == null,
                "ClassValue shapes do not globally pin closed defining/generated loaders");
    }

    private static WeakReference<?>[] isolatedLoaders() throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        DefiningLoader first = new DefiningLoader();
        DefiningLoader second = new DefiningLoader();
        Class<?> firstBase = first.define();
        Class<?> secondBase = second.define();
        WeakReference<?>[] result = ScriptExecution.withOwner("host-loader-test", () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, "host-loader-test", scope, true,
                            Collections.emptyMap(), () -> true)) {
                NativeHostShape a = bindings.hostShapes.resolve(type(bindings, firstBase, "value", "()I"), context);
                NativeHostShape b = bindings.hostShapes.resolve(type(bindings, secondBase, "value", "()I"), context);
                require(a.generated != b.generated && a.base != b.base && a.generated.getSuperclass() == firstBase
                        && b.generated.getSuperclass() == secondBase,
                        "same binary name preserves two defining-loader identities");
                return new WeakReference<?>[]{new WeakReference<>(first),
                        new WeakReference<>(a.generated.getClassLoader())};
            }
        });
        bindings.close();
        loaders.close();
        return result;
    }

    private static NativeHostType type(NativeBindings bindings, Class<?> base, String... selected) {
        NativeTypeRef reference = bindings.reference(base);
        List<NativeMemberRef> overrides = new ArrayList<>();
        for (int index = 0; index < selected.length; index += 2) {
            overrides.add(member(reference, selected[index], selected[index + 1]));
        }
        return new NativeHostType(reference, new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, reference, null,
                "new", "()V", "runtime", "runtime"), overrides);
    }

    private static NativeMemberRef member(NativeTypeRef owner, String name, String descriptor) {
        return new NativeMemberRef(NativeMemberRef.Kind.METHOD, owner, null, name, descriptor, "runtime", "runtime");
    }

    private static void expect(String reason, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected " + reason);
        } catch (NativeAccessFailure failure) {
            require(failure.reason.equals(reason), "expected " + reason + " but got " + failure.reason);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static class ConsumerBase {
        public int value() {
            return 2;
        }
    }

    public static class Consumer {
        final List<ConsumerBase> values = new ArrayList<>();
        boolean failAdd;
        public void add(ConsumerBase value) {
            values.add(value);
            if (failAdd) {
                throw new IllegalStateException("partial install");
            }
        }

        public void remove(ConsumerBase value) {
            values.removeIf(candidate -> candidate == value);
        }
    }

    public static class ThrowingConstructor {
        static ThrowingConstructor escaped;
        public ThrowingConstructor() {
            escaped = this;
            throw new IllegalStateException("escaped");
        }

        public int value() {
            return 3;
        }
    }

    public static class FailingDisposer extends ConsumerBase {
        static int disposals;
        public void dispose() {
            disposals++;
            throw new IllegalStateException("dispose");
        }
    }

    public static class Signatures {
        public double mix(long first, double second, String[] names, Object optional) {
            return first + second;
        }
    }

    public interface HelperDefault {
        default int betamoon$host$super$0() {
            return -1;
        }
    }

    public static class InterfaceCollision implements HelperDefault {
        public int value() {
            return 6;
        }
    }

    public static class Collision {
        public int value() {
            return 6;
        }

        public final int betamoon$host$super$0() {
            return -1;
        }
    }

    public abstract static class Opaque {
        abstract int hidden();

        public int value() {
            return 1;
        }
    }

    public abstract static class Exposed extends Opaque {
        @Override
        public abstract int hidden();
    }

    public static class Many {
        public int m0() {
            return 0;
        }

        public int m1() {
            return 1;
        }

        public int m2() {
            return 2;
        }

        public int m3() {
            return 3;
        }

        public int m4() {
            return 4;
        }

        public int m5() {
            return 5;
        }

        public int m6() {
            return 6;
        }
    }

    private static final class DefiningLoader extends ClassLoader {
        DefiningLoader() {
            super(NativeHostEdgeTest.class.getClassLoader());
        }

        Class<?> define() {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/HostLoaderBase", null, "java/lang/Object", null);
            GeneratorAdapter ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, new Method("<init>", "()V"), null, null,
                    writer);
            ctor.loadThis();
            ctor.invokeConstructor(Type.getType(Object.class), new Method("<init>", "()V"));
            ctor.returnValue();
            ctor.endMethod();
            GeneratorAdapter value = new GeneratorAdapter(Opcodes.ACC_PUBLIC, new Method("value", "()I"), null, null,
                    writer);
            value.push(1);
            value.returnValue();
            value.endMethod();
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            return defineClass("fixture.HostLoaderBase", bytes, 0, bytes.length);
        }
    }
}
