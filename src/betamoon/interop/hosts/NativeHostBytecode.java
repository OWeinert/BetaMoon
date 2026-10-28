package betamoon.interop.hosts;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Generated classes link only their native base/signatures and JDK transport
 * types.
 */
final class NativeHostBytecode {
    private static final Type OBJECT = Type.getType(Object.class);
    private static final Type OBJECTS = Type.getType(Object[].class);
    private static final Type FUNCTION = Type.getType(Function.class);
    private static final String DISPATCH = "betamoon$host$dispatch";

    private NativeHostBytecode() {
    }

    static NativeHostShape generate(Class<?> base, Constructor<?> constructor, List<Method> methods,
            Map<String, Method> inherited, Map<String, Method> bridges, String fingerprint)
            throws ReflectiveOperationException {
        String helperPrefix = helperPrefix(base);
        String name = "betamoon/generated/hosts/Host$" + fingerprint;
        Type self = Type.getObjectType(name);
        Type parent = Type.getType(base);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null,
                parent.getInternalName(), null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, DISPATCH, FUNCTION.getDescriptor(), null, null)
                .visitEnd();
        Type[] original = Type.getArgumentTypes(Type.getConstructorDescriptor(constructor));
        Type[] parameters = new Type[original.length + 1];
        parameters[0] = FUNCTION;
        System.arraycopy(original, 0, parameters, 1, original.length);
        GeneratorAdapter create = method(writer, Opcodes.ACC_PUBLIC, "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, parameters));
        create.loadThis();
        for (int index = 1; index < parameters.length; index++) {
            create.loadArg(index);
        }
        create.invokeConstructor(parent,
                new org.objectweb.asm.commons.Method("<init>", Type.getConstructorDescriptor(constructor)));
        create.loadThis();
        create.loadArg(0);
        create.putField(self, DISPATCH, FUNCTION);
        create.returnValue();
        create.endMethod();
        for (int index = 0; index < methods.size(); index++) {
            Method target = methods.get(index);
            String descriptor = Type.getMethodDescriptor(target);
            GeneratorAdapter code = method(writer, Opcodes.ACC_PUBLIC, target.getName(), descriptor);
            Label active = code.newLabel();
            code.loadThis();
            code.getField(self, DISPATCH, FUNCTION);
            code.ifNonNull(active);
            nativeSuper(code, parent, target, "unsupported_native_host_construction");
            code.mark(active);
            code.loadThis();
            code.getField(self, DISPATCH, FUNCTION);
            code.push(3);
            code.newArray(OBJECT);
            code.dup();
            code.push(0);
            code.push(index);
            code.box(Type.INT_TYPE);
            code.arrayStore(OBJECT);
            code.dup();
            code.push(1);
            code.loadThis();
            code.arrayStore(OBJECT);
            code.dup();
            code.push(2);
            code.loadArgArray();
            code.arrayStore(OBJECT);
            code.invokeInterface(FUNCTION,
                    new org.objectweb.asm.commons.Method("apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
            code.checkCast(OBJECTS);
            int outcome = code.newLocal(OBJECTS);
            code.storeLocal(outcome);
            code.loadLocal(outcome);
            code.push(0);
            code.arrayLoad(OBJECT);
            code.unbox(Type.BOOLEAN_TYPE);
            Label callbackResult = code.newLabel();
            code.ifZCmp(GeneratorAdapter.EQ, callbackResult);
            nativeSuper(code, parent, target, "native_host_super_unavailable");
            code.mark(callbackResult);
            Type result = Type.getReturnType(descriptor);
            if (result.getSort() != Type.VOID) {
                code.loadLocal(outcome);
                code.push(1);
                code.arrayLoad(OBJECT);
                code.unbox(result);
            }
            code.returnValue();
            code.endMethod();
            if (!Modifier.isAbstract(target.getModifiers())) {
                GeneratorAdapter helper = method(writer, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                        helperPrefix + index, descriptor);
                nativeSuper(helper, parent, target, "native_host_super_unavailable");
                helper.endMethod();
            }
        }
        for (Map.Entry<String, Method> bridge : bridges.entrySet()) {
            Method target = bridge.getValue();
            Method contract = inherited.get(bridge.getKey());
            GeneratorAdapter code = method(writer, Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                    contract.getName(), Type.getMethodDescriptor(contract));
            code.loadThis();
            code.loadArgs();
            code.invokeVirtual(self,
                    new org.objectweb.asm.commons.Method(target.getName(), Type.getMethodDescriptor(target)));
            code.returnValue();
            code.endMethod();
        }
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> generated = new GeneratedLoader(base.getClassLoader()).define(name.replace('/', '.'), bytes);
        return new NativeHostShape(base, constructor, generated, methods, fingerprint, helperPrefix);
    }

    private static String helperPrefix(Class<?> base) {
        Set<String> names = new HashSet<>();
        for (Class<?> current = base; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                names.add(method.getName());
            }
        }
        for (Method method : base.getMethods()) {
            names.add(method.getName());
        }
        String prefix = "betamoon$host$super$";
        while (true) {
            boolean collision = false;
            for (String name : names) {
                if (name.startsWith(prefix)) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                return prefix;
            }
            prefix += "$";
        }
    }

    private static GeneratorAdapter method(ClassWriter writer, int flags, String name, String descriptor) {
        GeneratorAdapter code = new GeneratorAdapter(writer.visitMethod(flags, name, descriptor, null, null), flags,
                name, descriptor);
        code.visitCode();
        return code;
    }

    private static void nativeSuper(GeneratorAdapter code, Type parent, Method target, String reason) {
        if (Modifier.isAbstract(target.getModifiers())) {
            code.throwException(Type.getType(IllegalStateException.class), reason + ": " + target.getName());
            return;
        }
        code.loadThis();
        code.loadArgs();
        code.visitMethodInsn(Opcodes.INVOKESPECIAL, parent.getInternalName(), target.getName(),
                Type.getMethodDescriptor(target), false);
        code.returnValue();
    }

    private static final class GeneratedLoader extends ClassLoader {
        GeneratedLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
