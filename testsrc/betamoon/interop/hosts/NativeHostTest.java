package betamoon.interop.hosts;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeAccessFailure;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.interfaces.NativeInterfaces;
import betamoon.interop.interfaces.NativeImplementation;
import java.util.LinkedHashMap;
import java.util.Map;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.interop.NativeHostApiTest;
import betamoon.luamodloader.ScriptExecution;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.Type;

/** Actual Java 8 generated class verification and native virtual dispatch. */
public final class NativeHostTest {
    private static final String OWNER = "native-host-test";
    private static int checks;

    public static void main(String[] args) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(NativeHostTest.class.getClassLoader());
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, Collections.emptyMap(),
                            () -> true)) {
                shapes(bindings, context);
                lifecycle(bindings, context);
                NativeHostEdgeTest.run(bindings, context);
            }
            return null;
        });
        NativeHostEdgeTest.verifyDeferred(bindings);
        NativeHostApiTest.run(bindings);
        bindings.close();
        loaders.close();
        NativeHostEdgeTest.loaderCollection();
        System.out.println("Native class host contracts passed: " + checks + " checks.");
    }

    private static NativeHostType type(NativeBindings bindings, Class<?> base, String constructor, String... selected) {
        NativeTypeRef reference = bindings.reference(base);
        List<NativeMemberRef> overrides = new ArrayList<>();
        for (int index = 0; index < selected.length; index += 2) {
            overrides.add(new NativeMemberRef(NativeMemberRef.Kind.METHOD, reference, null, selected[index],
                    selected[index + 1], "runtime", "runtime"));
        }
        return new NativeHostType(reference, new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, reference, null,
                "new", constructor, "runtime", "runtime"), overrides);
    }

    private static void shapes(NativeBindings bindings, NativeContext context) {
        NativeHostShapes compiler = new NativeHostShapes(bindings);
        NativeHostType definition = type(bindings, Base.class, "(I)V", "answer", "()I", "decorate",
                "(Ljava/lang/String;)Ljava/lang/String;");
        NativeHostShape shape = compiler.resolve(definition, context);
        AtomicInteger callbacks = new AtomicInteger();
        Base instance = (Base) shape.construct(payload -> {
            callbacks.incrementAndGet();
            int index = (Integer) payload[0];
            return new Object[]{false, shape.methods.get(index).getName().equals("answer") ? 42 : "script"};
        }, new Object[]{3});
        require(instance.constructionAnswer == 4 && callbacks.get() == 0,
                "constructor virtual call runs concrete native super without Lua");
        require(instance.answer() == 42 && instance.readDecorated("x").equals("script") && callbacks.get() == 2,
                "public/protected overrides use real native virtual dispatch");
        int answer = shape.methods.get(0).getName().equals("answer") ? 0 : 1;
        require(shape.callSuper(answer, instance, new Object[0]).equals(4) && callbacks.get() == 2,
                "exact super helper bypasses scripted override once");
        require(compiler.resolve(definition, context) == shape
                && new NativeHostShapes(bindings).resolve(definition, context) == shape,
                "callback/session preparation reuses the actual generated class shape");
        expect("invalid_native_host_instance", () -> shape.requireInstance(new Base(1)));
        NativeHostShape covariant = compiler
                .resolve(type(bindings, Covariant.class, "()V", "value", "()Ljava/lang/String;"), context);
        Object narrowed = covariant.construct(payload -> new Object[]{false, "covariant"}, new Object[0]);
        require(((Wide) narrowed).value().equals("covariant") && ((Narrow) narrowed).value().equals("covariant"),
                "covariant abstract bridge fulfills both native descriptors");
        NativeHostShape defaults = compiler.resolve(type(bindings, DefaultBase.class, "()V", "defaultValue", "()I"),
                context);
        DefaultBase defaultInstance = (DefaultBase) defaults.construct(payload -> new Object[]{true, null},
                new Object[0]);
        require(defaultInstance.defaultValue() == 8, "native super dispatch resolves inherited interface default");
        NativeHostShape abstractConstruction = compiler
                .resolve(type(bindings, EarlyAbstract.class, "()V", "required", "()I"), context);
        try {
            abstractConstruction.construct(payload -> {
                callbacks.incrementAndGet();
                return new Object[]{false, 1};
            }, new Object[0]);
            throw new AssertionError("abstract constructor callback fabricated a value");
        } catch (NativeAccessFailure expected) {
            require(expected.reason.equals("unsupported_native_host_construction") && callbacks.get() == 2,
                    "abstract callback during construction fails before entering Lua");
        }
        for (String[] invalid : new String[][]{{"fixed", "()I"}, {"hidden", "()I"}, {"global", "()I"},
                {"locked", "()I"}, {"nativeValue", "()I"},
                {"privateResult", "()Lbetamoon/interop/hosts/NativeHostTest$PrivateType;"}}) {
            expect("invalid_native_host_type",
                    () -> compiler.resolve(type(bindings, Base.class, "(I)V", invalid), context));
        }
        expect("invalid_native_host_type",
                () -> compiler.resolve(type(bindings, FinalBase.class, "()V", "value", "()I"), context));
        expect("invalid_native_host_type",
                () -> compiler.resolve(type(bindings, PrivateConstructor.class, "()V", "value", "()I"), context));
        expect("missing_native_host_method",
                () -> compiler.resolve(type(bindings, Missing.class, "()V", "available", "()I"), context));
    }

    private static void lifecycle(NativeBindings bindings, NativeContext context) {
        NativeHostShape shape = bindings.hostShapes.resolve(type(bindings, Base.class, "(I)V", "answer", "()I"),
                context);
        AtomicInteger calls = new AtomicInteger();
        Map<String, NativeInterfaces.MethodCallback> callbacks = new LinkedHashMap<>();
        callbacks.put("answer()I", new NativeInterfaces.MethodCallback((method, arguments, scope) -> {
            calls.incrementAndGet();
            require(Boolean.FALSE.equals(NativeContext.current().describe().get("gameplayWrites")),
                    "host query callback inherits read-only authority");
            return 42;
        }, "query"));
        NativeImplementation token = bindings.interfaces.createClass(context, shape, callbacks, Collections.emptyMap(),
                new Object[]{3}, "owner", null);
        Base alias = (Base) token.value.use(context);
        require(alias.answer() == 42 && calls.get() == 1,
                "generated class activates through shared owned implementation registry");
        bindings.owned.invalidate(token.value, "released");
        require(alias.answer() == 4 && calls.get() == 1,
                "closed retained alias uses declared native super without new Lua callbacks");
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        int checksum = 0;
        for (int repeat = 0; repeat < 10000; repeat++) {
            checksum += alias.answer();
        }
        long thread = Thread.currentThread().getId();
        long bytes = memory.getThreadAllocatedBytes(thread);
        for (int repeat = 0; repeat < 100000; repeat++) {
            checksum += alias.answer();
        }
        bytes = memory.getThreadAllocatedBytes(thread) - bytes;
        require(checksum == 440000 && bytes == 0, "closed super fallback allocated transport buffers: " + bytes);
        bindings.interfaces.observe(null, null);
        require(((Number) bindings.interfaces.diagnostics().get("implementations")).intValue() == 0,
                "class host registry retires disabled implementation");
        Map<String, NativeInterfaces.MethodCallback> invalid = new LinkedHashMap<>();
        invalid.put("answer()I",
                new NativeInterfaces.MethodCallback((method, arguments, scope) -> "invalid int", "query"));
        NativeImplementation broken = bindings.interfaces.createClass(context, shape, invalid, Collections.emptyMap(),
                new Object[]{5}, "owner", null);
        Base brokenAlias = (Base) broken.value.use(context);
        expect("conversion_failed", brokenAlias::answer);
        require(brokenAlias.answer() == 6, "invalid typed callback disables its dispatch before closed fallback");
        NativeHostShape abstractShape = bindings.hostShapes
                .resolve(type(bindings, Covariant.class, "()V", "value", "()Ljava/lang/String;"), context);
        Map<String, NativeInterfaces.MethodCallback> value = new LinkedHashMap<>();
        value.put("value()Ljava/lang/String;",
                new NativeInterfaces.MethodCallback((method, arguments, scope) -> "value", "query"));
        NativeImplementation abstractToken = bindings.interfaces.createClass(context, abstractShape, value,
                Collections.emptyMap(), new Object[0], "owner", null);
        Wide abstractAlias = (Wide) abstractToken.value.use(context);
        require(abstractAlias.value().equals("value"), "abstract class callbacks are owned and typed");
        bindings.owned.invalidate(abstractToken.value, "released");
        expect("native_implementation_closed", abstractAlias::value);
        NativeHostShape voidShape = bindings.hostShapes.resolve(type(bindings, VoidBase.class, "()V", "changed", "()V"),
                context);
        Map<String, NativeInterfaces.MethodCallback> observe = new LinkedHashMap<>();
        NativeImplementation[] closing = new NativeImplementation[1];
        AtomicInteger observations = new AtomicInteger();
        observe.put("changed()V", new NativeInterfaces.MethodCallback((method, arguments, scope) -> {
            observations.incrementAndGet();
            bindings.owned.invalidate(closing[0].value, "released");
            return null;
        }, "query"));
        closing[0] = bindings.interfaces.createClass(context, voidShape, observe,
                Collections.singletonMap("changed()V", "noop"), new Object[0], "owner", null);
        VoidBase closingAlias = (VoidBase) closing[0].value.use(context);
        closingAlias.changed();
        closingAlias.changed();
        require(observations.get() == 1,
                "closing during dispatch expires callbacks immediately and declared void noop remains safe");
        bindings.interfaces.observe(null, null);
        require(((Number) bindings.interfaces.diagnostics().get("implementations")).intValue() == 0,
                "class failure and close leave no callback entries");
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

    public static class Base {
        public final int input;
        public final int constructionAnswer;

        protected Base(int input) {
            this.input = input;
            constructionAnswer = answer();
        }

        public int answer() {
            return input + 1;
        }

        protected String decorate(String text) {
            return "base:" + text;
        }

        public String readDecorated(String text) {
            return decorate(text);
        }

        public final int fixed() {
            return 1;
        }

        private int hidden() {
            return 1;
        }

        public static int global() {
            return 1;
        }

        public synchronized int locked() {
            return 1;
        }

        public native int nativeValue();

        public PrivateType privateResult() {
            return null;
        }
    }

    public abstract static class VoidBase {
        public abstract void changed();
    }
    private static class PrivateType {
    }
    public static final class FinalBase {
        public int value() {
            return 1;
        }
    }
    public static class PrivateConstructor {
        private PrivateConstructor() {
        }

        public int value() {
            return 1;
        }
    }
    public static abstract class Missing {
        public int available() {
            return 1;
        }

        public abstract int missing();
    }
    public interface Wide {
        Object value();
    }
    public interface Narrow {
        String value();
    }
    public abstract static class Covariant implements Wide, Narrow {
    }
    public interface Default {
        default int defaultValue() {
            return 8;
        }
    }
    public static class DefaultBase implements Default {
    }
    public static abstract class EarlyAbstract {
        public EarlyAbstract() {
            required();
        }

        public abstract int required();
    }
}
