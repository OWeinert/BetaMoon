package betamoon.interop.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reads bytecode structure without defining a class or visiting executable
 * method bodies.
 */
public final class ClassFileMetadataReader {
    public NativeClassInfo read(byte[] bytes) {
        MetadataVisitor visitor = new MetadataVisitor();
        new ClassReader(bytes).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        return visitor.result();
    }

    private static final class MetadataVisitor extends ClassVisitor {
        private String name;
        private int version;
        private int access;
        private String superName;
        private List<String> interfaces;
        private String signature;
        private String sourceFile;
        private String outerName;
        private String enclosingMethod;
        private String enclosingDescriptor;
        private final List<NativeMemberInfo> members = new ArrayList<>();

        private MetadataVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.name = name;
            this.version = version;
            this.access = access;
            this.signature = signature;
            this.superName = superName;
            this.interfaces = Arrays.asList(interfaces);
        }

        @Override
        public void visitSource(String source, String debug) {
            sourceFile = source;
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            outerName = owner;
            enclosingMethod = name;
            enclosingDescriptor = descriptor;
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (this.name.equals(name)) {
                if (outerName != null) {
                    this.outerName = outerName;
                }
                // Nested-type visibility is carried by InnerClasses, not the class header.
                int visibility = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
                this.access = (this.access & ~visibility) | (access & visibility);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            members.add(new NativeMemberInfo(NativeMemberInfo.Kind.FIELD, name, descriptor, access, signature, value,
                    Collections.emptyList(), Collections.emptyList()));
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            if ((access & Opcodes.ACC_SYNTHETIC) != 0 && name.startsWith("betamoon$hook$body$")) {
                return null;
            }
            NativeMemberInfo.Kind kind = NativeMemberInfo.Kind.METHOD;
            if ("<init>".equals(name)) {
                kind = NativeMemberInfo.Kind.CONSTRUCTOR;
            } else if ("<clinit>".equals(name)) {
                kind = NativeMemberInfo.Kind.CLASS_INITIALIZER;
            }
            final NativeMemberInfo.Kind memberKind = kind;
            return new MethodVisitor(Opcodes.ASM9) {
                private final List<String> parameterNames = new ArrayList<>();

                @Override
                public void visitParameter(String parameterName, int parameterAccess) {
                    parameterNames.add(parameterName);
                }

                @Override
                public void visitEnd() {
                    members.add(new NativeMemberInfo(memberKind, name, descriptor, access, signature, null,
                            exceptions == null ? Collections.emptyList() : Arrays.asList(exceptions), parameterNames));
                }
            };
        }

        private NativeClassInfo result() {
            return new NativeClassInfo(name, version, access, superName, interfaces, signature, sourceFile, outerName,
                    enclosingMethod, enclosingDescriptor, members);
        }
    }
}
