package betamoon.interop.hosts;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Actual defining identities and generated Java shape; never stores Lua
 * callbacks or roots.
 */
public final class NativeHostShape {
    public final Class<?> base;
    public final Constructor<?> baseConstructor;
    public final Class<?> generated;
    public final List<Method> methods;
    public final String fingerprint;
    private final Constructor<?> factory;
    private final List<Method> supers;

    NativeHostShape(Class<?> base, Constructor<?> constructor, Class<?> generated, List<Method> methods,
            String fingerprint, String helperPrefix) throws NoSuchMethodException {
        this.base = base;
        this.baseConstructor = constructor;
        this.generated = generated;
        this.methods = Collections.unmodifiableList(new ArrayList<>(methods));
        this.fingerprint = fingerprint;
        Class<?>[] parameters = new Class<?>[constructor.getParameterCount() + 1];
        parameters[0] = Function.class;
        System.arraycopy(constructor.getParameterTypes(), 0, parameters, 1, parameters.length - 1);
        factory = generated.getConstructor(parameters);
        List<Method> helpers = new ArrayList<>();
        for (int index = 0; index < methods.size(); index++) {
            Method method = methods.get(index);
            helpers.add(Modifier.isAbstract(method.getModifiers())
                    ? null
                    : generated.getMethod(helperPrefix + index, method.getParameterTypes()));
        }
        supers = Collections.unmodifiableList(helpers);
    }

    public Object construct(Function<Object[], Object> dispatch, Object[] arguments) {
        if (arguments.length != baseConstructor.getParameterCount()) {
            throw new NativeAccessFailure("invalid_native_host_arguments",
                    "supply the exact constructor parameter list");
        }
        Object[] input = new Object[arguments.length + 1];
        input[0] = dispatch;
        System.arraycopy(arguments, 0, input, 1, arguments.length);
        try {
            return factory.newInstance(input);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            if (cause instanceof ThreadDeath) {
                throw (ThreadDeath) cause;
            }
            String reason = cause.getClass() == IllegalStateException.class && cause.getMessage() != null
                    && cause.getMessage().startsWith("unsupported_native_host_construction:")
                            ? "unsupported_native_host_construction"
                            : "native_host_construction_failed";
            throw new NativeAccessFailure(reason, base.getName(), cause);
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError failure) {
            throw new NativeAccessFailure("native_host_construction_failed", base.getName(), failure);
        }
    }

    public boolean clientScreen(NativeBindings bindings) {
        return baseConstructor.getParameterCount() == 0
                && bindings.names.curatedClass(base, "net/minecraft/src/GuiScreen");
    }

    public boolean hasSuper(int index) {
        return supers.get(index) != null;
    }

    public Object callSuper(int index, Object receiver, Object[] arguments) {
        requireInstance(receiver);
        Method helper = supers.get(index);
        if (helper == null) {
            throw new NativeAccessFailure("native_host_super_unavailable", methods.get(index).getName());
        }
        try {
            return helper.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            if (cause instanceof ThreadDeath) {
                throw (ThreadDeath) cause;
            }
            throw new NativeAccessFailure("native_exception", "native host super body failed", cause);
        } catch (ReflectiveOperationException | IllegalArgumentException failure) {
            throw new NativeAccessFailure("native_host_super_failed", helper.getName(), failure);
        }
    }

    public void requireInstance(Object value) {
        if (value == null || value.getClass() != generated) {
            throw new NativeAccessFailure("invalid_native_host_instance", "use this exact generated class identity");
        }
    }
}
