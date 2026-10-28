package betamoon.hooks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Type;

/**
 * Prepared type structure, associated with the actual declaring class rather
 * than its binary name.
 */
final class HookSignature {
    private static final ClassValue<Map<String, HookSignature>> SIGNATURES = new ClassValue<Map<String, HookSignature>>() {
        @Override
        protected Map<String, HookSignature> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    final String descriptor;
    final List<Type> parameters;
    final Type result;
    private final ClassLoader loader;
    private final Map<Type, Class<?>> resolved = new ConcurrentHashMap<>();

    private HookSignature(Class<?> anchor, String descriptor) {
        this.descriptor = descriptor;
        loader = anchor.getClassLoader();
        parameters = Collections.unmodifiableList(Arrays.asList(Type.getArgumentTypes(descriptor)));
        result = Type.getReturnType(descriptor);
    }

    static HookSignature forSite(Class<?> anchor, String site) {
        return cached(anchor, site, true);
    }

    static HookSignature get(Class<?> anchor, String descriptor) {
        return cached(anchor, descriptor, false);
    }

    private static HookSignature cached(Class<?> anchor, String key, boolean site) {
        Map<String, HookSignature> cache = SIGNATURES.get(anchor);
        HookSignature existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (cache) {
            HookSignature found = cache.get(key);
            if (found == null) {
                if (cache.size() >= 512) {
                    cache.remove(cache.keySet().iterator().next());
                }
                found = new HookSignature(anchor, site ? key.substring(key.indexOf('(')) : key);
                cache.put(key, found);
            }
            return found;
        }
    }

    Class<?> valueClass(Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                return Void.TYPE;
            case Type.BOOLEAN:
                return Boolean.TYPE;
            case Type.BYTE:
                return Byte.TYPE;
            case Type.SHORT:
                return Short.TYPE;
            case Type.CHAR:
                return Character.TYPE;
            case Type.INT:
                return Integer.TYPE;
            case Type.LONG:
                return Long.TYPE;
            case Type.FLOAT:
                return Float.TYPE;
            case Type.DOUBLE:
                return Double.TYPE;
            default:
                Class<?> found = resolved.get(type);
                if (found == null) {
                    try {
                        found = Class.forName(type.getSort() == Type.ARRAY
                                ? type.getDescriptor().replace('/', '.')
                                : type.getClassName(), false, loader);
                    } catch (ClassNotFoundException failure) {
                        throw new HookFailure("missing_dependency", "hook value type " + type, failure);
                    }
                    if (resolved.size() >= 256) {
                        resolved.remove(resolved.keySet().iterator().next());
                    }
                    resolved.put(type, found);
                }
                return found;
        }
    }

    void validate(Type type, Object value) {
        if (type.getSort() == Type.VOID) {
            if (value != null) {
                throw new HookFailure("hook_invalid_value", "void results must have no value");
            }
            return;
        }
        if (value == null && type.getSort() >= Type.ARRAY) {
            return;
        }
        Class<?> expected;
        switch (type.getSort()) {
            case Type.BOOLEAN:
                expected = Boolean.class;
                break;
            case Type.BYTE:
                expected = Byte.class;
                break;
            case Type.SHORT:
                expected = Short.class;
                break;
            case Type.CHAR:
                expected = Character.class;
                break;
            case Type.INT:
                expected = Integer.class;
                break;
            case Type.LONG:
                expected = Long.class;
                break;
            case Type.FLOAT:
                expected = Float.class;
                break;
            case Type.DOUBLE:
                expected = Double.class;
                break;
            default:
                expected = valueClass(type);
        }
        if (!expected.isInstance(value)) {
            throw new HookFailure("hook_invalid_value", "expected exact bridge value for " + type.getClassName());
        }
    }
}
