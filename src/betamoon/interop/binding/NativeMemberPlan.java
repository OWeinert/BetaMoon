package betamoon.interop.binding;

import betamoon.interop.runtime.NativeContext;
import java.lang.reflect.AccessibleObject;
import java.lang.invoke.MethodHandle;
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
    private ClassValue<NativeContext.Effect> virtualEffects;
    private MethodHandle callInvoker;
    private MethodHandle getInvoker;
    private MethodHandle setInvoker;
    private boolean callPrepared;
    private boolean getPrepared;
    private boolean setPrepared;

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
        return call(context, direct, receiver, arguments, false);
    }

    /**
     * Internal fast path only after NativeBindings.arguments has
     * validated/conformed every argument.
     */
    public Object callConverted(NativeContext context, boolean direct, Object receiver, Object[] arguments) {
        return call(context, direct, receiver, arguments, true);
    }

    private Object call(NativeContext context, boolean direct, Object receiver, Object[] arguments, boolean converted) {
        try {
            return executeCall(context, direct, receiver, arguments, converted);
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
            Class<?> receiverClass = receiver.getClass();
            if (System.getSecurityManager() == null) {
                if (Modifier.isPublic(member.getModifiers())
                        && (receiverClass == member.getDeclaringClass() || Modifier.isFinal(member.getModifiers()))) {
                    context.require(isStatic ? NativeContext.Effect.INITIALIZE : actual, direct);
                    return;
                }
                if (virtualEffects == null) {
                    virtualEffects = new ClassValue<NativeContext.Effect>() {
                        @Override
                        protected NativeContext.Effect computeValue(Class<?> type) {
                            return dispatchedEffect(type);
                        }
                    };
                }
                context.require(isStatic ? NativeContext.Effect.INITIALIZE : virtualEffects.get(receiverClass), direct);
                return;
            }
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

    private NativeContext.Effect dispatchedEffect(Class<?> receiverClass) {
        Method selected = (Method) member;
        try {
            Method actual = receiverClass.getMethod(selected.getName(), parameters);
            return actual.getDeclaringClass() == selected.getDeclaringClass() ? effect : NativeContext.Effect.UNKNOWN;
        } catch (NoSuchMethodException failure) {
            return NativeContext.Effect.UNKNOWN;
        } catch (SecurityException failure) {
            throw new NativeAccessFailure("access_denied", identity(), failure);
        } catch (LinkageError failure) {
            throw new NativeAccessFailure("native_linkage_failure", identity(), failure);
        }
    }

    private Object executeCall(NativeContext context, boolean direct, Object receiver, Object[] arguments,
            boolean converted) {
        preflight(context, direct, receiver);
        try {
            Object result;
            if (converted && System.getSecurityManager() == null && callAdapter() != null) {
                if (isStatic || member instanceof Constructor) {
                    // Reflection initializes the owner before entering the callee exception
                    // wrapper.
                    Class<?> owner = member.getDeclaringClass();
                    Class.forName(owner.getName(), true, owner.getClassLoader());
                }
                try {
                    result = (Object) callInvoker.invokeExact(receiver, arguments);
                } catch (Throwable failure) {
                    throw nativeFailure(failure);
                }
            } else if (member instanceof Method) {
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
            Object result;
            MethodHandle adapter = System.getSecurityManager() == null ? getAdapter() : null;
            if (adapter == null) {
                result = ((Field) member).get(receiver);
            } else {
                try {
                    result = (Object) adapter.invokeExact(receiver);
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    throw fatal;
                } catch (LinkageError failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new NativeAccessFailure("access_denied", identity(), failure);
                }
            }
            context.check();
            return result;
        } catch (IllegalAccessException | SecurityException exception) {
            throw new NativeAccessFailure("access_denied", identity(), exception);
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("native_linkage_failure", identity(), exception);
        }
    }

    public void set(NativeContext context, boolean direct, Object receiver, Object value) {
        set(context, direct, receiver, value, false);
    }

    public void setConverted(NativeContext context, boolean direct, Object receiver, Object value) {
        set(context, direct, receiver, value, true);
    }

    private void set(NativeContext context, boolean direct, Object receiver, Object value, boolean converted) {
        try {
            executeSet(context, direct, receiver, value, converted);
        } catch (NativeAccessFailure failure) {
            throw context.bindings == null
                    ? failure
                    : context.bindings.explain(failure, null, null, this, context, direct);
        }
    }

    private void executeSet(NativeContext context, boolean direct, Object receiver, Object value, boolean converted) {
        context.check();
        receiver(receiver);
        if (Modifier.isFinal(member.getModifiers())) {
            throw new NativeAccessFailure("final_field", identity());
        }
        context.require(isStatic ? NativeContext.Effect.INITIALIZE : NativeContext.Effect.WRITE, direct);
        accessibility(context, direct);
        try {
            MethodHandle adapter = converted && System.getSecurityManager() == null ? setAdapter() : null;
            if (adapter == null) {
                ((Field) member).set(receiver, value);
            } else {
                try {
                    adapter.invokeExact(receiver, value);
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    throw fatal;
                } catch (LinkageError failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new NativeAccessFailure("access_denied", identity(), failure);
                }
            }
            context.check();
        } catch (IllegalAccessException | SecurityException exception) {
            throw new NativeAccessFailure("access_denied", identity(), exception);
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("native_linkage_failure", identity(), exception);
        }
    }

    private MethodHandle callAdapter() {
        if (!callPrepared) {
            callPrepared = true;
            try {
                callInvoker = NativeInvoker.call(this);
            } catch (IllegalAccessException | RuntimeException | LinkageError unavailable) {
                /* Reflection remains valid. */ }
        }
        return callInvoker;
    }

    private MethodHandle getAdapter() {
        if (!getPrepared) {
            getPrepared = true;
            try {
                getInvoker = NativeInvoker.get(this);
            } catch (IllegalAccessException | RuntimeException | LinkageError unavailable) {
                /* Reflection remains valid. */ }
        }
        return getInvoker;
    }

    private MethodHandle setAdapter() {
        if (!setPrepared) {
            setPrepared = true;
            try {
                setInvoker = NativeInvoker.set(this);
            } catch (IllegalAccessException | RuntimeException | LinkageError unavailable) {
                /* Reflection remains valid. */ }
        }
        return setInvoker;
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
