package betamoon.luamodloader;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookInvocation;
import betamoon.interop.binding.NativeAccessFailure;
import java.util.Arrays;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.function.Supplier;

/**
 * Owns only this hook's observation frame; caller restrictions remain installed
 * during continuation.
 */
final class HookCallbackObservation implements AutoCloseable {
    private final String owner;
    private final HookInvocation invocation;
    private final NativeContext callerContext;
    private final String caller;
    private final long generation;
    private LuaCallbackScope callback;
    private NativeContext context;
    private RuntimeException unavailable;

    HookCallbackObservation(String owner, String caller, HookInvocation invocation, NativeContext callerContext) {
        this.owner = owner;
        this.invocation = invocation;
        this.callerContext = callerContext;
        this.caller = caller;
        generation = ScriptResourceTracker.ownerGeneration(owner);
        open();
    }

    NativeContext context() {
        try {
            if (unavailable != null) {
                throw unavailable;
            }
            if (callerContext != null) {
                ScriptExecution.withOwner(caller, () -> {
                    callerContext.check();
                    return null;
                });
            }
            context.check();
            return context;
        } catch (NativeAccessFailure failure) {
            if (invocation.definition.at.equals("around") && invocation.hasContinued()
                    && Arrays
                            .asList("stale_environment", "stale_owner", "native_session_closed", "expired_scope",
                                    "stale_world", "stale_player", "wrong_owner", "inactive_scope")
                            .contains(failure.reason)) {
                invocation.invalidateObservation();
            }
            throw failure;
        } catch (HookFailure failure) {
            if (invocation.definition.at.equals("around") && invocation.hasContinued()
                    && failure.reason.equals("stale_owner")) {
                invocation.invalidateObservation();
            }
            throw failure;
        }
    }

    Object continueWith(Supplier<Object> continuation) {
        context();
        close();
        try {
            return ScriptExecution.withOwner(caller, continuation);
        } finally {
            // Always reinstall read-only guards before Lua resumes, including invalidated
            // generations.
            open();
        }
    }

    private void open() {
        callback = new LuaCallbackScope(false, false);
        unavailable = null;
        try {
            if (ScriptResourceTracker.ownerGeneration(owner) != generation) {
                throw new HookFailure("stale_owner", "around owner changed during continuation");
            }
            NativeEntry entry = NativeEventAccess.interfaceEntry(callback);
            if (entry == null) {
                throw new HookFailure("owner_not_ready", "native client context is unavailable");
            }
            context = entry.asHook().open(owner);
            if (ScriptResourceTracker.ownerGeneration(owner) != generation) {
                context.close();
                context = null;
                throw new HookFailure("stale_owner", "around owner changed while opening observation");
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            unavailable = failure instanceof RuntimeException
                    ? (RuntimeException) failure
                    : new HookFailure("owner_not_ready", "hook observation could not be opened", failure);
            context = NativeContext.denied(owner);
        } finally {
            if (context == null) {
                callback.close();
                callback = null;
            }
        }
    }

    @Override
    public void close() {
        try {
            if (context != null) {
                context.close();
                context = null;
            }
        } finally {
            if (callback != null) {
                callback.close();
                callback = null;
            }
        }
    }
}
