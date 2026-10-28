package betamoon.luamodloader;

import betamoon.hooks.HookDefinition;
import betamoon.hooks.HookDispatcher;
import betamoon.hooks.HookFailure;
import betamoon.hooks.HookFileCollectionContext;
import betamoon.hooks.HookPreparation;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.interop.NativeHookCall;
import betamoon.luaapi.interop.NativeHookOperationCall;
import betamoon.luaapi.interop.NativeHookConstructedCall;
import org.luaj.vm2.LuaValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.luaj.vm2.Globals;
import org.luaj.vm2.Varargs;

/**
 * Preflight before unloading, stage before entrypoint, publish only after
 * successful owner initialization.
 */
final class HookLifecycle {
    private static final ThreadLocal<Map<String, Attempt>> PREFLIGHT = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<String, Integer>> DECLARATIONS = ThreadLocal.withInitial(HashMap::new);

    private HookLifecycle() {
    }

    static void beginAttempt() {
        PREFLIGHT.remove();
        DECLARATIONS.remove();
    }

    static void endAttempt() {
        PREFLIGHT.remove();
        DECLARATIONS.remove();
    }

    static void preflight(LuaModSource source) throws IOException {
        try {
            Attempt attempt = inspect(source);
            reserve(source, attempt);
            PREFLIGHT.get().put(key(source), attempt);
        } catch (IOException | RuntimeException failure) {
            HookPreparation preparation = HookPreparation.current();
            if (preparation != null) {
                preparation.diagnose(source.ownerId(), "",
                        failure instanceof HookFailure ? ((HookFailure) failure).reason : "hook_file_invalid",
                        failure.getMessage());
            }
            throw failure;
        }
    }

    private static void reserve(LuaModSource source, Attempt attempt) throws IOException {
        Map<String, Integer> declarations = DECLARATIONS.get();
        String key = key(source);
        int total = attempt.declarations.size();
        for (Map.Entry<String, Integer> entry : declarations.entrySet()) {
            if (!entry.getKey().equals(key)) {
                total += entry.getValue();
            }
        }
        if (total > 512) {
            throw new HookFailure("hook_file_invalid", "session exceeds 512 hook definitions");
        }
        declarations.put(key, attempt.declarations.size());
    }

    private static Attempt inspect(LuaModSource source) throws IOException {
        HookPackages.Snapshot snapshot = HookPackages.snapshot(source);
        if (snapshot == null) {
            return new Attempt(null, Collections.emptyList());
        }
        HookPreparation preparation = HookPreparation.current();
        List<HookFileCollectionContext.Registration> definitions = preparation == null
                ? HookPreparation.evaluate(snapshot, null)
                : preparation.preflight(snapshot);
        Attempt result = new Attempt(snapshot, definitions);
        for (HookFileCollectionContext.Registration registration : definitions) {
            if (registration.definition.required) {
                if (preparation == null) {
                    throw new HookFailure("agent_unavailable", registration.definition.key());
                }
                result.required.put(registration.definition.id, resolveRequired(preparation, registration.definition));
            }
        }
        return result;
    }

    static Stage stage(LuaModSource source, Globals globals) throws IOException {
        Attempt attempt = PREFLIGHT.get().remove(key(source));
        if (attempt == null) {
            preflight(source);
            attempt = PREFLIGHT.get().remove(key(source));
        }
        if (attempt.snapshot == null) {
            return null;
        }
        HookPreparation preparation = HookPreparation.current();
        List<HookFileCollectionContext.Registration> callbacks = preparation == null
                ? HookPreparation.evaluate(attempt.snapshot, globals)
                : preparation.runtime(attempt.snapshot, globals, attempt.declarations);
        if (preparation == null && !requestedMetadata(callbacks).equals(requestedMetadata(attempt.declarations))) {
            throw new HookFailure("declaration_mismatch",
                    attempt.snapshot.owner + ": declarations differ between phases");
        }
        Stage stage = new Stage(attempt, callbacks);
        ScriptExecution.withOwner(source.entrypointRelative(), () -> {
            ScriptResourceTracker.track(stage);
            return null;
        });
        return stage;
    }

    private static Map<String, String> requestedMetadata(List<HookFileCollectionContext.Registration> registrations) {
        Map<String, String> result = new TreeMap<>();
        for (HookFileCollectionContext.Registration registration : registrations) {
            HookDefinition definition = registration.definition;
            result.put(definition.key(), definition.file + ":" + definition.metadataKey());
        }
        return result;
    }

    private static String key(LuaModSource source) throws IOException {
        return source.scriptsRoot().getCanonicalPath() + ":" + source.entrypointRelative();
    }

    private static Class<?> resolveRequired(HookPreparation preparation, HookDefinition definition) {
        Set<Class<?>> matches = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ClassLoader> routes = new ArrayList<>();
        ClassLoader preferred = HookLifecycle.class.getClassLoader();
        routes.add(preferred);
        for (ClassLoader route : NativeDiscoveryRuntime.hookLoaders()) {
            if (route != ClassLoader.getSystemClassLoader() || preferred == route) {
                routes.add(route);
            }
        }
        for (HookPreparation.Site site : preparation.targets(definition, NativeDiscoveryRuntime.hookNamespace())) {
            ClassLoader verified = HookDispatcher.targetLoader(site.id);
            List<ClassLoader> loaders = verified == null ? routes : Collections.singletonList(verified);
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                try {
                    Class<?> type = Class.forName(site.target.owner.replace('/', '.'), false, loader);
                    preparation.refreshSites();
                    if (site.status.equals("applied")) {
                        if (type.getClassLoader() != HookDispatcher.targetLoader(site.id)) {
                            throw new HookFailure("ambiguous_loader",
                                    "required target has an unrelated defining loader");
                        }
                        matches.add(type);
                        break;
                    } else if (!site.status.equals("target_pending_load")) {
                        throw new HookFailure(site.status, site.target.key());
                    }
                } catch (ClassNotFoundException absent) {
                    // The alternative namespace or an optional loader need not contain this exact
                    // type.
                } catch (LinkageError error) {
                    throw new HookFailure("match_failed", site.target.key() + ": " + error, error);
                }
            }
        }
        if (matches.size() != 1) {
            throw new HookFailure("match_failed", definition.key() + ": required target has " + matches.size()
                    + " verified defining classes; expected exactly one");
        }
        return matches.iterator().next();
    }

    private static final class Attempt {
        private final HookPackages.Snapshot snapshot;
        private final List<HookFileCollectionContext.Registration> declarations;
        private final Map<String, Class<?>> required = new HashMap<>();

        Attempt(HookPackages.Snapshot snapshot, List<HookFileCollectionContext.Registration> declarations) {
            this.snapshot = snapshot;
            this.declarations = declarations;
        }
    }

    static final class Stage implements ScriptResourceTracker.Cleanup {
        private final Attempt attempt;
        private final List<HookFileCollectionContext.Registration> callbacks;
        private final List<HookDispatcher.Subscription> subscriptions = new ArrayList<>();
        private boolean closed;
        private boolean published;

        Stage(Attempt attempt, List<HookFileCollectionContext.Registration> callbacks) {
            this.attempt = attempt;
            this.callbacks = new ArrayList<>(callbacks);
        }

        void publish() {
            HookPreparation preparation = HookPreparation.current();
            if (closed || published || preparation == null) {
                return;
            }
            String namespace = NativeDiscoveryRuntime.hookNamespace();
            if (!namespace.equals("named") && !namespace.equals("client")) {
                preparation.diagnose(attempt.snapshot.owner, "", "agent_unavailable",
                        "Optional hooks remain inactive because the client namespace is unavailable");
                callbacks.clear();
                return;
            }
            HookDispatcher.clientThread();
            try {
                for (HookFileCollectionContext.Registration registration : callbacks) {
                    HookDefinition definition = registration.definition;
                    for (HookPreparation.Site site : preparation.targets(definition,
                            NativeDiscoveryRuntime.hookNamespace())) {
                        Class<?> required = attempt.required.get(definition.id);
                        if (required != null && !required.getName().replace('.', '/').equals(site.target.owner)) {
                            continue;
                        }
                        if (!site.status.equals("applied") && !site.status.equals("target_pending_load")) {
                            if (definition.required) {
                                throw new HookFailure(site.status, site.target.key());
                            }
                            continue;
                        }
                        subscriptions.add(HookDispatcher.subscribe(site.id, required, definition, invocation -> {
                            if (closed) {
                                return;
                            }
                            String caller = LuaScriptRegistry.getCurrentScriptFile();
                            boolean edits = NativeContext.hookEditsAllowed();
                            NativeContext callerContext = NativeContext.captureCaller();
                            ScriptExecution.withOwner(attempt.snapshot.resourceOwner, () -> {
                                try (HookCallbackObservation observation = new HookCallbackObservation(
                                        attempt.snapshot.resourceOwner, caller, invocation, callerContext)) {
                                    LuaValue call = definition.at.equals("constructed")
                                            ? new NativeHookConstructedCall(invocation, observation::context)
                                            : definition.point == null
                                                    ? new NativeHookCall(invocation, observation::context, edits,
                                                            values -> observation
                                                                    .continueWith(() -> invocation.proceed(values)))
                                                    : new NativeHookOperationCall(invocation, observation::context,
                                                            edits);
                                    Varargs returned = ScriptExecution.invokeNative(attempt.snapshot.resourceOwner,
                                            definition.callbackLabel, registration.callback, call);
                                    if (definition.at.equals("around")) {
                                        ((NativeHookCall) call).complete(returned);
                                    }
                                    observation.context().check();
                                }
                                return null;
                            });
                        }));
                    }
                }
                HookDispatcher.activate(subscriptions);
                published = true;
                callbacks.clear();
            } catch (RuntimeException | Error failure) {
                run();
                throw failure;
            }
        }

        @Override
        public void run() {
            closed = true;
            for (HookDispatcher.Subscription subscription : subscriptions) {
                subscription.close();
            }
            subscriptions.clear();
            callbacks.clear();
            attempt.required.clear();
        }
    }
}
