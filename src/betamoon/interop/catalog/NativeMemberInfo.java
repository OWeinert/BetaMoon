package betamoon.interop.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Opcodes;

/** Detached declared-member metadata. Presence never implies permission to invoke a member. */
public final class NativeMemberInfo {
    public enum Kind {
        FIELD, METHOD, CONSTRUCTOR, CLASS_INITIALIZER
    }

    private final Kind kind;
    private final String name;
    private final String descriptor;
    private final int access;
    private final String signature;
    private final Object constantValue;
    private final List<String> exceptions;
    private final List<String> parameterNames;

    public NativeMemberInfo(Kind kind, String name, String descriptor, int access, String signature,
            Object constantValue, List<String> exceptions, List<String> parameterNames) {
        if (kind == null || name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Member kind and name are required");
        }
        if (kind == Kind.FIELD) {
            JvmDescriptors.requireField(descriptor);
        } else {
            JvmDescriptors.requireMethod(descriptor);
        }
        if ((kind == Kind.CONSTRUCTOR && (!"<init>".equals(name) || !descriptor.endsWith(")V")))
                || (kind == Kind.CLASS_INITIALIZER && (!"<clinit>".equals(name) || !"()V".equals(descriptor)))) {
            throw new IllegalArgumentException("Invalid initializer selector: " + name + descriptor);
        }
        if (constantValue != null && !(constantValue instanceof String) && !(constantValue instanceof Integer)
                && !(constantValue instanceof Long) && !(constantValue instanceof Float)
                && !(constantValue instanceof Double)) {
            throw new IllegalArgumentException("Unsupported class-file constant: " + constantValue.getClass());
        }
        this.kind = kind;
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
        this.signature = signature;
        this.constantValue = constantValue;
        this.exceptions = Collections.unmodifiableList(new ArrayList<>(exceptions));
        this.parameterNames = Collections.unmodifiableList(new ArrayList<>(parameterNames));
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getAccess() {
        return access;
    }

    public String getSignature() {
        return signature;
    }

    public Object getConstantValue() {
        return constantValue;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public boolean isPublicCandidate() {
        return (access & Opcodes.ACC_PUBLIC) != 0 && kind != Kind.CLASS_INITIALIZER;
    }

    public String getKey() {
        return kind + ":" + name + ":" + descriptor;
    }
}
