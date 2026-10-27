package betamoon.interop.binding;

import betamoon.interop.runtime.NativeContext;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.objectweb.asm.Type;

/**
 * Cached structure only. Receiver, owner and dynamic permissions are checked on
 * every operation.
 */
public final class NativeMemberPlan {
    public final Member member;
    public final Class<?>[] parameters;
    public final Class<?> valueType;
    public final String descriptor;
    public final boolean isStatic;
    public final boolean nonPublic;
    public final NativeContext.Effect effect;

    NativeMemberPlan(Member member, NativeNames names) {
        this.member = member;
        this.isStatic = Modifier.isStatic(member.getModifiers());
        this.nonPublic = !Modifier.isPublic(member.getModifiers())
                || !Modifier.isPublic(member.getDeclaringClass().getModifiers());
        if (member instanceof Method) {
            Method method = (Method) member;
            parameters = method.getParameterTypes();
            valueType = method.getReturnType();
            descriptor = Type.getMethodDescriptor(method);
            if (names.curatedClass(method.getDeclaringClass(), "java/lang/String")
                    && ((method.getName().equals("length") && descriptor.equals("()I"))
                            || (method.getName().equals("charAt") && descriptor.equals("(I)C"))
                            || (method.getName().equals("substring") && (descriptor.equals("(I)Ljava/lang/String;")
                                    || descriptor.equals("(II)Ljava/lang/String;"))))) {
                effect = NativeContext.Effect.READ;
            } else if (names.curatedClass(method.getDeclaringClass(), "net/minecraft/src/World")
                    && descriptor.equals("(III)I") && (names.methodMatches(method, "getBlockId", "mapped")
                            || names.methodMatches(method, "getBlockMetadata", "mapped"))) {
                // Both can load chunks. They are permitted gameplay operations, not pure
                // getters.
                effect = NativeContext.Effect.WRITE;
            } else if (names.curatedClass(method.getDeclaringClass(), "net/minecraft/src/ItemStack")
                    && parameters.length == 0 && valueType == method.getDeclaringClass()
                    && names.methodMatches(method, "copy", "mapped")) {
                effect = NativeContext.Effect.READ;
            } else {
                effect = NativeContext.Effect.UNKNOWN;
            }
        } else if (member instanceof Constructor) {
            Constructor<?> constructor = (Constructor<?>) member;
            parameters = constructor.getParameterTypes();
            valueType = constructor.getDeclaringClass();
            descriptor = Type.getConstructorDescriptor(constructor);
            effect = NativeContext.Effect.INITIALIZE;
        } else {
            parameters = new Class<?>[0];
            valueType = ((Field) member).getType();
            descriptor = Type.getDescriptor(valueType);
            effect = NativeContext.Effect.READ;
        }
    }

    public Object call(NativeContext context, boolean direct, Object receiver, Object[] arguments) {
        try {
            return executeCall(context, direct, receiver, arguments);
        } catch (NativeAccessFailure failure) {
            throw context.bindings == null
                    ? failure
                    : context.bindings.explain(failure, null, null, this, context, direct);
        }
    }

    public void preflightPolicy(NativeContext context, boolean direct) {
        context.check();
        accessibility(context, direct);
        context.require(isStatic ? NativeContext.Effect.INITIALIZE : effect, direct);
    }

    public void preflight(NativeContext context, boolean direct, Object receiver) {
        context.check();
        receiver(receiver);
        accessibility(context, direct);
        NativeContext.Effect actual = effect;
        if (member instanceof Method && receiver != null && actual != NativeContext.Effect.UNKNOWN) {
            Method selected = (Method) member;
            try {
                Method dispatched = receiver.getClass().getMethod(selected.getName(), parameters);
                if (dispatched.getDeclaringClass() != selected.getDeclaringClass()) {
                    actual = NativeContext.Effect.UNKNOWN;
                }
            } catch (NoSuchMethodException exception) {
                actual = NativeContext.Effect.UNKNOWN;
            } catch (SecurityException exception) {
                throw new NativeAccessFailure("access_denied", identity(), exception);
            } catch (LinkageError exception) {
                throw new NativeAccessFailure("native_linkage_failure", identity(), exception);
            }
        }
        context.require(isStatic ? NativeContext.Effect.INITIALIZE : actual, direct);
    }

    private Object executeCall(NativeContext context, boolean direct, Object receiver, Object[] arguments) {
        preflight(context, direct, receiver);
        try {
            Object result;
            if (member instanceof Method) {
                result = ((Method) member).invoke(receiver, arguments);
            } else {
                result = ((Constructor<?>) member).newInstance(arguments);
            }
            context.check();
            return result;
        } catch (InvocationTargetException exception) {
            throw nativeFailure(exception.getCause());
        } catch (ReflectiveOperationException | SecurityException exception) {
            throw new NativeAccessFailure("access_denied", identity(), exception);
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("native_linkage_failure", identity(), exception);
        }
    }

    public Object get(NativeContext context, boolean direct, Object receiver) {
        try {
            return executeGet(context, direct, receiver);
        } catch (NativeAccessFailure failure) {
            throw context.bindings == null
                    ? failure
                    : context.bindings.explain(failure, null, null, this, context, direct);
        }
    }

    private Object executeGet(NativeContext context, boolean direct, Object receiver) {
        preflight(context, direct, receiver);
        try {
            Object result = ((Field) member).get(receiver);
            context.check();
            return result;
        } catch (IllegalAccessException | SecurityException exception) {
            throw new NativeAccessFailure("access_denied", identity(), exception);
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("native_linkage_failure", identity(), exception);
        }
    }

    public void set(NativeContext context, boolean direct, Object receiver, Object value) {
        try {
            executeSet(context, direct, receiver, value);
        } catch (NativeAccessFailure failure) {
            throw context.bindings == null
                    ? failure
                    : context.bindings.explain(failure, null, null, this, context, direct);
        }
    }

    private void executeSet(NativeContext context, boolean direct, Object receiver, Object value) {
        context.check();
        receiver(receiver);
        if (Modifier.isFinal(member.getModifiers())) {
            throw new NativeAccessFailure("final_field", identity());
        }
        context.require(isStatic ? NativeContext.Effect.INITIALIZE : NativeContext.Effect.WRITE, direct);
        accessibility(context, direct);
        try {
            ((Field) member).set(receiver, value);
            context.check();
        } catch (IllegalAccessException | SecurityException exception) {
            throw new NativeAccessFailure("access_denied", identity(), exception);
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("native_linkage_failure", identity(), exception);
        }
    }

    public String identity() {
        return member.getDeclaringClass().getName() + "." + member.getName() + descriptor;
    }

    public void prepareInvocation(NativeContext context, boolean direct) {
        context.require(NativeContext.Effect.UNKNOWN, direct);
        accessibility(context, direct);
    }

    void accessibility(NativeContext context, boolean direct) {
        if (!nonPublic) {
            return;
        }
        if (!direct) {
            throw new NativeAccessFailure("nonpublic_requires_direct", identity());
        }
        if (((AccessibleObject) member).isAccessible()) {
            return;
        }
        context.prepare();
        try {
            ((AccessibleObject) member).setAccessible(true);
        } catch (SecurityException exception) {
            throw new NativeAccessFailure("access_denied", identity(), exception);
        }
    }

    private void receiver(Object receiver) {
        boolean constructor = member instanceof Constructor;
        if (isStatic || constructor) {
            if (receiver != null) {
                throw new NativeAccessFailure("static_receiver_must_be_nil", identity());
            }
        } else if (receiver == null || !member.getDeclaringClass().isInstance(receiver)) {
            throw new NativeAccessFailure("wrong_receiver", identity());
        }
    }

    private NativeAccessFailure nativeFailure(Throwable cause) {
        if (cause instanceof VirtualMachineError) {
            throw (VirtualMachineError) cause;
        }
        if (cause instanceof ThreadDeath) {
            throw (ThreadDeath) cause;
        }
        return new NativeAccessFailure("native_exception", identity() + " threw " + cause.getClass().getName(), cause);
    }
}
