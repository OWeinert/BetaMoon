package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
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
    private static final ThreadLocal<Set<String>> ENTERED = ThreadLocal.withInitial(HashSet::new);

    private HookDispatcher() {
    }

    public static String protocol() {
        return "betamoon-hooks-jdk-abi-2";
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
                    callback.invoke(invocation);
                    invocation.validate();
                    return new Object[]{invocation.committedArguments(), invocation.result(),
                            invocation.shortCircuit()};
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
                || !state.site.status.equals("applied") && !state.site.status.equals("target_pending_load")) {
            throw new HookFailure("owner_not_ready", "target is not applied for " + definition.key());
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
                        "order",
                        state.phase(definition.at).contains(entry) ? state.phase(definition.at).indexOf(entry) : null,
                        "calls", entry.calls, "failures", entry.failures, "skippedReentry", entry.skipped,
                        "callbackNanos", entry.nanos, "shortCircuits", entry.shortCircuits));
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
        return dispatch(site, anchor, receiver, arguments, result, null, "after").result;
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
            Object[] outcome = callback
                    .apply(new Object[]{invocation.site, invocation.declaringClass(), invocation.receiver(),
                            invocation.committedArguments(), invocation.result(), invocation.exception()});
            if (outcome == null || outcome.length != 3 || !(outcome[0] instanceof Object[])
                    || !(outcome[2] instanceof Boolean)) {
                throw new HookFailure("hook_invalid_value", "invalid callback transport outcome");
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
                            definition.file + " [" + phase + "]: " + failure.getMessage());
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
        private volatile Callback callback;
        private volatile boolean alive;
        private volatile boolean closed;
        private final AutoCloseable remote;
        public long calls;
        public long failures;
        public long skipped;
        public long nanos;
        public long shortCircuits;

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

        State(HookPreparation.Site site) {
            this.site = site;
        }

        void refresh() {
            List<Subscription> entry = new ArrayList<>();
            List<Subscription> returned = new ArrayList<>();
            List<Subscription> exceptional = new ArrayList<>();
            for (Subscription subscription : subscriptions) {
                if (subscription.alive && !subscription.closed) {
                    String at = subscription.definition.at;
                    (at.equals("before") ? entry : at.equals("after") ? returned : exceptional).add(subscription);
                }
            }
            Collections.reverse(returned);
            before = Collections.unmodifiableList(entry);
            after = Collections.unmodifiableList(returned);
            error = Collections.unmodifiableList(exceptional);
            active = (entry.isEmpty() ? 0 : 1) | (returned.isEmpty() ? 0 : 2) | (exceptional.isEmpty() ? 0 : 4);
        }

        List<Subscription> phase(String phase) {
            return phase.equals("before") ? before : phase.equals("after") ? after : error;
        }
    }
}
