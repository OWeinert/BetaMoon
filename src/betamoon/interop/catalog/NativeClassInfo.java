package betamoon.interop.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.objectweb.asm.Opcodes;

/** Immutable class-file metadata; contains no Class, loader, live object, or execution context. */
public final class NativeClassInfo {
    private final String internalName;
    private final int version;
    private final int access;
    private final String superName;
    private final List<String> interfaces;
    private final String signature;
    private final String sourceFile;
    private final String outerName;
    private final String enclosingMethod;
    private final String enclosingDescriptor;
    private final Map<String, NativeMemberInfo> members;

    public NativeClassInfo(String internalName, int version, int access, String superName, List<String> interfaces,
            String signature, String sourceFile, String outerName, String enclosingMethod, String enclosingDescriptor,
            List<NativeMemberInfo> members) {
        this.internalName = JvmDescriptors.requireInternalName(internalName);
        this.version = version;
        this.access = access;
        this.superName = superName == null ? null : JvmDescriptors.requireInternalName(superName);
        List<String> checkedInterfaces = new ArrayList<>();
        for (String interfaceName : interfaces) {
            checkedInterfaces.add(JvmDescriptors.requireInternalName(interfaceName));
        }
        this.interfaces = Collections.unmodifiableList(checkedInterfaces);
        this.signature = signature;
        this.sourceFile = sourceFile;
        this.outerName = outerName == null ? null : JvmDescriptors.requireInternalName(outerName);
        this.enclosingMethod = enclosingMethod;
        this.enclosingDescriptor = enclosingDescriptor;
        Map<String, NativeMemberInfo> byKey = new TreeMap<>();
        for (NativeMemberInfo member : members) {
            if (byKey.put(member.getKey(), member) != null) {
                throw new IllegalArgumentException("Duplicate member in " + internalName + ": " + member.getKey());
            }
        }
        this.members = Collections.unmodifiableMap(byKey);
    }

    public String getInternalName() {
        return internalName;
    }

    public int getVersion() {
        return version;
    }

    public int getAccess() {
        return access;
    }

    public String getKind() {
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            return "annotation";
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            return "enum";
        }
        return (access & Opcodes.ACC_INTERFACE) != 0 ? "interface" : "class";
    }

    public String getSuperName() {
        return superName;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public String getSignature() {
        return signature;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getOuterName() {
        return outerName;
    }

    public String getEnclosingMethod() {
        return enclosingMethod;
    }

    public String getEnclosingDescriptor() {
        return enclosingDescriptor;
    }

    public Map<String, NativeMemberInfo> getMembers() {
        return members;
    }

    public NativeMemberInfo findMember(boolean method, String name, String descriptor) {
        NativeMemberInfo.Kind kind = method ? NativeMemberInfo.Kind.METHOD : NativeMemberInfo.Kind.FIELD;
        if (method && "<init>".equals(name)) {
            kind = NativeMemberInfo.Kind.CONSTRUCTOR;
        } else if (method && "<clinit>".equals(name)) {
            kind = NativeMemberInfo.Kind.CLASS_INITIALIZER;
        }
        return members.get(kind + ":" + name + ":" + descriptor);
    }
}
