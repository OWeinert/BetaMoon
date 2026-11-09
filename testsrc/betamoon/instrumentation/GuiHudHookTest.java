package betamoon.instrumentation;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.ResolvedField;
import betamoon.instrumentation.mapping.ResolvedMethod;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.BuiltinHookModules;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Audits real native HUD bytes and independent fallback for unfamiliar shapes.
 */
final class GuiHudHookTest {
    private static final ClassRef HUD = new ClassRef("net/minecraft/src/GuiIngame");
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/gui/GuiHudCallbacks";

    static void run(TinyMappingResolver mappings, String clientJar) throws Exception {
        for (RuntimeNamespace namespace : new RuntimeNamespace[]{RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT}) {
            byte[] original = source(mappings, namespace, clientJar);
            for (int mutation = 0; mutation < 3; mutation++) {
                verify(mappings, namespace, original, mutation);
            }
        }
    }

    private static void verify(TinyMappingResolver mappings, RuntimeNamespace namespace, byte[] original, int mutation)
            throws Exception {
        ClassNode source = new ClassNode();
        new ClassReader(original).accept(source, 0);
        ResolvedMethod render = mappings.resolveMethod(new MethodRef(HUD, "renderGameOverlay", "(FZII)V"), namespace);
        MethodNode method = source.methods.stream()
                .filter(value -> value.name.equals(render.getName()) && value.desc.equals(render.getDescriptor()))
                .findFirst().get();
        if (mutation == 1) {
            ResolvedMethod rectangle = mappings.resolveMethod(
                    new MethodRef(new ClassRef("net/minecraft/src/Gui"), "drawTexturedModalRect", "(IIIIII)V"),
                    namespace);
            boolean changed = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).name.equals(rectangle.getName())
                        && ((MethodInsnNode) instruction).desc.equals(rectangle.getDescriptor())) {
                    AbstractInsnNode height = instruction.getPrevious();
                    while (height.getOpcode() < 0) {
                        height = height.getPrevious();
                    }
                    method.instructions.set(height, new LdcInsnNode(23));
                    changed = true;
                    break;
                }
            }
            require(changed, "Missing native rectangle to mutate");
        } else if (mutation == 2) {
            ResolvedField messages = mappings.resolveField(new FieldRef(HUD, "chatMessageList", "Ljava/util/List;"),
                    namespace);
            InsnList extraRead = new InsnList();
            extraRead.add(new VarInsnNode(Opcodes.ALOAD, 0));
            extraRead.add(new FieldInsnNode(Opcodes.GETFIELD, messages.getOwner(), messages.getName(),
                    messages.getDescriptor()));
            extraRead.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(extraRead);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        source.accept(writer);
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        BuiltinHookModules.registerAll(registry);
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, namespace, namespace),
                mappings, report, true, false);
        byte[] transformed = transformer.transform(null, source.name, null, null, writer.toByteArray());
        require(transformed != null && !report.hasFailures(), "HUD shape fallback discarded unrelated hooks");
        require(count(transformed, "texture") == (mutation == 1 ? 0 : 13),
                "Atlas redirect accepted unknown geometry or rejected supported geometry in " + namespace);
        require(count(transformed, "chat") == (mutation == 2 ? 0 : 4),
                "Chat read filter accepted an unknown read count or rejected native reads in " + namespace);
        require(count(transformed, "beforeNative") == 1 && count(transformed, "afterNative") == 2
                && count(transformed, "beforeHotbar") == 1 && count(transformed, "armor") == 1,
                "An optional HUD shape displaced independent lifecycle/item/armor seams");
        String refused = mutation == 1 ? "textures" : "chat";
        if (mutation != 0) {
            require(report.snapshot().stream()
                    .anyMatch(value -> value.getHookId().equals("betamoon:gui_hud:" + refused)
                            && value.getStatus() == HookStatus.NO_MATCH),
                    "Unsupported HUD shape did not report NO_MATCH");
        }
        require(transformer.transform(null, source.name, null, null, transformed) == null,
                "HUD hooks were not idempotent after optional shape fallback");
    }

    private static int count(byte[] bytes, String name) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals(CALLBACKS)
                        && ((MethodInsnNode) instruction).name.equals(name)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static byte[] source(TinyMappingResolver mappings, RuntimeNamespace namespace, String clientJar)
            throws Exception {
        String path = mappings.resolveClass(HUD, namespace) + ".class";
        if (namespace == RuntimeNamespace.NAMED) {
            try (InputStream input = GuiHudHookTest.class.getResourceAsStream("/" + path)) {
                require(input != null, "Missing compiled named HUD fixture");
                return read(input);
            }
        }
        try (ZipFile jar = new ZipFile(clientJar); InputStream input = jar.getInputStream(jar.getEntry(path))) {
            return read(input);
        }
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
