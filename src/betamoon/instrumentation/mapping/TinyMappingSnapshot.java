package betamoon.instrumentation.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Presence-aware, detached export. Empty cells remain empty instead of becoming fallback names. */
public final class TinyMappingSnapshot {
    private final List<String> namespaces;
    private final Map<String, ClassEntry> classes;

    TinyMappingSnapshot(List<String> namespaces, Map<String, ClassEntry> classes) {
        this.namespaces = Collections.unmodifiableList(new ArrayList<>(namespaces));
        this.classes = Collections.unmodifiableMap(new TreeMap<>(classes));
    }

    public List<String> getNamespaces() {
        return namespaces;
    }

    public Map<String, ClassEntry> getClasses() {
        return classes;
    }

    public static final class ClassEntry {
        private final Map<String, String> names;
        private final List<MemberEntry> members;

        ClassEntry(Map<String, String> names, List<MemberEntry> members) {
            this.names = Collections.unmodifiableMap(new TreeMap<>(names));
            List<MemberEntry> sorted = new ArrayList<>(members);
            sorted.sort((left, right) -> left.getKey().compareTo(right.getKey()));
            this.members = Collections.unmodifiableList(sorted);
        }

        public Map<String, String> getNames() {
            return names;
        }

        public List<MemberEntry> getMembers() {
            return members;
        }
    }

    public static final class MemberEntry {
        private final boolean method;
        private final String descriptor;
        private final Map<String, String> names;

        MemberEntry(boolean method, String descriptor, Map<String, String> names) {
            this.method = method;
            this.descriptor = descriptor;
            this.names = Collections.unmodifiableMap(new TreeMap<>(names));
        }

        public boolean isMethod() {
            return method;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public Map<String, String> getNames() {
            return names;
        }

        public String getKey() {
            return (method ? "m:" : "f:") + names.get("named") + ":" + descriptor;
        }
    }
}
