package betamoon.hooks;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.CatalogValues;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Byte-backed virtual override names. Preparation never defines or initializes
 * an ancestor.
 */
public final class HookOverrideMappings {
    private HookOverrideMappings() {
    }

    static Selection resolve(String owner, String requested, String namedDescriptor, String runtimeDescriptor,
            TinyMappingResolver mappings, TinyMappingSnapshot snapshot, RuntimeNamespace namespace) {
        ClassLoader loader = HookOverrideMappings.class.getClassLoader();
        Header implementation = read(owner, loader);
        if (implementation == null) {
            return null;
        }
        Deque<String> pending = new ArrayDeque<>(implementation.parents);
        Set<String> visited = new HashSet<>();
        Selection selected = null;
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (visited.size() > 128) {
                throw new HookFailure("mapping_conflict", "override hierarchy exceeds 128 types");
            }
            Header ancestor = read(current, loader);
            if (ancestor == null) {
                continue;
            }
            String named = mappings.resolveNamedClass(current, namespace);
            TinyMappingSnapshot.ClassEntry entry = snapshot.getClasses().get(named);
            if (entry != null) {
                for (TinyMappingSnapshot.MemberEntry member : entry.getMembers()) {
                    if (!member.isMethod() || !requested.equals(member.getNames().get("named"))
                            || !namedDescriptor.equals(member.getDescriptor())) {
                        continue;
                    }
                    String name = member.getNames().get(namespace.getMappingName());
                    if (name == null || name.isEmpty() || !implementation.virtual(name, runtimeDescriptor, false)
                            || !ancestor.virtual(name, runtimeDescriptor, true)) {
                        continue;
                    }
                    if (selected != null && !selected.name.equals(name)) {
                        throw new HookFailure("mapping_conflict",
                                "ancestor mappings disagree for " + owner + "." + requested);
                    }
                    selected = new Selection(name, current);
                }
            }
            pending.addAll(ancestor.parents);
        }
        return selected;
    }

    public static boolean visibleVirtual(String owner, String name, String descriptor, ClassLoader loader) {
        Header header = read(owner, loader);
        return header != null && header.virtual(name, descriptor, true);
    }

    private static Header read(String name, ClassLoader loader) {
        try (InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(name + ".class")
                : loader.getResourceAsStream(name + ".class")) {
            if (input == null) {
                return null;
            }
            ClassReader reader = new ClassReader(CatalogValues.bytes(input, 2 * 1024 * 1024));
            if (!reader.getClassName().equals(name)) {
                throw new HookFailure("mapping_conflict", "hierarchy resource has a different class identity: " + name);
            }
            Header header = new Header();
            if (reader.getSuperName() != null) {
                header.parents.add(reader.getSuperName());
            }
            for (String contract : reader.getInterfaces()) {
                header.parents.add(contract);
            }
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String method, String descriptor, String signature,
                        String[] exceptions) {
                    header.methods.put(method + descriptor, access);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return header;
        } catch (IOException | IllegalArgumentException failure) {
            throw new HookFailure("mapping_conflict", "cannot read bounded override hierarchy: " + name, failure);
        }
    }

    static final class Selection {
        final String name;
        final String owner;

        Selection(String name, String owner) {
            this.name = name;
            this.owner = owner;
        }
    }

    private static final class Header {
        final List<String> parents = new ArrayList<>();
        final Map<String, Integer> methods = new HashMap<>();

        boolean virtual(String name, String descriptor, boolean inherited) {
            Integer flags = methods.get(name + descriptor);
            return flags != null && (flags & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0
                    && (!inherited || (flags & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0);
        }
    }
}
