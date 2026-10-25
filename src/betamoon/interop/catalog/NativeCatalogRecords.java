package betamoon.interop.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit schema records. No reflective getter traversal or native object evaluation occurs here. */
public final class NativeCatalogRecords {
    public static final int SCHEMA_VERSION = 1;
    public static final String EXTRACTOR_VERSION = "betamoon-classfile-1";

    private NativeCatalogRecords() {
    }

    public static NativeClassInfo readType(Map<String, Object> record) {
        List<NativeMemberInfo> members = new ArrayList<>();
        Object entries = record.get("members");
        if (!(entries instanceof List) || ((List<?>) entries).size() > 65535) {
            throw new IllegalArgumentException("Invalid class member inventory");
        }
        for (Object entry : (List<?>) entries) {
            Map<String, Object> member = CatalogValues.object(entry);
            members.add(new NativeMemberInfo(NativeMemberInfo.Kind.valueOf(CatalogValues.text(member, "kind")),
                    CatalogValues.text(member, "name"), CatalogValues.text(member, "descriptor"),
                    CatalogValues.integer(member, "access"), optionalText(member, "signature"), constant(member),
                    CatalogValues.strings(member.get("exceptions")), CatalogValues.strings(member.get("parameterNames"))));
        }
        return new NativeClassInfo(CatalogValues.text(record, "name"), CatalogValues.integer(record, "classFileVersion"),
                CatalogValues.integer(record, "access"), optionalText(record, "superName"),
                CatalogValues.strings(record.get("interfaces")), optionalText(record, "signature"),
                optionalText(record, "sourceFile"), optionalText(record, "outerName"),
                optionalText(record, "enclosingMethod"), optionalText(record, "enclosingDescriptor"), members);
    }

    private static String optionalText(Map<String, Object> record, String key) {
        return record.get(key) == null ? null : CatalogValues.text(record, key);
    }

    private static Object constant(Map<String, Object> member) {
        if (member.get("compiledConstant") == null) {
            return null;
        }
        Map<String, Object> constant = CatalogValues.object(member.get("compiledConstant"));
        String value = CatalogValues.text(constant, "value");
        switch (CatalogValues.text(constant, "kind")) {
            case "Integer":
                return Integer.valueOf(value);
            case "Long":
                return Long.valueOf(value);
            case "Float":
                return Float.valueOf(value);
            case "Double":
                return Double.valueOf(value);
            case "String":
                return value;
            default:
                throw new IllegalArgumentException("Unknown compiled constant type");
        }
    }

    public static Map<String, Object> type(NativeClassInfo type) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", type.getInternalName());
        record.put("binaryName", type.getInternalName().replace('/', '.'));
        record.put("kind", type.getKind());
        record.put("classFileVersion", type.getVersion());
        record.put("access", type.getAccess());
        record.put("superName", type.getSuperName());
        record.put("interfaces", type.getInterfaces());
        record.put("signature", type.getSignature());
        record.put("sourceFile", type.getSourceFile());
        record.put("outerName", type.getOuterName());
        record.put("enclosingMethod", type.getEnclosingMethod());
        record.put("enclosingDescriptor", type.getEnclosingDescriptor());
        List<Object> members = new ArrayList<>();
        for (NativeMemberInfo member : type.getMembers().values()) {
            members.add(member(type, member));
        }
        record.put("members", members);
        return record;
    }

    public static Map<String, Object> member(NativeClassInfo owner, NativeMemberInfo member) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("declaringOwner", owner.getInternalName());
        record.put("kind", member.getKind().name());
        record.put("name", member.getName());
        record.put("descriptor", member.getDescriptor());
        record.put("access", member.getAccess());
        record.put("signature", member.getSignature());
        record.put("exceptions", member.getExceptions());
        record.put("parameterNames", member.getParameterNames());
        record.put("publicCandidate", member.isPublicCandidate() && (owner.getAccess() & 1) != 0);
        // Even public candidates require loader, signature, phase and lifetime checks before execution.
        record.put("invocability", "not_bound");
        Object constant = member.getConstantValue();
        if (constant != null) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("kind", constant.getClass().getSimpleName());
            // Decimal text preserves exact longs and nonfinite floating constants in valid JSON.
            value.put("value", constant.toString());
            record.put("compiledConstant", value);
        }
        return record;
    }
}
