package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;

/**
 * Stable generation cells invalidate old scopes immediately, including
 * publication from another thread.
 */
final class NativeOwnerGenerationTest {
    static void run(NativeBindings bindings) {
        String owner = "native-generation-performance";
        ScriptResourceTracker.OwnerGeneration token = ScriptResourceTracker.ownerToken(owner);
        long generation = token.current();
        ScriptExecution.withOwner(owner, () -> {
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, owner, callback, true, Collections.emptyMap(),
                            () -> true)) {
                context.check();
                Thread unload = new Thread(() -> ScriptResourceTracker.unload(owner));
                unload.start();
                try {
                    unload.join();
                } catch (InterruptedException failure) {
                    throw new AssertionError(failure);
                }
                if (token.matches(generation) || ScriptResourceTracker.ownerToken(owner) != token) {
                    throw new AssertionError("owner generation cell replaced or not invalidated");
                }
                expectStale(context);
                try (LuaCallbackScope childScope = new LuaCallbackScope(true);
                        NativeContext child = NativeContext.open(bindings, owner, childScope, true,
                                Collections.emptyMap(), () -> true)) {
                    expectStale(child);
                }
            }
            try (LuaCallbackScope callback = new LuaCallbackScope(true);
                    NativeContext fresh = NativeContext.open(bindings, owner, callback, true, Collections.emptyMap(),
                            () -> true)) {
                fresh.check();
            }
            return null;
        });
        System.out
                .println("Owner cells passed: cross-thread invalidation, stale ancestors and fresh-generation entry.");
    }

    private static void expectStale(NativeContext context) {
        try {
            context.check();
            throw new AssertionError("invalidated owner scope was accepted");
        } catch (NativeAccessFailure failure) {
            if (!failure.reason.equals("stale_owner")) {
                throw failure;
            }
        }
    }
}
