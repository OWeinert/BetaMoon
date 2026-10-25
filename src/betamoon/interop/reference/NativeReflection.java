package betamoon.interop.reference;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.JvmDescriptors;
import betamoon.interop.discovery.NativeIndexSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Structured metadata from detached catalogs and descriptor syntax; never binds
 * a class.
 */
public final class NativeReflection {
    private NativeReflection() {
    }

    public static Map<String, Object> describe(NativeTypeRef reference, NativeIndexSnapshot snapshot) {
        Map<String, Object> structural = CatalogValues.record("ref", reference, "name", reference.name(), "descriptor",
                reference.descriptor, "generation", snapshot.generation, "coverage", snapshot.coverage);
        if (reference.dimensions() > 0) {
            structural.putAll(CatalogValues.record("kind", "array", "dimensions", reference.dimensions(),
                    "componentType", reference.component(), "elementType",
                    NativeTypeRef.descriptor(reference.descriptor.substring(reference.dimensions()), reference.names)));
            return structural;
        }
        if (!reference.reference()) {
            String name = reference.name();
            structural.put("kind", name.equals("void") ? "void" : "primitive");
            structural.put("primitive", name);
            if (!name.equals("void") && !name.equals("boolean")) {
                int bits = name.equals("byte")
                        ? 8
                        : name.equals("short") || name.equals("char")
                                ? 16
                                : name.equals("int") || name.equals("float") ? 32 : 64;
                structural.put("bits", bits);
                structural.put("signed", !name.equals("char"));
                structural.put("numericKind", name.equals("float") || name.equals("double") ? "floating" : "integer");
            }
            return structural;
        }
        Map<String, Object> found = null;
        for (Map<String, Object> row : snapshot.rows("classes")) {
            if (!reference.name().equals(row.get("runtimeName"))
                    && (reference.names.equals("runtime") || !reference.name().equals(row.get("name")))) {
                continue;
            }
            if (reference.originId != null && !reference.originId.equals(row.get("originId"))) {
                continue;
            }
            if (reference.loaderId != null && !contains(row.get("definingLoaderIds"), reference.loaderId)
                    && !contains(row.get("loaderIds"), reference.loaderId)) {
                continue;
            }
            if (found != null) {
                throw new NativeAccessFailure("ambiguous_origin", reference.name() + "; select originId");
            }
            found = row;
        }
        if (found == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(found);
        result.putAll(structural);
        result.put("name", found.get("name"));
        result.put("ref", typeReference(found));
        flags(result);
        Object parent = found.get("superName");
        if (parent instanceof String) {
            result.put("superType", NativeTypeRef.named((String) parent, "runtime", null, null));
        }
        List<NativeTypeRef> interfaces = new ArrayList<>();
        if (found.get("interfaces") instanceof List) {
            for (Object name : (List<?>) found.get("interfaces")) {
                interfaces.add(NativeTypeRef.named((String) name, "runtime", null, null));
            }
        }
        result.put("interfaceTypes", interfaces);
        result.put("members", CatalogValues.record("kind", "members", "declaringType", found.get("name"), "originId",
                found.get("originId")));
        List<Map<String, Object>> constants = new ArrayList<>();
        for (Map<String, Object> member : (((Number) found.get("modifiers")).intValue() & 16384) != 0
                ? snapshot.rows("members")
                : Collections.<Map<String, Object>>emptyList()) {
            if (found.get("originId").equals(member.get("originId"))
                    && found.get("name").equals(member.get("declaringType")) && "FIELD".equals(member.get("kind"))
                    && (((Number) member.get("modifiers")).intValue() & 16384) != 0) {
                constants.add(CatalogValues.record("name", member.get("name"), "ref", memberReference(member)));
            }
        }
        if (!constants.isEmpty()) {
            result.put("enumConstants", constants);
        }
        return result;
    }

    public static Map<String, Object> describe(NativeMemberRef reference, NativeIndexSnapshot snapshot) {
        Map<String, Object> type = describe(
                reference.declaringType == null ? reference.lookupType : reference.declaringType, snapshot);
        if (type == null) {
            return null;
        }
        List<Map<String, Object>> owners = hierarchy(type, snapshot,
                reference.declaringType != null || reference.kind == NativeMemberRef.Kind.CONSTRUCTOR);
        Map<String, List<Map<String, Object>>> members = new LinkedHashMap<>();
        for (Map<String, Object> row : snapshot.rows("members")) {
            members.computeIfAbsent(row.get("originId") + ":" + row.get("declaringType"), key -> new ArrayList<>())
                    .add(row);
        }
        Map<String, Set<String>> aliases = new LinkedHashMap<>();
        for (Map<String, Object> owner : owners) {
            for (Map<String, Object> row : members.getOrDefault(owner.get("originId") + ":" + owner.get("name"),
                    Collections.emptyList())) {
                if ("METHOD".equals(row.get("kind")) && (((Number) row.get("modifiers")).intValue() & 10) == 0) {
                    aliases.computeIfAbsent(row.get("runtimeName") + ":" + row.get("runtimeDescriptor"),
                            key -> new HashSet<>()).add((String) row.get("name"));
                }
            }
        }
        Map<String, Object> found = null;
        for (Map<String, Object> owner : owners) {
            for (Map<String, Object> row : members.getOrDefault(owner.get("originId") + ":" + owner.get("name"),
                    Collections.emptyList())) {
                if (!reference.kind.name().equals(row.get("kind")) || !owner.get("originId").equals(row.get("originId"))
                        || !owner.get("name").equals(row.get("declaringType"))) {
                    continue;
                }
                if (reference.kind != NativeMemberRef.Kind.CONSTRUCTOR && !memberNameMatches(reference, row, aliases)) {
                    continue;
                }
                if (reference.descriptor != null && !reference.descriptor.equals(row.get("descriptor"))
                        && !reference.descriptor.equals(row.get("runtimeDescriptor"))
                        && (reference.descriptorNames.equals("runtime")
                                || !runtimeDescriptor(reference.descriptor, snapshot)
                                        .equals(row.get("runtimeDescriptor")))) {
                    continue;
                }
                if (found != null) {
                    int flags = ((Number) row.get("modifiers")).intValue();
                    if (reference.kind == NativeMemberRef.Kind.METHOD && (flags & 10) == 0
                            && row.get("runtimeName").equals(found.get("runtimeName"))
                            && row.get("runtimeDescriptor").equals(found.get("runtimeDescriptor"))) {
                        continue;
                    }
                    throw new NativeAccessFailure("ambiguous_member", reference.key());
                }
                found = row;
            }
        }
        if (found == null) {
            return null;
        }
        Map<String, Object> result = memberRecord(found, snapshot.generation);
        result.put("lookupType", reference.lookupType);
        return result;
    }

    private static boolean memberNameMatches(NativeMemberRef reference, Map<String, Object> row,
            Map<String, Set<String>> aliases) {
        if (reference.names.equals("runtime")) {
            return reference.name.equals(row.get("runtimeName"));
        }
        if (reference.name.equals(row.get("name")) || reference.name.equals(row.get("runtimeName"))) {
            return true;
        }
        int flags = ((Number) row.get("modifiers")).intValue();
        if (reference.kind != NativeMemberRef.Kind.METHOD || reference.names.equals("runtime") || (flags & 10) != 0) {
            return false;
        }
        return aliases.getOrDefault(row.get("runtimeName") + ":" + row.get("runtimeDescriptor"), Collections.emptySet())
                .contains(reference.name);
    }

    private static String runtimeDescriptor(String descriptor, NativeIndexSnapshot snapshot) {
        return JvmDescriptors.remap(descriptor, name -> {
            for (Map<String, Object> type : snapshot.rows("classes")) {
                if ("mapped".equals(type.get("nameDomain")) && name.replace('/', '.').equals(type.get("name"))) {
                    return ((String) type.get("runtimeName")).replace('.', '/');
                }
            }
            return name;
        });
    }

    private static List<Map<String, Object>> hierarchy(Map<String, Object> start, NativeIndexSnapshot snapshot,
            boolean declaredOnly) {
        List<Map<String, Object>> result = new ArrayList<>();
        Deque<Map<String, Object>> pending = new ArrayDeque<>();
        Set<Object> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            Map<String, Object> type = pending.removeFirst();
            if (!visited.add(type.get("id"))) {
                continue;
            }
            if (visited.size() > 256) {
                throw new NativeAccessFailure("index_incomplete", "metadata hierarchy limit exceeded");
            }
            result.add(type);
            if (declaredOnly) {
                continue;
            }
            List<String> parents = new ArrayList<>();
            if (type.get("superName") instanceof String) {
                parents.add((String) type.get("superName"));
            }
            if (type.get("interfaces") instanceof List) {
                for (Object parent : (List<?>) type.get("interfaces")) {
                    parents.add((String) parent);
                }
            }
            for (String parent : parents) {
                List<Map<String, Object>> candidates = new ArrayList<>();
                for (Map<String, Object> row : snapshot.rows("classes")) {
                    if (parent.replace('/', '.').equals(row.get("runtimeName"))) {
                        candidates.add(row);
                    }
                }
                Map<String, Object> selected = parentMetadata(type, candidates, parent);
                if (selected != null) {
                    pending.add(selected);
                }
            }
        }
        return result;
    }

    private static Map<String, Object> parentMetadata(Map<String, Object> child, List<Map<String, Object>> candidates,
            String name) {
        if (candidates.size() <= 1) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        // This describes a class-file catalog hierarchy, not proof of runtime
        // superclass identity.
        // A patched game JAR and its original can both contain the same parent. Keep
        // the selected
        // archive's structural graph when present; otherwise use unique observed
        // provenance.
        for (Map<String, Object> row : candidates) {
            if (child.get("originId").equals(row.get("originId"))) {
                return row;
            }
        }
        Map<String, Object> observed = null;
        for (Map<String, Object> row : candidates) {
            if (row.get("definingLoaderIds") instanceof List && !((List<?>) row.get("definingLoaderIds")).isEmpty()) {
                if (observed != null) {
                    throw new NativeAccessFailure("index_incomplete", "ambiguous metadata ancestor " + name);
                }
                observed = row;
            }
        }
        if (observed == null) {
            throw new NativeAccessFailure("index_incomplete", "ambiguous metadata ancestor " + name);
        }
        return observed;
    }

    public static Map<String, Object> memberRecord(Map<String, Object> row, long generation) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        NativeMemberRef reference = memberReference(row);
        result.put("ref", reference);
        result.put("lookupType", reference.lookupType);
        result.put("declaringTypeRef", reference.lookupType);
        result.put("generation", generation);
        flags(result);
        if (reference.kind == NativeMemberRef.Kind.FIELD) {
            result.put("valueType", reference.valueType());
        } else {
            List<Map<String, Object>> parameters = new ArrayList<>();
            List<?> names = row.get("parameterNames") instanceof List
                    ? (List<?>) row.get("parameterNames")
                    : Collections.emptyList();
            List<NativeTypeRef> types = reference.parameters();
            for (int i = 0; i < types.size(); i++) {
                parameters.add(CatalogValues.record("index", i, "type", types.get(i), "name",
                        i < names.size() ? names.get(i) : null));
            }
            result.put("parameters", parameters);
            if (reference.kind == NativeMemberRef.Kind.METHOD) {
                result.put("returnType", reference.valueType());
            }
        }
        return result;
    }

    public static NativeMemberRef memberReference(Map<String, Object> row) {
        NativeMemberRef.Kind kind = NativeMemberRef.Kind.valueOf(CatalogValues.text(row, "kind"));
        boolean runtime = "runtime".equals(row.get("nameDomain"));
        NativeTypeRef owner = NativeTypeRef.named(CatalogValues.text(row, "declaringType"),
                runtime ? "runtime" : "auto", uniqueLoader(row), (String) row.get("originId"));
        return new NativeMemberRef(kind, owner, null,
                kind == NativeMemberRef.Kind.CONSTRUCTOR ? "new" : CatalogValues.text(row, "name"),
                CatalogValues.text(row, "descriptor"), runtime ? "runtime" : "auto", runtime ? "runtime" : "auto");
    }

    public static NativeTypeRef typeReference(Map<String, Object> row) {
        return NativeTypeRef.named(CatalogValues.text(row, "name"),
                "runtime".equals(row.get("nameDomain")) ? "runtime" : "auto", uniqueLoader(row),
                (String) row.get("originId"));
    }

    private static String uniqueLoader(Map<String, Object> row) {
        Object ids = row.get("definingLoaderIds");
        return ids instanceof List && ((List<?>) ids).size() == 1 ? (String) ((List<?>) ids).get(0) : null;
    }

    private static boolean contains(Object value, String identity) {
        return value instanceof List && ((List<?>) value).contains(identity);
    }

    private static void flags(Map<String, Object> result) {
        int flags = ((Number) result.getOrDefault("modifiers", 0)).intValue();
        result.put("static", (flags & 8) != 0);
        result.put("final", (flags & 16) != 0);
        result.put("abstract", (flags & 1024) != 0);
        result.put("synthetic", (flags & 4096) != 0);
        result.put("bridge", "METHOD".equals(result.get("kind")) && (flags & 64) != 0);
        result.put("varargs", !"FIELD".equals(result.get("kind")) && (flags & 128) != 0);
        result.put("accessSupport",
                "public".equals(result.get("access")) ? "public_candidate" : "exact_direct_required");
    }
}
