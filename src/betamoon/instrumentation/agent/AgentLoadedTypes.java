package betamoon.instrumentation.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

/** Internal read-only census bridge. Instrumentation and its Class array never enter the Lua API. */
public final class AgentLoadedTypes {
    private static volatile Instrumentation instrumentation;

    private AgentLoadedTypes() {
    }

    static void attach(Instrumentation value) {
        instrumentation = value;
    }

    public static boolean visit(Consumer<Class<?>> consumer) {
        Instrumentation current = instrumentation;
        if (current == null) {
            return visitSystemAgent(consumer);
        }
        Class<?>[] loaded = current.getAllLoadedClasses();
        int limit = Math.min(loaded.length, 100000);
        for (int i = 0; i < limit; i++) {
            consumer.accept(loaded[i]);
        }
        return loaded.length <= limit;
    }

    private static boolean visitSystemAgent(Consumer<Class<?>> consumer) {
        try {
            // Game loaders can have their own copy of this class; the agent's copy owns Instrumentation.
            Class<?> shared = Class.forName(AgentLoadedTypes.class.getName(), false, ClassLoader.getSystemClassLoader());
            if (shared != AgentLoadedTypes.class) {
                return Boolean.TRUE.equals(shared.getMethod("visit", Consumer.class).invoke(null, consumer));
            }
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof VirtualMachineError) {
                throw (VirtualMachineError) exception.getCause();
            }
        } catch (ReflectiveOperationException | SecurityException exception) {
            // An unavailable optional census leaves loaded state unknown.
        }
        return false;
    }
}
