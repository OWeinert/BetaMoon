package betamoon.interop.reference;

import betamoon.interop.catalog.JvmDescriptors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exact member identity. Field descriptors may be omitted until an unambiguous
 * field is selected.
 */
public final class NativeMemberRef {
    public enum Kind {
        METHOD, FIELD, CONSTRUCTOR
    }

    public final Kind kind;
    public final NativeTypeRef lookupType;
    public final NativeTypeRef declaringType;
    public final String name;
    public final String descriptor;
    public final String names;
    public final String descriptorNames;

    public NativeMemberRef(Kind kind, NativeTypeRef lookupType, NativeTypeRef declaringType, String name,
            String descriptor, String names, String descriptorNames) {
        if (name == null || name.isEmpty() || name.length() > 1024 || name.indexOf('\0') >= 0
                || name.matches(".*[.;\\[/<>].*")) {
            throw new IllegalArgumentException("invalid_member_name");
        }
        if (descriptor != null && descriptor.length() > 8192) {
            throw new IllegalArgumentException("invalid_member_descriptor");
        }
        if (kind == Kind.FIELD) {
            if (descriptor != null) {
                JvmDescriptors.requireField(descriptor);
            }
        } else {
            JvmDescriptors.requireMethod(descriptor);
            if (kind == Kind.CONSTRUCTOR && !descriptor.endsWith(")V")) {
                throw new IllegalArgumentException("constructor_must_return_void");
            }
        }
        if (!lookupType.descriptor.startsWith("L")) {
            throw new IllegalArgumentException("member_requires_class_type");
        }
        this.kind = kind;
        this.lookupType = lookupType;
        this.declaringType = declaringType;
        this.name = name;
        this.descriptor = descriptor;
        this.names = NativeTypeRef.policy(names == null ? lookupType.names : names);
        this.descriptorNames = NativeTypeRef.policy(descriptorNames);
    }

    public List<NativeTypeRef> parameters() {
        if (kind == Kind.FIELD) {
            return Collections.emptyList();
        }
        List<NativeTypeRef> result = new ArrayList<>();
        int index = 1;
        while (descriptor.charAt(index) != ')') {
            int end = typeEnd(descriptor, index);
            result.add(NativeTypeRef.descriptor(descriptor.substring(index, end), descriptorNames));
            index = end;
        }
        return Collections.unmodifiableList(result);
    }

    public NativeTypeRef valueType() {
        return descriptor == null
                ? null
                : NativeTypeRef.descriptor(
                        kind == Kind.FIELD ? descriptor : descriptor.substring(descriptor.indexOf(')') + 1),
                        descriptorNames);
    }

    public String key() {
        return kind + ":" + lookupType.key() + ":" + (declaringType == null ? "" : declaringType.key()) + ":" + name
                + ":" + descriptor + ":" + names + ":" + descriptorNames;
    }

    private static int typeEnd(String descriptor, int start) {
        int index = start;
        while (descriptor.charAt(index) == '[') {
            index++;
        }
        return descriptor.charAt(index) == 'L' ? descriptor.indexOf(';', index) + 1 : index + 1;
    }
}
