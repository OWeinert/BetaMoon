package betamoon.luaapi.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.attachments.NativeAttachmentDisposal;
import betamoon.interop.interfaces.NativeInterfaceContract;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.fixture.NativeInterfaceFixture;
import betamoon.interop.fixture.NativeInterfaceFixture.Values;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Public API round-trips into a real unrelated synchronous native consumer. */
public final class NativeInterfaceApiTest {
    private static final String OWNER = "native_interface_fixture.lua";

    public static void main(String[] arguments) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.capture(Collections.singletonList(NativeInterfaceFixture.class), Collections.emptyList(),
                Collections.emptyList(), false, null, false);
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        bindings.interfaces.entryFactory(scope -> new NativeEntry(bindings, scope, NativeEntry.Phase.INTERFACE, false,
                Collections.emptyMap(), () -> true));
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        new NativeAccessApi(NativeIndexSnapshot::empty).attach(api);
        lua.set("n", api);
        try {
            verifyContracts(lua, bindings);
            verifyAttachments(lua, bindings);
            verifyCallbackPolicies(lua, bindings);
            verifyOwnersAndRemoval(lua, bindings);
            verifyVisibility(lua, bindings);
            verifyFailures(lua, bindings);
            verifySessionClose(lua, bindings);
        } finally {
            if (!bindings.diagnostics().get("session").equals("closed")) {
                bindings.close();
            }
            ScriptResourceTracker.unload(OWNER);
            loaders.close();
        }
        System.out.println(
                "Native interfaces passed: exact contracts, returns, callback policy and reusable attachments.");
    }

    private static void verifyContracts(Globals lua, NativeBindings bindings) throws Exception {
        invoke(lua, bindings, true, "local a=(...).native:direct(); "
                + "V=n:type('betamoon.interop.fixture.NativeInterfaceFixture$Values'); "
                + "A=n:type('betamoon.interop.fixture.NativeInterfaceFixture$Alias'); "
                + "M=n:type('betamoon.interop.fixture.NativeInterfaceFixture$Marker'); " + "methods={"
                + "{method=V:method('measure','(I)I'),callback=function(ctx,x) savedContext=ctx; return x+2 end},"
                + "{method=V:method('measure','(J)J'),callback=function(ctx,x) return x end},"
                + "{method=V:method('echo','(Ljava/lang/Object;)Ljava/lang/Object;'),callback=function(ctx,x) return x end},"
                + "{method=V:method('offset','(I)I'),callback=function(ctx,x) return x+10 end}}; "
                + "impl=a:implement{interfaces={V,A,M},methods=methods}; "
                + "assert(impl:describe().policy=='query' and #impl.interfaces==3); "
                + "local value=a:use(impl); assert(value:measure(4)==6); "
                + "assert(value:measure(n:long('9223372036854775807')):decimal()=='9223372036854775807'); "
                + "assert(value:echo(n.NULL)==n.NULL); assert(value:offset(2)==12); "
                + "assert(not pcall(function() a:implement{interfaces={V},methods={}} end)); "
                + "local B=n:type('betamoon.interop.fixture.NativeInterfaceFixture$Incompatible'); "
                + "assert(not pcall(function() a:implement{interfaces={V,B},methods=methods} end)); "
                + "local partial={methods[1],methods[2],methods[3]}; "
                + "assert(not pcall(function() a:implement{interfaces={V},methods=partial} end)); "
                + "marker=a:implement{interfaces={M},methods={}}; a:release(marker)");
        Values proxy = (Values) borrow(lua, bindings, "impl");
        require(proxy.measure(5) == 7 && proxy.offset(3) == 13, "Standalone client callback failed");
        require(proxy.echo(proxy) == proxy, "Reference return lost actual native identity");
        require(proxy.equals(proxy) && !proxy.equals(new Object()) && proxy.hashCode() == System.identityHashCode(proxy)
                && proxy.toString().startsWith("BetaMoon native implementation@"), "Object methods entered Lua");
        invoke(lua, bindings, false,
                "local a=(...).native; " + "assert(not pcall(function() savedContext.native:world() end)); "
                        + "assert(not pcall(function() a:direct():implement{interfaces={V},methods=methods} end)); "
                        + "assert(not pcall(function() a:direct():release(impl) end))");
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                proxy.measure(1);
                workerFailure.set(new AssertionError("Worker entered Lua"));
            } catch (NativeAccessFailure expected) {
                if (!expected.reason.equals("wrong_thread")) {
                    workerFailure.set(expected);
                }
            } catch (Throwable failure) {
                workerFailure.set(failure);
            }
        }, "native interface worker fixture");
        worker.start();
        worker.join(5000);
        require(!worker.isAlive() && workerFailure.get() == null, "Worker callback failed: " + workerFailure.get());
        expectFailure(() -> proxy.measure(1), "native_implementation_closed");
        requireCallbacksCleared(proxy);
        // Stable identity methods continue working after Lua dispatch is cleared.
        require(proxy.equals(proxy) && proxy.toString().contains("implementation"), "Closed proxy lost identity");
        bindings.interfaces.observe(null, null);
    }

    private static void verifyAttachments(Globals lua, NativeBindings bindings) {
        NativeInterfaceFixture.Observer unrelated = value -> {
        };
        NativeInterfaceFixture.HOST.add(unrelated);
        invoke(lua, bindings, true, "local a=(...).native:direct(); "
                + "O=n:type('betamoon.interop.fixture.NativeInterfaceFixture$Observer'); "
                + "C=n:type('betamoon.interop.fixture.NativeInterfaceFixture$Consumer'); "
                + "local f=assert(a:bind('betamoon.interop.fixture.NativeInterfaceFixture')); " + "host=f.HOST; "
                + "local p=a:implement{interfaces={O},methods={{method=O:method('changed','(I)V'),"
                + "callback=function(ctx,x) seen=(seen or 0)+x; savedObserver=ctx end}}}; "
                + "attachment=a:attach{implementation=p,receiver=host,add=C:method('add',"
                + "'(Lbetamoon/interop/fixture/NativeInterfaceFixture$Observer;)V'),"
                + "remove=C:method('remove','(Lbetamoon/interop/fixture/NativeInterfaceFixture$Observer;)V'),"
                + "lifetime='owner'}; assert(attachment:describe().state=='active'); host:emit(3)");
        require(lua.get("seen").toint() == 3 && NativeInterfaceFixture.HOST.size() == 2,
                "Generic attachment did not participate");
        NativeInterfaceFixture.HOST.emit(4);
        require(lua.get("seen").toint() == 7, "Foreign native client callback did not reach Lua");
        invoke(lua, bindings, false, "local a=(...).native; attachment:close(); attachment:close(); "
                + "assert(not pcall(function() a:use(attachment.implementation) end))");
        require(NativeInterfaceFixture.HOST.size() == 2, "Close mutated a possibly iterated native listener list");
        bindings.interfaces.observe(null, null);
        require(NativeInterfaceFixture.HOST.size() == 1, "Close removed another native listener");
        NativeInterfaceFixture.HOST.remove(unrelated);
    }

    private static void verifyFailures(Globals lua, NativeBindings bindings) {
        invoke(lua, bindings, true,
                "local a=(...).native:direct(); "
                        + "bad=a:implement{interfaces={A},methods={{method=A:method('measure','(I)I'),"
                        + "callback=function() return 'bad' end}}}");
        NativeInterfaceFixture.Alias bad = (NativeInterfaceFixture.Alias) borrow(lua, bindings, "bad");
        expectFailure(() -> bad.measure(1), "conversion_failed");
        invoke(lua, bindings, true,
                "local a=(...).native:direct(); "
                        + "badVoid=a:implement{interfaces={O},methods={{method=O:method('changed','(I)V'),"
                        + "callback=function() return 1 end}}}");
        NativeInterfaceFixture.Observer badVoid = (NativeInterfaceFixture.Observer) borrow(lua, bindings, "badVoid");
        expectFailure(() -> badVoid.changed(1), "invalid_callback_return");
        invoke(lua, bindings, true, "local a=(...).native:direct(); "
                + "local p=a:implement{interfaces={O},methods={{method=O:method('changed','(I)V'),callback=function() end}}}; "
                + "local h=assert(a:bind('betamoon.interop.fixture.NativeInterfaceFixture')).HOST; "
                + "local ok,e=pcall(function() a:attach{implementation=p,receiver=h,"
                + "add=C:method('addFail','(Lbetamoon/interop/fixture/NativeInterfaceFixture$Observer;)V'),"
                + "remove=C:method('remove','(Lbetamoon/interop/fixture/NativeInterfaceFixture$Observer;)V'),lifetime='owner'} end); "
                + "assert(not ok and tostring(e):find('native_exception')); assert(p:describe().ownership.state~='active')");
        require(NativeInterfaceFixture.HOST.size() == 1, "Partial-install fixture did not install before failing");
        bindings.interfaces.observe(null, null);
        require(NativeInterfaceFixture.HOST.size() == 0, "Partial-install cleanup did not remove the exact identity");
        invoke(lua, bindings, true, "local a=(...).native:direct(); "
                + "reload=a:implement{interfaces={A},methods={{method=A:method('measure','(I)I'),callback=function(ctx,x) return x end}}}");
        NativeInterfaceFixture.Alias reload = (NativeInterfaceFixture.Alias) borrow(lua, bindings, "reload");
        ScriptResourceTracker.unload(OWNER);
        expectFailure(() -> reload.measure(2), "native_implementation_closed");
        bindings.interfaces.observe(null, null);
        require(((Number) bindings.interfaces.diagnostics().get("implementations")).intValue() == 0,
                "Owner unload retained an implementation");
    }

    private static void verifyCallbackPolicies(Globals lua, NativeBindings bindings) {
        invoke(lua, bindings, true, "local a=(...).native:direct(); "
                + "gp=a:implement{interfaces={A},policy='gameplay',methods={{method=A:method('measure','(I)I'),"
                + "callback=function(ctx,x) return ctx.native:describe().gameplayWrites and 1 or 0 end}}}; "
                + "meta=a:implement{interfaces={A},methods={{method=A:method('measure','(I)I'),"
                + "callback=function(ctx,x) metadataBodyRan=true; return x end}}}; "
                + "recursive=a:implement{interfaces={A},methods={{method=A:method('measure','(I)I'),"
                + "callback=function(ctx,x) return recurseNative() end}}}; "
                + "methods[1].policy='query'; methods[1].callback=function(ctx,x) return ctx.native:describe().gameplayWrites and 1 or 0 end; "
                + "methods[4].policy='gameplay'; methods[4].callback=methods[1].callback; "
                + "mixed=a:implement{interfaces={V},policy='gameplay',methods=methods}");
        NativeInterfaceFixture.Alias gameplay = (NativeInterfaceFixture.Alias) borrow(lua, bindings, "gp");
        NativeInterfaceFixture.Alias metadata = (NativeInterfaceFixture.Alias) borrow(lua, bindings, "meta");
        NativeInterfaceFixture.Alias recursive = (NativeInterfaceFixture.Alias) borrow(lua, bindings, "recursive");
        Values mixed = (Values) borrow(lua, bindings, "mixed");
        lua.set("callGameplay", bridge(gameplay));
        lua.set("callMetadata", bridge(metadata));
        lua.set("recurseNative", bridge(recursive));
        lua.set("mixedPolicies", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.varargsOf(
                        new LuaValue[]{LuaValue.valueOf(mixed.measure(1)), LuaValue.valueOf(mixed.offset(1))});
            }
        });
        invoke(lua, bindings, true, "assert(callGameplay()==1)");
        invoke(lua, bindings, false, "assert(callGameplay()==0)");
        invoke(lua, bindings, true, "local q,g=mixedPolicies(); assert(q==0 and g==1)");
        invoke(lua, bindings, false, "local q,g=mixedPolicies(); assert(q==0 and g==0)");
        require(mixed.measure(1) == 0, "Method query override inherited the default gameplay entry requirement");
        expectFailure(() -> mixed.offset(1), "native_context_unavailable");
        expectFailure(() -> gameplay.measure(1), "native_context_unavailable");
        ScriptExecution.invoke(OWNER, "metadata fixture",
                lua.load("assert(not pcall(callMetadata)); assert(not metadataBodyRan)", "metadata interface barrier"),
                LuaValue.NONE);
        try {
            recursive.measure(1);
            throw new AssertionError("Unbounded recursive native callback");
        } catch (NativeAccessFailure failure) {
            boolean depth = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                depth |= cause.getMessage() != null && cause.getMessage().contains("native_depth_limit");
            }
            require(depth, "Recursion did not produce the explicit native depth refusal");
        }
        requireCallbacksCleared(recursive);
        lua.set("callGameplay", LuaValue.NIL);
        lua.set("callMetadata", LuaValue.NIL);
        lua.set("recurseNative", LuaValue.NIL);
        lua.set("mixedPolicies", LuaValue.NIL);
        bindings.interfaces.observe(null, null);
    }

    private static LuaValue bridge(NativeInterfaceFixture.Alias proxy) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                try {
                    return LuaValue.valueOf(proxy.measure(0));
                } catch (NativeAccessFailure failure) {
                    throw NativeLuaSupport.error(failure);
                }
            }
        };
    }

    private static void verifyOwnersAndRemoval(Globals lua, NativeBindings bindings) throws Exception {
        String otherOwner = "native_other_interface_fixture.lua";
        invoke(lua, bindings, true, attachment("ownedFirst", "add", "removeFail", "function() end"));
        invoke(lua, bindings, otherOwner, true, attachment("ownedSecond", "add", "remove", "function() end"));
        invoke(lua, bindings, otherOwner, false,
                "local ok,e=pcall(function() ownedFirst:close() end); assert(not ok and tostring(e):find('wrong_owner'))");
        require(NativeInterfaceFixture.HOST.size() == 2, "Two owners did not register independent identities");
        Thread worker = new Thread(() -> ScriptResourceTracker.unload(OWNER), "native interface owner unload fixture");
        worker.start();
        worker.join(5000);
        require(!worker.isAlive() && NativeInterfaceFixture.HOST.size() == 2, "Worker unload performed native removal");
        bindings.interfaces.observe(null, null);
        require(NativeInterfaceFixture.HOST.size() == 1, "Failed removal discarded an unrelated owner's listener");
        invoke(lua, bindings, otherOwner, true, "ownedSecond:close()");
        bindings.interfaces.observe(null, null);
        require(NativeInterfaceFixture.HOST.size() == 0, "Removal failure poisoned later cleanup");
        ScriptResourceTracker.unload(otherOwner);
        invoke(lua, bindings, true,
                "assert(ownedFirst:describe().state=='detach_failed'); assert(ownedSecond:describe().state=='closed')");
    }

    private static void verifyVisibility(Globals lua, NativeBindings bindings) {
        invoke(lua, bindings, true, "local a=(...).native:direct(); local ok,e=pcall(function() "
                + "a:implement{interfaces={'betamoon.interop.fixture.NativeInterfaceFixture$Hidden'},methods={}} end); "
                + "assert(not ok and tostring(e):find('invalid_native_interfaces'))");
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                "fixture/NativeDuplicateInterface", null, "java/lang/Object", null);
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        class DuplicateLoader extends ClassLoader {
            Class<?> create() {
                return defineClass("fixture.NativeDuplicateInterface", bytes, 0, bytes.length);
            }
        }
        Class<?> first = new DuplicateLoader().create();
        Class<?> second = new DuplicateLoader().create();
        expectFailure(() -> new NativeInterfaceContract(Arrays.asList(first, second)), "interface_loader_visibility");
        ClassWriter missing = new ClassWriter(0);
        missing.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                "fixture/NativeMissingDependency", null, "java/lang/Object", null);
        missing.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "missing", "()Lfixture/AbsentDependency;", null,
                null).visitEnd();
        missing.visitEnd();
        byte[] missingBytes = missing.toByteArray();
        Class<?> absent = new ClassLoader() {
            Class<?> create() {
                return defineClass("fixture.NativeMissingDependency", missingBytes, 0, missingBytes.length);
            }
        }.create();
        expectFailure(() -> new NativeInterfaceContract(Collections.singletonList(absent)), "missing_dependency");
    }

    private static void verifySessionClose(Globals lua, NativeBindings bindings) {
        invoke(lua, bindings, true, attachment("sessionAttachment", "add", "remove", "function() end"));
        Object proxy = borrow(lua, bindings, "sessionAttachment", true);
        lua.set("closeNativeSession", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                bindings.close();
                return LuaValue.NONE;
            }
        });
        invoke(lua, bindings, true, "closeNativeSession()");
        require(NativeInterfaceFixture.HOST.size() == 1, "Session close removed a listener inside an active callback");
        requireCallbacksCleared(proxy);
        NativeAttachmentDisposal.drain();
        require(NativeInterfaceFixture.HOST.size() == 0, "Closed session lost its deferred removal work");
        lua.set("closeNativeSession", LuaValue.NIL);
    }

    private static String attachment(String name, String add, String remove, String callback) {
        return "local a=(...).native:direct(); local p=a:implement{interfaces={O},methods={{method=O:method('changed','(I)V'),callback="
                + callback + "}}}; local host=assert(a:bind('betamoon.interop.fixture.NativeInterfaceFixture')).HOST; "
                + name + "=a:attach{implementation=p,receiver=host,lifetime='owner',add=C:method('" + add
                + "','(Lbetamoon/interop/fixture/NativeInterfaceFixture$Observer;)V'),remove=C:method('" + remove
                + "','(Lbetamoon/interop/fixture/NativeInterfaceFixture$Observer;)V')}";
    }

    private static void requireCallbacksCleared(Object proxy) {
        try {
            Object handler = Proxy.getInvocationHandler(proxy);
            Field callbacks = handler.getClass().getDeclaredField("callbacks");
            callbacks.setAccessible(true);
            require(callbacks.get(handler) == null, "Retained closed proxy kept Lua dispatch references");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object borrow(Globals lua, NativeBindings bindings, String name) {
        return borrow(lua, bindings, name, false);
    }

    private static Object borrow(Globals lua, NativeBindings bindings, String name, boolean attachment) {
        return ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, scope, true, Collections.emptyMap(),
                            () -> true)) {
                return ((NativeLuaImplementation) (attachment
                        ? lua.get(name).get("implementation")
                        : lua.get(name))).value.use(context);
            }
        });
    }

    private static void invoke(Globals lua, NativeBindings bindings, boolean mutable, String code) {
        invoke(lua, bindings, OWNER, mutable, code);
    }

    private static void invoke(Globals lua, NativeBindings bindings, String owner, boolean mutable, String code) {
        try (LuaCallbackScope scope = new LuaCallbackScope(mutable)) {
            scope.nativeEntry(new NativeEntry(bindings, scope, NativeEntry.Phase.INTERFACE, mutable,
                    Collections.emptyMap(), () -> true));
            ScriptExecution.invoke(owner, "native interface fixture", lua.load(code, "native interface fixture"),
                    new NativeCallbackTable(scope));
        }
    }

    private static void expectFailure(Runnable action, String reason) {
        try {
            action.run();
            throw new AssertionError("Expected " + reason);
        } catch (NativeAccessFailure failure) {
            require(failure.reason.equals(reason), "Expected " + reason + " but got " + failure.reason);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
