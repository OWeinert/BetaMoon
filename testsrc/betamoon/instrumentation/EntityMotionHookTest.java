package betamoon.instrumentation;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.hooks.entity.EntityMotionHook;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.ClassTransformPlan;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Native field anchors and repeated transformation against real named and
 * obfuscated classes.
 */
final class EntityMotionHookTest {
    private EntityMotionHookTest() {
    }

    static void verify(TinyMappingResolver mappings, String clientJarPath) throws Exception {
        for (RuntimeNamespace namespace : new RuntimeNamespace[]{RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT}) {
            TransformationReport report = new TransformationReport();
            HookRegistry registry = new HookRegistry(report);
            registry.registerModule(new EntityMotionHook());
            Map<String, ClassTransformPlan> plans = registry.freeze(mappings, namespace);
            BetaMoonTransformer transformer = new BetaMoonTransformer(plans, mappings, report, true, false);
            for (String canonical : new String[]{"EntityLiving", "Entity", "EntityPlayerSP"}) {
                String owner = mappings.resolveClass(new ClassRef("net/minecraft/src/" + canonical), namespace);
                byte[] original;
                try (JarFile jar = new JarFile(clientJarPath)) {
                    InputStream source = namespace == RuntimeNamespace.NAMED
                            ? EntityMotionHookTest.class.getResourceAsStream("/" + owner + ".class")
                            : jar.getInputStream(jar.getJarEntry(owner + ".class"));
                    try (InputStream input = source) {
                        require(input != null, "Missing movement fixture " + owner);
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            bytes.write(buffer, 0, count);
                        }
                        original = bytes.toByteArray();
                    }
                }
                byte[] modified = transformer.transform(null, owner, null, null, original);
                require(modified != null, "Movement hooks did not modify " + owner);
                require(transformer.transform(null, owner, null, null, modified) == null,
                        "Movement hooks are not idempotent for " + owner);
                if (canonical.equals("EntityLiving")) {
                    require(calls(modified, "gravity") == 3, "Expected three native gravity consumers");
                    require(calls(modified, "dragY") == 3, "Expected three vertical drag consumers");
                    require(calls(modified, "dragX") == 3 && calls(modified, "dragZ") == 3,
                            "Expected land/water/lava horizontal drag consumers");
                    require(calls(modified, "jumpVelocity") == 1, "Ground jump must be scaled once");
                    require(calls(modified, "accelerate") == 3, "Expected three native acceleration consumers");
                    require(calls(modified, "collide") == 3, "Expected three original native collision calls");
                }
            }
            for (HookDiagnostic diagnostic : report.snapshot()) {
                require(diagnostic.getStatus() == HookStatus.ALREADY_APPLIED, "Movement hook unavailable in "
                        + namespace + ": " + diagnostic.getHookId() + " (" + diagnostic.getStatus() + ")");
            }
        }
    }

    private static int calls(byte[] bytes, String name) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals(name)
                        && ((MethodInsnNode) instruction).owner
                                .equals("betamoon/instrumentation/hooks/entity/EntityMotionCallbacks")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
