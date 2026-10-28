package betamoon.hooks;

import java.util.Arrays;
import java.util.function.Function;
import org.objectweb.asm.Type;

/**
 * One callback's transactional edit buffer. Never reused for a different
 * callback or native invocation.
 */
public final class HookInvocation implements AutoCloseable {
    public final HookDefinition definition;
    public final String site;
    private Class<?> declaringClass;
    private Object receiver;
    private Throwable exception;
    private final Object[] arguments;
    private Object result;
    private boolean shortCircuit;
    private Function<Object[], Object> continuation;
    private boolean proceeded;
    private boolean observationInvalidated;
    private HookOperationInvocation operation;
    private Object continuedResult;
    private Throwable continuedFailure;
    private boolean active = true;
    private final Thread thread = Thread.currentThread();

    HookInvocation(HookDefinition definition, String site, Class<?> declaringClass, Object receiver, Object[] arguments,
            Object result, Throwable exception) {
        this.definition = definition;
        this.site = site;
        this.declaringClass = declaringClass;
        this.receiver = receiver;
        this.arguments = arguments.clone();
        this.result = result;
        this.exception = exception;
    }

    public void check() {
        if (!active || Thread.currentThread() != thread) {
            throw new HookFailure("hook_call_expired", "the hook call view expired");
        }
    }

    public Class<?> declaringClass() {
        check();
        return declaringClass;
    }

    public Object receiver() {
        check();
        return receiver;
    }

    public Throwable exception() {
        check();
        return exception;
    }

    public int argumentCount() {
        check();
        return arguments.length;
    }

    public Object argument(int index) {
        checkIndex(index);
        return arguments[index];
    }

    public void argument(int index, Object value) {
        if (definition.at.equals("around")) {
            checkEdit("around");
            if (proceeded) {
                throw new HookFailure("hook_continuation_used", "arguments cannot be edited after continuation");
            }
        } else if (definition.point != null && definition.point.before()) {
            checkEdit(definition.at);
        } else {
            checkEdit("before");
        }
        checkIndex(index);
        arguments[index] = value;
    }

    public Object result() {
        check();
        return result;
    }

    public void result(Object value) {
        check();
        if (!definition.edit || definition.at.equals("error")) {
            throw new HookFailure("hook_edit_denied", "declare effects=edit for before/after/around");
        }
        result = value;
        shortCircuit = definition.at.equals("before");
    }

    private void checkEdit(String phase) {
        check();
        if (!definition.edit || !definition.at.equals(phase)) {
            throw new HookFailure("hook_edit_denied", "this operation requires an editing " + phase + " hook");
        }
    }

    private void checkIndex(int index) {
        check();
        if (index < 0 || index >= arguments.length) {
            throw new HookFailure("hook_argument_index", "use zero-based Java parameter positions");
        }
    }

    Object[] committedArguments() {
        check();
        return arguments.clone();
    }

    boolean shortCircuit() {
        check();
        return shortCircuit;
    }

    void acceptTransport(Object[] values, Object value, boolean early) {
        check();
        if (values.length != arguments.length || early && (!definition.edit || !definition.at.equals("before"))) {
            throw new HookFailure("hook_edit_denied", "invalid transported callback outcome");
        }
        for (int index = 0; index < values.length; index++) {
            if (definition.edit && (definition.at.equals("before") || definition.at.equals("around")
                    || definition.point != null && definition.point.before())) {
                arguments[index] = values[index];
            } else if (arguments[index] != values[index]) {
                throw new HookFailure("hook_edit_denied", "transport edited arguments outside an editing before hook");
            }
        }
        if (definition.edit && (early || definition.at.equals("after") || definition.at.equals("around")
                || definition.point != null && !definition.point.before())) {
            result(value);
        } else if (result != value) {
            throw new HookFailure("hook_edit_denied", "transport edited a read-only result");
        }
    }

    void validate() {
        check();
        if (!definition.edit) {
            return;
        }
        String descriptor = descriptor();
        Type[] parameters = Type.getArgumentTypes(descriptor);
        for (int index = 0; index < parameters.length; index++) {
            validateValue(parameters[index], arguments[index]);
        }
        if (shortCircuit || definition.at.equals("after") || definition.at.equals("around")
                || definition.point != null && !definition.point.before()) {
            validateValue(Type.getReturnType(descriptor), result);
        }
    }

    void continuation(Function<Object[], Object> continuation) {
        this.continuation = continuation;
    }

    public Object proceed(Object[] replacement) {
        checkEdit("around");
        if (proceeded || continuation == null) {
            throw new HookFailure("hook_continuation_used", "continue once, during the active around callback");
        }
        Type[] parameters = Type.getArgumentTypes(descriptor());
        if (replacement.length != parameters.length) {
            throw new HookFailure("hook_invalid_value", "continuation requires the exact parameter list");
        }
        for (int index = 0; index < replacement.length; index++) {
            validateValue(parameters[index], replacement[index]);
        }
        System.arraycopy(replacement, 0, arguments, 0, arguments.length);
        proceeded = true;
        try {
            continuedResult = continuation.apply(arguments.clone());
            return continuedResult;
        } catch (Throwable failure) {
            continuedFailure = failure;
            throw HookDispatcher.rethrow(failure);
        }
    }

    public Object[] argumentValues() {
        return committedArguments();
    }

    boolean proceeded() {
        return proceeded;
    }

    public boolean hasContinued() {
        check();
        return proceeded;
    }

    Object continuedResult() {
        return continuedResult;
    }

    Throwable continuedFailure() {
        return continuedFailure;
    }

    public void invalidateObservation() {
        check();
        if (!nativeOutcomeAvailable()) {
            throw new HookFailure("hook_call_expired", "observation can expire after a native outcome");
        }
        observationInvalidated = true;
    }

    public boolean nativeOutcomeAvailable() {
        check();
        return definition.at.equals("around") && proceeded || operation != null && !operation.operation.before();
    }

    public String descriptor() {
        check();
        return operation == null ? site.substring(site.indexOf('(')) : operation.operation.hookDescriptor();
    }

    public HookOperationInvocation operation() {
        check();
        return operation;
    }

    void operation(HookOperationInvocation operation) {
        this.operation = operation;
    }

    public boolean observationInvalidated() {
        check();
        return observationInvalidated;
    }

    public Throwable nativeFailure() {
        check();
        return continuedFailure != null
                && continuedFailure.getClass().getName().equals(HookCallbackFailure.class.getName())
                        ? null
                        : continuedFailure;
    }

    private void validateValue(Type type, Object value) {
        Class<?> expected;
        switch (type.getSort()) {
            case Type.VOID:
                if (value != null) {
                    throw new HookFailure("hook_invalid_value", "void results must have no value");
                }
                return;
            case Type.BOOLEAN:
                expected = Boolean.class;
                break;
            case Type.BYTE:
                expected = Byte.class;
                break;
            case Type.SHORT:
                expected = Short.class;
                break;
            case Type.CHAR:
                expected = Character.class;
                break;
            case Type.INT:
                expected = Integer.class;
                break;
            case Type.LONG:
                expected = Long.class;
                break;
            case Type.FLOAT:
                expected = Float.class;
                break;
            case Type.DOUBLE:
                expected = Double.class;
                break;
            default:
                if (value == null) {
                    return;
                }
                try {
                    expected = Class.forName(
                            type.getSort() == Type.ARRAY ? type.getDescriptor().replace('/', '.') : type.getClassName(),
                            false, declaringClass.getClassLoader());
                } catch (ClassNotFoundException error) {
                    throw new HookFailure("missing_dependency", "hook value type " + type, error);
                }
        }
        if (!expected.isInstance(value)) {
            throw new HookFailure("hook_invalid_value", "expected exact bridge value for " + type.getClassName());
        }
    }

    @Override
    public void close() {
        active = false;
        Arrays.fill(arguments, null);
        result = null;
        continuedResult = null;
        continuedFailure = null;
        continuation = null;
        if (operation != null) {
            operation.close();
            operation = null;
        }
        receiver = null;
        exception = null;
        declaringClass = null;
    }
}
