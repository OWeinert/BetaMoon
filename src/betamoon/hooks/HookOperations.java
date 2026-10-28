package betamoon.hooks;

import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime for operand envelopes. Frames snapshot subscriptions before the
 * original instruction executes.
 */
final class HookOperations {
    private static final ThreadLocal<Set<String>> ENTERED = ThreadLocal.withInitial(HashSet::new);

    private HookOperations() {
    }

    static Object[] begin(int site, int before, int after, Class<?> anchor, Object enclosingReceiver,
            Object[] enclosingArguments, Object receiver, Object[] arguments) {
        HookPreparation.Site container = HookDispatcher.operationContainer(site, anchor);
        List<HookDispatcher.Subscription> beforeCallbacks = HookDispatcher.operationSubscriptions(site, before, anchor);
        List<HookDispatcher.Subscription> afterCallbacks = HookDispatcher.operationSubscriptions(site, after, anchor);
        Frame frame = new Frame(container, after, anchor, enclosingReceiver, enclosingArguments, receiver, arguments,
                afterCallbacks);
        if (before >= 0) {
            Outcome outcome = dispatch(container, container.operations.get(before), beforeCallbacks, anchor,
                    enclosingReceiver, enclosingArguments, receiver, arguments, null);
            frame.arguments = outcome.arguments;
        }
        return new Object[]{frame.arguments, frame};
    }

    static Object finish(Object[] token, Object result) {
        if (token == null || token.length != 2 || !(token[1] instanceof Frame)) {
            throw new HookFailure("hook_registry_context_mismatch", "invalid operation frame transport");
        }
        Frame frame = (Frame) token[1];
        if (frame.thread != Thread.currentThread() || frame.closed) {
            throw new HookFailure("hook_call_expired", "finish the operation once on its original thread");
        }
        try {
            return frame.after < 0
                    ? result
                    : dispatch(frame.container, frame.container.operations.get(frame.after), frame.callbacks,
                            frame.anchor, frame.enclosingReceiver, frame.enclosingArguments, frame.receiver,
                            frame.arguments, result).result;
        } finally {
            frame.close();
            Arrays.fill(token, null);
        }
    }

    private static Outcome dispatch(HookPreparation.Site container, HookOperationSite operation,
            List<HookDispatcher.Subscription> subscriptions, Class<?> anchor, Object enclosingReceiver,
            Object[] enclosingArguments, Object receiver, Object[] arguments, Object result) {
        Outcome outcome = new Outcome(arguments, result);
        for (HookDispatcher.Subscription subscription : subscriptions) {
            HookDefinition definition = subscription.definition;
            HookDispatcher.Callback callback = subscription.callback;
            if (!subscription.alive || subscription.closed || callback == null) {
                continue;
            }
            Set<String> entered = ENTERED.get();
            String key = container.id + ":" + operation.id + ":" + definition.owner;
            boolean added = false;
            long started = System.nanoTime();
            try {
                if (!HookDispatcher.onClientThread()) {
                    throw new HookFailure("unsupported_thread", "operation callbacks require the client thread");
                }
                if (entered.contains(key)) {
                    subscription.skipped++;
                    if (definition.edit) {
                        throw new HookFailure("hook_recursion", "editing operation reentered its owner/site");
                    }
                    continue;
                }
                if (entered.size() >= 16) {
                    throw new HookFailure("hook_recursion", "operation callbacks exceed 16 nested owner/sites");
                }
                entered.add(key);
                added = true;
                try (HookInvocation invocation = new HookInvocation(definition, container.target.key(), anchor,
                        receiver, outcome.arguments, outcome.result, null)) {
                    invocation.operation(new HookOperationInvocation(operation, enclosingReceiver, enclosingArguments));
                    try {
                        callback.invoke(invocation);
                    } catch (VirtualMachineError | ThreadDeath fatal) {
                        throw fatal;
                    } catch (Throwable failure) {
                        if (!subscription.alive || subscription.closed || invocation.observationInvalidated()) {
                            continue;
                        }
                        throw failure;
                    }
                    if (!subscription.alive || subscription.closed || invocation.observationInvalidated()) {
                        continue;
                    }
                    invocation.validate();
                    outcome.arguments = invocation.committedArguments();
                    outcome.result = invocation.result();
                    subscription.calls++;
                }
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                subscription.failures++;
                HookPreparation preparation = HookPreparation.current();
                if (preparation != null) {
                    preparation.diagnose(definition.owner, definition.id,
                            failure instanceof HookFailure
                                    ? ((HookFailure) failure).reason
                                    : definition.edit ? "callback_failed" : "callback_disabled",
                            operation.kind + " " + operation.owner + "." + operation.name + ": "
                                    + NativeDiagnosticEvidence.message(failure));
                }
                if (definition.edit) {
                    throw new HookCallbackFailure("Hook " + definition.key() + " failed at " + operation.kind, failure);
                }
                subscription.close();
            } finally {
                subscription.nanos += System.nanoTime() - started;
                if (added) {
                    entered.remove(key);
                }
                if (entered.isEmpty()) {
                    ENTERED.remove();
                }
            }
        }
        return outcome;
    }

    private static final class Outcome {
        private Object[] arguments;
        private Object result;

        Outcome(Object[] arguments, Object result) {
            this.arguments = arguments;
            this.result = result;
        }
    }

    private static final class Frame implements AutoCloseable {
        private final HookPreparation.Site container;
        private final int after;
        private Class<?> anchor;
        private Object enclosingReceiver;
        private final Object[] enclosingArguments;
        private Object receiver;
        private Object[] arguments;
        private List<HookDispatcher.Subscription> callbacks;
        private final Thread thread = Thread.currentThread();
        private boolean closed;

        Frame(HookPreparation.Site container, int after, Class<?> anchor, Object enclosingReceiver,
                Object[] enclosingArguments, Object receiver, Object[] arguments,
                List<HookDispatcher.Subscription> callbacks) {
            this.container = container;
            this.after = after;
            this.anchor = anchor;
            this.enclosingReceiver = enclosingReceiver;
            this.enclosingArguments = enclosingArguments;
            this.receiver = receiver;
            this.arguments = arguments;
            this.callbacks = callbacks;
        }

        @Override
        public void close() {
            closed = true;
            anchor = null;
            enclosingReceiver = null;
            Arrays.fill(enclosingArguments, null);
            receiver = null;
            Arrays.fill(arguments, null);
            callbacks = Collections.emptyList();
        }
    }
}
