package betamoon.interop.live;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Test-only driver entry at the real client tick boundary; never included in
 * the mod artifact.
 */
public final class NativeLiveClientAgent {
    private NativeLiveClientAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                NativeLiveClientAgent.class.getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny"));
        RuntimeNamespace namespace = RuntimeNamespace.valueOf(System.getProperty("betamoon.live.namespace", "NAMED"));
        ResolvedMethod target = mappings.resolveMethod(
                new MethodRef(new ClassRef("net/minecraft/client/Minecraft"), "runTick", "()V"), namespace);
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain,
                    byte[] bytes) {
                if (!target.getOwner().equals(name)) {
                    return null;
                }
                ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, output) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                            String[] exceptions) {
                        MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (!name.equals(target.getName()) || !descriptor.equals(target.getDescriptor())) {
                            return method;
                        }
                        return new MethodVisitor(Opcodes.ASM9, method) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                visitVarInsn(Opcodes.ALOAD, 0);
                                visitMethodInsn(Opcodes.INVOKESTATIC, "betamoon/interop/live/NativeLiveClientDriver",
                                        "beforeTick", "(Ljava/lang/Object;)V", false);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    visitVarInsn(Opcodes.ALOAD, 0);
                                    visitMethodInsn(Opcodes.INVOKESTATIC,
                                            "betamoon/interop/live/NativeLiveClientDriver", "tick",
                                            "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                }, 0);
                return output.toByteArray();
            }
        });
    }
}
