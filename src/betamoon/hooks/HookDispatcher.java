package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Stable load-time bridge. Dormant sites allocate no arrays or invocation
 * frames.
 */
public final class HookDispatcher {
    private static final HookAgentBridge AGENT = HookAgentBridge.remote();
    @FunctionalInterface
    public interface Callback {
        void invoke(HookInvocation invocation);
    }

    private static volatile State[] states = new State[0];
    private static volatile Thread clientThread;
    private static final ThreadLocal<Integer> AROUND_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Set<String>> AROUND_ENTERED = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Set<String>> ENTERED = ThreadLocal.withInitial(HashSet::new);

    private HookDispatcher() {
    }

    public static String protocol() {
        return HookAgentBridge.PROTOCOL;
    }

    static void prepare(List<HookPreparation.Site> sites) {
        if (AGENT != null) {
            return;
        }
        State[] prepared = new State[sites.size()];
        for (HookPreparation.Site site : sites) {
            prepared[site.id] = new State(site);
        }
        states = prepared;
    }

    public static synchronized void clientThread() {
        if (AGENT != null) {
            try {
                AGENT.clientThread.invokeExact();
                return;
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        if (clientThread != null && clientThread != Thread.currentThread()) {
            throw new HookFailure("unsupported_thread", "hook activation requires the original client thread");
        }
        clientThread = Thread.currentThread();
    }

    public static boolean active(int site, int phase, Class<?> declaringClass) {
        if (AGENT != null) {
            try {
                return (boolean) AGENT.active.invokeExact(site, phase, declaringClass);
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        State state = state(site);
        if (state != null && state.anchor == null && state.loader == declaringClass.getClassLoader()
                && state.site.status.equals("applied")) {
            synchronized (HookDispatcher.class) {
                if (state.anchor == null) {
                    state.anchor = declaringClass;
                }
            }
        }
        return state != null && state.anchor == declaringClass && (state.active & (1 << phase)) != 0;
    }

    public static synchronized void applied(int site, ClassLoader loader) {
        State state = state(site);
        if (state != null) {
            state.loader = loader;
            state.refresh();
            changed();
        }
    }

    public static synchronized void requireLoader(int site, ClassLoader loader) {
        State state = state(site);
        if (state != null && state.loader != null && state.loader != loader) {
            throw new HookFailure("ambiguous_loader", "another defining loader already owns this prepared method");
        }
    }

    public static ClassLoader targetLoader(int site) {
        if (AGENT != null) {
            try {
                return (ClassLoader) AGENT.targetLoader.invokeExact(site);
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        State state = state(site);
        return state == null ? null : state.loader;
    }

    public static synchronized Subscription subscribe(int site, Class<?> anchor, HookDefinition definition,
            Callback callback) {
        if (AGENT != null) {
            Function<Object[], Object[]> transport = payload -> {
                try (HookInvocation invocation = new HookInvocation(definition, (String) payload[0],
                        (Class<?>) payload[1], payload[2], (Object[]) payload[3], payload[4], (Throwable) payload[5])) {
                    if (payload.length > 7 && payload[7] instanceof Object[]) {
                        invocation.operation(HookOperationInvocation.transport((Object[]) payload[7]));
                    }
                    if (payload.length > 6 && payload[6] instanceof Function) {
                        @SuppressWarnings("unchecked")
                        Function<Object[], Object> continuation = (Function<Object[], Object>) payload[6];
                        invocation.continuation(continuation);
                    }
                    try {
                        callback.invoke(invocation);
                        invocation.validate();
                        return new Object[]{invocation.committedArguments(), invocation.result(),
                                invocation.shortCircuit(), invocation.observationInvalidated()};
                    } catch (VirtualMachineError | ThreadDeath fatal) {
                        throw fatal;
                    } catch (Throwable failure) {
                        if (invocation.observationInvalidated()) {
                            return new Object[]{invocation.committedArguments(), null, false, true};
                        }
                        throw rethrow(failure);
                    }
                }
            };
            try {
                AutoCloseable remote = (AutoCloseable) AGENT.subscribe.invokeExact(site, anchor, definition.transport(),
                        transport);
                return new Subscription(definition, remote);
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        State state = state(site);
        if (state == null || !state.site.owners.contains(definition.owner)
                || definition.at.equals("around") && !state.site.around
                || definition.point != null && !state.site.points.containsKey(definition.point.key())
                || !state.site.status.equals("applied") && !state.site.status.equals("target_pending_load")) {
            throw new HookFailure("owner_not_ready", "target is not applied for " + definition.key());
        }
        if (definition.at.equals("around") && state.subscriptions.stream()
                .filter(entry -> entry.definition.at.equals("around") && !entry.closed).count() >= 16) {
            throw new HookFailure("hook_recursion", "a method supports at most 16 around subscriptions");
        }
        int total = 0;
        for (State prepared : states) {
            total += prepared.subscriptions.size();
        }
        if (total >= 512) {
            throw new HookFailure("hook_file_invalid", "session exceeds 512 hook subscriptions");
        }
        if (anchor != null
                && (state.loader != anchor.getClassLoader() || state.anchor != null && state.anchor != anchor)) {
            throw new HookFailure("dispatcher_not_visible", "target loader differs from the verified site loader");
        }
        if (anchor != null) {
            state.anchor = anchor;
        }
        Subscription result = new Subscription(state, definition, callback);
        List<Subscription> entries = new ArrayList<>(state.subscriptions);
        for (Subscription entry : entries) {
            if (entry.definition.owner.equals(definition.owner) && entry.definition.id.equals(definition.id)) {
                throw new HookFailure("declaration_mismatch", "owner/hook is already subscribed: " + definition.key());
            }
        }
        entries.add(result);
        entries.sort(Comparator.comparingInt((Subscription value) -> value.definition.priority).reversed()
                .thenComparing(value -> value.definition.owner).thenComparing(value -> value.definition.id));
        state.subscriptions = Collections.unmodifiableList(entries);
        state.refresh();
        changed();
        return result;
    }

    public static synchronized void activate(List<Subscription> subscriptions) {
        for (Subscription subscription : subscriptions) {
            if (subscription.closed) {
                throw new HookFailure("owner_not_ready", "cannot reactivate a disposed hook subscription");
            }
        }
        if (AGENT != null) {
            List<AutoCloseable> handles = new ArrayList<>();
            for (Subscription subscription : subscriptions) {
                handles.add(subscription.remote);
            }
            try {
                AGENT.activate.invokeExact((List<?>) handles);
                for (Subscription subscription : subscriptions) {
                    subscription.alive = true;
                }
                return;
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        for (Subscription subscription : subscriptions) {
            subscription.alive = true;
        }
        for (Subscription subscription : subscriptions) {
            subscription.state.refresh();
        }
        changed();
    }

    private static void changed() {
        HookPreparation preparation = HookPreparation.current();
        if (preparation != null) {
            preparation.changed();
        }
    }

    public static synchronized List<Map<String, Object>> status() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (State state : states) {
            List<Subscription> ordered = state.subscriptions;
            for (int index = 0; index < ordered.size(); index++) {
                Subscription entry = ordered.get(index);
                HookDefinition definition = entry.definition;
                rows.add(CatalogValues.record("id", "subscription-" + state.site.id + ":" + definition.identity(),
                        "name", definition.id, "kind", "HOOK_SUBSCRIPTION", "owner", definition.owner, "source",
                        definition.file, "target", state.site.target.key(), "at", definition.at, "sourceSha256",
                        definition.sourceSha256, "effects", definition.edit ? "edit" : "observe", "priority",
                        definition.priority, "status",
                        entry.closed || !entry.alive
                                ? "owner_not_ready"
                                : state.site.status.equals("applied") ? "active" : state.site.status,
                        "order", state.order(entry), "calls", entry.calls, "failures", entry.failures, "skippedReentry",
                        entry.skipped, "callbackNanos", entry.nanos, "shortCircuits", entry.shortCircuits,
                        "nativeErrors", definition.nativeErrors, "reentry", definition.reentry, "recoveredErrors",
                        entry.recovered, "point", definition.point == null ? null : definition.point.transport()));
            }
        }
        return rows;
    }

    public static Entry before(int site, Class<?> anchor, Object receiver, Object[] arguments) {
        if (AGENT != null) {
            try {
                Object[] payload = (Object[]) AGENT.before.invokeExact(site, anchor, receiver, arguments);
                Entry result = new Entry((Object[]) payload[0], payload[1]);
                result.shortCircuit = Boolean.TRUE.equals(payload[2]);
                return result;
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        return dispatch(site, anchor, receiver, arguments, null, null, "before");
    }

    public static Object after(int site, Class<?> anchor, Object receiver, Object[] arguments, Object result) {
        if (AGENT != null) {
            try {
                return (Object) AGENT.after.invokeExact(site, anchor, receiver, arguments, result);
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        State state = state(site);
        String phase = state != null && state.site.target.constructor() ? "constructed" : "after";
        return dispatch(site, anchor, receiver, arguments, result, null, phase).result;
    }

    public static void error(int site, Class<?> anchor, Object receiver, Object[] arguments, Throwable exception) {
        if (AGENT != null) {
            try {
                AGENT.error.invokeExact(site, anchor, receiver, arguments, exception);
                return;
            } catch (Throwable error) {
                throw HookAgentBridge.failure(error);
            }
        }
        if (!(exception instanceof HookCallbackFailure)) {
            dispatch(site, anchor, receiver, arguments, null, exception, "error");
        }
    }

    public static Object[] beforeTransport(int site, Class<?> anchor, Object receiver, Object[] arguments) {
        Entry result = before(site, anchor, receiver, arguments);
        return new Object[]{result.arguments, result.result, result.shortCircuit};
    }

    public static AutoCloseable subscribeTransport(int site, Class<?> anchor, Map<String, Object> metadata,
            Function<Object[], Object[]> callback) {
        HookDefinition definition = HookDefinition.transport(metadata);
        HookPreparation preparation = HookPreparation.current();
        if (preparation == null || preparation.targets(definition).stream().noneMatch(value -> value.id == site)) {
            throw new HookFailure("restart_required", "transport target is not in the frozen plan");
        }
        return subscribe(site, anchor, definition, invocation -> {
            Object[] outcome = callback.apply(new Object[]{invocation.site, invocation.declaringClass(),
                    invocation.receiver(), invocation.committedArguments(), invocation.result(), invocation.exception(),
                    invocation.definition.at.equals("around") ? (Function<Object[], Object>) invocation::proceed : null,
                    invocation.operation() == null ? null : invocation.operation().transport()});
            if (outcome == null || outcome.length < 3 || outcome.length > 4 || !(outcome[0] instanceof Object[])
                    || !(outcome[2] instanceof Boolean)) {
                throw new HookFailure("hook_invalid_value", "invalid callback transport outcome");
            }
            if (outcome.length == 4 && Boolean.TRUE.equals(outcome[3])) {
                invocation.invalidateObservation();
                return;
            }
            invocation.acceptTransport((Object[]) outcome[0], outcome[1], (Boolean) outcome[2]);
        });
    }

    public static void activateTransport(List<?> handles) {
        List<Subscription> subscriptions = new ArrayList<>();
        for (Object handle : handles) {
            if (!(handle instanceof Subscription)) {
                throw new HookFailure("hook_registry_context_mismatch", "subscription belongs to another agent");
            }
            subscriptions.add((Subscription) handle);
        }
        activate(subscriptions);
    }

    /**
     * The extracted terminal is invoked exactly, never by virtual dispatch to a
     * subclass override.
     */
    public static Object around(int site, Class<?> anchor, Object receiver, Object[] arguments, String helper)
            throws Throwable {
        if (AGENT != null) {
            return (Object) AGENT.around.invokeExact(site, anchor, receiver, arguments, helper);
        }
        State state = state(site);
        if (state == null || state.anchor != anchor || !state.site.around || !helper.equals(state.site.helperName)) {
            throw new HookFailure("dispatcher_not_visible", "around helper differs from the installed exact site");
        }
        MethodHandle body = state.body;
        if (body == null) {
            MethodType signature = MethodType.fromMethodDescriptorString(state.site.target.descriptor,
                    anchor.getClassLoader());
            Method selected = anchor.getDeclaredMethod(helper, signature.parameterArray());
            if (!selected.isSynthetic() || !Modifier.isPrivate(selected.getModifiers())) {
                throw new HookFailure("dispatcher_not_visible", "around body must be a private generated helper");
            }
            selected.setAccessible(true);
            body = MethodHandles.lookup().unreflect(selected);
            state.body = body;
        }
        MethodHandle terminal = body;
        Set<String> inherited = new HashSet<>(AROUND_ENTERED.get());
        return aroundAt(state, state.around, 0, anchor, receiver, arguments, values -> {
            List<Object> actual = new ArrayList<>();
            if (receiver != null) {
                actual.add(receiver);
            }
            Collections.addAll(actual, values);
            try {
                return terminal.invokeWithArguments(actual);
            } catch (Throwable failure) {
                throw rethrow(failure);
            }
        }, inherited);
    }

    private static Object aroundAt(State state, List<Subscription> wrappers, int index, Class<?> anchor,
            Object receiver, Object[] arguments, Function<Object[], Object> terminal, Set<String> inherited) {
        if (index == wrappers.size()) {
            return terminal.apply(arguments);
        }
        Subscription subscription = wrappers.get(index);
        if (!subscription.alive || subscription.closed || subscription.callback == null) {
            return aroundAt(state, wrappers, index + 1, anchor, receiver, arguments, terminal, inherited);
        }
        HookDefinition definition = subscription.definition;
        String recursion = state.site.id + ":" + definition.owner;
        Set<String> nested = AROUND_ENTERED.get();
        if (inherited.contains(recursion)) {
            subscription.skipped++;
            if (definition.reentry.equals("error")) {
                throw new HookCallbackFailure("around reentry refused: " + definition.key(),
                        new HookFailure("hook_recursion", "around callback reentered its owner/site"));
            }
            return aroundAt(state, wrappers, index + 1, anchor, receiver, arguments, terminal, inherited);
        }
        if (Thread.currentThread() != clientThread || AROUND_DEPTH.get() >= 16) {
            throw new HookCallbackFailure("around entry refused: " + definition.key(),
                    new HookFailure(Thread.currentThread() != clientThread ? "unsupported_thread" : "hook_recursion",
                            "around requires the client thread and at most 16 nested callbacks"));
        }
        boolean ownsGuard = nested.add(recursion);
        int depth = AROUND_DEPTH.get();
        AROUND_DEPTH.set(depth + 1);
        long started = System.nanoTime();
        try (HookInvocation invocation = new HookInvocation(definition, state.site.target.key(), anchor, receiver,
                arguments, null, null)) {
            invocation.continuation(
                    values -> aroundAt(state, wrappers, index + 1, anchor, receiver, values, terminal, inherited));
            try {
                subscription.callback.invoke(invocation);
                if (!subscription.alive || subscription.closed || invocation.observationInvalidated()) {
                    return terminalOutcome(invocation);
                }
                invocation.validate();
                Throwable nativeFailure = invocation.nativeFailure();
                if (nativeFailure instanceof VirtualMachineError || nativeFailure instanceof ThreadDeath) {
                    throw rethrow(nativeFailure);
                }
                if (nativeFailure != null) {
                    if (!definition.nativeErrors.equals("recover")) {
                        throw rethrow(nativeFailure);
                    }
                    subscription.recovered++;
                    diagnose(subscription, "native_error_recovered", nativeFailure);
                } else if (invocation.continuedFailure() != null) {
                    // Recovery never swallows an inner wrapper/contract failure.
                    throw rethrow(invocation.continuedFailure());
                }
                subscription.calls++;
                if (!invocation.proceeded()) {
                    subscription.shortCircuits++;
                }
                return invocation.result();
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                if (!subscription.alive || subscription.closed || invocation.observationInvalidated()) {
                    return terminalOutcome(invocation);
                }
                Throwable nativeFailure = invocation.nativeFailure();
                if (nativeFailure != null) {
                    if (failure != nativeFailure && !failure.getClass().getName()
                            .equals("betamoon.luaapi.interop.NativeHookCall$ContinuationError")) {
                        diagnose(subscription, "around_secondary_failure", failure);
                    }
                    throw rethrow(nativeFailure);
                }
                subscription.failures++;
                diagnose(subscription,
                        failure instanceof HookFailure ? ((HookFailure) failure).reason : "callback_failed", failure);
                if (failure instanceof HookCallbackFailure) {
                    throw (HookCallbackFailure) failure;
                }
                throw new HookCallbackFailure("Hook " + definition.key() + " failed at around", failure);
            }
        } finally {
            subscription.nanos += System.nanoTime() - started;
            if (depth == 0) {
                AROUND_DEPTH.remove();
            } else {
                AROUND_DEPTH.set(depth);
            }
            if (ownsGuard) {
                nested.remove(recursion);
            }
            if (nested.isEmpty()) {
                AROUND_ENTERED.remove();
            }
        }
    }

    private static Object terminalOutcome(HookInvocation invocation) {
        if (!invocation.proceeded()) {
            throw new HookCallbackFailure("around owner invalidated before continuation",
                    new HookFailure("stale_owner", "an invalidated callback cannot publish a replacement"));
        }
        if (invocation.continuedFailure() != null) {
            throw rethrow(invocation.continuedFailure());
        }
        return invocation.continuedResult();
    }

    private static void diagnose(Subscription subscription, String reason, Throwable error) {
        HookPreparation preparation = HookPreparation.current();
        if (preparation != null) {
            preparation.diagnose(subscription.definition.owner, subscription.definition.id, reason,
                    error.getClass().getName() + ": " + NativeDiagnosticEvidence.message(error));
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable failure) throws T {
        throw (T) failure;
    }

    public static boolean activeOperation(int site, int before, int after, Class<?> anchor) {
        if (AGENT != null) {
            try {
                return (boolean) AGENT.activeOperation.invokeExact(site, before, after, anchor);
            } catch (Throwable failure) {
                throw HookAgentBridge.failure(failure);
            }
        }
        // Resolve the same defining-class anchor used by ordinary envelopes without
        // allocating a frame.
        active(site, 0, anchor);
        State state = state(site);
        if (state == null || state.anchor != anchor) {
            return false;
        }
        List<Subscription>[] operations = state.operations;
        return before >= 0 && before < operations.length && !operations[before].isEmpty()
                || after >= 0 && after < operations.length && !operations[after].isEmpty();
    }

    public static Object[] beginOperation(int site, int before, int after, Class<?> anchor, Object enclosingReceiver,
            Object[] enclosingArguments, Object receiver, Object[] arguments) {
        if (AGENT != null) {
            try {
                return (Object[]) AGENT.beginOperation.invokeExact(site, before, after, anchor, enclosingReceiver,
                        enclosingArguments, receiver, arguments);
            } catch (Throwable failure) {
                throw HookAgentBridge.failure(failure);
            }
        }
        return HookOperations.begin(site, before, after, anchor, enclosingReceiver, enclosingArguments, receiver,
                arguments);
    }

    public static Object finishOperation(Object[] frame, Object result) {
        if (AGENT != null) {
            try {
                return (Object) AGENT.finishOperation.invokeExact(frame, result);
            } catch (Throwable failure) {
                throw HookAgentBridge.failure(failure);
            }
        }
        return HookOperations.finish(frame, result);
    }

    static List<Subscription> operationSubscriptions(int site, int operation, Class<?> anchor) {
        State state = state(site);
        if (state == null || state.anchor != anchor) {
            throw new HookFailure("dispatcher_not_visible", "operation belongs to another defining class");
        }
        List<Subscription>[] current = state.operations;
        return operation < 0 || operation >= current.length ? Collections.emptyList() : current[operation];
    }

    static HookPreparation.Site operationContainer(int site, Class<?> anchor) {
        State state = state(site);
        if (state == null || state.anchor != anchor || !state.site.status.equals("applied")) {
            throw new HookFailure("dispatcher_not_visible", "operation container is not the installed class");
        }
        return state.site;
    }

    static boolean onClientThread() {
        return Thread.currentThread() == clientThread;
    }

    private static Entry dispatch(int site, Class<?> anchor, Object receiver, Object[] arguments, Object result,
            Throwable exception, String phase) {
        State state = state(site);
        Entry outcome = new Entry(arguments, result);
        if (state == null || state.anchor != anchor) {
            return outcome;
        }
        List<Subscription> subscriptions = state.phase(phase);
        for (Subscription subscription : subscriptions) {
            HookDefinition definition = subscription.definition;
            if (!subscription.alive || subscription.closed || !definition.at.equals(phase)) {
                continue;
            }
            boolean entered = false;
            Set<String> nested = null;
            String recursion = site + ":" + definition.owner;
            try {
                if (Thread.currentThread() != clientThread) {
                    throw new HookFailure("unsupported_thread", "synchronous hook requires the client thread");
                }
                nested = ENTERED.get();
                if (nested.contains(recursion)) {
                    subscription.skipped++;
                    if (definition.edit) {
                        throw new HookFailure("hook_recursion", "editing hook reentered its owner/site");
                    }
                    continue;
                }
                if (nested.size() >= 16) {
                    throw new HookFailure("hook_recursion", "hook callback nesting exceeds 16");
                }
                nested.add(recursion);
                entered = true;
                Callback callback = subscription.callback;
                if (callback == null || !subscription.alive) {
                    continue;
                }
                long started = System.nanoTime();
                try (HookInvocation invocation = new HookInvocation(definition, state.site.target.key(), anchor,
                        receiver, outcome.arguments, outcome.result, exception)) {
                    callback.invoke(invocation);
                    if (!subscription.alive || subscription.closed) {
                        continue;
                    }
                    invocation.validate();
                    // Only successful, still-owned callbacks publish their complete edit buffer.
                    outcome.arguments = invocation.committedArguments();
                    outcome.result = invocation.result();
                    outcome.shortCircuit = invocation.shortCircuit();
                    subscription.calls++;
                } finally {
                    subscription.nanos += System.nanoTime() - started;
                }
                if (outcome.shortCircuit) {
                    subscription.shortCircuits++;
                    break;
                }
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                subscription.failures++;
                HookPreparation prepared = HookPreparation.current();
                if (prepared != null) {
                    prepared.diagnose(definition.owner, definition.id,
                            failure instanceof HookFailure
                                    ? ((HookFailure) failure).reason
                                    : definition.edit ? "callback_failed" : "callback_disabled",
                            definition.file + " [" + phase + "]: " + NativeDiagnosticEvidence.message(failure));
                }
                if (definition.edit) {
                    throw new HookCallbackFailure("Hook " + definition.key() + " failed at " + phase, failure);
                }
                subscription.close();
            } finally {
                if (entered) {
                    nested.remove(recursion);
                    if (nested.isEmpty()) {
                        ENTERED.remove();
                    }
                }
            }
        }
        return outcome;
    }

    private static State state(int site) {
        State[] current = states;
        return site >= 0 && site < current.length ? current[site] : null;
    }

    public static final class Entry {
        public Object[] arguments;
        public Object result;
        public boolean shortCircuit;

        Entry(Object[] arguments, Object result) {
            this.arguments = arguments;
            this.result = result;
        }
    }

    public static final class Subscription implements AutoCloseable {
        private final State state;
        public final HookDefinition definition;
        volatile Callback callback;
        volatile boolean alive;
        volatile boolean closed;
        private final AutoCloseable remote;
        public long calls;
        public long failures;
        public long skipped;
        public long nanos;
        public long shortCircuits;
        public long recovered;

        Subscription(State state, HookDefinition definition, Callback callback) {
            this.state = state;
            this.definition = definition;
            this.callback = callback;
            this.remote = null;
        }

        Subscription(HookDefinition definition, AutoCloseable remote) {
            this.state = null;
            this.definition = definition;
            this.remote = remote;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (remote != null) {
                alive = false;
                try {
                    remote.close();
                    return;
                } catch (Exception error) {
                    throw HookAgentBridge.failure(error);
                }
            }
            synchronized (HookDispatcher.class) {
                alive = false;
                callback = null;
                List<Subscription> remaining = new ArrayList<>(state.subscriptions);
                remaining.remove(this);
                state.subscriptions = Collections.unmodifiableList(remaining);
                state.refresh();
                changed();
            }
        }
    }

    private static final class State {
        private final HookPreparation.Site site;
        private volatile ClassLoader loader;
        private volatile Class<?> anchor;
        private volatile int active;
        private volatile List<Subscription> subscriptions = Collections.emptyList();
        private volatile List<Subscription> before = Collections.emptyList();
        private volatile List<Subscription> after = Collections.emptyList();
        private volatile List<Subscription> error = Collections.emptyList();
        private volatile List<Subscription> around = Collections.emptyList();
        private volatile MethodHandle body;
        @SuppressWarnings("unchecked")
        private volatile List<Subscription>[] operations = new List[0];

        State(HookPreparation.Site site) {
            this.site = site;
        }

        void refresh() {
            List<Subscription> entry = new ArrayList<>();
            List<Subscription> returned = new ArrayList<>();
            List<Subscription> exceptional = new ArrayList<>();
            List<Subscription> wrappers = new ArrayList<>();
            List<List<Subscription>> operationLists = new ArrayList<>();
            for (int index = 0; index < site.operations.size(); index++) {
                operationLists.add(new ArrayList<>());
            }
            for (Subscription subscription : subscriptions) {
                if (subscription.alive && !subscription.closed) {
                    if (subscription.definition.point != null) {
                        HookPointSite point = site.points.get(subscription.definition.point.key());
                        if (point != null && point.operationId >= 0 && point.operationId < operationLists.size()) {
                            operationLists.get(point.operationId).add(subscription);
                        }
                        continue;
                    }
                    String at = subscription.definition.at;
                    (at.equals("before")
                            ? entry
                            : (at.equals("after") || at.equals("constructed"))
                                    ? returned
                                    : at.equals("around") ? wrappers : exceptional)
                            .add(subscription);
                }
            }
            @SuppressWarnings("unchecked")
            List<Subscription>[] selectedOperations = new List[operationLists.size()];
            for (int index = 0; index < selectedOperations.length; index++) {
                List<Subscription> entries = operationLists.get(index);
                if (!site.operations.get(index).before()) {
                    Collections.reverse(entries);
                }
                selectedOperations[index] = Collections.unmodifiableList(entries);
            }
            operations = selectedOperations;
            Collections.reverse(returned);
            before = Collections.unmodifiableList(entry);
            after = Collections.unmodifiableList(returned);
            error = Collections.unmodifiableList(exceptional);
            around = Collections.unmodifiableList(wrappers);
            active = (entry.isEmpty() ? 0 : 1) | (returned.isEmpty() ? 0 : 2) | (exceptional.isEmpty() ? 0 : 4)
                    | (wrappers.isEmpty() ? 0 : 8);
        }

        Integer order(Subscription subscription) {
            if (subscription.definition.point == null) {
                int index = phase(subscription.definition.at).indexOf(subscription);
                return index < 0 ? null : index;
            }
            HookPointSite point = site.points.get(subscription.definition.point.key());
            if (point == null || point.operationId < 0 || point.operationId >= operations.length) {
                return null;
            }
            int index = operations[point.operationId].indexOf(subscription);
            return index < 0 ? null : index;
        }

        List<Subscription> phase(String phase) {
            return phase.equals("before")
                    ? before
                    : (phase.equals("after") || phase.equals("constructed"))
                            ? after
                            : phase.equals("around") ? around : error;
        }
    }
}
