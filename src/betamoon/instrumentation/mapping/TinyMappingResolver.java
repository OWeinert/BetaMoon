package betamoon.instrumentation.mapping;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Tiny v2 reader for class, method, field, and descriptor resolution.
 */
public final class TinyMappingResolver implements MappingResolver {
    private final List<String> namespaces;
    private final Map<String, ClassMapping> classes;
    private final Map<RuntimeNamespace, Map<String, String>> namedClasses;

    private TinyMappingResolver(List<String> namespaces, Map<String, ClassMapping> classes) {
        this.namespaces = namespaces;
        this.classes = classes;
        namedClasses = new EnumMap<>(RuntimeNamespace.class);
        for (RuntimeNamespace namespace : RuntimeNamespace.values()) {
            Map<String, String> reverse = new HashMap<>();
            int index = namespaces.indexOf(namespace.getMappingName());
            if (index >= 0) {
                for (Map.Entry<String, ClassMapping> entry : classes.entrySet()) {
                    String runtimeName = entry.getValue().names[index];
                    if (!runtimeName.isEmpty()) {
                        reverse.put(runtimeName, entry.getKey());
                    }
                }
            }
            namedClasses.put(namespace, reverse);
        }
    }

    public static TinyMappingResolver read(InputStream input) throws IOException {
        return read(input, false);
    }

    /** Strict catalog input validation, leaving existing instrumentation fallback behavior unchanged. */
    public static TinyMappingResolver readStrict(InputStream input) throws IOException {
        return read(input, true);
    }

    private static TinyMappingResolver read(InputStream input, boolean strict) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Mapping input is required");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        try {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Tiny mapping file is empty");
            }
            String[] headerParts = header.split("\\t", -1);
            if (headerParts.length < 5 || !"tiny".equals(headerParts[0]) || !"2".equals(headerParts[1])) {
                throw new IOException("Expected a Tiny v2 mapping header");
            }

            List<String> namespaces = new ArrayList<String>();
            for (int i = 3; i < headerParts.length; i++) {
                if (strict && (headerParts[i].isEmpty() || namespaces.contains(headerParts[i]))) {
                    throw new IOException("Empty or duplicate Tiny namespace: " + headerParts[i]);
                }
                namespaces.add(headerParts[i]);
            }
            int namedIndex = namespaces.indexOf(RuntimeNamespace.NAMED.getMappingName());
            if (namedIndex < 0) {
                throw new IOException("Tiny mappings do not contain the named namespace");
            }
            if (strict && namedIndex != 0) {
                throw new IOException("Native catalog descriptors require named as the Tiny source namespace");
            }

            Map<String, ClassMapping> classes = new HashMap<String, ClassMapping>();
            ClassMapping currentClass = null;
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                String[] parts = line.split("\\t", -1);
                if ("c".equals(parts[0])) {
                    requireColumns(parts, namespaces.size() + 1, lineNumber);
                    String[] names = copyRange(parts, 1, namespaces.size());
                    String namedClass = names[namedIndex];
                    if (namedClass.length() == 0) {
                        throw new IOException("Missing named class at Tiny mapping line " + lineNumber);
                    }
                    currentClass = new ClassMapping(names);
                    if (strict && classes.containsKey(namedClass)) {
                        throw new IOException("Duplicate Tiny class at line " + lineNumber + ": " + namedClass);
                    }
                    classes.put(namedClass, currentClass);
                } else if (parts.length > 2 && parts[0].length() == 0 && currentClass != null) {
                    if ("m".equals(parts[1]) || "f".equals(parts[1])) {
                        requireColumns(parts, namespaces.size() + 3, lineNumber);
                        String descriptor = parts[2];
                        String[] names = copyRange(parts, 3, namespaces.size());
                        String key = memberKey(names[namedIndex], descriptor);
                        Map<String, String[]> members = "m".equals(parts[1])
                                ? currentClass.methods : currentClass.fields;
                        if (strict && (names[namedIndex].isEmpty() || members.containsKey(key))) {
                            throw new IOException("Empty or duplicate Tiny member at line " + lineNumber);
                        }
                        if ("m".equals(parts[1])) {
                            currentClass.methods.put(key, names);
                        } else {
                            currentClass.fields.put(key, names);
                        }
                    }
                } else if (strict && parts.length > 1 && parts[0].isEmpty()
                        && ("m".equals(parts[1]) || "f".equals(parts[1]))) {
                    throw new IOException("Tiny member has no declaring class at line " + lineNumber);
                }
            }
            return new TinyMappingResolver(namespaces, classes);
        } finally {
            reader.close();
        }
    }

    public TinyMappingSnapshot snapshot() {
        Map<String, TinyMappingSnapshot.ClassEntry> entries = new HashMap<>();
        for (Map.Entry<String, ClassMapping> entry : classes.entrySet()) {
            ClassMapping mapping = entry.getValue();
            List<TinyMappingSnapshot.MemberEntry> members = new ArrayList<>();
            exportMembers(mapping.fields, false, members);
            exportMembers(mapping.methods, true, members);
            entries.put(entry.getKey(), new TinyMappingSnapshot.ClassEntry(exportNames(mapping.names), members));
        }
        return new TinyMappingSnapshot(namespaces, entries);
    }

    private void exportMembers(Map<String, String[]> source, boolean method,
            List<TinyMappingSnapshot.MemberEntry> target) {
        for (Map.Entry<String, String[]> entry : source.entrySet()) {
            String descriptor = entry.getKey().substring(entry.getKey().indexOf('\u0000') + 1);
            target.add(new TinyMappingSnapshot.MemberEntry(method, descriptor, exportNames(entry.getValue())));
        }
    }

    private Map<String, String> exportNames(String[] names) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < namespaces.size(); i++) {
            result.put(namespaces.get(i), names[i]);
        }
        return result;
    }

    @Override
    public String resolveClass(ClassRef classRef, RuntimeNamespace namespace) {
        ClassMapping mapping = classes.get(classRef.getInternalName());
        if (mapping == null) {
            return classRef.getInternalName();
        }
        return mappedName(mapping.names, namespace, classRef.getInternalName());
    }

    /** Resolves a runtime class back to its canonical name without loading or transforming the class. */
    public String resolveNamedClass(String runtimeInternalName, RuntimeNamespace namespace) {
        Map<String, String> reverse = namedClasses.get(namespace);
        return reverse == null ? runtimeInternalName : reverse.getOrDefault(runtimeInternalName, runtimeInternalName);
    }

    @Override
    public String resolveDescriptor(String namedDescriptor, RuntimeNamespace namespace) {
        StringBuilder resolved = new StringBuilder(namedDescriptor.length());
        int index = 0;
        while (index < namedDescriptor.length()) {
            char value = namedDescriptor.charAt(index);
            if (value != 'L') {
                resolved.append(value);
                index++;
                continue;
            }
            int end = namedDescriptor.indexOf(';', index);
            if (end < 0) {
                throw new IllegalArgumentException("Invalid JVM descriptor: " + namedDescriptor);
            }
            String className = namedDescriptor.substring(index + 1, end);
            resolved.append('L').append(resolveClass(new ClassRef(className), namespace)).append(';');
            index = end + 1;
        }
        return resolved.toString();
    }

    @Override
    public ResolvedMethod resolveMethod(MethodRef method, RuntimeNamespace namespace) {
        String owner = resolveClass(method.getOwner(), namespace);
        String name = resolveMemberName(method.getMappingOwner(), method.getName(), method.getDescriptor(), namespace,
                true);
        return new ResolvedMethod(owner, name, resolveDescriptor(method.getDescriptor(), namespace));
    }

    @Override
    public ResolvedField resolveField(FieldRef field, RuntimeNamespace namespace) {
        String owner = resolveClass(field.getOwner(), namespace);
        String name = resolveMemberName(field.getOwner(), field.getName(), field.getDescriptor(), namespace, false);
        return new ResolvedField(owner, name, resolveDescriptor(field.getDescriptor(), namespace));
    }

    private String resolveMemberName(ClassRef owner, String name, String descriptor, RuntimeNamespace namespace,
            boolean method) {
        ClassMapping classMapping = classes.get(owner.getInternalName());
        if (classMapping == null) {
            return name;
        }
        Map<String, String[]> members = method ? classMapping.methods : classMapping.fields;
        String[] names = members.get(memberKey(name, descriptor));
        return names == null ? name : mappedName(names, namespace, name);
    }

    private String mappedName(String[] names, RuntimeNamespace namespace, String fallback) {
        int index = namespaces.indexOf(namespace.getMappingName());
        if (index < 0 || index >= names.length || names[index].length() == 0) {
            return fallback;
        }
        return names[index];
    }

    private static String memberKey(String name, String descriptor) {
        return name + '\u0000' + descriptor;
    }

    private static String[] copyRange(String[] values, int start, int count) {
        String[] result = new String[count];
        System.arraycopy(values, start, result, 0, count);
        return result;
    }

    private static void requireColumns(String[] parts, int expected, int lineNumber) throws IOException {
        if (parts.length < expected) {
            throw new IOException("Incomplete Tiny mapping at line " + lineNumber);
        }
    }

    private static final class ClassMapping {
        private final String[] names;
        private final Map<String, String[]> methods = new HashMap<String, String[]>();
        private final Map<String, String[]> fields = new HashMap<String, String[]>();

        private ClassMapping(String[] names) {
            this.names = names;
        }
    }
}
