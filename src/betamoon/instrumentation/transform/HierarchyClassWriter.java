package betamoon.instrumentation.transform;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Computes frames from class resources without defining classes while their
 * transformer is running.
 */
final class HierarchyClassWriter extends ClassWriter {
    private final ClassLoader loader;
    private final Map<String, Hierarchy> hierarchy = new HashMap<>();

    HierarchyClassWriter(ClassLoader loader, ClassNode current) {
        super((current.version & 0xFFFF) >= Opcodes.V1_7 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS);
        this.loader = loader;
        hierarchy.put(current.name,
                new Hierarchy(current.access, current.superName, current.interfaces.toArray(new String[0])));
    }

    @Override
    protected String getCommonSuperClass(String first, String second) {
        if (assignable(first, second)) {
            return first;
        }
        if (assignable(second, first)) {
            return second;
        }
        if (first.startsWith("[") && second.startsWith("[")) {
            String left = component(first);
            String right = component(second);
            if (reference(left) && reference(right)) {
                String common = getCommonSuperClass(internal(left), internal(right));
                return "[" + (common.startsWith("[") ? common : "L" + common + ";");
            }
        }
        if (first.startsWith("[") || second.startsWith("[") || metadata(first).isInterface()
                || metadata(second).isInterface()) {
            return "java/lang/Object";
        }
        String parent = metadata(first).parent;
        while (parent != null && !assignable(parent, second)) {
            parent = metadata(parent).parent;
        }
        return parent == null ? "java/lang/Object" : parent;
    }

    private boolean assignable(String target, String value) {
        if (target.equals(value) || target.equals("java/lang/Object")) {
            return true;
        }
        if (value.startsWith("[")) {
            if (target.equals("java/lang/Cloneable") || target.equals("java/io/Serializable")) {
                return true;
            }
            if (!target.startsWith("[")) {
                return false;
            }
            String targetComponent = component(target);
            String valueComponent = component(value);
            return reference(targetComponent) && reference(valueComponent)
                    && assignable(internal(targetComponent), internal(valueComponent));
        }
        if (target.startsWith("[")) {
            return false;
        }
        Hierarchy info = metadata(value);
        for (String implemented : info.interfaces) {
            if (assignable(target, implemented)) {
                return true;
            }
        }
        return info.parent != null && assignable(target, info.parent);
    }

    private Hierarchy metadata(String name) {
        Hierarchy cached = hierarchy.get(name);
        if (cached != null) {
            return cached;
        }
        String resource = name + ".class";
        try (InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : loader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing class hierarchy resource: " + resource);
            }
            ClassReader reader = new ClassReader(input);
            Hierarchy result = new Hierarchy(reader.getAccess(), reader.getSuperName(), reader.getInterfaces());
            hierarchy.put(name, result);
            return result;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read class hierarchy: " + resource, error);
        }
    }

    private static String component(String array) {
        return array.substring(1);
    }

    private static boolean reference(String descriptor) {
        return descriptor.startsWith("[") || descriptor.startsWith("L");
    }

    private static String internal(String descriptor) {
        return descriptor.startsWith("L") ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }

    private static final class Hierarchy {
        private final int access;
        private final String parent;
        private final String[] interfaces;

        private Hierarchy(int access, String parent, String[] interfaces) {
            this.access = access;
            this.parent = parent;
            this.interfaces = interfaces;
        }

        private boolean isInterface() {
            return (access & Opcodes.ACC_INTERFACE) != 0;
        }
    }
}
