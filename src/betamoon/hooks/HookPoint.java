package betamoon.hooks;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.mapping.TinyMappingSnapshot;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.JvmDescriptors;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeTypeRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Immutable operation selector; containing executable and referenced member
 * retain separate name policies.
 */
public final class HookPoint {
    public final String kind;
    public final NativeMemberRef member;
    public final int ordinal;
    public final int exact;

    private HookPoint(String kind, NativeMemberRef member, int ordinal, int exact) {
        this.kind = kind;
        this.member = member;
        this.ordinal = ordinal;
        this.exact = exact;
    }

    static HookPoint read(LuaValue value) {
        LuaTable table = HookDefinition.fields(value, "kind", "method", "field", "ordinal", "matches");
        String kind = HookDefinition.text(table.rawget("kind"));
        boolean field = kind.equals("field_read") || kind.equals("field_write");
        if (!field && !kind.equals("invoke_before") && !kind.equals("invoke_after")) {
            throw new HookFailure("hook_file_invalid",
                    "operation kind must be invoke_before, invoke_after, field_read or field_write");
        }
        if (!table.rawget(field ? "method" : "field").isnil()) {
            throw new HookFailure("hook_file_invalid",
                    "use only the referenced member required by this operation kind");
        }
        LuaValue requested = table.rawget(field ? "field" : "method");
        NativeMemberRef member;
        if (requested instanceof NativeMemberReference) {
            member = ((NativeMemberReference) requested).nativeMemberReference();
        } else {
            LuaTable selector = HookDefinition.fields(requested, "class", field ? "field" : "method", "descriptor",
                    "names", "descriptorNames");
            member = new NativeMemberRef(field ? NativeMemberRef.Kind.FIELD : NativeMemberRef.Kind.METHOD,
                    NativeTypeRef.named(HookDefinition.text(selector.rawget("class")),
                            HookDefinition.optional(selector.rawget("names")), null, null),
                    null, HookDefinition.text(selector.rawget(field ? "field" : "method")),
                    HookDefinition.text(selector.rawget("descriptor")),
                    HookDefinition.optional(selector.rawget("names")),
                    HookDefinition.optional(selector.rawget("descriptorNames")));
        }
        if (member == null || member.kind != (field ? NativeMemberRef.Kind.FIELD : NativeMemberRef.Kind.METHOD)
                || member.descriptor == null || member.name.startsWith("<") || member.lookupType.loaderId != null
                || member.lookupType.originId != null
                || member.declaringType != null
                        && (!member.lookupType.descriptor.equals(member.declaringType.descriptor)
                                || member.declaringType.loaderId != null || member.declaringType.originId != null)) {
            throw new HookFailure("hook_file_invalid",
                    "operation requires an exact symbolic member and operand owner without session identities");
        }
        LuaTable matches = HookDefinition.fields(table.rawget("matches"), "exact");
        int ordinal = integer(table.rawget("ordinal"), 0, 65535);
        int exact = integer(matches.rawget("exact"), 1, 65535);
        if (ordinal >= exact) {
            throw new HookFailure("hook_file_invalid", "ordinal must select an operation inside matches.exact");
        }
        return new HookPoint(kind, member, ordinal, exact);
    }

    private static int integer(LuaValue value, int minimum, int maximum) {
        double number = value.todouble();
        if (value.type() != LuaValue.TNUMBER || !Double.isFinite(number) || number != Math.rint(number)
                || number < minimum || number > maximum) {
            throw new HookFailure("hook_file_invalid", "ordinal/match count must be an exact bounded integer");
        }
        return (int) number;
    }

    public boolean before() {
        return kind.equals("invoke_before") || kind.equals("field_write");
    }

    public boolean field() {
        return kind.equals("field_read") || kind.equals("field_write");
    }

    public String key() {
        return kind + ":" + member.kind + ":" + member.lookupType.key() + ":" + member.name + ":" + member.descriptor
                + ":" + member.names + ":" + member.descriptorNames + ":" + ordinal + ":" + exact;
    }

    public Map<String, Object> transport() {
        return CatalogValues.record("kind", kind, "class", member.lookupType.name(), "classNames",
                member.lookupType.names, "name", member.name, "descriptor", member.descriptor, "names", member.names,
                "descriptorNames", member.descriptorNames, "ordinal", ordinal, "exact", exact);
    }

    static HookPoint transport(Map<String, Object> value) {
        String kind = CatalogValues.text(value, "kind");
        return new HookPoint(kind, new NativeMemberRef(
                kind.startsWith("field_") ? NativeMemberRef.Kind.FIELD : NativeMemberRef.Kind.METHOD,
                NativeTypeRef.named(CatalogValues.text(value, "class"), CatalogValues.text(value, "classNames"), null,
                        null),
                null, CatalogValues.text(value, "name"), CatalogValues.text(value, "descriptor"),
                CatalogValues.text(value, "names"), CatalogValues.text(value, "descriptorNames")),
                CatalogValues.integer(value, "ordinal"), CatalogValues.integer(value, "exact"));
    }

    public List<Member> resolve(TinyMappingResolver mappings, TinyMappingSnapshot snapshot) {
        List<Member> result = new ArrayList<>();
        for (RuntimeNamespace namespace : new RuntimeNamespace[]{RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT}) {
            String requestedOwner = member.lookupType.name().replace('.', '/');
            String mappingOwner = snapshot.getClasses().containsKey(requestedOwner)
                    ? requestedOwner
                    : mappings.resolveNamedClass(requestedOwner, namespace);
            TinyMappingSnapshot.ClassEntry known = snapshot.getClasses().get(mappingOwner);
            String owner = requestedOwner;
            if (!member.lookupType.names.equals("runtime") && known != null) {
                owner = known.getNames().get(namespace.getMappingName());
                if (owner == null || owner.isEmpty()) {
                    continue;
                }
            } else if (member.lookupType.names.equals("mapped") && known == null) {
                continue;
            }
            String namedDescriptor = JvmDescriptors.remap(member.descriptor,
                    name -> snapshot.getClasses().containsKey(name)
                            ? name
                            : mappings.resolveNamedClass(name, namespace));
            String descriptor = member.descriptorNames.equals("runtime")
                    ? member.descriptor
                    : mappings.resolveDescriptor(member.descriptor, namespace);
            String name = member.name;
            String mappedName = null;
            if (!member.names.equals("runtime") && known != null) {
                for (TinyMappingSnapshot.MemberEntry entry : known.getMembers()) {
                    if (entry.isMethod() != field() && member.name.equals(entry.getNames().get("named"))
                            && namedDescriptor.equals(entry.getDescriptor())) {
                        mappedName = entry.getNames().get(namespace.getMappingName());
                        break;
                    }
                }
            }
            if (!member.names.equals("runtime") && mappedName == null) {
                HookOverrideMappings.Selection inherited = HookOverrideMappings.resolveReference(owner, member.name,
                        namedDescriptor, descriptor, mappings, snapshot, namespace, field());
                if (inherited != null) {
                    mappedName = inherited.name;
                }
            }
            if (mappedName != null && !mappedName.isEmpty()) {
                name = mappedName;
            } else if (member.names.equals("mapped")) {
                continue;
            }
            Member selected = new Member(owner, name, descriptor);
            if (result.stream().noneMatch(existing -> existing.key().equals(selected.key()))) {
                result.add(selected);
            }
        }
        result.sort((left, right) -> left.key().compareTo(right.key()));
        return Collections.unmodifiableList(result);
    }

    public static final class Member {
        public final String owner;
        public final String name;
        public final String descriptor;

        public Member(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        public String key() {
            return owner + "." + name + ":" + descriptor;
        }
    }
}
