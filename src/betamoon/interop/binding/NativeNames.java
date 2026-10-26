package betamoon.interop.binding;

import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.JvmDescriptors;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Type;

/**
 * Presence-aware mapping followed by actual reflection validation, including
 * virtual mod overrides.
 */
public final class NativeNames {
    private final TinyMappingSnapshot mappings;
    private final String namespace;
    private final Map<String, String> canonical = new HashMap<>();
    private final Map<String, Class<?>> curated;

    public NativeNames(TinyMappingSnapshot mappings, String namespace) {
        this(mappings, namespace, Collections.emptyMap());
    }

    public NativeNames(TinyMappingSnapshot mappings, String namespace, Map<String, Class<?>> curated) {
        this.mappings = mappings;
        this.namespace = namespace;
        this.curated = Collections.unmodifiableMap(new HashMap<>(curated));
        for (Map.Entry<String, TinyMappingSnapshot.ClassEntry> entry : mappings.getClasses().entrySet()) {
            String runtime = entry.getValue().getNames().get(namespace);
            if (runtime != null && !runtime.isEmpty()) {
                String previous = canonical.put(runtime, entry.getKey());
                if (previous != null && !previous.equals(entry.getKey())) {
                    throw new NativeAccessFailure("mapping_conflict", runtime);
                }
            }
        }
    }

    public boolean curatedClass(Class<?> type, String canonicalName) {
        return canonicalName.equals("java/lang/String") ? type == String.class : type == curated.get(canonicalName);
    }

    public String runtimeClass(String internal, String policy) {
        TinyMappingSnapshot.ClassEntry entry = mappings.getClasses().get(internal);
        if (policy.equals("runtime") || entry == null) {
            return internal;
        }
        String name = entry.getNames().get(namespace);
        if (name == null || name.isEmpty()) {
            throw new NativeAccessFailure("side_absent", internal + " in " + namespace);
        }
        return name;
    }

    public String canonicalClass(Class<?> type) {
        String internal = Type.getInternalName(type);
        return canonicalName(internal);
    }

    public String canonicalName(String internal) {
        return canonical.getOrDefault(internal, internal);
    }

    public String descriptor(String descriptor, String policy) {
        return JvmDescriptors.remap(descriptor, name -> runtimeClass(name, policy));
    }

    public boolean methodMatches(Method actual, String requested, String policy) {
        if (policy.equals("runtime")) {
            return actual.getName().equals(requested);
        }
        Set<String> names = memberNames(actual.getDeclaringClass(), requested, Type.getMethodDescriptor(actual), true,
                !Modifier.isPrivate(actual.getModifiers()) && !Modifier.isStatic(actual.getModifiers()));
        return names.isEmpty() ? actual.getName().equals(requested) : names.contains(actual.getName());
    }

    public boolean fieldMatches(Class<?> owner, String actual, String descriptor, String requested, String policy) {
        if (policy.equals("runtime")) {
            return actual.equals(requested);
        }
        Set<String> names = memberNames(owner, requested, descriptor, false, false);
        return names.isEmpty() ? actual.equals(requested) : names.contains(actual);
    }

    private Set<String> memberNames(Class<?> owner, String requested, String runtimeDescriptor, boolean method,
            boolean ancestors) {
        Set<String> result = new HashSet<>();
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!visited.add(type)) {
                continue;
            }
            TinyMappingSnapshot.ClassEntry entry = mappings.getClasses().get(canonicalClass(type));
            if (entry != null) {
                for (TinyMappingSnapshot.MemberEntry member : entry.getMembers()) {
                    if (member.isMethod() == method && requested.equals(member.getNames().get("named"))
                            && descriptor(member.getDescriptor(), "mapped").equals(runtimeDescriptor)) {
                        String name = member.getNames().get(namespace);
                        if (name == null || name.isEmpty()) {
                            throw new NativeAccessFailure("side_absent", requested + member.getDescriptor());
                        }
                        result.add(name);
                    }
                }
            }
            if (ancestors) {
                if (type.getSuperclass() != null) {
                    pending.add(type.getSuperclass());
                }
                for (Class<?> contract : type.getInterfaces()) {
                    pending.add(contract);
                }
            }
        }
        if (result.size() > 1) {
            throw new NativeAccessFailure("mapping_conflict", requested + runtimeDescriptor);
        }
        return result;
    }
}
