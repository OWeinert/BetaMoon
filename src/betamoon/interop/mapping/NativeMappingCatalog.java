package betamoon.interop.mapping;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.ClassArchiveCatalog;
import betamoon.interop.catalog.JvmDescriptors;
import betamoon.interop.catalog.NativeClassInfo;
import betamoon.interop.catalog.NativeMemberInfo;
import org.objectweb.asm.Opcodes;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict, compiled-input-backed mapping, separate from the engine transformer's fallback resolver. */
public final class NativeMappingCatalog {
    private final TinyMappingSnapshot mappings;
    private final ClassArchiveCatalog named;
    private final ClassArchiveCatalog namedServer;
    private final Map<RuntimeNamespace, ClassArchiveCatalog> targets;
    private final Map<String, Map<String, TinyMappingSnapshot.MemberEntry>> members = new HashMap<>();

    public NativeMappingCatalog(TinyMappingSnapshot mappings, ClassArchiveCatalog named,
            Map<RuntimeNamespace, ClassArchiveCatalog> targets) {
        this(mappings, named, named, targets);
    }

    public NativeMappingCatalog(TinyMappingSnapshot mappings, ClassArchiveCatalog named,
            ClassArchiveCatalog namedServer, Map<RuntimeNamespace, ClassArchiveCatalog> targets) {
        this.mappings = mappings;
        this.named = named;
        this.namedServer = namedServer;
        this.targets = new EnumMap<>(RuntimeNamespace.class);
        this.targets.putAll(targets);
        this.targets.put(RuntimeNamespace.NAMED, named);
        validateMappings();
    }

    public MappingResolution resolveClass(String canonicalName, RuntimeNamespace namespace) {
        JvmDescriptors.requireInternalName(canonicalName);
        ClassArchiveCatalog target = requireTarget(namespace);
        TinyMappingSnapshot.ClassEntry row = mappings.getClasses().get(canonicalName);
        String runtimeName = row == null ? canonicalName : row.getNames().get(namespace.getMappingName());
        if (runtimeName == null || runtimeName.isEmpty()) {
            return result(MappingResolution.State.SIDE_ABSENT, null, null, null, canonicalName);
        }
        if (!sourceFor(namespace).getClasses().containsKey(canonicalName)) {
            return result(row == null ? MappingResolution.State.NOT_CATALOGUED
                    : MappingResolution.State.NAMED_TYPE_MISSING, null, null, null, canonicalName);
        }
        if (!target.getClasses().containsKey(runtimeName)) {
            return result(row == null ? MappingResolution.State.MAPPING_MISSING
                    : MappingResolution.State.TARGET_TYPE_MISSING, runtimeName, null, null, canonicalName);
        }
        return result(canonicalName.equals(runtimeName) ? MappingResolution.State.VERIFIED_UNCHANGED
                : MappingResolution.State.VERIFIED_MAPPED, runtimeName, null, null, null);
    }

    /** The owner must be the declaring type, not an arbitrary subclass used as an invocation receiver. */
    public MappingResolution resolveMember(String declaringOwner, String name, String descriptor, boolean method,
            RuntimeNamespace namespace) {
        if (method) {
            JvmDescriptors.requireMethod(descriptor);
        } else {
            JvmDescriptors.requireField(descriptor);
        }
        MappingResolution owner = resolveClass(declaringOwner, namespace);
        if (!owner.isVerified()) {
            return owner;
        }
        NativeClassInfo namedClass = sourceFor(namespace).getClasses().get(declaringOwner);
        NativeMemberInfo namedMember = namedClass.findMember(method, name, descriptor);
        Map<String, TinyMappingSnapshot.MemberEntry> classMembers = members.get(declaringOwner);
        TinyMappingSnapshot.MemberEntry row = classMembers == null ? null
                : classMembers.get(memberKey(method, name, descriptor));
        String mappingOwner = row == null ? null : declaringOwner;
        if (row == null && method && isVirtual(namedMember)) {
            Map<String, TinyMappingSnapshot.MemberEntry> inherited = inheritedMappings(namedClass, name, descriptor,
                    namespace);
            String selectedName = null;
            for (Map.Entry<String, TinyMappingSnapshot.MemberEntry> candidate : inherited.entrySet()) {
                String candidateName = candidate.getValue().getNames().get(namespace.getMappingName());
                if (selectedName != null && !selectedName.equals(candidateName)) {
                    return result(MappingResolution.State.MAPPING_CONFLICT, owner.getOwner(), name, descriptor,
                            "Conflicting ancestor mappings: " + inherited.keySet());
                }
                if (row == null) {
                    row = candidate.getValue();
                    mappingOwner = candidate.getKey();
                }
                selectedName = candidateName;
            }
        }
        String runtimeName = row == null ? name : row.getNames().get(namespace.getMappingName());
        if (runtimeName == null || runtimeName.isEmpty()) {
            return result(MappingResolution.State.SIDE_ABSENT, owner.getOwner(), null, null, name);
        }
        if (namedMember == null) {
            return result(MappingResolution.State.NAMED_MEMBER_MISSING, owner.getOwner(), name, descriptor,
                    declaringOwner);
        }
        String runtimeDescriptor;
        try {
            runtimeDescriptor = resolveDescriptor(descriptor, namespace);
        } catch (UnavailableDescriptor exception) {
            return result(MappingResolution.State.DESCRIPTOR_UNAVAILABLE, owner.getOwner(), runtimeName, null,
                    exception.getMessage());
        }
        NativeClassInfo runtimeClass = requireTarget(namespace).getClasses().get(owner.getOwner());
        if (runtimeClass.findMember(method, runtimeName, runtimeDescriptor) == null) {
            return result(row == null ? MappingResolution.State.MAPPING_MISSING
                    : MappingResolution.State.TARGET_MEMBER_MISSING, owner.getOwner(), runtimeName,
                    runtimeDescriptor, name + descriptor);
        }
        // A same-name fallback is accepted only after checking both declared binary members.
        return new MappingResolution(name.equals(runtimeName) ? MappingResolution.State.VERIFIED_UNCHANGED
                : MappingResolution.State.VERIFIED_MAPPED, owner.getOwner(), runtimeName, runtimeDescriptor, null,
                mappingOwner);
    }

    private Map<String, TinyMappingSnapshot.MemberEntry> inheritedMappings(NativeClassInfo type, String name,
            String descriptor, RuntimeNamespace namespace) {
        Map<String, TinyMappingSnapshot.MemberEntry> found = new TreeMap<>();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        addParents(type, pending);
        while (!pending.isEmpty()) {
            String parentName = pending.removeFirst();
            if (!visited.add(parentName)) {
                continue;
            }
            NativeClassInfo parent = sourceFor(namespace).getClasses().get(parentName);
            if (parent == null) {
                continue;
            }
            Map<String, TinyMappingSnapshot.MemberEntry> declared = members.get(parentName);
            TinyMappingSnapshot.MemberEntry candidate = declared == null ? null
                    : declared.get(memberKey(true, name, descriptor));
            NativeMemberInfo declaration = parent.findMember(true, name, descriptor);
            if (candidate != null && isVirtual(declaration) && isInheritedFrom(parentName, type, declaration)) {
                String targetName = candidate.getNames().get(namespace.getMappingName());
                if (targetName != null && !targetName.isEmpty()) {
                    found.put(parentName, candidate);
                }
            }
            addParents(parent, pending);
        }
        return found;
    }

    private static boolean isVirtual(NativeMemberInfo member) {
        return member != null && member.getKind() == NativeMemberInfo.Kind.METHOD
                && (member.getAccess() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0;
    }

    private static boolean isInheritedFrom(String owner, NativeClassInfo child, NativeMemberInfo member) {
        if ((member.getAccess() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
            return true;
        }
        String ownerPackage = owner.substring(0, owner.lastIndexOf('/') + 1);
        String childName = child.getInternalName();
        return childName.substring(0, childName.lastIndexOf('/') + 1).equals(ownerPackage);
    }

    private static void addParents(NativeClassInfo type, Deque<String> pending) {
        if (type.getSuperName() != null) {
            pending.addLast(type.getSuperName());
        }
        pending.addAll(type.getInterfaces());
    }

    private ClassArchiveCatalog sourceFor(RuntimeNamespace namespace) {
        return namespace == RuntimeNamespace.SERVER ? namedServer : named;
    }

    public String resolveDescriptor(String descriptor, RuntimeNamespace namespace) {
        requireTarget(namespace);
        return JvmDescriptors.remap(descriptor, className -> {
            if (!mappings.getClasses().containsKey(className)
                    && !sourceFor(namespace).getClasses().containsKey(className)) {
                return className;
            }
            MappingResolution resolved = resolveClass(className, namespace);
            if (!resolved.isVerified()) {
                throw new UnavailableDescriptor(className + ":" + resolved.getState());
            }
            return resolved.getOwner();
        });
    }

    private void validateMappings() {
        Map<RuntimeNamespace, Map<String, String>> classOwners = new EnumMap<>(RuntimeNamespace.class);
        for (RuntimeNamespace namespace : targets.keySet()) {
            if (!mappings.getNamespaces().contains(namespace.getMappingName())) {
                throw new IllegalArgumentException("Missing mapping namespace: " + namespace);
            }
            classOwners.put(namespace, new HashMap<>());
        }
        for (Map.Entry<String, TinyMappingSnapshot.ClassEntry> entry : mappings.getClasses().entrySet()) {
            String owner = JvmDescriptors.requireInternalName(entry.getKey());
            for (RuntimeNamespace namespace : targets.keySet()) {
                String runtimeName = entry.getValue().getNames().get(namespace.getMappingName());
                if (runtimeName != null && !runtimeName.isEmpty()) {
                    JvmDescriptors.requireInternalName(runtimeName);
                    String previous = classOwners.get(namespace).put(runtimeName, owner);
                    if (previous != null && !previous.equals(owner)) {
                        throw new IllegalArgumentException("Duplicate mapped class identity in " + namespace
                                + ": " + runtimeName);
                    }
                }
            }
            Map<String, TinyMappingSnapshot.MemberEntry> byKey = new HashMap<>();
            for (TinyMappingSnapshot.MemberEntry member : entry.getValue().getMembers()) {
                if (member.isMethod()) {
                    JvmDescriptors.requireMethod(member.getDescriptor());
                } else {
                    JvmDescriptors.requireField(member.getDescriptor());
                }
                byKey.put(member.getKey(), member);
            }
            members.put(owner, byKey);
            validateTargetMembers(owner, entry.getValue());
        }
    }

    private void validateTargetMembers(String owner, TinyMappingSnapshot.ClassEntry type) {
        for (RuntimeNamespace namespace : targets.keySet()) {
            Map<String, String> selected = new HashMap<>();
            for (TinyMappingSnapshot.MemberEntry member : type.getMembers()) {
                String name = member.getNames().get(namespace.getMappingName());
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String descriptor;
                try {
                    descriptor = resolveDescriptor(member.getDescriptor(), namespace);
                } catch (UnavailableDescriptor exception) {
                    continue;
                }
                String key = memberKey(member.isMethod(), name, descriptor);
                String previous = selected.put(key, member.getKey());
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate mapped member in " + namespace + ": "
                            + owner + ":" + previous + ":" + member.getKey());
                }
            }
        }
    }

    private ClassArchiveCatalog requireTarget(RuntimeNamespace namespace) {
        ClassArchiveCatalog target = targets.get(namespace);
        if (target == null) {
            throw new IllegalArgumentException("No compiled catalog for namespace: " + namespace);
        }
        return target;
    }

    private static String memberKey(boolean method, String name, String descriptor) {
        return (method ? "m:" : "f:") + name + ":" + descriptor;
    }

    private static MappingResolution result(MappingResolution.State state, String owner, String name,
            String descriptor, String detail) {
        return new MappingResolution(state, owner, name, descriptor, detail);
    }

    private static final class UnavailableDescriptor extends IllegalArgumentException {
        private UnavailableDescriptor(String detail) {
            super(detail);
        }
    }
}
