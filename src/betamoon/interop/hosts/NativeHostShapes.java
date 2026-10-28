package betamoon.interop.hosts;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.cache.NativeLookupCache;
import java.lang.ref.WeakReference;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.reference.NativeMemberRef;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.Type;

/**
 * Bounded shapes associated with actual base Class identity, reusable across
 * owner callback reloads.
 */
public final class NativeHostShapes {
    private static final ClassValue<ShapeCache> SHAPES = new ClassValue<ShapeCache>() {
        @Override
        protected ShapeCache computeValue(Class<?> type) {
            return new ShapeCache();
        }
    };
    private final NativeBindings bindings;
    private final Map<String, WeakReference<NativeHostShape>> validated = new NativeLookupCache<>(64);
    private long validatedRevision = -1;
    private final Set<String> admitted = new HashSet<>();
    private final Set<String> attempted = new HashSet<>();

    public NativeHostShapes(NativeBindings bindings) {
        this.bindings = bindings;
    }

    public NativeHostShape resolve(NativeHostType type, NativeContext context) {
        context.prepare();
        if (context.bindings != bindings) {
            throw invalid("host shape belongs to another native session");
        }
        long observed = bindings.bindingRevision();
        if (validatedRevision != observed) {
            validated.clear();
            validatedRevision = observed;
        }
        WeakReference<NativeHostShape> previous = validated.get(type.key());
        NativeHostShape prepared = previous == null ? null : previous.get();
        if (prepared != null) {
            return prepared;
        }
        Class<?> base = bindings.type(type.base, context);
        if (base.isInterface() || Modifier.isFinal(base.getModifiers()) || !publicType(base)) {
            throw invalid("base must be a public non-final class");
        }
        NativeMemberPlan selectedConstructor = bindings.exact(type.constructor, context, true);
        Constructor<?> constructor = (Constructor<?>) selectedConstructor.member;
        int access = constructor.getModifiers();
        if (constructor.getDeclaringClass() != base || !Modifier.isPublic(access) && !Modifier.isProtected(access)) {
            throw invalid("constructor must be public/protected and declared by the actual base");
        }
        int slots = 3; // Generated constructor adds its JDK dispatch/gate arguments plus this.
        for (Class<?> parameter : constructor.getParameterTypes()) {
            visible(base, parameter);
            slots += parameter == Long.TYPE || parameter == Double.TYPE ? 2 : 1;
        }
        if (slots > 255) {
            throw invalid("constructor has no JVM parameter slot for its dispatch record");
        }
        Map<String, Method> inherited = inherited(base);
        TreeMap<String, Method> selected = new TreeMap<>();
        for (NativeMemberRef reference : type.overrides) {
            Method requested = (Method) bindings.exact(reference, context, true).member;
            if (!requested.getDeclaringClass().isAssignableFrom(base)) {
                throw invalid("override is not a member of the actual base hierarchy");
            }
            validateOverride(requested);
            Method actual = inherited.get(key(requested));
            if (actual == null) {
                throw invalid("selected method is not an inherited virtual member");
            }
            validateOverride(actual);
            visible(base, actual.getReturnType());
            for (Class<?> parameter : actual.getParameterTypes()) {
                visible(base, parameter);
            }
            if (selected.put(key(actual), actual) != null) {
                throw invalid("duplicate resolved override");
            }
        }
        validateErasedOverrides(selected, inherited);
        validateOpaqueAbstracts(base);
        Map<String, Method> bridges = new TreeMap<>();
        for (Method method : inherited.values()) {
            if (!Modifier.isAbstract(method.getModifiers()) || selected.containsKey(key(method))) {
                continue;
            }
            Method implementation = null;
            for (Method candidate : selected.values()) {
                if (signature(method).equals(signature(candidate)) && !method.getReturnType().isPrimitive()
                        && method.getReturnType().isAssignableFrom(candidate.getReturnType())) {
                    if (implementation != null
                            && !implementation.getReturnType().isAssignableFrom(candidate.getReturnType())
                            && !candidate.getReturnType().isAssignableFrom(implementation.getReturnType())) {
                        throw invalid("incompatible covariant abstract returns");
                    }
                    if (implementation == null
                            || implementation.getReturnType().isAssignableFrom(candidate.getReturnType())) {
                        implementation = candidate;
                    }
                }
            }
            if (implementation == null
                    || !Modifier.isPublic(method.getModifiers()) && !Modifier.isProtected(method.getModifiers())) {
                throw new NativeAccessFailure("missing_native_host_method", method.toString());
            }
            visible(base, method.getReturnType());
            bridges.put(key(method), implementation);
        }
        String key = "class-host-2:" + Type.getConstructorDescriptor(constructor) + ":"
                + String.join(";", selected.keySet());
        String admission = bindings.reference(base).key() + ":" + key;
        if (!admitted.contains(admission) && admitted.size() >= 256) {
            throw new NativeAccessFailure("native_host_shape_limit", "session supports 256 distinct prepared shapes");
        }
        ShapeCache cache = SHAPES.get(base);
        synchronized (cache) {
            NativeHostShape shape = cache.shapes.get(key);
            if (shape == null) {
                if (cache.failures.containsKey(key)) {
                    throw new NativeAccessFailure("native_host_generation_failed", cache.failures.get(key));
                }
                if (cache.attempts >= 64 || !attempted.contains(admission) && attempted.size() >= 256) {
                    throw new NativeAccessFailure("native_host_shape_limit",
                            "base supports 64 generated shapes before restart");
                }
                cache.attempts++;
                attempted.add(admission);
                String fingerprint = CatalogFingerprint
                        .sha256((base.getName() + ":" + key).getBytes(StandardCharsets.UTF_8));
                try {
                    shape = NativeHostBytecode.generate(base, constructor, new ArrayList<>(selected.values()),
                            inherited, bridges, fingerprint);
                } catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
                    cache.failures.put(key,
                            "generation refused for " + base.getName() + ": " + failure.getClass().getName());
                    throw new NativeAccessFailure("native_host_generation_failed", base.getName(), failure);
                }
                cache.shapes.put(key, shape);
            }
            admitted.add(admission);
            if (validatedRevision != bindings.bindingRevision()) {
                validated.clear();
            }
            validatedRevision = bindings.bindingRevision();
            validated.put(type.key(), new WeakReference<>(shape));
            return shape;
        }
    }

    public int preparedCount() {
        return admitted.size();
    }

    /** Eviction/refresh never resets session generation admission. */
    public void clear() {
        validated.clear();
    }

    public void close() {
        clear();
        admitted.clear();
        attempted.clear();
    }

    static String key(Method method) {
        return method.getName() + Type.getMethodDescriptor(method);
    }

    private static String signature(Method method) {
        String descriptor = Type.getMethodDescriptor(method);
        return method.getName() + descriptor.substring(0, descriptor.indexOf(')') + 1);
    }

    private static void validateErasedOverrides(Map<String, Method> selected, Map<String, Method> inherited) {
        Map<String, Method> signatures = new LinkedHashMap<>();
        for (Method method : selected.values()) {
            if (signatures.put(signature(method), method) != null) {
                throw invalid("choose one most-specific return descriptor; covariant bridges are generated");
            }
            for (Method parent : inherited.values()) {
                if (signature(parent).equals(signature(method)) && Modifier.isFinal(parent.getModifiers())
                        && (Modifier.isPublic(parent.getModifiers()) || Modifier.isProtected(parent.getModifiers()))) {
                    throw invalid("a final virtual signature cannot be overridden with another return descriptor");
                }
            }
        }
    }

    private static void validateOpaqueAbstracts(Class<?> base) {
        for (Class<?> owner = base; owner != null; owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                int flags = method.getModifiers();
                if (!Modifier.isAbstract(flags) || Modifier.isPublic(flags) || Modifier.isProtected(flags)
                        || Modifier.isPrivate(flags)) {
                    continue;
                }
                boolean implemented = false;
                boolean resolved = false;
                for (Class<?> candidate = base; candidate != owner; candidate = candidate.getSuperclass()) {
                    if (candidate.getClassLoader() != owner.getClassLoader()
                            || !packageName(candidate).equals(packageName(owner))) {
                        continue;
                    }
                    for (Method actual : candidate.getDeclaredMethods()) {
                        if (key(actual).equals(key(method)) && !Modifier.isPrivate(actual.getModifiers())
                                && !Modifier.isStatic(actual.getModifiers())) {
                            int actualFlags = actual.getModifiers();
                            implemented = !Modifier.isAbstract(actualFlags) || Modifier.isPublic(actualFlags)
                                    || Modifier.isProtected(actualFlags);
                            resolved = true;
                            break;
                        }
                    }
                    if (resolved) {
                        break;
                    }
                }
                if (!implemented) {
                    throw new NativeAccessFailure("missing_native_host_method",
                            "abstract runtime-package member is inaccessible: " + method.getName());
                }
            }
        }
    }

    private static String packageName(Class<?> type) {
        String name = type.getName();
        return name.substring(0, Math.max(0, name.lastIndexOf('.')));
    }

    private static void validateOverride(Method method) {
        int flags = method.getModifiers();
        if (!Modifier.isPublic(flags) && !Modifier.isProtected(flags) || Modifier.isStatic(flags)
                || Modifier.isFinal(flags) || Modifier.isPrivate(flags) || Modifier.isNative(flags)
                || Modifier.isSynchronized(flags) || method.getName().startsWith("betamoon$host$")) {
            throw invalid("override must be public/protected, non-final, non-static, non-native and non-synchronized: "
                    + method.getName());
        }
    }

    private static Map<String, Method> inherited(Class<?> base) {
        Map<String, Method> methods = new LinkedHashMap<>();
        int count = 0;
        for (Class<?> current = base; current != null; current = current.getSuperclass()) {
            if (++count > 128) {
                throw invalid("base hierarchy exceeds 128 classes");
            }
            for (Method method : current.getDeclaredMethods()) {
                if (!Modifier.isPrivate(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())) {
                    methods.putIfAbsent(key(method), method);
                }
            }
        }
        for (Method method : base.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                methods.putIfAbsent(key(method), method);
            }
        }
        if (methods.size() > 4096) {
            throw invalid("base virtual method inventory exceeds 4096");
        }
        return methods;
    }

    private static boolean publicType(Class<?> type) {
        if (type.isArray()) {
            return publicType(type.getComponentType());
        }
        for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
            if (!current.isPrimitive() && !Modifier.isPublic(current.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static void visible(Class<?> base, Class<?> type) {
        if (!publicType(type)) {
            throw invalid("generated child loader cannot use a nonpublic signature type: " + type.getName());
        }
        if (type.isPrimitive()) {
            return;
        }
        try {
            if (Class.forName(type.getName(), false, base.getClassLoader()) != type) {
                throw invalid("base loader sees a different signature type identity: " + type.getName());
            }
        } catch (ClassNotFoundException failure) {
            throw new NativeAccessFailure("native_host_loader_visibility", type.getName(), failure);
        }
    }

    private static final class ShapeCache {
        private final Map<String, NativeHostShape> shapes = new LinkedHashMap<>();
        private final Map<String, String> failures = new LinkedHashMap<>();
        private int attempts;
    }

    private static NativeAccessFailure invalid(String message) {
        return new NativeAccessFailure("invalid_native_host_type", message);
    }
}
