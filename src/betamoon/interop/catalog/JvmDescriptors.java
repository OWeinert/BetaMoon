package betamoon.interop.catalog;

import java.util.function.Function;

/** Strict descriptor validation and class-name remapping without loading Java classes. */
public final class JvmDescriptors {
    private JvmDescriptors() {
    }

    public static String requireInternalName(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.endsWith("/")
                || name.contains("//")) {
            throw new IllegalArgumentException("Invalid JVM internal class name: " + name);
        }
        for (int i = 0; i < name.length(); i++) {
            char value = name.charAt(i);
            if (value == '.' || value == ';' || value == '[' || value == '\u0000') {
                throw new IllegalArgumentException("Invalid JVM internal class name: " + name);
            }
        }
        return name;
    }

    public static String requireField(String descriptor) {
        if (descriptor == null || readType(descriptor, 0, false) != descriptor.length()) {
            throw invalid(descriptor);
        }
        return descriptor;
    }

    public static String requireMethod(String descriptor) {
        if (descriptor == null || descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            throw invalid(descriptor);
        }
        int index = 1;
        int parameterSlots = 0;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            char first = descriptor.charAt(index);
            index = readType(descriptor, index, false);
            parameterSlots += first == 'J' || first == 'D' ? 2 : 1;
            if (parameterSlots > 255) {
                throw invalid(descriptor);
            }
        }
        if (index >= descriptor.length() || readType(descriptor, index + 1, true) != descriptor.length()) {
            throw invalid(descriptor);
        }
        return descriptor;
    }

    public static String remap(String descriptor, Function<String, String> classNames) {
        if (descriptor != null && descriptor.startsWith("(")) {
            requireMethod(descriptor);
        } else {
            requireField(descriptor);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < descriptor.length(); i++) {
            char value = descriptor.charAt(i);
            result.append(value);
            if (value == 'L') {
                int end = descriptor.indexOf(';', i);
                String mapped = classNames.apply(descriptor.substring(i + 1, end));
                result.append(requireInternalName(mapped)).append(';');
                i = end;
            }
        }
        return result.toString();
    }

    private static int readType(String descriptor, int start, boolean allowVoid) {
        int index = start;
        int dimensions = 0;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            dimensions++;
            index++;
        }
        if (index >= descriptor.length() || dimensions > 255) {
            throw invalid(descriptor);
        }
        char type = descriptor.charAt(index);
        if ("BCDFIJSZ".indexOf(type) >= 0 || (type == 'V' && allowVoid && dimensions == 0)) {
            return index + 1;
        }
        if (type == 'L') {
            int end = descriptor.indexOf(';', index);
            if (end > index + 1) {
                requireInternalName(descriptor.substring(index + 1, end));
                return end + 1;
            }
        }
        throw invalid(descriptor);
    }

    private static IllegalArgumentException invalid(String descriptor) {
        return new IllegalArgumentException("Invalid JVM descriptor: " + descriptor);
    }
}
