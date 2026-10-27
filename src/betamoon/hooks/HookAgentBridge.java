package betamoon.hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * JDK-only ABI between a launcher's BetaMoon copy and the system agent. Exact
 * primitive handles keep the inactive path free of reflective argument arrays
 * and boxing. No game or Lua class crosses this ABI.
 */
final class HookAgentBridge {
    static final String PROTOCOL = "betamoon-hooks-jdk-abi-2";
    private static String unavailableReason;
    private static String unavailableMessage;
    private static final HookAgentBridge REMOTE = discover();
    final MethodHandle active;
    final MethodHandle before;
    final MethodHandle after;
    final MethodHandle error;
    final MethodHandle clientThread;
    final MethodHandle subscribe;
    final MethodHandle activate;
    final MethodHandle status;
    final MethodHandle plan;
    final MethodHandle revision;
    final MethodHandle diagnostics;
    final MethodHandle states;
    final MethodHandle targetLoader;

    private HookAgentBridge(Class<?> dispatcher, Class<?> preparation) throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        active = lookup.findStatic(dispatcher, "active",
                MethodType.methodType(boolean.class, int.class, int.class, Class.class));
        before = lookup.findStatic(dispatcher, "beforeTransport",
                MethodType.methodType(Object[].class, int.class, Class.class, Object.class, Object[].class));
        after = lookup.findStatic(dispatcher, "after", MethodType.methodType(Object.class, int.class, Class.class,
                Object.class, Object[].class, Object.class));
        error = lookup.findStatic(dispatcher, "error", MethodType.methodType(void.class, int.class, Class.class,
                Object.class, Object[].class, Throwable.class));
        clientThread = lookup.findStatic(dispatcher, "clientThread", MethodType.methodType(void.class));
        subscribe = lookup.findStatic(dispatcher, "subscribeTransport",
                MethodType.methodType(AutoCloseable.class, int.class, Class.class, Map.class, Function.class));
        activate = lookup.findStatic(dispatcher, "activateTransport", MethodType.methodType(void.class, List.class));
        status = lookup.findStatic(preparation, "statusTransport", MethodType.methodType(List.class));
        plan = lookup.findStatic(preparation, "planTransport", MethodType.methodType(Map.class));
        revision = lookup.findStatic(preparation, "revisionTransport", MethodType.methodType(long.class));
        diagnostics = lookup.findStatic(preparation, "diagnoseTransport",
                MethodType.methodType(void.class, String.class, String.class, String.class, String.class));
        states = lookup.findStatic(preparation, "statesTransport", MethodType.methodType(List.class));
        targetLoader = lookup.findStatic(dispatcher, "targetLoader",
                MethodType.methodType(ClassLoader.class, int.class));
    }

    static HookAgentBridge remote() {
        return REMOTE;
    }

    static String unavailableReason() {
        return unavailableReason;
    }

    static String unavailableMessage() {
        return unavailableMessage;
    }

    private static HookAgentBridge discover() {
        try {
            ClassLoader system = ClassLoader.getSystemClassLoader();
            Class<?> dispatcher = Class.forName(HookDispatcher.class.getName(), false, system);
            if (dispatcher == HookDispatcher.class) {
                return null;
            }
            String protocol = (String) dispatcher.getMethod("protocol").invoke(null);
            // Use the constant: invoking the child dispatcher here can initialize AGENT
            // before REMOTE is published and permanently disconnect lazy target loaders.
            if (!protocol.equals(PROTOCOL)) {
                throw new HookFailure("dispatcher_not_visible", "agent and mod hook ABI versions differ");
            }
            Class<?> preparation = Class.forName(HookPreparation.class.getName(), false, system);
            return new HookAgentBridge(dispatcher, preparation);
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | HookFailure | LinkageError error) {
            Throwable cause = error;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof VirtualMachineError) {
                    throw (VirtualMachineError) cause;
                }
                if (cause instanceof ThreadDeath) {
                    throw (ThreadDeath) cause;
                }
            }
            unavailableReason = "dispatcher_not_visible";
            unavailableMessage = "Cannot link the system hook agent: " + error.getMessage();
            return null;
        }
    }

    static RuntimeException failure(Throwable error) {
        if (error instanceof Error) {
            throw (Error) error;
        }
        return error instanceof RuntimeException
                ? (RuntimeException) error
                : new HookFailure("agent_unavailable", "hook agent transport failed", error);
    }
}
