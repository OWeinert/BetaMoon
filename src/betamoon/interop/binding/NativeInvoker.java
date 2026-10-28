package betamoon.interop.binding;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Fixed Java 8 adapters. Receiver policy and exact Lua conversion are enforced
 * by the calling plan.
 */
final class NativeInvoker {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType CALL = MethodType.methodType(Object.class, Object.class, Object[].class);
    private static final MethodType GET = MethodType.methodType(Object.class, Object.class);
    private static final MethodType SET = MethodType.methodType(void.class, Object.class, Object.class);

    private NativeInvoker() {
    }

    static MethodHandle call(NativeMemberPlan plan) throws IllegalAccessException {
        if (plan.member instanceof Method && plan.member.getDeclaringClass().getClassLoader() == null) {
            for (Annotation annotation : ((Method) plan.member).getDeclaredAnnotations()) {
                if (annotation.annotationType().getName().equals("sun.reflect.CallerSensitive")) {
                    throw new IllegalAccessException("Preserve the reflection caller for caller-sensitive methods");
                }
            }
        }
        if (plan.member instanceof Constructor && (plan.member.getDeclaringClass().isEnum()
                || Modifier.isAbstract(plan.member.getDeclaringClass().getModifiers()))) {
            throw new IllegalAccessException("Preserve reflective constructor admission");
        }
        MethodHandle handle = plan.member instanceof Method
                ? LOOKUP.unreflect((Method) plan.member)
                : LOOKUP.unreflectConstructor((Constructor<?>) plan.member);
        handle = handle.asFixedArity().asSpreader(Object[].class, plan.parameters.length);
        if (plan.isStatic || plan.member instanceof Constructor) {
            handle = MethodHandles.dropArguments(handle, 0, Object.class);
        }
        return handle.asType(CALL);
    }

    static MethodHandle get(NativeMemberPlan plan) throws IllegalAccessException {
        MethodHandle handle = LOOKUP.unreflectGetter((Field) plan.member);
        if (plan.isStatic) {
            handle = MethodHandles.dropArguments(handle, 0, Object.class);
        }
        return handle.asType(GET);
    }

    static MethodHandle set(NativeMemberPlan plan) throws IllegalAccessException {
        MethodHandle handle = LOOKUP.unreflectSetter((Field) plan.member);
        if (plan.isStatic) {
            handle = MethodHandles.dropArguments(handle, 0, Object.class);
        }
        return handle.asType(SET);
    }
}
