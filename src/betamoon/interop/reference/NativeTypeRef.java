package betamoon.interop.reference;

import betamoon.interop.catalog.JvmDescriptors;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A symbolic request, never a loaded Class or a captured execution context. */
public final class NativeTypeRef {
    public static final Map<String, String> PRIMITIVES;

    static {
        Map<String, String> values = new LinkedHashMap<>();
        String[] names = {"void", "boolean", "byte", "short", "int", "long", "char", "float", "double"};
        String[] descriptors = {"V", "Z", "B", "S", "I", "J", "C", "F", "D"};
        for (int i = 0; i < names.length; i++) {
            values.put(names[i], descriptors[i]);
        }
        PRIMITIVES = Collections.unmodifiableMap(values);
    }

    public final String descriptor;
    public final String names;
    public final String loaderId;
    public final String originId;
    private final String key;

    private NativeTypeRef(String descriptor, String names, String loaderId, String originId) {
        if (!"V".equals(descriptor)) {
            JvmDescriptors.requireField(descriptor);
        }
        this.descriptor = descriptor;
        this.names = policy(names);
        this.loaderId = loaderId;
        this.originId = originId;
        key = descriptor + ":" + this.names + ":" + loaderId + ":" + originId;
    }

    public static NativeTypeRef named(String name, String names, String loaderId, String originId) {
        if (name == null || name.length() > 1024 || name.isEmpty()) {
            throw new IllegalArgumentException("invalid_type_name");
        }
        int dimensions = 0;
        while (name.endsWith("[]")) {
            dimensions++;
            name = name.substring(0, name.length() - 2);
        }
        if (dimensions > 255 || (dimensions > 0 && name.equals("void"))) {
            throw new IllegalArgumentException("invalid_array_type");
        }
        String descriptor = PRIMITIVES.get(name);
        if (descriptor == null) {
            String internal = name.replace('.', '/');
            JvmDescriptors.requireInternalName(internal);
            descriptor = "L" + internal + ";";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            result.append('[');
        }
        return new NativeTypeRef(result + descriptor, names, loaderId, originId);
    }

    public static NativeTypeRef descriptor(String descriptor, String names) {
        if (descriptor == null || descriptor.length() > 8192) {
            throw new IllegalArgumentException("invalid_type_descriptor");
        }
        return new NativeTypeRef(descriptor, names, null, null);
    }

    public NativeTypeRef selected(String names, String loader, String origin) {
        return new NativeTypeRef(descriptor, names == null ? this.names : names, loader == null ? loaderId : loader,
                origin == null ? originId : origin);
    }

    public int dimensions() {
        int count = 0;
        while (descriptor.charAt(count) == '[') {
            count++;
        }
        return count;
    }

    public boolean reference() {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }

    public NativeTypeRef component() {
        if (dimensions() == 0) {
            throw new IllegalArgumentException("not_an_array_type");
        }
        return descriptor(descriptor.substring(1), names);
    }

    public String name() {
        int dimensions = dimensions();
        String leaf = descriptor.substring(dimensions);
        String name = null;
        for (Map.Entry<String, String> primitive : PRIMITIVES.entrySet()) {
            if (primitive.getValue().equals(leaf)) {
                name = primitive.getKey();
            }
        }
        if (name == null) {
            name = leaf.substring(1, leaf.length() - 1).replace('/', '.');
        }
        StringBuilder result = new StringBuilder(name);
        for (int i = 0; i < dimensions; i++) {
            result.append("[]");
        }
        return result.toString();
    }

    public String key() {
        return key;
    }

    public static String policy(String value) {
        if (value == null) {
            return "auto";
        }
        if (!value.equals("auto") && !value.equals("mapped") && !value.equals("runtime")) {
            throw new IllegalArgumentException("invalid_name_policy");
        }
        return value;
    }
}
