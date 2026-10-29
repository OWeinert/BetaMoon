package betamoon.interop.interfaces;

import betamoon.interop.binding.NativeAccessFailure;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Type;

/**
 * Validates actual JVM identities; indexed metadata alone cannot authorize a
 * proxy.
 */
public final class NativeInterfaceContract {
    public final List<Class<?>> interfaces;
    public final Map<String, Method> methods;
    public final ClassLoader loader;

    public NativeInterfaceContract(List<Class<?>> requested) {
        if (requested.isEmpty() || requested.size() > 8) {
            throw new NativeAccessFailure("invalid_native_interfaces", "supply one to eight interfaces");
        }
        Set<Class<?>> distinct = new LinkedHashSet<>();
        Map<String, Method> found = new LinkedHashMap<>();
        Map<String, Class<?>> returns = new LinkedHashMap<>();
        for (Class<?> type : requested) {
            if (!type.isInterface() || !publicType(type) || !distinct.add(type)) {
                throw new NativeAccessFailure("invalid_native_interfaces", "use distinct public interfaces");
            }
            for (Method method : inspectMethods(type)) {
                if (Modifier.isStatic(method.getModifiers()) || objectMethod(method)) {
                    continue;
                }
                String descriptor = Type.getMethodDescriptor(method);
                String signature = method.getName() + descriptor.substring(0, descriptor.indexOf(')') + 1);
                Class<?> previous = returns.get(signature);
                Class<?> result = method.getReturnType();
                if (previous != null && previous != result && (previous.isPrimitive() || result.isPrimitive()
                        || !previous.isAssignableFrom(result) && !result.isAssignableFrom(previous))) {
                    throw new NativeAccessFailure("incompatible_native_interfaces", signature);
                }
                returns.put(signature, previous == null || previous.isAssignableFrom(result) ? result : previous);
                found.putIfAbsent(key(method), method);
            }
        }
        if (found.size() > 256) {
            throw new NativeAccessFailure("native_interface_limit", "interface contract exceeds 256 methods");
        }
        interfaces = Collections.unmodifiableList(new ArrayList<>(distinct));
        methods = Collections.unmodifiableMap(found);
        loader = visibleLoader(interfaces);
    }

    public String select(Method method) {
        String key = key(method);
        if (!method.getDeclaringClass().isInterface() || !methods.containsKey(key)
                || interfaces.stream().noneMatch(type -> method.getDeclaringClass().isAssignableFrom(type))) {
            throw new NativeAccessFailure("invalid_interface_method", method.toString());
        }
        return key;
    }

    public static String key(Method method) {
        return method.getName() + Type.getMethodDescriptor(method);
    }

    public static boolean objectMethod(Method method) {
        String key = key(method);
        return key.equals("equals(Ljava/lang/Object;)Z") || key.equals("hashCode()I")
                || key.equals("toString()Ljava/lang/String;");
    }

    private static Method[] inspectMethods(Class<?> type) {
        try {
            return type.getMethods();
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("missing_dependency", type.getName(), exception);
        } catch (SecurityException exception) {
            throw new NativeAccessFailure("access_denied", type.getName(), exception);
        }
    }

    private static boolean publicType(Class<?> type) {
        for (Class<?> enclosing = type; enclosing != null; enclosing = enclosing.getEnclosingClass()) {
            if (!Modifier.isPublic(enclosing.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static ClassLoader visibleLoader(List<Class<?>> interfaces) {
        List<ClassLoader> candidates = new ArrayList<>();
        for (Class<?> type : interfaces) {
            candidates.add(type.getClassLoader());
        }
        candidates.add(NativeInterfaceContract.class.getClassLoader());
        for (ClassLoader loader : candidates) {
            boolean visible = true;
            for (Class<?> type : interfaces) {
                try {
                    visible &= Class.forName(type.getName(), false, loader) == type;
                } catch (ClassNotFoundException | LinkageError exception) {
                    visible = false;
                }
            }
            if (visible) {
                return loader;
            }
        }
        throw new NativeAccessFailure("interface_loader_visibility", "no existing loader sees every exact interface");
    }
}
