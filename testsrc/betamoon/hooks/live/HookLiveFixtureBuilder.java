package betamoon.hooks.live;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Builds a real separate fixture mod for either supported client namespace
 * without shipping it.
 */
public final class HookLiveFixtureBuilder {
    public static void main(String[] args) throws Exception {
        boolean named = args[0].equals("NAMED");
        String name = (named ? "net/minecraft/src/" : "") + "mod_NativeHookFixture";
        String parent = (named ? "net/minecraft/src/" : "") + "BaseMod";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, name, null, parent, null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitMethodInsn(Opcodes.INVOKESTATIC, "hookfixture/LiveHookTarget", "beforeOwnerInit", "()V",
                false);
        constructor.visitLdcInsn("betamoon.hooks.fixtureLoaded");
        constructor.visitLdcInsn("true");
        constructor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        constructor.visitInsn(Opcodes.POP);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor version = writer.visitMethod(Opcodes.ACC_PUBLIC, "Version", "()Ljava/lang/String;", null, null);
        version.visitCode();
        version.visitLdcInsn("hook-acceptance");
        version.visitInsn(Opcodes.ARETURN);
        version.visitMaxs(0, 0);
        version.visitEnd();
        writer.visitEnd();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(Paths.get(args[1])))) {
            byte[] entry = writer.toByteArray();
            output.putNextEntry(new JarEntry("mod_NativeHookFixture.class"));
            output.write(entry);
            output.closeEntry();
            if (named) {
                output.putNextEntry(new JarEntry(name + ".class"));
                output.write(entry);
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("hookfixture/LiveHookTarget.class"));
            try (InputStream input = HookLiveFixtureBuilder.class
                    .getResourceAsStream("/hookfixture/LiveHookTarget.class")) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                }
            }
            output.closeEntry();
        }
    }
}
