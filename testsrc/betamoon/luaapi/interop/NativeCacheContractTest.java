package betamoon.luaapi.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.binding.NativeOrdinaryMember;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.hosts.NativeHostShape;
import betamoon.interop.hosts.NativeHostType;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Capacity-overflow hot subsets, preparation without initialization and
 * retained expired loader projections.
 */
final class NativeCacheContractTest {
    static void run() throws Exception {
        Retained retained = prepare();
        for (int attempt = 0; attempt < 40 && retained.loader.get() != null; attempt++) {
            System.gc();
            Thread.sleep(25);
        }
        if (retained.loader.get() != null) {
            throw new AssertionError("Expired facades pinned a defining loader");
        }
        for (LuaValue helper : retained.helpers) {
            try {
                helper.call();
                throw new AssertionError("Expired metadata helper revived");
            } catch (LuaError expected) {
            }
        }
        System.out.println(
                "Cache contracts passed: 512/1024 overflow hot sets, host admission, no preparation initialization and expired loader collection.");
    }

    private static Retained prepare() throws Exception {
        ClassLoader loader = new FixtureLoader();
        Class<?> actual = Class.forName("cache.NativeCacheFixture", false, loader);
        NativeLoaderRegistry registry = new NativeLoaderRegistry();
        registry.id(loader);
        registry.id(null);
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), registry);
        NativeAccessApi api = new NativeAccessApi(NativeIndexSnapshot::empty);
        LuaValue[] helpers = new LuaValue[3];
        Globals lua = JsePlatform.standardGlobals();
        ScriptExecution.withOwner("native-cache-contract", () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, "native-cache-contract", scope, true,
                            Collections.emptyMap(), () -> true)) {
                NativeTypeRef type = bindings.reference(actual);
                NativeMemberRef first = field(type, 0);
                NativeMemberPlan hot = bindings.exact(first, context, false);
                NativeOrdinaryMember ordinary = bindings.ordinary(type, "f0", false, context);
                for (int index = 1; index < 521; index++) {
                    if (bindings.exact(first, context, false) != hot) {
                        throw new AssertionError("Hot exact plan evicted");
                    }
                    bindings.exact(field(type, index), context, false);
                    bindings.hasMethod(type, "m0", false, context);
                    bindings.hasMethod(type, "m" + index, false, context);
                    if (bindings.ordinary(type, "f0", false, context) != ordinary) {
                        throw new AssertionError("Hot ordinary classification evicted");
                    }
                    bindings.ordinary(type, "absent" + index, false, context);
                }
                NativeHostType hostType = new NativeHostType(type,
                        new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, type, type, "new", "()V", "runtime",
                                "runtime"),
                        Collections.singletonList(new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, type, "m0",
                                "()I", "runtime", "runtime")));
                NativeHostShape shape = bindings.hostShapes.resolve(hostType, context);
                int admissions = bindings.hostShapes.preparedCount();
                for (String primitive : new String[]{"int", "long", "byte", "short", "char"}) {
                    StringBuilder array = new StringBuilder(primitive);
                    for (int depth = 1; depth <= 255; depth++) {
                        if (bindings.type(type, context) != actual) {
                            throw new AssertionError("Hot type identity changed");
                        }
                        array.append("[]");
                        bindings.type(NativeTypeRef.named(array.toString(), "runtime", null, null), context);
                    }
                }
                bindings.hostShapes.clear();
                if (bindings.hostShapes.preparedCount() != admissions
                        || bindings.hostShapes.resolve(hostType, context) != shape) {
                    throw new AssertionError("Lookup eviction reset lifetime generation admission");
                }
                if (System.getProperty("betamoon.cache.initialized") != null) {
                    throw new AssertionError("Adapter or host preparation initialized target");
                }
                try (LuaCallbackScope query = new LuaCallbackScope(false);
                        NativeContext readOnly = NativeContext.open(bindings, "native-cache-contract", query, false,
                                Collections.emptyMap(), () -> true)) {
                    if (bindings.exact(first, readOnly, false) != hot
                            || bindings.ordinary(type, "f0", false, readOnly) != ordinary
                            || !bindings.hasMethod(type, "m0", false, readOnly)
                            || bindings.type(type, readOnly) != actual) {
                        throw new AssertionError("Overflow discarded a repeatedly used prepared hot subset");
                    }
                }
                NativeLuaView classView = new NativeLuaView(api, context, true, type, actual);
                helpers[0] = classView.get("method").call(LuaValue.valueOf("m0"), LuaValue.valueOf("()I"))
                        .get("describe");
                LuaTable module = new LuaTable();
                api.attach(module);
                lua.set("n", module);
                lua.set("a", NativeAccessApi.contextAccess(context));
                lua.set("name", LuaValue.valueOf(actual.getName()));
                lua.load("local C=n:type(name); local M=C:method('m0','()I'); "
                        + "local H=n:hostType{base=C,constructor=C:constructor('()V'),overrides={M}}; "
                        + "local h=a:direct():newHost(H,{arguments={},methods={{method=M,callback=function(ctx) savedSuper=ctx.callSuper; return 8 end}}}); "
                        + "local o=a:direct():use(h); local f=o:field('f0','I'); savedGet=f.get; "
                        + "assert(o:m0()==8); savedClass=a:direct():bind(C);").call();
                helpers[1] = lua.get("savedSuper");
                helpers[2] = lua.get("savedGet");
                // Keep the views themselves alive too, to catch class metadata retained outside
                // their scopes.
                lua.set("retainedClass", classView);
            }
            return null;
        });
        bindings.close();
        registry.close();
        return new Retained(new WeakReference<>(loader), helpers, lua);
    }

    private static NativeMemberRef field(NativeTypeRef type, int index) {
        return new NativeMemberRef(NativeMemberRef.Kind.FIELD, type, type, "f" + index, "I", "runtime", "runtime");
    }

    private static final class Retained {
        private final WeakReference<ClassLoader> loader;
        private final LuaValue[] helpers;
        @SuppressWarnings("unused")
        private final Globals globals;
        private Retained(WeakReference<ClassLoader> loader, LuaValue[] helpers, Globals globals) {
            this.loader = loader;
            this.helpers = helpers;
            this.globals = globals;
        }
    }

    private static final class FixtureLoader extends ClassLoader {
        private FixtureLoader() {
            super(null);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals("cache.NativeCacheFixture")) {
                throw new ClassNotFoundException(name);
            }
            ClassWriter code = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            code.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "cache/NativeCacheFixture", null, "java/lang/Object", null);
            MethodVisitor constructor = code.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
            MethodVisitor init = code.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            init.visitCode();
            init.visitLdcInsn("betamoon.cache.initialized");
            init.visitLdcInsn("yes");
            init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
            init.visitInsn(Opcodes.POP);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();
            for (int index = 0; index < 521; index++) {
                code.visitField(Opcodes.ACC_PUBLIC, "f" + index, "I", null, null).visitEnd();
                MethodVisitor method = code.visitMethod(Opcodes.ACC_PUBLIC, "m" + index, "()I", null, null);
                method.visitCode();
                method.visitIntInsn(Opcodes.SIPUSH, index);
                method.visitInsn(Opcodes.IRETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
            }
            code.visitEnd();
            byte[] bytes = code.toByteArray();
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
