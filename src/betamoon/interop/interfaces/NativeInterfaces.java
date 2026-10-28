package betamoon.interop.interfaces;

import betamoon.interop.attachments.NativeAttachment;
import betamoon.interop.attachments.NativeAttachmentDisposal;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.hosts.NativeHostShape;
import betamoon.interop.hosts.NativeHostDispatch;
import betamoon.interop.value.NativeConversions;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.interop.runtime.NativeOwnedValue;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.BiConsumer;

/**
 * Client-owned proxy/attachment lifecycle. Foreign retained proxies only weakly
 * refer back here.
 */
public final class NativeInterfaces {
    @FunctionalInterface
    public interface Callback {
        Object invoke(Method method, Object[] arguments, LuaCallbackScope scope);
    }

    public static final class MethodCallback {
        public final Callback callback;
        public final String policy;

        public MethodCallback(Callback callback, String policy) {
            if (callback == null || !policy.equals("query") && !policy.equals("gameplay")) {
                throw new NativeAccessFailure("invalid_interface_policy", "choose query or gameplay per method");
            }
            this.callback = callback;
            this.policy = policy;
        }
    }

    private final NativeBindings bindings;
    private final Map<NativeOwnedValue, Entry> entries = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<NativeImplementation> disabled = new ConcurrentLinkedQueue<>();
    private final List<Removal> removals = new ArrayList<>();
    private final AtomicLong failures = new AtomicLong();
    private Function<LuaCallbackScope, NativeEntry> entryFactory = scope -> null;
    private volatile Map<String, Object> status = CatalogValues.record("implementations", 0, "attachments", 0,
            "pendingRemovals", 0);

    public NativeInterfaces(NativeBindings bindings) {
        this.bindings = bindings;
    }

    public void entryFactory(Function<LuaCallbackScope, NativeEntry> factory) {
        bindings.requireThread();
        entryFactory = factory;
    }

    public NativeImplementation create(NativeContext context, NativeInterfaceContract contract,
            Map<String, MethodCallback> supplied, String policy, String lifetime, boolean observerDefaults) {
        context.prepare();
        if (entries.size() >= 2048 || removals.size() >= 2048
                || entries.values().stream().filter(entry -> entry.owner.equals(context.owner())).count() >= 256) {
            throw new NativeAccessFailure("native_interface_limit",
                    "implementation/attachment lifecycle registry is full");
        }
        if (!policy.equals("query") && !policy.equals("gameplay")) {
            throw new NativeAccessFailure("invalid_interface_policy", "choose query or gameplay");
        }
        Map<String, MethodCallback> callbacks = new LinkedHashMap<>(supplied);
        if (!contract.methods.keySet().containsAll(callbacks.keySet())) {
            throw new NativeAccessFailure("invalid_interface_method", "callback is outside the interface contract");
        }
        for (Map.Entry<String, Method> method : contract.methods.entrySet()) {
            if (!callbacks.containsKey(method.getKey())) {
                if (!observerDefaults || method.getValue().getReturnType() != Void.TYPE) {
                    throw new NativeAccessFailure("missing_interface_method", method.getKey());
                }
                callbacks.put(method.getKey(), new MethodCallback((selected, arguments, scope) -> null, "query"));
            }
        }
        if (observerDefaults && (!policy.equals("query")
                || contract.methods.values().stream().anyMatch(method -> method.getReturnType() != Void.TYPE)
                || callbacks.values().stream().anyMatch(callback -> !callback.policy.equals("query")))) {
            throw new NativeAccessFailure("invalid_observer_contract",
                    "observer defaults require read-only void methods");
        }
        Handler handler = new Handler(this, callbacks, new ArrayList<>(contract.methods.values()), observerDefaults);
        Object proxy;
        try {
            proxy = Proxy.newProxyInstance(contract.loader, contract.interfaces.toArray(new Class<?>[0]), handler);
        } catch (IllegalArgumentException | SecurityException | LinkageError exception) {
            handler.clear();
            throw new NativeAccessFailure("native_proxy_creation_failed", "interface visibility or JVM contract",
                    exception);
        }
        List<NativeTypeRef> interfaces = new ArrayList<>();
        for (Class<?> type : contract.interfaces) {
            interfaces.add(bindings.reference(type));
        }
        return register(context, proxy, handler, interfaces, policy, lifetime, null, null);
    }

    public NativeImplementation createClass(NativeContext context, NativeHostShape shape,
            Map<String, MethodCallback> supplied, Map<String, String> closedPolicies, Object[] arguments,
            String lifetime, NativeMemberPlan disposer) {
        return createClass(context, shape, supplied, closedPolicies, arguments, lifetime, disposer,
                (token, instance) -> {
                });
    }

    public NativeImplementation createClass(NativeContext context, NativeHostShape shape,
            Map<String, MethodCallback> supplied, Map<String, String> closedPolicies, Object[] arguments,
            String lifetime, NativeMemberPlan disposer, BiConsumer<NativeImplementation, Object> setup) {
        if (setup == null) {
            throw new NativeAccessFailure("invalid_native_host_setup",
                    "setup callback is required by the class factory");
        }
        if (context.bindings != bindings || !lifetime.equals("owner") && !lifetime.equals("world")) {
            throw new NativeAccessFailure("invalid_native_lifetime",
                    "use this native session and owner/world lifetime");
        }
        if (lifetime.equals("world") && context.root("world") == null) {
            throw new NativeAccessFailure("world_unavailable", "world host lifetime needs the current world");
        }
        if (disposer != null && (!(disposer.member instanceof Method) || disposer.isStatic
                || disposer.parameters.length != 0 || disposer.valueType != Void.TYPE
                || !disposer.member.getDeclaringClass().isAssignableFrom(shape.generated))) {
            throw new NativeAccessFailure("invalid_native_disposer", "use an exact instance method ()V on this host");
        }
        if (shape.clientScreen(bindings)) {
            context.requireClientResource();
            if (disposer != null && (disposer.member.getDeclaringClass() != shape.base
                    || !bindings.names.methodMatches((Method) disposer.member, "onGuiClosed", "mapped"))) {
                throw new NativeAccessFailure("native_phase_denied",
                        "curated screen cleanup supports only native onGuiClosed");
            }
        } else {
            context.require(NativeContext.Effect.UNKNOWN, true);
            context.require(NativeContext.Effect.INITIALIZE, true);
        }
        context.prepare();
        if (entries.size() >= 2048 || removals.size() >= 2048
                || entries.values().stream().filter(entry -> entry.owner.equals(context.owner())).count() >= 256) {
            throw new NativeAccessFailure("native_interface_limit",
                    "implementation/attachment lifecycle registry is full");
        }
        Map<String, MethodCallback> callbacks = new LinkedHashMap<>();
        String[] policies = new String[shape.methods.size()];
        for (int index = 0; index < shape.methods.size(); index++) {
            Method method = shape.methods.get(index);
            String key = NativeInterfaceContract.key(method);
            MethodCallback callback = supplied.get(key);
            if (callback == null) {
                throw new NativeAccessFailure("missing_native_host_method", key);
            }
            policies[index] = closedPolicies.getOrDefault(key, shape.hasSuper(index) ? "super" : "error");
            callbacks.put(key, new MethodCallback((actual, input, scope) -> {
                Object result = callback.callback.invoke(actual, input, scope);
                if (actual.getReturnType() == Void.TYPE) {
                    if (result != null) {
                        throw new NativeAccessFailure("invalid_callback_return", "void host callbacks return no value");
                    }
                    return null;
                }
                return NativeConversions.convert(result, actual.getReturnType());
            }, callback.policy));
        }
        if (!callbacks.keySet().equals(supplied.keySet()) || !callbacks.keySet().containsAll(closedPolicies.keySet())
                || arguments.length != shape.baseConstructor.getParameterCount()) {
            throw new NativeAccessFailure("invalid_native_host_methods",
                    "callbacks/arguments must match the exact prepared shape");
        }
        Object[] converted = new Object[arguments.length];
        Class<?>[] parameters = shape.baseConstructor.getParameterTypes();
        for (int index = 0; index < converted.length; index++) {
            bindings.validateNull(arguments[index], parameters[index], context);
            converted[index] = NativeConversions.convert(arguments[index], parameters[index]);
            if (!lifetime.equals("world") && converted[index] != null
                    && (context.isRoot(converted[index]) || bindings.borrowedRootType(converted[index].getClass()))) {
                throw new NativeAccessFailure("invalid_native_lifetime",
                        "live constructor roots require world lifetime");
            }
        }
        Handler handler = new Handler(this, callbacks, shape.methods, false);
        String callbackOwner = context.owner();
        NativeHostDispatch dispatch = new NativeHostDispatch(shape, policies, handler::invokeSlot,
                LuaScriptRegistry.callbackOwnerReadiness(callbackOwner));
        handler.invalidation = dispatch::close;
        try {
            Object instance = shape.construct(dispatch, converted);
            context.check();
            NativeImplementation token = register(context, instance, handler, Collections.emptyList(), "per_method",
                    lifetime, disposer, shape);
            setup.accept(token, instance);
            context.check();
            requireLive(token, context);
            dispatch.activate();
            return token;
        } catch (RuntimeException | Error failure) {
            handler.disable("construction_failed");
            throw failure;
        }
    }

    private NativeImplementation register(NativeContext context, Object payload, Handler handler,
            List<NativeTypeRef> interfaces, String policy, String lifetime, NativeMemberPlan disposer,
            NativeHostShape shape) {
        NativeOwnedValue owned = shape == null
                ? bindings.owned.retain(context, payload, lifetime, disposer)
                : bindings.owned.retainGeneratedHost(context, payload, lifetime, disposer, shape);
        NativeImplementation token = new NativeImplementation(owned, interfaces, policy, context.owner());
        handler.token = token;
        String owner = context.owner();
        long generation = ScriptResourceTracker.ownerGeneration(owner);
        ScriptResourceTracker.Cleanup cleanup = () -> handler.disable("stale_owner");
        Entry entry = new Entry(token, handler, owner, generation, cleanup);
        entries.put(owned, entry);
        bindings.owned.onInvalidation(owned, handler::disable);
        ScriptResourceTracker.track(cleanup);
        publish();
        return token;
    }

    public NativeAttachment attach(NativeContext context, NativeImplementation token, Object receiver,
            NativeMemberPlan add, NativeMemberPlan remove, String lifetime, boolean managed) {
        context.require(managed ? NativeContext.Effect.WRITE : NativeContext.Effect.UNKNOWN, !managed);
        Object proxy = token.value.use(context);
        Entry entry = requireEntry(token);
        if (entry.attachment != null) {
            throw new NativeAccessFailure("already_attached", "one implementation belongs to one managed attachment");
        }
        if (!lifetime.equals("owner") && !lifetime.equals("world")) {
            throw new NativeAccessFailure("invalid_native_lifetime", "choose owner or world explicitly");
        }
        if (lifetime.equals("owner") && receiver != null
                && (context.isRoot(receiver) || bindings.borrowedRootType(receiver.getClass()))) {
            throw new NativeAccessFailure("borrowed_root", "live roots require world attachment lifetime");
        }
        Object world = lifetime.equals("world") ? context.root("world") : null;
        if (lifetime.equals("world") && world == null) {
            throw new NativeAccessFailure("world_unavailable", "world attachment requires the current world");
        }
        NativeAttachment attachment = new NativeAttachment(receiver, proxy, add, remove);
        attachment.prepare(context);
        entry.attachment = attachment;
        entry.handler.attachmentStatus = attachment.status;
        entry.worldBound = lifetime.equals("world");
        entry.world = world;
        entry.player = entry.worldBound ? context.root("player") : null;
        entry.hostLease = receiver == null ? null : bindings.owned.find(receiver);
        try {
            attachment.install(context);
            requireLive(token, context);
        } catch (RuntimeException | Error failure) {
            entry.handler.disable("attachment_install_failed");
            flushDisabled();
            throw failure;
        }
        publish();
        return attachment;
    }

    public void requireLive(NativeImplementation token, NativeContext context) {
        token.value.use(context);
        Entry entry = requireEntry(token);
        if (entry.worldBound && (entry.world != context.root("world") || entry.player != context.root("player"))
                || entry.hostLease != null && !entry.hostLease.isActive()) {
            entry.handler.disable("stale_environment");
            throw new NativeAccessFailure("stale_environment", "attachment world/player/host changed");
        }
    }

    /**
     * Only invalidation is immediate; no native code or list mutation runs here.
     */
    public void requestClose(NativeImplementation token, NativeContext context) {
        token.value.checkOwner(context);
        Entry entry = entries.get(token.value);
        if (entry != null) {
            entry.handler.disable("released");
        }
    }

    private Entry requireEntry(NativeImplementation token) {
        Entry entry = entries.get(token.value);
        if (entry == null || !entry.handler.active()) {
            throw new NativeAccessFailure("native_implementation_closed", "implementation is disabled");
        }
        return entry;
    }

    /**
     * The caller is the audited client lifecycle boundary, outside native/Lua
     * dispatch.
     */
    public void observe(Object world, Object player) {
        bindings.requireThread();
        NativeAttachmentDisposal.drain();
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (!entry.token.value.isActive() || entry.worldBound && (entry.world != world || entry.player != player)
                    || entry.hostLease != null && !entry.hostLease.isActive()) {
                entry.handler.disable("stale_environment");
            }
        }
        flushDisabled();
        drainRemovals();
    }

    public void close() {
        bindings.requireThread();
        for (Entry entry : new ArrayList<>(entries.values())) {
            entry.handler.disable("session_closed");
        }
        flushDisabled();
        drainRemovals();
        for (Removal removal : removals) {
            NativeAttachmentDisposal.defer(removal.owner, removal.attachment, failures);
        }
        removals.clear();
        entryFactory = scope -> null;
        publish();
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>(status);
        result.put("failures", failures.get());
        return result;
    }

    private void flushDisabled() {
        NativeImplementation token;
        while ((token = disabled.poll()) != null) {
            Entry entry = entries.remove(token.value);
            if (entry != null) {
                ScriptResourceTracker.untrack(entry.owner, entry.cleanup);
                bindings.owned.invalidate(token.value, (String) token.value.describe().get("state"));
                if (entry.attachment != null) {
                    entry.attachment.pending();
                    removals.add(new Removal(entry.owner, entry.attachment));
                }
            }
        }
        publish();
    }

    private void drainRemovals() {
        if (NativeContext.hasFrame() || !NativeContext.cleanupAllowed()) {
            return;
        }
        List<Removal> work = new ArrayList<>(removals);
        removals.clear();
        for (Removal removal : work) {
            NativeAttachmentDisposal.remove(removal.owner, removal.attachment, failures);
        }
        publish();
    }

    private void publish() {
        status = CatalogValues.record("implementations", entries.size(), "classHosts",
                entries.values().stream().filter(entry -> entry.token.interfaces.isEmpty()).count(), "attachments",
                entries.values().stream().filter(entry -> entry.attachment != null).count(), "pendingRemovals",
                removals.size());
    }

    private static final class Handler implements InvocationHandler {
        private final WeakReference<NativeInterfaces> registry;
        private volatile Dispatch callbacks;
        private final boolean observer;
        private NativeImplementation token;
        private volatile NativeAttachment.Status attachmentStatus;
        private Runnable invalidation = () -> {
        };

        private Handler(NativeInterfaces registry, Map<String, MethodCallback> callbacks, List<Method> methods,
                boolean observer) {
            this.registry = new WeakReference<>(registry);
            this.callbacks = new Dispatch(callbacks, methods);
            this.observer = observer;
        }

        private static final class Dispatch {
            private final Map<Method, MethodCallback> methods = new LinkedHashMap<>();
            private final MethodCallback[] slots;

            private Dispatch(Map<String, MethodCallback> callbacks, List<Method> selected) {
                slots = new MethodCallback[selected.size()];
                for (int index = 0; index < selected.size(); index++) {
                    Method method = selected.get(index);
                    MethodCallback callback = callbacks.get(NativeInterfaceContract.key(method));
                    slots[index] = callback;
                    methods.put(method, callback);
                }
            }
        }

        private boolean active() {
            return callbacks != null;
        }

        private void clear() {
            callbacks = null;
            invalidation.run();
        }

        private synchronized boolean disable(String reason) {
            if (!active()) {
                return false;
            }
            clear();
            if (token != null) {
                token.value.disable(reason);
            }
            NativeAttachment.Status attachment = attachmentStatus;
            if (attachment != null) {
                attachment.pending();
            }
            NativeInterfaces service = registry.get();
            if (service != null && token != null) {
                service.disabled.add(token);
            }
            return true;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return invokeSlot(proxy, -1, method, arguments);
        }

        private Object invokeSlot(Object proxy, int slot, Method method, Object[] arguments) {
            if (!token.interfaces.isEmpty() && NativeInterfaceContract.objectMethod(method)) {
                switch (method.getName()) {
                    case "equals":
                        return proxy == arguments[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    default:
                        return "BetaMoon native implementation@" + Integer.toHexString(System.identityHashCode(proxy));
                }
            }
            NativeInterfaces service = registry.get();
            try {
                Dispatch dispatch = callbacks;
                if (service == null || dispatch == null) {
                    throw new NativeAccessFailure("native_implementation_closed", method.getName());
                }
                service.bindings.requireThread();
                if (!token.value.isActive()) {
                    throw new NativeAccessFailure("stale_owner", "native implementation lifetime ended");
                }
                MethodCallback selected = slot < 0 ? dispatch.methods.get(method) : dispatch.slots[slot];
                if (selected == null) {
                    throw new NativeAccessFailure("invalid_interface_method", method.getName());
                }
                boolean gameplay = selected.policy.equals("gameplay");
                if (gameplay && !NativeContext.hasFrame()) {
                    throw new NativeAccessFailure("native_context_unavailable",
                            "gameplay callback requires an audited caller");
                }
                try (LuaCallbackScope scope = new LuaCallbackScope(gameplay)) {
                    NativeEntry entry = NativeContext.hasFrame()
                            ? NativeContext.interfaceEntry(scope)
                            : service.entryFactory.apply(scope);
                    if (entry == null) {
                        throw new NativeAccessFailure("native_context_unavailable",
                                "consumer has no client callback entry");
                    }
                    scope.nativeEntry(entry);
                    return ScriptExecution.withOwner(token.owner, () -> {
                        try (NativeContext context = entry.open(token.owner)) {
                            service.requireLive(token, context);
                            Object result = selected.callback.invoke(method,
                                    arguments == null ? new Object[0] : arguments, scope);
                            context.check();
                            service.requireLive(token, context);
                            return result;
                        }
                    });
                }
            } catch (RuntimeException | LinkageError failure) {
                boolean stale = failure instanceof NativeAccessFailure
                        && Arrays.asList("stale_owner", "stale_environment", "world_unavailable", "owned_value_closed",
                                "native_session_closed").contains(((NativeAccessFailure) failure).reason);
                if (service != null && disable(stale ? "stale_environment" : "callback_failed")) {
                    if (!stale) {
                        service.failures.incrementAndGet();
                        LuaScriptErrors.add(token.owner, "Native implementation callback disabled: " + method.getName(),
                                LuaScriptErrors.Outcome.CALLBACK_DISABLED, failure);
                    }
                }
                if (observer && method.getReturnType() == Void.TYPE) {
                    return null;
                }
                throw failure instanceof NativeAccessFailure
                        ? (NativeAccessFailure) failure
                        : new NativeAccessFailure("native_callback_failed", method.getName(), failure);
            }
        }
    }

    private static final class Entry {
        private final NativeImplementation token;
        private final Handler handler;
        private final String owner;
        private final long generation;
        private final ScriptResourceTracker.Cleanup cleanup;
        private NativeAttachment attachment;
        private boolean worldBound;
        private Object world;
        private Object player;
        private NativeOwnedValue hostLease;

        private Entry(NativeImplementation token, Handler handler, String owner, long generation,
                ScriptResourceTracker.Cleanup cleanup) {
            this.token = token;
            this.handler = handler;
            this.owner = owner;
            this.generation = generation;
            this.cleanup = cleanup;
        }
    }

    private static final class Removal {
        private final String owner;
        private final NativeAttachment attachment;

        private Removal(String owner, NativeAttachment attachment) {
            this.owner = owner;
            this.attachment = attachment;
        }
    }
}
