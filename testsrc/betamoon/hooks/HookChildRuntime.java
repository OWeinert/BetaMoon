package betamoon.hooks;

import com.sun.management.ThreadMXBean;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Loaded child-first with its own BetaMoon/LuaJ types, like the real launcher.
 */
public final class HookChildRuntime {
    public static void verify() throws Throwable {
        HookPreparation preparation = HookPreparation.current();
        Class<?> target = Class.forName("fixture.hooks.ChildHookFixture", false,
                HookChildRuntime.class.getClassLoader());
        if (preparation == null) {
            throw new AssertionError("child runtime did not import the frozen agent plan");
        }
        MethodHandle call = MethodHandles.publicLookup().findStatic(target, "call",
                MethodType.methodType(int.class, int.class));
        if ((int) call.invokeExact(1) != 2) {
            throw new AssertionError("child dormant method changed");
        }
        Globals globals = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        module.set("native", new LuaTable());
        globals.set("betamoon", module);
        globals.set("hits", 0);
        HookFileCollectionContext.Registration registration = HookDeclarations.evaluate("bootstrap", "child.lua",
                "local r=betamoon.hooks:createRegistry(); r:register({id='child_active',"
                        + "target={class='fixture.hooks.ChildHookFixture',method='call',descriptor='(I)I'},"
                        + "at='before',effects='edit'},function(call) hits=hits+1; saved=call; call:returnValue(42) end)",
                1, globals, new HookDeclarations.Budget()).get(0);
        HookDispatcher.clientThread();
        int site = preparation.targets(registration.definition).get(0).id;
        HookDispatcher.Subscription subscription = HookDispatcher.subscribe(site, target, registration.definition,
                invocation -> registration.callback.call(new Call(invocation)));
        HookDispatcher.activate(Collections.singletonList(subscription));
        if ((int) call.invokeExact(1) != 42 || globals.get("hits").toint() != 1
                || target.getField("bodies").getInt(null) != 1) {
            throw new AssertionError("independent LuaJ callback did not cross the JDK bridge atomically");
        }
        globals.load("assert(not pcall(function() saved:returnValue(3) end))").call();
        HookDispatcher.configureTiming("full", 64);
        int timedValue = (int) call.invokeExact(1);
        java.util.Map<String, Object> fullTiming = preparation.status().stream()
                .filter(row -> "HOOK_SUBSCRIPTION".equals(row.get("kind"))).findFirst().get();
        if (timedValue != 42 || !"full".equals(fullTiming.get("timingMode"))
                || ((Number) fullTiming.get("timedCalls")).longValue() != 1) {
            throw new AssertionError("timing configuration did not cross the JDK-only agent bridge");
        }
        HookDispatcher.configureTiming("sampled", 4);
        for (int index = 0; index < 8; index++) {
            int ignored = (int) call.invokeExact(1);
        }
        java.util.Map<String, Object> sampledTiming = preparation.status().stream()
                .filter(row -> "HOOK_SUBSCRIPTION".equals(row.get("kind"))).findFirst().get();
        if (((Number) sampledTiming.get("timedCalls")).longValue() != 2
                || ((Number) sampledTiming.get("timingAttempts")).longValue() != 8) {
            throw new AssertionError("sample interval/reset did not cross agent bridge");
        }
        HookDispatcher.configureTiming("counts", 64);
        long[] costs = new long[1000];
        for (int index = 0; index < 200; index++) {
            int ignored = (int) call.invokeExact(index);
        }
        for (int index = 0; index < costs.length; index++) {
            long start = System.nanoTime();
            int ignored = (int) call.invokeExact(index);
            costs[index] = System.nanoTime() - start;
        }
        Arrays.sort(costs);
        subscription.close();
        subscription.close();
        if ((int) call.invokeExact(1) != 2) {
            throw new AssertionError("child subscription cleanup left an active callback");
        }
        HookFileCollectionContext.Registration around = HookDeclarations.evaluate("bootstrap", "child.lua",
                "local r=betamoon.hooks:createRegistry(); r:register({id='child_wrapper',"
                        + "target={class='fixture.hooks.ChildHookFixture',method='call',descriptor='(I)I'},"
                        + "at='around',effects='edit'},function(call) local value=call:proceed(); call:returnValue(value+5) end)",
                2, globals, new HookDeclarations.Budget()).get(0);
        HookDispatcher.Subscription wrapped = HookDispatcher.subscribe(site, target, around.definition,
                invocation -> around.callback.call(new Call(invocation)));
        HookDispatcher.activate(Collections.singletonList(wrapped));
        if ((int) call.invokeExact(1) != 7) {
            throw new AssertionError("around continuation failed across independent LuaJ/dispatcher loaders");
        }
        Object originalFailure = target.getField("FAILURE").get(null);
        try {
            int ignored = (int) call.invokeExact(-1);
            throw new AssertionError("child around lost the native exception");
        } catch (IllegalStateException expected) {
            if (expected != originalFailure) {
                throw new AssertionError("child around changed original Throwable identity", expected);
            }
        }
        long[] aroundCosts = new long[1000];
        for (int index = 0; index < 200; index++) {
            int ignored = (int) call.invokeExact(index);
        }
        for (int index = 0; index < aroundCosts.length; index++) {
            long began = System.nanoTime();
            int ignored = (int) call.invokeExact(index);
            aroundCosts[index] = System.nanoTime() - began;
        }
        Arrays.sort(aroundCosts);
        System.out.println("Child-first around Lua continuation ns p50=" + aroundCosts[500] + ", p95="
                + aroundCosts[950] + ", p99=" + aroundCosts[990] + "; informational Java 8 fixture.");
        wrapped.close();
        HookFileCollectionContext.Registration operation = HookDeclarations.evaluate("bootstrap", "child.lua",
                "local r=betamoon.hooks:createRegistry(); r:register({id='child_point',"
                        + "target={class='fixture.hooks.ChildHookFixture',method='call',descriptor='(I)I'},"
                        + "at={kind='field_read',field={class='fixture.hooks.ChildHookFixture',field='bodies',"
                        + "descriptor='I'},ordinal=0,matches={exact=1}}},function() hits=hits+1 end)",
                3, globals, new HookDeclarations.Budget()).get(0);
        int previousHits = globals.get("hits").toint();
        int previousBodies = target.getField("bodies").getInt(null);
        HookDispatcher.Subscription selected = HookDispatcher.subscribe(site, target, operation.definition,
                invocation -> {
                    HookOperationInvocation record = invocation.operation();
                    if (record == null || !invocation.descriptor().equals("()I") || invocation.receiver() != null
                            || record.enclosingArgumentCount() != 1 || !record.enclosingArgument(0).equals(1)
                            || !invocation.result().equals(previousBodies)) {
                        throw new AssertionError("operation operands/enclosing data did not cross the JDK-only bridge");
                    }
                    operation.callback.call();
                });
        HookDispatcher.activate(Collections.singletonList(selected));
        if ((int) call.invokeExact(1) != 2 || globals.get("hits").toint() != previousHits + 1) {
            throw new AssertionError("independent child operation callback changed native outcome or failed");
        }
        selected.close();
        HookDispatcher.Subscription profiled = HookDispatcher.subscribe(site, target, operation.definition,
                invocation -> operation.callback.call());
        HookDispatcher.activate(Collections.singletonList(profiled));
        long[] operationCosts = new long[1000];
        for (int index = 0; index < 2000; index++) {
            int ignored = (int) call.invokeExact(1);
        }
        for (int index = 0; index < operationCosts.length; index++) {
            long began = System.nanoTime();
            int ignored = (int) call.invokeExact(1);
            operationCosts[index] = System.nanoTime() - began;
        }
        Arrays.sort(operationCosts);
        profiled.close();
        System.out.println("Child-first operation Lua callback ns p50=" + operationCosts[500] + ", p95="
                + operationCosts[950] + ", p99=" + operationCosts[990] + "; informational Java 8 fixture.");
        HookFileCollectionContext.Registration constructed = HookDeclarations.evaluate("bootstrap", "child.lua",
                "local r=betamoon.hooks:createRegistry(); r:register({id='constructed_child',"
                        + "target={class='fixture.hooks.ChildHookFixture',constructor='()V'},at='constructed'},"
                        + "function() hits=hits+1 end)",
                4, globals, new HookDeclarations.Budget()).get(0);
        Object[] observed = new Object[1];
        HookDispatcher.Subscription constructor = HookDispatcher.subscribe(
                preparation.targets(constructed.definition).get(0).id, target, constructed.definition, invocation -> {
                    if (!invocation.definition.at.equals("constructed") || invocation.argumentCount() != 0
                            || invocation.result() != null || invocation.exception() != null) {
                        throw new AssertionError("constructor observation identity was lost across loaders");
                    }
                    observed[0] = invocation.receiver();
                    constructed.callback.call();
                });
        HookDispatcher.activate(Collections.singletonList(constructor));
        Object created = target.newInstance();
        if (observed[0] != created) {
            throw new AssertionError("child constructor callback did not receive the native instance");
        }
        constructor.close();
        long checksum = 0;
        for (int index = 0; index < 30000; index++) {
            checksum += (int) call.invokeExact(index);
        }
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!memory.isThreadAllocatedMemorySupported()) {
            throw new AssertionError("allocation verification requires the supported Java8 HotSpot test VM");
        }
        memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        memory.getThreadAllocatedBytes(thread);
        long start = memory.getThreadAllocatedBytes(thread);
        for (int index = 0; index < 100000; index++) {
            checksum += (int) call.invokeExact(index);
        }
        long allocated = memory.getThreadAllocatedBytes(thread) - start;
        if (allocated != 0 || checksum == 0) {
            throw new AssertionError("inactive child bridge allocated " + allocated + " bytes");
        }
        for (int cycle = 0; cycle < 100; cycle++) {
            HookDispatcher.Subscription repeated = HookDispatcher.subscribe(site, target, registration.definition,
                    invocation -> registration.callback.call(new Call(invocation)));
            try {
                HookDispatcher.activate(Collections.singletonList(repeated));
                if ((int) call.invokeExact(1) != 42) {
                    throw new AssertionError("repeated hook activation lost its callback");
                }
            } finally {
                repeated.close();
            }
            if ((int) call.invokeExact(1) != 2
                    || preparation.status().stream().anyMatch(row -> "HOOK_SUBSCRIPTION".equals(row.get("kind")))) {
                throw new AssertionError("repeated hook cleanup retained a callback subscription");
            }
        }
        System.out.println("Child-first hook bridge passed; inactive 100000 calls allocated " + allocated
                + " bytes; 100 activation/cleanup cycles; active Lua short circuit ns p50=" + costs[500] + ", p95="
                + costs[950] + ", p99=" + costs[990]);
    }

    private static final class Call extends HookLuaValue {
        Call(HookInvocation invocation) {
            properties.put("proceed",
                    method(args -> LuaValue.valueOf((Integer) invocation.proceed(invocation.argumentValues()))));
            properties.put("returnValue", method(args -> {
                invocation.result(args.checkint(1));
                return LuaValue.NONE;
            }));
        }
    }
}
