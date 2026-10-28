package betamoon.hooks;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;
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
    private List<?> arguments;
    private Object[] nativeArguments;
    private Object[] edits;
    private boolean ownsEdits;
    private HookSignature signature;
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
        this.arguments = Collections.unmodifiableList(Arrays.asList(arguments));
        nativeArguments = arguments;
        signature = HookSignature.forSite(declaringClass, site);
        this.result = result;
        this.exception = exception;
    }

    HookInvocation(HookDefinition definition, String site, Class<?> declaringClass, Object receiver, List<?> arguments,
            Object result, Throwable exception) {
        this.definition = definition;
        this.site = site;
        this.declaringClass = declaringClass;
        this.receiver = receiver;
        this.arguments = arguments;
        this.result = result;
        this.exception = exception;
        signature = HookSignature.forSite(declaringClass, site);
    }

    private Object[] editBuffer() {
        if (!ownsEdits) {
            edits = argumentValues();
            ownsEdits = true;
        }
        return edits;
    }

    List<?> transportArguments() {
        check();
        if (edits == null) {
            return arguments;
        }
        ownsEdits = false;
        return Collections.unmodifiableList(Arrays.asList(edits));
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
        return arguments.size();
    }

    public Object argument(int index) {
        checkIndex(index);
        return edits == null ? arguments.get(index) : edits[index];
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
        editBuffer()[index] = value;
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
        if (index < 0 || index >= arguments.size()) {
            throw new HookFailure("hook_argument_index", "use zero-based Java parameter positions");
        }
    }

    Object[] committedArguments() {
        check();
        if (edits != null) {
            ownsEdits = false;
            return edits;
        }
        return nativeArguments == null ? arguments.toArray() : nativeArguments;
    }

    boolean shortCircuit() {
        check();
        return shortCircuit;
    }

    void acceptTransport(List<?> values, Object value, boolean early) {
        check();
        if (values.size() != arguments.size() || early && (!definition.edit || !definition.at.equals("before"))) {
            throw new HookFailure("hook_edit_denied", "invalid transported callback outcome");
        }
        if (definition.edit && (definition.at.equals("before") || definition.at.equals("around")
                || definition.point != null && definition.point.before())) {
            edits = values.toArray();
            ownsEdits = true;
        } else {
            for (int index = 0; index < values.size(); index++) {
                if (argument(index) != values.get(index)) {
                    throw new HookFailure("hook_edit_denied", "transport edited read-only arguments");
                }
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
        for (int index = 0; index < signature.parameters.size(); index++) {
            signature.validate(signature.parameters.get(index), argument(index));
        }
        if (shortCircuit || definition.at.equals("after") || definition.at.equals("around")
                || definition.point != null && !definition.point.before()) {
            signature.validate(signature.result, result);
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
        List<Type> parameters = signature.parameters;
        if (replacement.length != parameters.size()) {
            throw new HookFailure("hook_invalid_value", "continuation requires the exact parameter list");
        }
        for (int index = 0; index < replacement.length; index++) {
            signature.validate(parameters.get(index), replacement[index]);
        }
        edits = replacement.clone();
        ownsEdits = true;
        proceeded = true;
        try {
            continuedResult = continuation.apply(edits.clone());
            return continuedResult;
        } catch (Throwable failure) {
            continuedFailure = failure;
            throw HookDispatcher.rethrow(failure);
        }
    }

    public Object[] argumentValues() {
        check();
        return edits == null ? arguments.toArray() : edits.clone();
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
        return signature.descriptor;
    }

    public HookOperationInvocation operation() {
        check();
        return operation;
    }

    void operation(HookOperationInvocation operation) {
        this.operation = operation;
        signature = HookSignature.get(declaringClass, operation.operation.hookDescriptor());
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

    public List<Type> enclosingParameterTypes() {
        check();
        return HookSignature.forSite(declaringClass, site).parameters;
    }

    public List<Type> parameterTypes() {
        check();
        return signature.parameters;
    }

    public Type returnType() {
        check();
        return signature.result;
    }

    public Class<?> valueClass(Type type) {
        check();
        return signature.valueClass(type);
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        active = false;
        if (ownsEdits) {
            Arrays.fill(edits, null);
        }
        edits = null;
        arguments = null;
        nativeArguments = null;
        signature = null;
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
