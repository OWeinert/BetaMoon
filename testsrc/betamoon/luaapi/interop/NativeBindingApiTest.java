package betamoon.luaapi.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.NativeDiscoveryTest;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.fixture.NativeFixtureMod;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.ByteArrayInputStream;
import java.io.FilePermission;
import java.lang.reflect.ReflectPermission;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** No Minecraft or raw LuaJ Java coercion on the fixture process classpath. */
public final class NativeBindingApiTest {
    private static final String OWNER = "native_binding_fixture.lua";

    private NativeBindingApiTest() {
    }

    public static void main(String[] args) throws Exception {
        try {
            Class.forName("net.minecraft.src.World");
            throw new AssertionError("Native binding fixture inherited Minecraft");
        } catch (ClassNotFoundException expected) {
        }
        Path parent = Paths.get("build").toAbsolutePath().normalize().toRealPath();
        Path work = Files.createTempDirectory(parent, "native-binding-");
        try {
            verify(work);
            NativeOrdinaryLookupTest.run();
        } finally {
            Path target = work.toRealPath();
            require(target.getParent().equals(parent) && target.getFileName().toString().startsWith("native-binding-"),
                    "Unsafe native binding cleanup target");
            try (Stream<Path> paths = Files.walk(target)) {
                for (Path path : (Iterable<Path>) paths.sorted(Collections.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        System.out.println(
                "Native binding passed: symbolic reflection, general invocation, exact values and scope guards.");
    }

    private static void verify(Path work) throws Exception {
        NativeLoaderRegistry registry = new NativeLoaderRegistry();
        NativeLoaderRegistry.Capture capture = registry.capture(Collections.singletonList(NativeFixtureMod.class),
                Collections.singletonList(NativeFixtureMod.class), Collections.emptyList(), false, null, false);
        TinyMappingResolver resolver = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        try (NativeDiscoveryService service = new NativeDiscoveryService(work.resolve("cache"))) {
            NativeDiscoveryTest.await(service, service.request(capture, "named", Collections.emptyList()));
            NativeBindings bindings = new NativeBindings(new NativeNames(resolver.snapshot(), "named"), registry,
                    service::snapshot);
            Globals lua = JsePlatform.standardGlobals();
            LuaTable module = new LuaTable();
            NativeDiscoveryApi.attach(module, OWNER, new NativeDiscoveryApi.Backend() {
                public NativeIndexSnapshot snapshot() {
                    return service.snapshot();
                }

                public Map<String, Object> status() {
                    return service.status();
                }

                public NativeDiscoveryService.Ticket refresh() {
                    return service.request(capture, "named", Collections.emptyList());
                }

                public void requireClient() {
                    bindings.requireThread();
                }
            });
            lua.set("betamoon", module);
            lua.set("fixtureClass", NativeFixtureMod.class.getName());
            lua.load("n=betamoon.native; T=n:type(fixtureClass); " + "assert(T==n:type(fixtureClass)); "
                    + "assert(n:describe(n:type('int')).bits==32); "
                    + "assert(n:describe(n:type('char')).signed==false); "
                    + "assert(n:describe(n:type('int[][]')).componentType.name=='int[]'); "
                    + "assert(not pcall(function() n:type('void[]') end)); "
                    + "local m=T:method('longEcho','(J)J'); assert(m.parameterTypes[1].name=='long'); "
                    + "assert(m.returnType.name=='long'); " + "assert(not pcall(function() T.name='changed' end)); "
                    + "local info=T:describe(); assert(info.ref.name==fixtureClass); "
                    + "assert(not pcall(function() info.name='changed' end)); "
                    + "assert(not pcall(function() n:withContext(function() end) end));", "native metadata").call();
            require(System.getProperty("betamoon.native.binding.initialized") == null, "Metadata initialized fixture");
            AtomicBoolean valid = new AtomicBoolean(true);
            Map<String, Object> roots = new LinkedHashMap<>();
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext nativeScope = NativeContext.open(bindings, OWNER, scope, true, roots, valid::get)) {
                lua.set("access", NativeAccessApi.contextAccess(nativeScope));
                run(lua, "a=access:direct(); C=assert(a:bind(T));");
                require(System.getProperty("betamoon.native.binding.initialized") == null,
                        "Binding initialized fixture");
                run(lua, "o=C:new(); assert(o.number==3); o.number=4; assert(o.number==4); "
                        + "assert(o:which(2)=='int'); assert(o:which(2.5)=='double'); "
                        + "assert(o:which(n:long('9007199254740992'))=='long'); "
                        + "assert(o:which(n:value('float',2))=='float'); " + "assert(o:covariant()=='child'); "
                        + "assert(o:byteEcho(7)==7); assert(not pcall(function() o:byteEcho(128) end)); "
                        + "assert(not pcall(function() o:narrow(7) end)); "
                        + "assert(o:narrow(n:value('short',7))=='short'); "
                        + "local before=C.counter; assert(not pcall(function() o:which(o.customNumber) end)); "
                        + "assert(C.counter==before); " + "assert(not pcall(function() o:ambiguous('a') end)); "
                        + "assert(o:nullable(n:null('java.lang.String'))=='string-null'); "
                        + "assert(not pcall(function() o:nullable(n.NULL) end)); " + "assert(o:nothing()==n.NULL); "
                        + "assert(o:longEcho(n:long('9223372036854775807')):decimal()=='9223372036854775807'); "
                        + "assert(o:minimum():decimal()=='-9223372036854775808'); "
                        + "assert(o:bits(o:negativeZero()):decimal()=='-9223372036854775808'); "
                        + "assert(o:bits(n:value('double','-0.0')):decimal()=='-9223372036854775808'); "
                        + "assert(not pcall(function() o:negativeZero():toNumber() end)); "
                        + "assert(o:maximum():compare(n:long('9223372036854775806'))==1); "
                        + "assert(not pcall(function() o:maximum():toNumber() end)); "
                        + "assert(type(o:maximum():toNumber({lossy=true}))=='number'); "
                        + "assert(o:character(n:value('char',55296)):toNumber()==55296); "
                        + "local u=o:unpaired(); assert(u:unit(1)==55296); "
                        + "assert(o:text(u):escaped()==u:escaped()); "
                        + "assert(not pcall(function() o:text(string.char(255)) end)); "
                        + "assert(select('#',o:update())==0); " + "assert(n:typeOf(o).name==fixtureClass); "
                        + "assert(n:typeOf(n:long('7')).name=='long'); "
                        + "assert(not pcall(function() n:typeOf(7) end));");
                require("yes".equals(System.getProperty("betamoon.native.binding.initialized")),
                        "Constructor did not initialize fixture");
                run(lua, "local missing,reason=a:bind('fixture.DoesNotExist'); "
                        + "assert(missing==nil and reason=='class_not_found'); "
                        + "assert(a:call(T,'which',o,2)=='int'); " + "assert(a:call(T,'staticEcho',nil,4)==5); "
                        + "M=T:method('which','(I)Ljava/lang/String;'); "
                        + "assert(not pcall(function() M:call(o,3) end)); "
                        + "bound=assert(a:bind(M)); assert(bound:call(o,3)=='int'); "
                        + "assert(not pcall(function() bound:call(nil,3) end)); "
                        + "local s=C:method('staticEcho','(I)I'); assert(s:call(nil,3)==4); "
                        + "assert(not pcall(function() s:call(o,3) end)); "
                        + "local noargs=C:method('staticNoArguments','()V'); noargs:call(nil); "
                        + "assert(not pcall(function() noargs:call() end)); "
                        + "assert(not pcall(function() a:call(T,'staticNoArguments') end)); "
                        + "local p=n:query({kind='members',name='which',descriptor='(I)Ljava/lang/String;'}); "
                        + "assert(#p.items==1 and p.items[1].returnType.name=='java.lang.String'); "
                        + "assert(assert(a:bind(p.items[1])):call(o,1)=='int'); "
                        + "local serialized={kind='METHOD',declaringType=fixtureClass,name='which', "
                        + "descriptor='(I)Ljava/lang/String;',nameDomain='runtime'}; "
                        + "assert(assert(a:bind(serialized)):call(o,1)=='int'); "
                        + "assert(not pcall(function() return o.collision end)); "
                        + "assert(o:field('collision','I'):get()==2); "
                        + "assert(o:method('collision','()Ljava/lang/String;'):call(o)=='method');");
                run(lua, "local private=o:method('secret','(I)Ljava/lang/String;'); "
                        + "assert(private:call(o,6)=='private:6'); "
                        + "assert(o:method('protectedEcho','(I)Ljava/lang/String;'):call(o,8)=='protected:8'); "
                        + "assert(o:method('packageEcho','(I)Ljava/lang/String;'):call(o,8)=='package:8'); "
                        + "assert(not pcall(function() return o.hidden end)); "
                        + "local h=o:field('hidden','I',{declaringType=fixtureClass}); assert(h:get()==17); "
                        + "local h2=o:field('hidden','I',{declaringType='betamoon.interop.fixture.NativeFixtureParent'}); "
                        + "assert(h2:get()==11); h2:set(12); assert(h2:get()==12); "
                        + "assert(not pcall(function() o.fixed=0 end)); "
                        + "local node=o:field('node'):get(); deep=node:field('value','I'); "
                        + "assert(deep:get()==7); deep:set(8); assert(deep:get()==8); "
                        + "assert(node:method('publicOnPrivateClass','()Ljava/lang/String;'):call(node)=='node'); "
                        + "assert(node:field('next'):get()==n.NULL); "
                        + "assert(not pcall(function() n.NULL:field('value') end)); "
                        + "local f=T:field('opaqueNode','Ljava/lang/Object;'):describe(); "
                        + "assert(f.valueType.name=='java.lang.Object'); "
                        + "assert(n:typeOf(o.opaqueNode).name==fixtureClass..'$Node'); "
                        + "assert(o.opaqueNode:describe().identity==node:describe().identity); "
                        + "local inherited=T:method('protectedEcho','(I)Ljava/lang/String;'):describe(); "
                        + "assert(inherited.declaringTypeRef.name=='betamoon.interop.fixture.NativeFixtureParent'); "
                        + "local ctor=C:constructor('(I)V'); assert(ctor:new(6).number==6); "
                        + "local managed=assert(access:bind(T)); "
                        + "assert(not pcall(function() managed:method('secret','(I)Ljava/lang/String;') end));");
                run(lua, "arr=a:array('int',{1,2,3}); assert(arr:length()==3 and arr[0]==1); "
                        + "arr[0]=4; assert(arr:getAt(0)==4); " + "assert(o:sum(arr)==9); "
                        + "assert(not pcall(function() o:sum(1,2) end)); "
                        + "assert(not pcall(function() arr:setAll(0,{5,2.5}) end)); assert(arr[0]==4); "
                        + "arr:setAll(0,{5,6}); assert(arr:snapshot().items[2]==6); "
                        + "local refs=a:array('java.lang.String',{'a',n.NULL}); assert(refs[1]==n.NULL); "
                        + "local list=o.list; assert(list:length()==3 and list:getAt(1)==n.NULL); "
                        + "assert(list:snapshot().detached==false); list:setAt(0,'changed'); "
                        + "list:add(n:long('9007199254740993')); assert(list:length()==4); "
                        + "local map=o.map; map:setAt('key',n:long('9223372036854775807')); "
                        + "assert(map:getAt('key'):decimal()=='9223372036854775807'); "
                        + "assert(map:snapshot().detached); " + "local b=assert(a:bind('java.util.ArrayList')):new(); "
                        + "local text=o:largeText(); b:add(text); b:add(text); "
                        + "local limited=b:snapshot(); assert(limited.truncated and #limited.items==1); "
                        + "local e=assert(a:bind(fixtureClass..'$Tag')):enum('SECOND'); "
                        + "assert(n:typeOf(e).name==fixtureClass..'$Tag'); "
                        + "local str=n:value('java.lang.String','hello'); "
                        + "len=n:type('java.lang.String'):method('length','()I'); assert(len:call(str)==5); "
                        + "assert(not pcall(function() o:fail() end));");
                NativeAuthorityTest.verify(lua, bindings, OWNER);
                NativeOwnershipTest.verify(lua, bindings);
                try (LuaCallbackScope readOnly = LuaCallbackScope.binding()) {
                    run(lua, "assert(arr[0]==5); "
                            + "assert(deep:get()==8); assert(not pcall(function() deep:set(2) end)); "
                            + "assert(not pcall(function() arr[0]=10 end)); "
                            + "assert(not pcall(function() bound:call(o,3) end)); "
                            + "assert(not pcall(function() a:call(T,'staticEcho',nil,1) end)); "
                            + "assert(not pcall(function() n:withContext(function(x) x:direct():bind('java.util.Date') end) end)); "
                            + "assert(len:call(n:value('java.lang.String','a'))==1);");
                }
                try {
                    run(lua, "o:fail()");
                    throw new AssertionError("Native exception was swallowed");
                } catch (LuaError exception) {
                    require(exception.getCause() instanceof NativeAccessFailure
                            && exception.getCause().getCause() instanceof IllegalStateException
                            && exception.getCause().getCause().getCause() instanceof IllegalArgumentException,
                            "Native exception causes were lost");
                }
                try {
                    run(lua, "o:fatal()");
                    throw new AssertionError("Fatal native signal was swallowed");
                } catch (NativeFixtureMod.FixtureFatalError expected) {
                }
                run(lua, "for i=1,3 do pcall(function() C:method('which','(Z)V') end) end");
                Map<String, Object> journal = (Map<String, Object>) bindings.diagnostics().get("failureDetails");
                boolean selectorRecorded = false;
                for (Object item : (List<?>) journal.get("records")) {
                    Map<?, ?> record = (Map<?, ?>) item;
                    if ("which".equals(record.get("requestedMember"))
                            && "(Z)V".equals(record.get("requestedDescriptor"))) {
                        require(((Number) record.get("count")).longValue() >= 3,
                                "Failure history was not deduplicated");
                        require("direct".equals(record.get("policy")) && OWNER.equals(record.get("owner")),
                                "Failure lost owner/policy context");
                        require(record.get("availableOverloads") instanceof List,
                                "Failure lost metadata-only overload alternatives");
                        List<?> alternatives = (List<?>) record.get("availableOverloads");
                        require(!alternatives.isEmpty() && alternatives.size() <= 8,
                                "Indexed overload alternatives were missing or exceeded their budget");
                        selectorRecorded = true;
                    }
                }
                require(selectorRecorded, "Native failure history omitted the attempted exact selector");
                require(CatalogJson.encode(journal).length() < 5 * 1024 * 1024, "Unbounded native failure history");
                for (int i = 0; i < 80; i++) {
                    bindings.recordFailure(new NativeAccessFailure("bounded_fixture_" + i, "fixture"));
                }
                Map<?, ?> boundedJournal = (Map<?, ?>) bindings.diagnostics().get("failureDetails");
                require(((List<?>) boundedJournal.get("records")).size() == 64
                        && ((Number) boundedJournal.get("omitted")).longValue() > 0,
                        "Failure history did not enforce its record budget");
                verifyDeniedReflection(bindings, nativeScope);
                verifyOriginReads(bindings);
                verifyLoaderChanges(registry, bindings, nativeScope);
                AtomicReference<Throwable> wrong = new AtomicReference<>();
                LuaValue retained = lua.get("o");
                Thread thread = new Thread(() -> {
                    try {
                        retained.get("number");
                    } catch (Throwable exception) {
                        wrong.set(exception);
                    }
                });
                thread.start();
                thread.join();
                require(wrong.get() instanceof LuaError && wrong.get().getMessage().contains("wrong_thread"),
                        "Off-thread native access was accepted");
                valid.set(false);
                expect(lua, "return o.number", "stale_environment");
                valid.set(true);
                ScriptResourceTracker.unload(OWNER);
                expect(lua, "return o.number", "stale_owner");
            }
            expect(lua, "return arr[0]", "expired_scope");
            lua.load("assert(T.name==fixtureClass); assert(n:long('9223372036854775807'):decimal() "
                    + "=='9223372036854775807')", "detached values").call();
        } finally {
            registry.close();
        }
    }

    private static void verifyDeniedReflection(NativeBindings bindings, NativeContext context) {
        SecurityManager previous = System.getSecurityManager();
        ScriptExecution.withOwner(OWNER, () -> {
            try {
                System.setSecurityManager(new SecurityManager() {
                    @Override
                    public void checkPermission(Permission permission) {
                        if (permission instanceof ReflectPermission
                                && permission.getName().equals("suppressAccessChecks")) {
                            throw new SecurityException("fixture denied visibility override");
                        }
                    }
                });
                try {
                    bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.FIELD,
                            NativeTypeRef.named(NativeFixtureMod.class.getName(), "runtime", null, null),
                            NativeTypeRef.named("betamoon.interop.fixture.NativeFixtureParent", "runtime", null, null),
                            "parentSecret", "I", null, null), context, true);
                    throw new AssertionError("Denied reflective access was accepted");
                } catch (NativeAccessFailure expected) {
                    require(expected.reason.equals("access_denied"), "Wrong denied-access diagnostic");
                }
            } finally {
                System.setSecurityManager(previous);
            }
            return null;
        });
    }

    private static void verifyOriginReads(NativeBindings bindings) {
        NativeTypeRef prepared = bindings.reference(NativeFixtureMod.class);
        SecurityManager previous = System.getSecurityManager();
        try {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission permission) {
                    if (permission instanceof FilePermission && permission.getActions().contains("read")) {
                        throw new SecurityException("fixture denied filesystem reads");
                    }
                }
            });
            NativeTypeRef observed = bindings.reference(NativeFixtureMod.class);
            require(prepared.originId != null && prepared.originId.equals(observed.originId),
                    "Runtime type observation performed uncached origin I/O");
        } finally {
            System.setSecurityManager(previous);
        }
    }

    private static void verifyLoaderChanges(NativeLoaderRegistry registry, NativeBindings bindings,
            NativeContext context) throws Exception {
        URL location = NativeFixtureMod.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader duplicate = new URLClassLoader(new URL[]{location}, null)) {
            Class<?> other = Class.forName(NativeFixtureMod.class.getName(), false, duplicate);
            registry.capture(Collections.singletonList(NativeFixtureMod.class), Collections.singletonList(other),
                    Collections.emptyList(), false, null, false);
            ScriptExecution.withOwner(OWNER, () -> {
                NativeTypeRef free = NativeTypeRef.named(NativeFixtureMod.class.getName(), "runtime", null, null);
                try {
                    bindings.type(free, context);
                    throw new AssertionError("A cached free reference ignored a newly discovered loader collision");
                } catch (NativeAccessFailure expected) {
                    require(expected.reason.equals("ambiguous_loader"), "Wrong duplicate-loader failure");
                }
                Class<?> selected = bindings.type(free.selected(null, registry.id(duplicate), null), context);
                require(selected == other && selected != NativeFixtureMod.class, "Loader identity was not honored");
                return null;
            });
        }
    }

    private static void run(Globals lua, String source) {
        ScriptExecution.invokeApi(OWNER, lua.load(source, "native binding fixture"), LuaValue.NONE);
    }

    private static void expect(Globals lua, String source, String reason) {
        try {
            run(lua, source);
            throw new AssertionError("Expected native refusal: " + reason);
        } catch (LuaError exception) {
            require(exception.getMessage().contains(reason), "Wrong refusal: " + exception.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
