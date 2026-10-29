package betamoon.luaapi.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import com.sun.management.ThreadMXBean;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Runs actual Lua declarations and queries through the real binder without
 * Minecraft.
 */
public final class NativeDataQueryTest {
    private static final String OWNER = "native-data-query-fixture";
    private final Globals lua = JsePlatform.standardGlobals();
    private final AtomicBoolean valid = new AtomicBoolean(true);
    private final NativeBindings bindings;
    private final NativeLoaderRegistry registry;
    private Fixture fixture;

    private NativeDataQueryTest() throws Exception {
        registry = new NativeLoaderRegistry();
        registry.capture(Collections.singletonList(Fixture.class), Collections.singletonList(Fixture.class),
                Collections.emptyList(), false, null, false);
        TinyMappingResolver mapping = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        bindings = new NativeBindings(new NativeNames(mapping.snapshot(), "named"), registry);
        LuaTable module = new LuaTable();
        NativeDiscoveryApi.attach(module, OWNER, new NativeDiscoveryApi.Backend() {
            public NativeIndexSnapshot snapshot() {
                return NativeIndexSnapshot.empty();
            }

            public Map<String, Object> status() {
                return Collections.emptyMap();
            }

            public NativeDiscoveryService.Ticket refresh() {
                throw new UnsupportedOperationException();
            }

            public void requireClient() {
                bindings.requireThread();
            }
        });
        lua.set("betamoon", module);
        lua.set("fixtureType", Fixture.class.getName());
        lua.set("nodeType", Node.class.getName());
        lua.set("baseType", BaseNode.class.getName());
        lua.set("nodeDescriptor", "(L" + Node.class.getName().replace('.', '/') + ";)I");
        run("n=betamoon.native; T=n:type(fixtureType); N=n:type(nodeType); "
                + "local f={number=T:field('number','I'),exact=T:field('exact','J'),text=T:field('text','Ljava/lang/String;')}; "
                + "basic=n:dataPlan{type=T,fields=f}; f.number=nil; "
                + "assert(basic.sample==nil and not pcall(function() basic.changed=true end)); "
                + "assert(not pcall(function() n:dataPlan(setmetatable({type=T},{__index=function() error('getter') end})) end)); "
                + "assert(not pcall(function() n:dataPlan{type=T,fields={x=T:field('number')},calls={{key='x',method=T:method('add','(I)I'),arguments={1}}}} end)); "
                + "assert(not pcall(function() n:dataPlan{type=T,calls={{key='v',method=T:method('nothing','()V')}}} end)); "
                + "assert(not pcall(function() n:dataPlan{type=T,calls={{key='v',method=T:method('add','(I)I'),arguments={function() end}}}} end)); "
                + "node=n:dataPlan{type=N,fields={value=N:field('value','I',{declaringType=N})}}; "
                + "local Base=n:type(baseType); "
                + "hidden=n:dataPlan{type=N,fields={own=N:field('value','I',{declaringType=N}),inherited=N:field('value','I',{declaringType=Base})}}; "
                + "hiddenRoot=n:dataPlan{type=T,fields={child={field=T:field('child'),select=hidden}}}; "
                + "private=n:dataPlan{type=T,fields={secret=T:field('secret','I')}}; "
                + "arrays=n:dataPlan{type=T,fields={values={field=T:field('values','[I'),slice={start=1,count=2}},bytes=T:field('bytes','[B')}}; "
                + "objects=n:dataPlan{type=T,fields={child={field=T:field('child'),select=node},children={field=T:field('children'),select={each=node}},lookup={field=T:field('lookup'),select={values=node}}}}; "
                + "scalarList=n:dataPlan{type=T,fields={scalars={field=T:field('scalars'),select={each=true}}}}; "
                + "calls=n:dataPlan{type=T,fields={before=T:field('number','I')},calls={{key='first',method=T:method('add','(I)I'),arguments={{input='delta'}}},{key='second',method=T:method('add','(I)I'),arguments={0}}}}; "
                + "reader=n:dataPlan{type=T,calls={{key='value',method=T:method('readNode',nodeDescriptor),arguments={{input='node'}}}}}; "
                + "static=n:dataPlan{type=T,fields={value=T:field('staticValue','I')}}; "
                + "nulls=n:dataPlan{type=T,fields={value={field=T:field('missing'),select=node}}}; "
                + "shallow=n:dataPlan{type=T,fields={value=T:field('child')}}; "
                + "cyclic=n:dataPlan{type=N,fields={next={field=N:field('next'),select=node}}}; "
                + "cycle=n:dataPlan{type=T,fields={value={field=T:field('child'),select=cyclic}}}; "
                + "tiny=n:dataPlan{type=T,fields={value=T:field('number')},maxBytes=1}; "
                + "failure=n:dataPlan{type=T,calls={{key='failure',method=T:method('fail','()I')}}}; "
                + "mutatingNode=n:dataPlan{type=N,calls={{key='read',method=N:method('read','()I')}}}; "
                + "mutating=n:dataPlan{type=T,fields={children={field=T:field('children'),select={each=mutatingNode}}}};");
        run("local calls={}; for i=1,160 do calls[i]={key='v'..i,method=T:method('readNode',nodeDescriptor),"
                + "arguments={n:null('example.'..string.rep('A',900))}} end; "
                + "assert(not pcall(function() n:dataPlan{type=T,calls=calls} end))");
        require(System.getProperty("betamoon.query.fixture.initialized") == null, "declarations initialize no target");
        fixture = new Fixture();
    }

    public static void main(String[] args) throws Exception {
        NativeDataQueryTest test = new NativeDataQueryTest();
        test.verify();
        test.advanced();
        test.performance();
        test.definingLoaders();
        test.bindings.close();
        System.out.println(
                "Native data queries passed: real Lua plans, exact bindings, detachment, batch preflight and policy/lifetime guards.");
    }

    private void verify() throws Exception {
        entry(true, "saved=assert(a:sample(basic,o)); assert(saved.number==3); "
                + "assert(saved.exact:decimal()=='9223372036854775807'); "
                + "assert(saved.text:escaped()==[[\\ud800\\u0078]],saved.text:escaped()); "
                + "local data=assert(a:sample(arrays,o)); "
                + "assert(#data.values==2 and data.values[1]==20 and data.values[2]==30); "
                + "assert(data.bytes[1]==-1 and data.bytes[2]==2); "
                + "assert(a:direct():sample(private,o).secret==11); "
                + "local hidden=assert(a:direct():sample(hiddenRoot,o)).child; "
                + "assert(hidden.own==4 and hidden.inherited==99); "
                + "local value,reason=a:sample(private,o); assert(value==nil and reason=='nonpublic_requires_direct'); "
                + "local list=assert(a:sample(scalarList,o)).scalars; "
                + "assert(#list==4 and list[2]==n.NULL and list[3]:decimal()=='9223372036854775807'); "
                + "assert(a:sample(nulls,o).value==n.NULL); "
                + "local value,reason=a:sample(shallow,o); assert(value==nil and reason=='native_data_projection_required'); "
                + "local value,reason=a:sample(cycle,o); assert(value==nil and reason=='native_data_cycle'); "
                + "local value,reason=a:sample(tiny,o); assert(value==nil and reason=='native_data_byte_limit'); "
                + "local batch=assert(a:direct():sampleMany(calls,{{receiver=o,inputs={delta=2}},{receiver=o,inputs={delta=3}}})); "
                + "assert(batch[1].before==3 and batch[1].first==5 and batch[1].second==5); "
                + "assert(batch[2].before==5 and batch[2].first==8); "
                + "assert(a:direct():sample(static,nil).value==9); "
                + "local deep=assert(a:direct():sample(objects,o)); "
                + "assert(deep.child.value==4 and deep.children[2].value==5 and deep.lookup[1].key=='alpha' and deep.lookup[1].value.value==4)");
    }

    private void advanced() throws Exception {
        run("assert(saved.number==3 and saved.exact:decimal()=='9223372036854775807'); "
                + "assert(not pcall(function() o:describe() end)); "
                + "assert(not pcall(function() n:dataPlan{type=T,fields={bad={field=T:field('values'),slice={start=0.5,count=1}}}} end)); "
                + "badShape=n:dataPlan{type=T,calls={{key='first',method=T:method('add','(I)I'),arguments={1}},{key='wrong',method=T:method('add','(I)I'),arguments={1},select=node}}}; "
                + "cold=n:dataPlan{type=T,fields={number=T:field('number','I')}}; "
                + "wide=n:dataPlan{type=T,fields={number=T:field('number'),text=T:field('largeText')}};");
        entry(false, "assert(a:sample(basic,o).number==8); " + "assert(a:direct():sample(private,o).secret==11); "
                + "local value,reason=a:sample(cold,o); assert(value==nil and reason=='native_phase_denied'); "
                + "local value,reason=a:direct():sample(calls,o,{delta=1}); "
                + "assert(value==nil and reason=='native_phase_denied'); "
                + "local value,reason=a:direct():sample(static,nil); assert(value==nil and reason=='native_phase_denied'); "
                + "local value,reason=a:direct():sample(objects,o); assert(value==nil and reason=='native_phase_denied'); "
                + "assert(#a:sampleMany(cold,{})==0)");
        entry(true, "local before=o.number; "
                + "local value,reason,detail=a:direct():sampleMany(calls,{{receiver=o,inputs={delta=2}},{receiver=o,inputs={delta='wrong'}}}); "
                + "assert(value==nil and reason=='conversion_failed' and detail.requestIndex==2 and detail.dataPath==[=[$[\"first\"]]=]); "
                + "assert(o.number==before); "
                + "local value,reason=a:direct():sampleMany(calls,{{receiver=o,inputs={delta=2}},{receiver=n:value('java.lang.String','bad'),inputs={delta=2}}}); "
                + "assert(value==nil and reason=='wrong_receiver' and o.number==before); "
                + "local value,reason=a:direct():sample(badShape,o); "
                + "assert(value==nil and reason=='invalid_data_projection' and o.number==before); "
                + "local value,reason=a:direct():sample(calls,o,{}); "
                + "assert(value==nil and reason=='native_data_input_missing'); "
                + "local value,reason=a:sampleMany(basic,setmetatable({},{__index=function() error('getter') end})); "
                + "assert(value==nil and reason=='invalid_data_plan'); "
                + "assert(not pcall(function() n:dataPlan{type=T,calls={{key='v',method=T:method('add','(I)I'),arguments={o}}}} end)); "
                + "local value,reason=a:sample(wide,o); assert(value==nil and reason=='native_data_byte_limit'); "
                + "local value,reason=a:sample(basic,o,{unused=string.rep('x',262145)}); "
                + "assert(value==nil and reason=='native_data_input_limit')");
        try {
            entry(true, "a:direct():sample(failure,o)");
            throw new AssertionError("Native query exception was swallowed");
        } catch (LuaError error) {
            Throwable cause = error.getCause();
            while (cause instanceof LuaError) {
                cause = cause.getCause();
            }
            require(cause instanceof NativeAccessFailure && cause.getCause() == fixture.nativeFailure,
                    "query preserves the original native exception identity");
            require(HostileQueryFailure.calls == 0, "query diagnostics invoke no foreign exception getters");
        }
        run("fatal=n:dataPlan{type=T,calls={{key='value',method=T:method('fatal','()I')}}}");
        try {
            entry(true, "a:direct():sample(fatal,o)");
            throw new AssertionError("Fatal query failure was swallowed");
        } catch (QueryFatal fatal) {
            require(fatal == fixture.fatalFailure, "VM-fatal errors preserve identity and escape ordinary refusals");
        }
        fixture.child.onRead = () -> fixture.children.remove(1);
        entry(true,
                "local value,reason=a:direct():sample(mutating,o); assert(value==nil and reason=='native_data_collection_changed')");
        fixture.child.onRead = null;
        fixture.children.add(new Node(5));
        entry(true, "assert(a:direct():sample(reader,o,{node=o.child}).value==4)");
        fixture.onCall = () -> run("a:direct():release(inputOwner)");
        entry(true,
                "inputOwner=a:direct():retain(o.child,{lifetime='owner'}); local input=a:use(inputOwner); "
                        + "local value,reason=a:direct():sample(reader,o,{node=input}); "
                        + "assert(value==nil and reason=='owned_value_closed')");
        fixture.onCall = null;
        run("local child=n:dataPlan{type=N,calls={{key='value',method=N:method('offset','(I)I'),arguments={{input='offset'}}}}}; "
                + "nestedInput=n:dataPlan{type=T,fields={child={field=T:field('child'),select=child}}}");
        entry(true, "local value,reason,detail=a:direct():sample(nestedInput,o,{offset='wrong'}); "
                + "assert(value==nil and reason=='conversion_failed' and detail.requestedType==nodeType and detail.requestedMember=='offset'); "
                + "assert(detail.dataPath:find('child',1,true) and detail.dataPath:find('value',1,true)); "
                + "assert(a:direct():sample(nestedInput,o,{offset=2}).child.value==6)");
        fixture.onCall = () -> valid.set(false);
        int before = fixture.number;
        entry(true,
                "local value,reason=a:direct():sampleMany(calls,{{receiver=o,inputs={delta=1}},{receiver=o,inputs={delta=1}}}); "
                        + "assert(value==nil and reason=='stale_environment')");
        require(fixture.number == before + 1,
                "root replacement stops the batch without pretending to roll back effects");
        fixture.onCall = null;
        entry(true, "local owned=a:direct():retain(o.child,{lifetime='owner'}); "
                + "local borrowed=a:use(owned); local copy=assert(a:sample(node,borrowed)); "
                + "a:direct():release(owned); assert(copy.value==4); "
                + "local value,reason=a:sample(node,borrowed); assert(value==nil and reason=='owned_value_closed')");
        entry(true,
                "local count=0; local reason; repeat "
                        + "local value; value,reason=a:sample(basic,o); count=count+1; until not value; "
                        + "assert(count>1 and reason=='native_data_work_limit')");
        entry(true, "local value,reason=n:withContext(function(child) "
                + "local value,reason; repeat value,reason=child:sample(basic,o) until not value; return value,reason end); "
                + "assert(value==nil and reason=='native_data_work_limit'); "
                + "local value,reason=a:sample(basic,o); assert(value==nil and reason=='native_data_work_limit')");
        entry(true, "held=access");
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                run("local value,reason=held:sample(basic,nil); assert(value==nil and reason=='wrong_thread')");
            } catch (Throwable failure) {
                workerFailure.set(failure);
            }
        }, "query-worker");
        worker.start();
        worker.join();
        require(workerFailure.get() == null, "worker query is refused before any native operation");
        fixture.onCall = () -> run("reentrant=assert(a:sample(basic,o))");
        entry(true, "local value=assert(a:direct():sample(calls,o,{delta=1})); assert(reentrant.number==value.first)");
        fixture.onCall = null;
        fixture.onCall = () -> ScriptResourceTracker.unload(OWNER);
        entry(true,
                "local value,reason=a:direct():sample(calls,o,{delta=1}); assert(value==nil and reason=='stale_owner')");
        fixture.onCall = null;
        run("assert(saved.number==3 and saved.exact:decimal()=='9223372036854775807')");
        entry(true,
                "for index=1,65 do local plan=n:dataPlan{type=T,fields={v=T:field('number','I')}}; assert(a:sample(plan,o)) end");
        entry(false, "local value,reason=a:sample(basic,o); assert(value==nil and reason=='native_phase_denied')");
        entry(true, "assert(a:sample(basic,o))");
        entry(false, "assert(a:sample(basic,o))");
        entry(true, "for index=1,70 do assert(a:sample(basic,o)); "
                + "assert(a:sample(n:dataPlan{type=T,fields={v=T:field('number','I')}},o)) end");
        entry(false, "assert(a:sample(basic,o))");
        for (int count : new int[]{1, 4, 64, 256}) {
            entry(true, "local requests={}; for i=1," + count + " do requests[i]={receiver=o} end; "
                    + "local result=assert(a:sampleMany(basic,requests)); assert(#result==" + count + "); "
                    + "assert(result[1].number==o.number and result[" + count
                    + "].exact:decimal()=='9223372036854775807'); " + "result[1].number=-10; assert(o.number~=-10)");
        }
    }

    private void definingLoaders() throws Exception {
        byte[] bytes;
        try (InputStream input = Fixture.class.getResourceAsStream("NativeDataQueryTest$Fixture.class")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        ClassLoader first = loader(bytes);
        ClassLoader second = loader(bytes);
        Class<?> firstType = Class.forName(Fixture.class.getName(), false, first);
        Class<?> secondType = Class.forName(Fixture.class.getName(), false, second);
        Object one = firstType.newInstance();
        Object two = secondType.newInstance();
        firstType.getField("number").setInt(one, 101);
        secondType.getField("number").setInt(two, 202);
        lua.set("firstLoader", registry.id(first));
        lua.set("secondLoader", registry.id(second));
        run("local A=n:type(fixtureType,{loaderId=firstLoader}); local B=n:type(fixtureType,{loaderId=secondLoader}); "
                + "firstPlan=n:dataPlan{type=A,fields={value=A:field('number','I')}}; "
                + "secondPlan=n:dataPlan{type=B,fields={value=B:field('number','I')}};");
        ScriptExecution.withOwner(OWNER, () -> {
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, OWNER, callback, true, Collections.emptyMap(),
                            () -> true)) {
                lua.set("a", NativeAccessApi.contextAccess(context));
                lua.set("one", NativeAccessApi.hookValue(one, context));
                lua.set("two", NativeAccessApi.hookValue(two, context));
                run("assert(a:sample(firstPlan,one).value==101 and a:sample(secondPlan,two).value==202); "
                        + "local value,reason=a:sample(firstPlan,two); assert(value==nil and reason=='wrong_receiver'); "
                        + "assert(a:sample(firstPlan,one).value==101)");
            }
            return null;
        });
    }

    private ClassLoader loader(byte[] bytes) {
        return new ClassLoader(Fixture.class.getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(Fixture.class.getName())) {
                    return super.loadClass(name, resolve);
                }
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        };
    }

    private void performance() {
        LuaValue construct = lua.load("profilePlan=n:dataPlan{type=T,fields={v=T:field('number','I')}}",
                "plan declaration profile");
        LuaValue firstQuery = lua.load("profileResult=assert(a:sample(profilePlan,o))", "first query profile");
        try (LuaCallbackScope callback = new LuaCallbackScope(true);
                NativeContext context = NativeContext.open(bindings, OWNER, callback, true,
                        Collections.singletonMap("subject", fixture), () -> true)) {
            lua.set("access", NativeAccessApi.contextAccess(context));
            run("a=access;o=a:subject()");
            long started = System.nanoTime();
            ScriptExecution.invokeApi(OWNER, construct, LuaValue.NONE);
            long declaration = System.nanoTime() - started;
            started = System.nanoTime();
            ScriptExecution.invokeApi(OWNER, firstQuery, LuaValue.NONE);
            System.out.println("Native query preparation profile: declaration=" + declaration
                    + " ns, first query/new-plan preparation=" + (System.nanoTime() - started)
                    + " ns (existing binder/class caches warm)");
        }
        LuaValue individual = lua.load("profileResult={number=o.number,exact=o.exact,text=o.text}", "individual reads");
        LuaValue bulk = lua.load("profileResult=assert(a:sample(basic,o))", "compiled query");
        LuaValue roots = lua.load("a=access;o=a:subject()", "query profile roots");
        long[][] times = {new long[600], new long[600]};
        long[] allocations = new long[2];
        ThreadMXBean meter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        require(meter.isThreadAllocatedMemorySupported(), "profile requires Java 8 allocation accounting");
        meter.setThreadAllocatedMemoryEnabled(true);
        for (int iteration = -200; iteration < 600; iteration++) {
            for (int variant = 0; variant < 2; variant++) {
                try (LuaCallbackScope callback = new LuaCallbackScope(true);
                        NativeContext context = NativeContext.open(bindings, OWNER, callback, true,
                                Collections.singletonMap("subject", fixture), () -> true)) {
                    lua.set("access", NativeAccessApi.contextAccess(context));
                    ScriptExecution.invokeApi(OWNER, roots, LuaValue.NONE);
                    long before = meter.getThreadAllocatedBytes(Thread.currentThread().getId());
                    long started = System.nanoTime();
                    ScriptExecution.invokeApi(OWNER, variant == 0 ? individual : bulk, LuaValue.NONE);
                    long elapsed = System.nanoTime() - started;
                    long allocated = meter.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
                    if (iteration >= 0) {
                        times[variant][iteration] = elapsed;
                        allocations[variant] += allocated;
                    }
                }
            }
        }
        for (int variant = 0; variant < 2; variant++) {
            Arrays.sort(times[variant]);
            System.out.println("Native query profile " + (variant == 0 ? "individual" : "compiled") + ": p50="
                    + times[variant][300] + " ns, p95=" + times[variant][570] + " ns, p99=" + times[variant][594]
                    + " ns, bytes/sample=" + allocations[variant] / 600 + ", Lua/native selection crossings="
                    + (variant == 0 ? 3 : 1));
        }
        Fixture other = new Fixture();
        Map<String, Object> paired = new LinkedHashMap<>();
        paired.put("subject", fixture);
        paired.put("player", other);
        LuaValue pairedRoots = lua.load("a=access:direct();o=a:subject();p=a:player()", "batch roots");
        LuaValue individualBatch = lua.load("profileResult={{before=o.number,first=o:add(0),second=o:add(0)},"
                + "{before=p.number,first=p:add(0),second=p:add(0)},{before=o.number,first=o:add(0),second=o:add(0)},"
                + "{before=p.number,first=p:add(0),second=p:add(0)}}", "individual repeated inputs");
        LuaValue bulkBatch = lua.load("profileResult=assert(a:sampleMany(calls,{{receiver=o,inputs={delta=0}},"
                + "{receiver=p,inputs={delta=0}},{receiver=o,inputs={delta=0}},{receiver=p,inputs={delta=0}}}))",
                "compiled repeated inputs");
        for (int iteration = -200; iteration < 600; iteration++) {
            for (int variant = 0; variant < 2; variant++) {
                try (LuaCallbackScope callback = new LuaCallbackScope(true);
                        NativeContext context = NativeContext.open(bindings, OWNER, callback, true, paired,
                                () -> true)) {
                    lua.set("access", NativeAccessApi.contextAccess(context));
                    ScriptExecution.invokeApi(OWNER, pairedRoots, LuaValue.NONE);
                    long before = meter.getThreadAllocatedBytes(Thread.currentThread().getId());
                    long started = System.nanoTime();
                    ScriptExecution.invokeApi(OWNER, variant == 0 ? individualBatch : bulkBatch, LuaValue.NONE);
                    long elapsed = System.nanoTime() - started;
                    long allocated = meter.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
                    if (iteration >= 0) {
                        times[variant][iteration] = elapsed;
                        if (iteration == 0) {
                            allocations[variant] = 0;
                        }
                        allocations[variant] += allocated;
                    }
                }
            }
        }
        for (int variant = 0; variant < 2; variant++) {
            Arrays.sort(times[variant]);
            System.out.println("Native batch profile " + (variant == 0 ? "individual" : "compiled") + ": p50="
                    + times[variant][300] + " ns, p95=" + times[variant][570] + " ns, p99=" + times[variant][594]
                    + " ns, bytes/batch=" + allocations[variant] / 600 + ", Lua/native selection crossings="
                    + (variant == 0 ? 12 : 1));
        }
    }

    private void entry(boolean mutable, String source) {
        valid.set(true);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("subject", fixture);
        try (LuaCallbackScope callback = new LuaCallbackScope(mutable);
                NativeContext context = NativeContext.open(bindings, OWNER, callback, mutable, roots, valid::get)) {
            lua.set("access", NativeAccessApi.contextAccess(context));
            run("a=access; o=a:subject(); " + source);
        }
    }

    private void run(String source) {
        ScriptExecution.invokeApi(OWNER, lua.load(source.replace("; ", ";\n"), "native data query fixture"),
                LuaValue.NONE);
    }

    private static void require(boolean value, String detail) {
        if (!value) {
            throw new AssertionError(detail);
        }
    }

    public static final class Fixture {
        static {
            System.setProperty("betamoon.query.fixture.initialized", "yes");
        }

        public static int staticValue = 9;
        public int number = 3;
        private int secret = 11;
        public long exact = Long.MAX_VALUE;
        public String text = new String(new char[]{(char) 0xD800, 'x'});
        public String largeText = String.join("", Collections.nCopies(270000, "a"));
        public int[] values = {10, 20, 30};
        public byte[] bytes = {-1, 2};
        public Node child = new Node(4);
        public Node missing;
        public ArrayList<Node> children = new ArrayList<>(Arrays.asList(child, new Node(5)));
        public Map<String, Node> lookup = new LinkedHashMap<>();
        public ArrayList<Object> scalars = new ArrayList<>(Arrays.asList("a", null, Long.MAX_VALUE, 'q'));
        public Runnable onCall;
        public final RuntimeException nativeFailure = new HostileQueryFailure();
        public final QueryFatal fatalFailure = new QueryFatal();

        public Fixture() {
            lookup.put("alpha", child);
        }

        public int add(int delta) {
            number += delta;
            if (onCall != null) {
                onCall.run();
            }
            return number;
        }

        public int readNode(Node node) {
            if (onCall != null) {
                onCall.run();
            }
            return node.value;
        }

        public void nothing() {
        }

        public int fail() {
            throw nativeFailure;
        }

        public int fatal() {
            throw fatalFailure;
        }
    }

    public static final class HostileQueryFailure extends RuntimeException {
        private static int calls;

        public HostileQueryFailure() {
            super("stored query failure");
        }

        @Override
        public String getMessage() {
            calls++;
            throw new AssertionError("foreign message getter invoked");
        }

        @Override
        public synchronized Throwable getCause() {
            calls++;
            throw new AssertionError("foreign cause getter invoked");
        }

        @Override
        public String toString() {
            calls++;
            throw new AssertionError("foreign formatter invoked");
        }
    }

    public static final class QueryFatal extends VirtualMachineError {
    }

    public static class BaseNode {
        protected int value = 99;
    }

    public static final class Node extends BaseNode {
        public int value;
        public Node next = this;
        public Runnable onRead;

        public Node(int value) {
            this.value = value;
        }

        public int offset(int amount) {
            return value + amount;
        }

        public int read() {
            if (onRead != null) {
                onRead.run();
            }
            return value;
        }
    }
}
