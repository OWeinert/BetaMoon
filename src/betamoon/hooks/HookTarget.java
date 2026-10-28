package betamoon.hooks;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.JvmDescriptors;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Presence-aware mapped/literal resolution; it never defines a target class.
 */
public final class HookTarget {
    public final String owner;
    public final String method;
    public final String descriptor;
    public final String mappingOwner;
    private final String key;

    public HookTarget(String owner, String method, String descriptor) {
        this(owner, method, descriptor, null);
    }

    private HookTarget(String owner, String method, String descriptor, String mappingOwner) {
        this.owner = owner;
        this.mappingOwner = mappingOwner;
        this.method = method;
        this.descriptor = descriptor;
        key = owner + "." + method + descriptor;
        if (owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("sun/")
                || owner.startsWith("jdk/") || owner.startsWith("com/sun/") || internal(owner)
                || owner.startsWith("org/luaj/") || owner.startsWith("org/objectweb/asm/")) {
            throw new HookFailure("match_failed", "bootstrap/dispatcher/runtime targets are unsupported: " + owner);
        }
    }

    public boolean constructor() {
        return method.equals("<init>");
    }

    public String key() {
        return key;
    }

    private static boolean internal(String owner) {
        return owner.startsWith("betamoon/hooks/") || owner.startsWith("betamoon/instrumentation/")
                || owner.startsWith("betamoon/interop/") || owner.startsWith("betamoon/luamodloader/")
                || owner.startsWith("betamoon/luaapi/") || owner.startsWith("betamoon/runtime/")
                || owner.startsWith("betamoon/io/");
    }

    public static List<HookTarget> resolve(NativeMemberRef ref, TinyMappingResolver mappings) {
        return resolve(ref, mappings, mappings.snapshot());
    }

    public static List<HookTarget> resolve(NativeMemberRef ref, TinyMappingResolver mappings,
            TinyMappingSnapshot snapshot) {
        return resolve(ref, mappings, snapshot, RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT);
    }

    public static List<HookTarget> resolve(NativeMemberRef ref, TinyMappingResolver mappings,
            TinyMappingSnapshot snapshot, RuntimeNamespace... namespaces) {
        List<HookTarget> result = new ArrayList<>();
        for (RuntimeNamespace namespace : namespaces) {
            String runtimeOwner = className(ref.lookupType, mappings, snapshot, namespace);
            if (runtimeOwner == null) {
                continue;
            }
            if (ref.declaringType != null
                    && !runtimeOwner.equals(className(ref.declaringType, mappings, snapshot, namespace))) {
                continue;
            }
            String requestedOwner = ref.lookupType.name().replace('.', '/');
            String mappingOwner = snapshot.getClasses().containsKey(requestedOwner)
                    ? requestedOwner
                    : mappings.resolveNamedClass(requestedOwner, namespace);
            TinyMappingSnapshot.ClassEntry known = snapshot.getClasses().get(mappingOwner);
            String namedDescriptor = JvmDescriptors.remap(ref.descriptor,
                    name -> snapshot.getClasses().containsKey(name)
                            ? name
                            : mappings.resolveNamedClass(name, namespace));
            if (ref.kind == NativeMemberRef.Kind.CONSTRUCTOR) {
                HookTarget target = new HookTarget(runtimeOwner, "<init>",
                        ref.descriptorNames.equals("runtime")
                                ? ref.descriptor
                                : mappings.resolveDescriptor(ref.descriptor, namespace));
                if (result.stream().noneMatch(value -> value.key().equals(target.key()))) {
                    result.add(target);
                }
                continue;
            }
            TinyMappingSnapshot.MemberEntry matched = null;
            if (!ref.names.equals("runtime") && known != null) {
                for (TinyMappingSnapshot.MemberEntry member : known.getMembers()) {
                    if (member.isMethod() && ref.name.equals(member.getNames().get("named"))
                            && namedDescriptor.equals(member.getDescriptor())) {
                        matched = member;
                        break;
                    }
                }
            }
            String descriptor = ref.descriptorNames.equals("runtime")
                    ? ref.descriptor
                    : mappings.resolveDescriptor(ref.descriptor, namespace);
            String mappedName = matched == null ? null : matched.getNames().get(namespace.getMappingName());
            HookOverrideMappings.Selection override = null;
            if (matched == null && !ref.names.equals("runtime")) {
                override = HookOverrideMappings.resolve(runtimeOwner, ref.name, namedDescriptor, descriptor, mappings,
                        snapshot, namespace);
                if (override != null) {
                    mappedName = override.name;
                }
            }
            if (ref.names.equals("mapped") && (mappedName == null || mappedName.isEmpty())) {
                continue;
            }
            String name = mappedName == null || mappedName.isEmpty() ? ref.name : mappedName;
            HookTarget target = new HookTarget(runtimeOwner, name, descriptor,
                    override == null ? null : override.owner);
            if (result.stream().noneMatch(value -> value.key().equals(target.key()))) {
                result.add(target);
            }
        }
        if (result.isEmpty() && namespaces.length > 1) {
            throw new HookFailure("match_failed", "exact mapped selector/declaring owner is unavailable: " + ref.key());
        }
        result.sort((left, right) -> left.key().compareTo(right.key()));
        return Collections.unmodifiableList(result);
    }

    private static String className(NativeTypeRef type, TinyMappingResolver mappings, TinyMappingSnapshot snapshot,
            RuntimeNamespace namespace) {
        String requested = type.name().replace('.', '/');
        if (type.names.equals("runtime")) {
            return requested;
        }
        TinyMappingSnapshot.ClassEntry known = snapshot.getClasses().get(requested);
        if (known == null) {
            return type.names.equals("mapped") ? null : requested;
        }
        String mapped = known.getNames().get(namespace.getMappingName());
        return mapped == null || mapped.isEmpty() ? null : mappings.resolveClass(new ClassRef(requested), namespace);
    }

}
