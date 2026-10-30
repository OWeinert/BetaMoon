package betamoon.gui.presentation;

import betamoon.gui.animation.GuiAnimationRequest;
import betamoon.gui.animation.GuiAnimationValueError;
import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.presentation.GuiGeometry.Bounds;
import betamoon.gui.presentation.GuiGeometry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptDiagnostics;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Coordinates source batches, pure derivation, atomic geometry, events and
 * per-screen ownership.
 */
public class GuiPresentationRuntime implements AutoCloseable {
    private final GuiDocumentDefinition definition;
    private final GuiPresentationHost host;
    private GuiPresentationSources sources;
    protected final ToIntFunction<String> textWidth;
    protected final DoubleSupplier clock;
    private final List<GuiPresentationNode> nodes = new ArrayList<>();
    private final Map<GuiPresentationNode, Integer> indices = new IdentityHashMap<>();
    private List<GuiGeometry> displayedGeometry;
    private final Map<String, GuiPresentationNode> ids = new LinkedHashMap<>();
    private final Map<GuiDocumentDefinition.Element, GuiPresentationNode> elements = new IdentityHashMap<>();
    private final Map<String, Change> changes = new LinkedHashMap<>();
    private final Map<String, Object> interactions = new LinkedHashMap<>();
    private final List<QueuedEvent> events = new ArrayList<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private final Map<String, Object> data = new LinkedHashMap<>();
    private Map<String, Object> state = Collections.emptyMap();
    private List<Command> commands;
    private boolean active = true;
    private boolean closing;
    private boolean delivering;
    private boolean eventFailed;
    private boolean ownerInvalidated;
    private boolean initialized;
    private boolean preparing;
    private boolean stagedReload;
    private boolean layoutDirty;
    private BiConsumer<GuiNodeDefinition, Map<String, Object>> viewValidator;

    public void setViewValidator(BiConsumer<GuiNodeDefinition, Map<String, Object>> validator) {
        requireActive();
        if (initialized) {
            throw new IllegalStateException("View validation must be installed before initial publication");
        }
        viewValidator = validator;
    }
    private int nextRequest = 1;
    private Bounds viewport = new Bounds(-1e9, -1e9, 2e9, 2e9);

    public GuiPresentationRuntime(GuiDocumentDefinition definition, GuiPresentationHost host,
            ToIntFunction<String> textWidth) {
        this(definition, host, textWidth, () -> System.nanoTime() / 1e9);
    }

    public GuiPresentationRuntime(GuiDocumentDefinition definition, GuiPresentationHost host,
            ToIntFunction<String> textWidth, DoubleSupplier clock) {
        this.definition = Objects.requireNonNull(definition, "GUI document");
        this.host = Objects.requireNonNull(host, "GUI presentation host");
        this.textWidth = Objects.requireNonNull(textWidth, "GUI text measurement");
        this.clock = Objects.requireNonNull(clock, "GUI clock");
        if (definition.presentation == null) {
            throw new IllegalArgumentException("A compiled presentation definition is required.");
        }
        for (GuiNodeDefinition nodeDefinition : definition.presentation.nodes) {
            GuiPresentationNode node = new GuiPresentationNode(nodeDefinition);
            indices.put(node, nodes.size());
            nodes.add(node);
            if (nodeDefinition.id != null) {
                ids.put(nodeDefinition.id, node);
            }
            if (nodeDefinition.element != null) {
                elements.put(nodeDefinition.element, node);
            }
        }
        snapshot();
        for (GuiPresentationNode node : nodes) {
            nativeBaseline(node);
            node.derived.putAll(node.baseline);
            node.targets.putAll(node.baseline);
            node.displayed.putAll(node.baseline);
        }
        subscriptions.addAll(host.observe(new GuiPresentationHost.Observer() {
            @Override
            public void changed(String source, Object previous, Object value) {
                change(source, previous, value);
            }

            @Override
            public void event(String type, Supplier<LuaTable> payload) {
                queue(type, payload);
            }
        }));
    }

    public GuiPresentationNode node(GuiDocumentDefinition.Element element) {
        return elements.get(element);
    }

    public GuiPresentationNode element(String id) {
        requireActive();
        GuiPresentationNode node = ids.get(id);
        if (node == null) {
            throw new LuaError("Unknown GUI element ID: " + id);
        }
        return node;
    }

    public void requireUsablePublication() {
        requireActive();
        if (!initialized || nodes.stream().anyMatch(node -> node.bindingFailed || node.pending != null)) {
            throw new LuaError("Presentation has invalid bindings or final geometry");
        }
    }

    public void prepareInitial() {
        requireActive();
        if (initialized) {
            throw new IllegalStateException("Initial presentation was already prepared");
        }
        preparing = true;
        update();
        requireUsablePublication();
    }

    public void activatePrepared() {
        if (stagedReload) {
            throw new IllegalStateException("Commit a prepared reload before activation");
        }
        requireUsablePublication();
        if (preparing) {
            preparing = false;
            queue("open", LuaTable::new);
        }
    }

    void requirePreparedViews() {
        requireActive();
        if (!preparing) {
            throw new IllegalStateException("Restore local view state before candidate activation");
        }
    }

    /**
     * Internal diagnostics only: immutable snapshots do not expose mutable render
     * state to Lua.
     */
    public Map<String, Object> debugSnapshot() {
        sample();
        Map<String, Object> result = new LinkedHashMap<>();
        for (GuiPresentationNode node : nodes) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("baseline", Collections.unmodifiableMap(new LinkedHashMap<>(node.baseline)));
            values.put("derived", Collections.unmodifiableMap(new LinkedHashMap<>(node.derived)));
            values.put("target", Collections.unmodifiableMap(new LinkedHashMap<>(node.targets)));
            values.put("displayed", Collections.unmodifiableMap(new LinkedHashMap<>(node.displayed)));
            values.put("dependencies", Collections.unmodifiableSet(new LinkedHashSet<>(node.dependencies)));
            values.put("evaluations", node.evaluations);
            values.put("suspended", node.suspended);
            values.put("waitingForLayout", node.pending != null);
            values.put("suppressedDiagnostics", node.suppressedDiagnostics);
            Map<String, Object> overrides = new LinkedHashMap<>();
            for (Map.Entry<String, GuiPresentationNode.Override> entry : node.overrides.entrySet()) {
                Map<String, Object> override = new LinkedHashMap<>();
                override.put("value", entry.getValue().value);
                override.put("held", entry.getValue().held);
                overrides.put(entry.getKey(), Collections.unmodifiableMap(override));
            }
            values.put("overrides", Collections.unmodifiableMap(overrides));
            Map<String, Object> tracks = new LinkedHashMap<>();
            for (Map.Entry<String, GuiPresentationNode.Track> entry : node.tracks.entrySet()) {
                GuiPresentationNode.Track track = entry.getValue();
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("from", track.from);
                detail.put("to", track.to);
                detail.put("start", track.start);
                detail.put("duration", track.options.duration);
                detail.put("delay", track.options.delay);
                detail.put("requestId", track.request.id);
                tracks.put(entry.getKey(), Collections.unmodifiableMap(detail));
            }
            values.put("tracks", Collections.unmodifiableMap(tracks));
            Map<String, Object> policies = new LinkedHashMap<>();
            for (String property : node.definition.properties.animatable()) {
                GuiTransitionPolicy.Options options = node.policy.forProperty(property);
                if (options != null) {
                    Map<String, Object> policy = new LinkedHashMap<>();
                    policy.put("duration", options.duration);
                    policy.put("delay", options.delay);
                    policy.put("easingSamples", Arrays.asList(options.easing.sample(0), options.easing.sample(.25),
                            options.easing.sample(.5), options.easing.sample(.75), options.easing.sample(1)));
                    policies.put(property, Collections.unmodifiableMap(policy));
                }
            }
            values.put("policy", Collections.unmodifiableMap(policies));
            result.put(label(node), Collections.unmodifiableMap(values));
        }
        return Collections.unmodifiableMap(result);
    }

    public int evaluationCount(String id) {
        return element(id).evaluations;
    }

    public void viewport(double x, double y, double width, double height) {
        if (viewport.x != x || viewport.y != y || viewport.width != width || viewport.height != height) {
            viewport = new Bounds(x, y, width, height);
            displayedGeometry = null;
        }
    }

    /**
     * Called on the UI update/input boundary, including while world simulation is
     * paused.
     */
    public void update() {
        requireActive();
        if (delivering) {
            return;
        }
        if (!callbacksValid()) {
            ownerInvalidated = true;
            events.clear();
            for (GuiPresentationNode node : nodes) {
                node.suspended = true;
                node.dirty = false;
                node.pending = null;
                node.dependencies.clear();
            }
        }
        Map<String, Change> batch;
        synchronized (changes) {
            batch = new LinkedHashMap<>(changes);
            changes.clear();
        }
        boolean initial = !initialized;
        boolean sourceBatch = initial || !batch.isEmpty();
        boolean dirty = nodes.stream().anyMatch(node -> node.dirty && !node.suspended);
        if (!sourceBatch && !dirty && !layoutDirty) {
            sample();
            deliver();
            return;
        }
        displayedGeometry = null;
        if (sourceBatch) {
            snapshot();
        }
        for (GuiPresentationNode node : nodes) {
            if (!node.suspended && !Collections.disjoint(node.dependencies, batch.keySet())) {
                node.dirty = true;
            }
            if (sourceBatch) {
                nativeBaseline(node);
            }
            if (node.dirty && !node.suspended) {
                evaluate(node);
                node.dirty = false;
            } else if (sourceBatch && !node.suspended) {
                Map<String, Object> output = node.pending == null ? node.bindingValues : node.pending.bindingValues;
                node.pending = candidate(node, output, node.pending == null ? node.policy : node.pending.policy,
                        new LinkedHashSet<>(node.dependencies));
            }
        }
        commitBindings(initial);
        layoutDirty = false;
        initialized = true;
        if (initial) {
            queue("open", LuaTable::new);
        }
        for (Map.Entry<String, Change> entry : batch.entrySet()) {
            String source = entry.getKey();
            Change change = entry.getValue();
            if (!Objects.equals(change.previous, change.value) && !source.startsWith("interaction:")
                    && !source.startsWith("native:")) {
                int colon = source.indexOf(':');
                queue("change", () -> {
                    LuaTable event = new LuaTable();
                    event.set("source", source.substring(0, colon));
                    event.set("key", source.substring(colon + 1));
                    event.set("value", lua(change.value));
                    event.set("previous", lua(change.previous));
                    return event;
                });
            }
        }
        sample();
        deliver();
    }

    /**
     * Java-only sampling; it cannot evaluate a binding or invoke an event callback.
     */
    public void sample() {
        double now = clock.getAsDouble();
        for (GuiPresentationNode node : nodes) {
            if (!node.tracks.isEmpty()) {
                displayedGeometry = null;
                node.sample(now);
            }
        }
    }

    public GuiGeometry geometry(GuiDocumentDefinition.Element element) {
        return geometry(node(element));
    }

    public GuiGeometry geometry(GuiPresentationNode node) {
        if (definition.retained) {
            throw new IllegalStateException("Retained input and painting use the scene's displayed frame");
        }
        if (displayedGeometry == null) {
            displayedGeometry = geometries(Collections.emptyMap(), false);
        }
        return displayedGeometry.get(indices.get(node));
    }

    public int parentTint(GuiPresentationNode node) {
        int parent = node.definition.parent;
        return parent < 0 ? 0xFFFFFF : geometry(nodes.get(parent)).tint;
    }

    public void pointerPosition(double x, double y) {
        for (GuiPresentationNode node : nodes) {
            interaction(node, "hovered", geometry(node).contains(x, y));
        }
    }

    public void interaction(GuiDocumentDefinition.Element element, String name, boolean value) {
        interaction(node(element), name, value);
    }

    public void interaction(String id, String name, boolean value) {
        GuiPresentationNode node = element(id);
        if (!node.interactionKeys.containsKey(name)) {
            throw new IllegalArgumentException("Unknown interaction state: " + name);
        }
        interaction(node, name, value);
    }

    private void interaction(GuiPresentationNode node, String name, boolean value) {
        String key = interactionKey(node, name);
        Object previous = interactions.put(key, Boolean.valueOf(value));
        previous = previous == null ? Boolean.FALSE : previous;
        if (!Objects.equals(previous, Boolean.valueOf(value))) {
            change(key, previous, Boolean.valueOf(value));
            Object old = previous;
            queue("interaction", () -> {
                LuaTable event = new LuaTable();
                event.set("elementId", lua(node.definition.id));
                event.set("name", name);
                event.set("value", LuaValue.valueOf(value));
                event.set("previous", lua(old));
                return event;
            });
        }
    }

    private void change(String source, Object previous, Object value) {
        if (active && !Objects.equals(previous, value)) {
            synchronized (changes) {
                Change first = changes.get(source);
                changes.put(source, new Change(first == null ? previous : first.previous, value));
            }
            layoutDirty = true;
        }
    }

    private void snapshot() {
        sources = host.snapshot();
        state = sources.state;
        data.clear();
        data.putAll(sources.data);
    }

    private void nativeBaseline(GuiPresentationNode node) {
        node.baseline.putAll(host.baseline(node.definition, sources));
    }

    private void evaluate(GuiPresentationNode node) {
        Set<String> reads = new LinkedHashSet<>();
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            Map<String, Object> output = Collections.emptyMap();
            GuiTransitionPolicy policy = node.definition.transition;
            if (!node.definition.binding.isnil()) {
                node.evaluations++;
                Varargs result = ScriptExecution.invoke(definition.presentation.callbackOwner,
                        "gui.bind." + label(node), node.definition.binding, view(node, scope, reads));
                output = node.definition.properties.read(result.arg1());
                LuaValue dynamicPolicy = result.arg(2);
                if (!dynamicPolicy.isnil()) {
                    policy = node.definition.properties.transition(dynamicPolicy);
                }
            }
            node.pending = candidate(node, output, policy, reads);
            validateView(node, node.pending.effective);
            node.bindingFailed = false;
        } catch (Throwable error) {
            node.pending = null;
            node.bindingFailed = true;
            node.dependencies.addAll(reads);
            node.suspended = !recoverable(error);
            diagnostic(node, error);
        }
    }

    private GuiPresentationNode.Candidate candidate(GuiPresentationNode node, Map<String, Object> output,
            GuiTransitionPolicy policy, Set<String> dependencies) {
        Map<String, Object> derived = new LinkedHashMap<>(node.baseline);
        derived.putAll(output);
        Map<String, GuiPresentationNode.Override> overrides = new LinkedHashMap<>(node.overrides);
        overrides.entrySet().removeIf(entry -> !entry.getValue().held
                && !Objects.equals(node.derived.get(entry.getKey()), derived.get(entry.getKey())));
        Map<String, Object> effective = new LinkedHashMap<>(derived);
        for (Map.Entry<String, GuiPresentationNode.Override> entry : overrides.entrySet()) {
            effective.put(entry.getKey(), entry.getValue().value);
        }
        return new GuiPresentationNode.Candidate(new LinkedHashMap<>(output), derived, effective, overrides,
                new LinkedHashSet<>(dependencies), policy);
    }

    private LuaTable view(GuiPresentationNode node, LuaCallbackScope scope, Set<String> reads) {
        LuaTable view = host.bindingContext(scope);
        view.set("state", observed(scope, reads, "state", state));
        view.set("data", observed(scope, reads, "data", data));
        view.set("interaction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int offset = arguments.arg1() == view ? 1 : 0;
                String name = arguments.arg(offset + 1).checkjstring();
                GuiPresentationNode target = arguments.arg(offset + 2).isnil()
                        ? node
                        : element(arguments.arg(offset + 2).checkjstring());
                requireInteraction(target, name);
                String key = interactionKey(target, name);
                reads.add(key);
                return lua(interactions.getOrDefault(key, Boolean.FALSE));
            }
        });
        return view;
    }

    private LuaTable observed(LuaCallbackScope scope, Set<String> reads, String source, Map<String, Object> values) {
        LuaTable access = new LuaTable();
        access.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                String key = arguments.arg(arguments.arg1() == access ? 2 : 1).checkjstring();
                if (!values.containsKey(key)) {
                    throw new LuaError("Unknown or unsynchronized GUI " + source + " field: " + key);
                }
                reads.add(source + ":" + key);
                return lua(values.get(key));
            }
        });
        return access;
    }

    private void commitBindings(boolean initial) {
        Map<GuiPresentationNode, Map<String, Object>> geometry = new IdentityHashMap<>();
        List<GuiPresentationNode> appearance = new ArrayList<>();
        for (GuiPresentationNode node : nodes) {
            if (node.pending != null) {
                if (affectsGeometry(node, node.pending.effective)) {
                    geometry.put(node, node.pending.effective);
                } else {
                    appearance.add(node);
                }
            }
        }
        double timestamp = clock.getAsDouble();
        if (!geometry.isEmpty() || initial) {
            try {
                validateLayout(geometry);
                for (GuiPresentationNode node : nodes) {
                    if (geometry.containsKey(node)) {
                        commit(node, initial, timestamp);
                    }
                }
                layoutDirty = false;
            } catch (LuaError invalid) {
                for (GuiPresentationNode node : geometry.keySet()) {
                    node.dependencies.addAll(node.pending.dependencies);
                    diagnostic(node, invalid);
                }
                layoutDirty = false;
            }
        }
        for (GuiPresentationNode node : appearance) {
            commit(node, initial, timestamp);
        }
    }

    private void commit(GuiPresentationNode node, boolean initial, double timestamp) {
        GuiPresentationNode.Candidate candidate = node.pending;
        if (!node.bindingValues.keySet().equals(candidate.bindingValues.keySet())
                || !node.overrides.keySet().equals(candidate.overrides.keySet())) {
            node.invalidateValues();
        }
        node.bindingValues.clear();
        node.bindingValues.putAll(candidate.bindingValues);
        node.derived.clear();
        node.derived.putAll(candidate.derived);
        node.overrides.clear();
        node.overrides.putAll(candidate.overrides);
        node.dependencies.clear();
        node.dependencies.addAll(candidate.dependencies);
        node.policy = candidate.policy;
        if (!node.bindingFailed) {
            node.diagnostics.clear();
            node.suppressedDiagnostics = 0;
        }
        Map<String, Object> changed = changed(node.targets, candidate.effective);
        if (changed.containsKey("__conditionVisible")) {
            Object condition = changed.remove("__conditionVisible");
            node.targets.put("__conditionVisible", condition);
            node.displayed.put("__conditionVisible", condition);
            node.invalidateValues();
        }
        if (!changed.isEmpty()) {
            GuiAnimationRequest request = initial ? null : request(node, changed.keySet());
            node.retarget(changed, node.policy, request, timestamp, initial, false, "binding_changed");
        }
        node.pending = null;
    }

    private boolean affectsGeometry(GuiPresentationNode node, Map<String, Object> candidate) {
        for (String property : changed(node.targets, candidate).keySet()) {
            if (node.definition.properties.affectsGeometry(property)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> changed(Map<String, Object> previous, Map<String, Object> next) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : next.entrySet()) {
            if (!Objects.equals(previous.get(entry.getKey()), entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private GuiPresentationLayout layout() {
        return new GuiPresentationLayout(definition, nodes, textWidth, viewport);
    }

    private List<GuiGeometry> geometries(Map<GuiPresentationNode, Map<String, Object>> candidates, boolean target) {
        return layout().geometries(candidates, target);
    }

    private void validateLayout(Map<GuiPresentationNode, Map<String, Object>> candidates) {
        layout().validate(candidates);
        for (GuiPresentationNode node : nodes) {
            validateView(node, candidates.getOrDefault(node, node.targets));
        }
    }

    private void validateView(GuiPresentationNode node, Map<String, Object> values) {
        if (viewValidator != null) {
            try {
                viewValidator.accept(node.definition, Collections.unmodifiableMap(values));
            } catch (IllegalArgumentException invalid) {
                throw new GuiProperties.ValueError(node.definition.path + ": " + invalid.getMessage());
            }
        }
    }

    private GuiAnimationRequest request(GuiPresentationNode node, Set<String> properties) {
        if (nextRequest == Integer.MAX_VALUE) {
            throw new LuaError("GUI animation request ID space exhausted; reopen the screen.");
        }
        return new GuiAnimationRequest(nextRequest++, properties, new GuiAnimationRequest.Listener() {
            @Override
            public void property(GuiAnimationRequest request, String property, boolean completed, String reason) {
                queue("animation_property", () -> {
                    LuaTable event = requestEvent(node, request, completed, reason);
                    event.set("property", property);
                    return event;
                });
            }

            @Override
            public void result(GuiAnimationRequest request, boolean completed, String reason) {
                queue("animation", () -> requestEvent(node, request, completed, reason));
            }
        });
    }

    private LuaTable requestEvent(GuiPresentationNode node, GuiAnimationRequest request, boolean completed,
            String reason) {
        LuaTable event = new LuaTable();
        event.set("elementId", lua(node.definition.id));
        event.set("requestId", request.id);
        LuaTable properties = new LuaTable();
        for (String property : request.properties) {
            properties.set(properties.length() + 1, property);
        }
        event.set("properties", properties);
        event.set("phase", completed ? "complete" : "cancel");
        event.set("status", request.status());
        event.set("reason", lua(reason));
        return event;
    }

    private void queue(String type, Supplier<LuaTable> payload) {
        if (active && !preparing && !eventFailed && definition.presentation.listens(type) && callbacksValid()) {
            events.add(new QueuedEvent(type, payload));
        }
    }

    private void deliver() {
        List<QueuedEvent> batch = new ArrayList<>(events);
        events.clear();
        batch.sort((a, b) -> Integer.compare(priority(a.type), priority(b.type)));
        delivering = true;
        try {
            for (QueuedEvent event : batch) {
                if (!active || eventFailed || !callbacksValid()) {
                    break;
                }
                invokeEvent(event);
            }
        } finally {
            delivering = false;
        }
    }

    private static int priority(String event) {
        return "open".equals(event) || "reload".equals(event)
                ? 0
                : "change".equals(event) ? 1 : event.startsWith("animation") ? 3 : 2;
    }

    /**
     * Routed semantic input shares the existing scoped event/command transaction.
     */
    public LuaValue dispatchUiEvent(String id, String name, Object value) {
        requireActive();
        LuaCallbackScope.requireNoBindingMutation();
        element(id);
        if (commands != null || delivering || preparing || closing) {
            throw new IllegalStateException("Semantic UI input requires its own active event boundary");
        }
        if (eventFailed || !callbacksValid()) {
            return LuaValue.FALSE;
        }
        if (!definition.presentation.listens("control")) {
            return LuaValue.NIL;
        }
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(Collections.singletonMap("value", value));
        return invokeEvent(new QueuedEvent("control", () -> {
            LuaTable event = new LuaTable();
            event.set("elementId", id);
            event.set("event", name);
            event.set("value", lua(detached.snapshot().get("value")));
            return event;
        }));
    }

    /**
     * Component-level callback isolation can abort its staged visual commands
     * without disabling other scopes.
     */
    public void discardUiCommands() {
        LuaCallbackScope.requireUiStateWrite();
        if (commands == null) {
            throw new IllegalStateException("No UI event command transaction is active");
        }
        for (Command command : commands) {
            if (command.request != null) {
                command.request.cancel("callback_error");
            }
        }
        commands.clear();
    }

    private LuaValue invokeEvent(QueuedEvent event) {
        commands = new ArrayList<>();
        try (LuaCallbackScope scope = new LuaCallbackScope(false, !closing)) {
            LuaTable context = host.context(scope);
            context.set("gui", guiAccess(scope));
            LuaTable payload = event.payload.get();
            payload.set("type", event.type);
            LuaValue result = ScriptExecution
                    .invoke(definition.presentation.callbackOwner, "gui.onEvent." + event.type,
                            definition.presentation.onEvent, LuaValue.varargsOf(new LuaValue[]{context, payload}))
                    .arg1();
            commitCommands();
            return result;
        } catch (Throwable error) {
            if (commands != null) {
                for (Command command : commands) {
                    if (command.request != null) {
                        command.request.cancel("callback_error");
                    }
                }
            }
            eventFailed = true;
            report("onEvent", error, LuaScriptErrors.Outcome.CALLBACK_DISABLED);
            return LuaValue.FALSE;
        } finally {
            commands = null;
        }
    }

    /**
     * Direct shared element commands for owned screen handles, with the same
     * validation/transaction path.
     */
    public LuaValue invokeElement(String id, String method, LuaValue first, LuaValue second) {
        requireActive();
        boolean read = "get".equals(method) || "target".equals(method);
        if (!Arrays
                .asList("set", "animate", "override", "clearOverride", "cancel", "finish", "refresh", "get", "target")
                .contains(method)) {
            throw new IllegalArgumentException("Unknown UI element operation: " + method);
        }
        if (!read) {
            LuaCallbackScope.requireUiStateWrite();
        }
        if (preparing || closing || !callbacksValid()) {
            throw new IllegalStateException("UI element revision is unavailable");
        }
        boolean ownCommands = !read && commands == null;
        if (ownCommands) {
            commands = new ArrayList<>();
        }
        try {
            LuaTable handle = nodeHandle(element(id));
            LuaValue result = handle.get(method).call(handle, first, second);
            if (ownCommands) {
                commitCommands();
            }
            return result;
        } catch (RuntimeException | Error failure) {
            if (ownCommands) {
                discardUiCommands();
            }
            throw failure;
        } finally {
            if (ownCommands) {
                commands = null;
            }
        }
    }

    private LuaTable guiAccess(LuaCallbackScope scope) {
        LuaTable gui = new LuaTable();
        gui.set("element", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return nodeHandle(element(arguments.arg(arguments.arg1() == gui ? 2 : 1).checkjstring()));
            }
        });
        return gui;
    }

    private LuaTable nodeHandle(GuiPresentationNode node) {
        LuaTable handle = new LuaTable();
        for (String method : Arrays.asList("set", "animate", "override", "clearOverride", "cancel", "finish", "refresh",
                "get", "target")) {
            handle.set(method, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    requireHandleActive();
                    LuaCallbackScope.requireNoBindingMutation();
                    int offset = args.arg1() == handle ? 1 : 0;
                    String property = args.arg(offset + 1).isnil() ? null : args.arg(offset + 1).tojstring();
                    if ("get".equals(method) || "target".equals(method)) {
                        if (property == null || !node.definition.properties.supports(property)) {
                            throw new LuaError("Unsupported GUI property: " + property);
                        }
                        sample();
                        if ("get".equals(method) && commands != null) {
                            Object staged = stagedValue(node, node.definition.properties.canonical(property));
                            return lua(staged);
                        }
                        return lua("get".equals(method) ? node.get(property) : node.target(property));
                    }
                    requireCommands();
                    if ("refresh".equals(method)) {
                        commands.add(new Command(node, method, Collections.emptyMap(), node.policy, null));
                        return NIL;
                    }
                    Command command;
                    if ("set".equals(method) || "animate".equals(method) || "override".equals(method)) {
                        Map<String, Object> values = node.definition.properties.read(args.arg(offset + 1));
                        if (values.isEmpty()) {
                            throw new LuaError("Presentation commands require at least one property.");
                        }
                        LuaValue options = args.arg(offset + 2);
                        GuiTransitionPolicy policy = options.isnil()
                                ? "animate".equals(method)
                                        ? node.definition.explicitTransition
                                                ? node.definition.transition
                                                : GuiTransitionPolicy.DEFAULT
                                        : GuiTransitionPolicy.IMMEDIATE
                                : node.definition.properties.transition(options);
                        GuiAnimationRequest request = "animate".equals(method)
                                || "override".equals(method) && !options.isnil() && options != FALSE
                                        ? request(node, values.keySet())
                                        : null;
                        command = new Command(node, method, values, policy, request);
                    } else {
                        Set<String> selected = selection(node, args.arg(offset + 1));
                        Map<String, Object> names = new LinkedHashMap<>();
                        for (String name : selected) {
                            names.put(name, null);
                        }
                        command = new Command(node, method, names, node.policy, null);
                    }
                    commands.add(command);
                    return command.request == null ? NIL : requestHandle(node, command.request);
                }
            });
        }
        return handle;
    }

    private Object stagedValue(GuiPresentationNode node, String property) {
        Object value = node.get(property);
        for (Command command : commands) {
            if (command.node == node && command.values.containsKey(property) && "set".equals(command.method)) {
                value = command.values.get(property);
            }
        }
        return value;
    }

    private LuaTable requestHandle(GuiPresentationNode node, GuiAnimationRequest request) {
        LuaTable handle = new LuaTable();
        handle.set("id", request.id);
        handle.set("status", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                requireHandleActive();
                LuaCallbackScope.requireNoBindingMutation();
                return LuaValue.valueOf(request.status());
            }
        });
        for (String method : Arrays.asList("cancel", "finish")) {
            handle.set(method, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    requireCommands();
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (String property : request.properties) {
                        values.put(property, null);
                    }
                    commands.add(new Command(node, method, values, GuiTransitionPolicy.IMMEDIATE, null, request));
                    return NIL;
                }
            });
        }
        return handle;
    }

    private Set<String> selection(GuiPresentationNode node, LuaValue value) {
        if (value.isnil()) {
            return new LinkedHashSet<>(node.definition.properties.baseline().keySet());
        }
        if (!value.istable() || value.checktable().keys().length != value.length()) {
            throw new LuaError("Property selection must be an array of names.");
        }
        Set<String> result = new LinkedHashSet<>();
        for (int i = 1; i <= value.length(); i++) {
            String name = value.get(i).checkjstring();
            if (!node.definition.properties.supports(name)) {
                throw new LuaError("Unsupported GUI property: " + name);
            }
            result.add(node.definition.properties.canonical(name));
        }
        return result;
    }

    private void commitCommands() {
        if (commands.isEmpty()) {
            return;
        }
        sample();
        Map<GuiPresentationNode, Map<String, Object>> candidates = new IdentityHashMap<>();
        Map<GuiPresentationNode, CommandPlan> plans = new IdentityHashMap<>();
        for (Command command : commands) {
            CommandPlan plan = plans.computeIfAbsent(command.node, CommandPlan::new);
            plan.stage(command);
            candidates.put(command.node, plan.targets);
        }
        Set<GuiPresentationNode> geometry = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<GuiPresentationNode, Map<String, Object>> geometryCandidates = new IdentityHashMap<>();
        for (Map.Entry<GuiPresentationNode, Map<String, Object>> entry : candidates.entrySet()) {
            if (definition.retained || plans.get(entry.getKey()).geometryChanged) {
                geometry.add(entry.getKey());
                geometryCandidates.put(entry.getKey(), entry.getValue());
            }
        }
        boolean valid = true;
        try {
            validateLayout(geometryCandidates);
        } catch (LuaError invalid) {
            valid = false;
            report("presentation layout", invalid);
        }
        double now = clock.getAsDouble();
        for (Command command : commands) {
            if (!valid && geometry.contains(command.node)) {
                if (command.request != null) {
                    command.request.cancel("invalid_layout");
                }
            } else {
                applyCommand(command, now);
            }
        }
        for (GuiPresentationNode node : plans.keySet()) {
            if (node.pending != null) {
                node.pending = candidate(node, node.pending.bindingValues, node.pending.policy,
                        node.pending.dependencies);
            }
        }
        layoutDirty = true;
        displayedGeometry = null;
    }

    private void applyCommand(Command command, double now) {
        GuiPresentationNode node = command.node;
        if ("refresh".equals(command.method)) {
            node.suspended = false;
            node.dirty = true;
            return;
        }
        node.invalidateValues();
        Map<String, Object> values = new LinkedHashMap<>();
        if (command.owner != null && "cancel".equals(command.method)) {
            command.owner.cancel("explicit_cancel");
        }
        for (Map.Entry<String, Object> property : command.values.entrySet()) {
            String name = property.getKey();
            if (command.owner != null) {
                GuiPresentationNode.Track track = node.tracks.get(name);
                if (track == null || track.request != command.owner) {
                    continue;
                }
            }
            if ("clearOverride".equals(command.method)) {
                if (node.overrides.remove(name) != null) {
                    values.put(name, node.derived.get(name));
                }
            } else if ("finish".equals(command.method)) {
                GuiPresentationNode.Track track = node.tracks.remove(name);
                if (track != null) {
                    node.displayed.put(name, track.to);
                    track.request.property(name, true, null);
                }
            } else if ("cancel".equals(command.method)) {
                GuiPresentationNode.Track track = node.tracks.remove(name);
                if (track != null) {
                    Object displayed = node.get(name);
                    GuiPresentationNode.Override previous = node.overrides.get(name);
                    node.overrides.put(name,
                            new GuiPresentationNode.Override(displayed, previous != null && previous.held));
                    node.targets.put(name, displayed);
                    track.request.property(name, false, "explicit_cancel");
                }
            } else {
                values.put(name, property.getValue());
                node.overrides.put(name,
                        new GuiPresentationNode.Override(property.getValue(), "override".equals(command.method)));
            }
        }
        if (!values.isEmpty()) {
            GuiAnimationRequest request = command.request == null && !"set".equals(command.method)
                    ? request(node, values.keySet())
                    : command.request;
            boolean immediate = "set".equals(command.method)
                    || "override".equals(command.method) && command.request == null;
            node.retarget(values, command.policy, request, now, immediate, true, "replaced");
        } else if (command.request != null) {
            command.request.commit();
        }
    }

    private void requireCommands() {
        requireHandleActive();
        LuaCallbackScope.requireNoBindingMutation();
        if (closing || commands == null) {
            throw new LuaError("Presentation commands require an active GUI event callback.");
        }
    }

    private boolean callbacksValid() {
        return !ownerInvalidated && ScriptResourceTracker
                .ownerGeneration(definition.presentation.callbackOwner) == definition.presentation.ownerGeneration;
    }

    private void requireHandleActive() {
        requireActive();
        if (!callbacksValid()) {
            throw new LuaError("GUI callback owner was unloaded; presentation handles are stale.");
        }
    }

    private void requireActive() {
        if (!active) {
            throw new LuaError("GUI presentation handle is closed or belongs to a replaced revision.");
        }
    }

    private static void requireInteraction(GuiPresentationNode node, String name) {
        if (!node.definition.properties.descriptors().isEmpty() && node.interactionKeys.containsKey(name)) {
            return;
        }
        if ("hovered".equals(name)) {
            return;
        }
        if (node.definition.element instanceof GuiDocumentDefinition.ControlElement
                && Arrays.asList("pressed", "focused", "disabled", "selected", "invalid").contains(name)) {
            GuiDocumentDefinition.ControlElement control = (GuiDocumentDefinition.ControlElement) node.definition.element;
            if (!"selected".equals(name) || control.type == GuiControlType.TOGGLE
                    || control.type == GuiControlType.CHOICE) {
                return;
            }
        }
        throw new LuaError("Unsupported interaction " + name + " on " + label(node));
    }

    private static String interactionKey(GuiPresentationNode node, String name) {
        return node.interactionKeys.get(name);
    }

    private static double number(Map<String, Object> values, String name, double fallback) {
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static boolean recoverable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof GuiAnimationValueError) {
                return true;
            }
        }
        return false;
    }

    private void diagnostic(GuiPresentationNode node, Throwable error) {
        String detail = error.getMessage() == null ? error.toString() : error.getMessage();
        String category = detail.replaceAll("[-+]?[0-9]+(?:\\.[0-9]+)?", "#");
        if (node.diagnostics.size() < 32 && node.diagnostics.add(category)) {
            report("element " + label(node) + " bind", error,
                    node.suspended
                            ? LuaScriptErrors.Outcome.BINDING_SUSPENDED
                            : LuaScriptErrors.Outcome.APPEARANCE_RETAINED);
        } else if (node.suppressedDiagnostics < Integer.MAX_VALUE) {
            node.suppressedDiagnostics++;
        }
    }

    private void report(String callback, Throwable error) {
        report(callback, error, LuaScriptErrors.Outcome.UNKNOWN);
    }

    private void report(String callback, Throwable error, LuaScriptErrors.Outcome outcome) {
        String message = "GUI " + definition.name + " / " + callback + ": "
                + (error.getMessage() == null ? error.toString() : error.getMessage());
        try (ScriptDiagnostics.Scope diagnostic = ScriptDiagnostics.scope(null,
                "GUI " + definition.name + " / " + callback)) {
            LuaScriptErrors.add(definition.presentation.callbackOwner, message, outcome, error);
        }
    }

    private static String label(GuiPresentationNode node) {
        return node.definition.id == null ? node.definition.path : node.definition.id;
    }

    public static LuaValue lua(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf((Boolean) value);
        }
        if (value instanceof Number) {
            return LuaValue.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof Map || value instanceof List) {
            return luaComposite(value, 0, new int[]{16384});
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    private static LuaValue luaComposite(Object value, int depth, int[] remaining) {
        if (depth > 16 || --remaining[0] < 0) {
            throw new LuaError("GUI data exceeds detached depth/value limits");
        }
        if (!(value instanceof Map || value instanceof List)) {
            return lua(value);
        }
        LuaTable result = new LuaTable();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new LuaError("GUI data record keys must be strings");
                }
                result.set((String) entry.getKey(), luaComposite(entry.getValue(), depth + 1, remaining));
            }
        } else {
            int index = 1;
            for (Object child : (List<?>) value) {
                result.set(index++, luaComposite(child, depth + 1, remaining));
            }
        }
        return result;
    }

    protected GuiPresentationRuntime createReplacement(GuiDocumentDefinition replacement,
            GuiPresentationHost replacementHost) {
        return new GuiPresentationRuntime(replacement, replacementHost, textWidth, clock);
    }

    /**
     * Validates a new visual revision before detaching this screen's active
     * presentation.
     */
    public GuiPresentationRuntime reload(GuiDocumentDefinition replacement) {
        return reload(replacement, candidate -> {
        });
    }

    /** Prepares detached candidate views before the active revision is retired. */
    public GuiPresentationRuntime reload(GuiDocumentDefinition replacement,
            Consumer<GuiPresentationRuntime> prepareViews) {
        return reload(replacement, candidate -> {
        }, prepareViews);
    }

    public GuiPresentationRuntime reload(GuiDocumentDefinition replacement, Consumer<GuiPresentationRuntime> configure,
            Consumer<GuiPresentationRuntime> prepareViews) {
        try (PreparedReload prepared = prepareReload(replacement, configure, prepareViews)) {
            return prepared.commit();
        }
    }

    public PreparedReload prepareReload(GuiDocumentDefinition replacement, Consumer<GuiPresentationRuntime> configure,
            Consumer<GuiPresentationRuntime> prepareViews) {
        requireActive();
        if (replacement.retained != definition.retained) {
            throw new LuaError("Presentation refresh cannot change its layout/host contract");
        }
        GuiPresentationHost replacementHost = host.forDefinition(replacement);
        GuiPresentationRuntime candidate = createReplacement(replacement, replacementHost);
        candidate.preparing = true;
        candidate.stagedReload = true;
        candidate.viewport = viewport;
        try {
            configure.accept(candidate);
            candidate.update();
            if (candidate.nodes.stream().anyMatch(node -> node.bindingFailed || node.pending != null)) {
                throw new LuaError("Presentation refresh has invalid bindings or final geometry.");
            }
            sample();
            candidate.nextRequest = nextRequest;
            for (GuiPresentationNode next : candidate.nodes) {
                GuiPresentationNode previous = next.definition.id == null ? null : ids.get(next.definition.id);
                if (previous == null || !previous.definition.type.equals(next.definition.type)) {
                    continue;
                }
                for (Map.Entry<String, GuiPresentationNode.Override> entry : previous.overrides.entrySet()) {
                    String property = entry.getKey();
                    if (next.definition.properties.supports(property) && (entry.getValue().held
                            || Objects.equals(previous.derived.get(property), next.derived.get(property)))) {
                        next.definition.properties.read(property, lua(entry.getValue().value));
                        next.overrides.put(property, entry.getValue());
                        next.targets.put(property, entry.getValue().value);
                    }
                }
                Map<String, Object> changing = new LinkedHashMap<>();
                for (String property : next.targets.keySet()) {
                    if (property.startsWith("__")) {
                        continue;
                    }
                    if (previous.displayed.containsKey(property)) {
                        next.displayed.put(property, previous.displayed.get(property));
                        if (!Objects.equals(next.targets.get(property), previous.targets.get(property))) {
                            changing.put(property, next.targets.get(property));
                        }
                    }
                }
                Map<String, GuiPresentationNode.Track> retained = new LinkedHashMap<>();
                for (Map.Entry<String, GuiPresentationNode.Track> entry : previous.tracks.entrySet()) {
                    if (next.targets.containsKey(entry.getKey()) && !changing.containsKey(entry.getKey())) {
                        retained.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!retained.isEmpty()) {
                    GuiAnimationRequest request = candidate.request(next, retained.keySet());
                    for (Map.Entry<String, GuiPresentationNode.Track> entry : retained.entrySet()) {
                        GuiPresentationNode.Track track = entry.getValue();
                        next.tracks.put(entry.getKey(), new GuiPresentationNode.Track(track.from, track.to, track.start,
                                track.options, request));
                    }
                    request.commit();
                }
                if (!changing.isEmpty()) {
                    GuiAnimationRequest request = candidate.request(next, changing.keySet());
                    next.retarget(changing, next.policy, request, clock.getAsDouble(), false, true, "binding_changed");
                }
            }
            candidate.validateLayout(Collections.emptyMap());
            candidate.displayedGeometry = null;
            prepareViews.accept(candidate);
            return new PreparedReload(this, candidate);
        } catch (Throwable error) {
            candidate.dispose(false);
            throw error;
        }
    }

    /** Multiple host candidates may validate before any predecessor is retired. */
    public static final class PreparedReload implements AutoCloseable {
        private final GuiPresentationRuntime previous;
        private final GuiPresentationRuntime candidate;
        private boolean finished;

        private PreparedReload(GuiPresentationRuntime previous, GuiPresentationRuntime candidate) {
            this.previous = previous;
            this.candidate = candidate;
        }

        public GuiPresentationRuntime candidate() {
            if (finished) {
                throw new IllegalStateException("Prepared reload is already committed or discarded");
            }
            return candidate;
        }

        public void validate() {
            candidate();
            previous.requireActive();
            candidate.requireUsablePublication();
            if (!candidate.callbacksValid()) {
                throw new LuaError("Prepared reload owner has expired");
            }
        }

        public GuiPresentationRuntime commit() {
            validate();
            previous.dispose(false);
            candidate.stagedReload = false;
            candidate.preparing = false;
            candidate.queue("reload", LuaTable::new);
            finished = true;
            return candidate;
        }

        @Override
        public void close() {
            if (!finished) {
                finished = true;
                candidate.dispose(false);
            }
        }
    }

    @Override
    public void close() {
        dispose(true);
    }

    private void dispose(boolean notifyClose) {
        if (!active) {
            return;
        }
        closing = true;
        events.clear();
        if (notifyClose && !preparing && !eventFailed && definition.presentation.listens("close") && callbacksValid()) {
            invokeEvent(new QueuedEvent("close", LuaTable::new));
        }
        active = false;
        for (AutoCloseable subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception error) {
                report("dispose", error);
            }
        }
        subscriptions.clear();
        events.clear();
        changes.clear();
        for (GuiPresentationNode node : nodes) {
            node.tracks.clear();
            node.dependencies.clear();
            node.pending = null;
        }
    }

    private static final class Change {
        private final Object previous;
        private final Object value;

        private Change(Object previous, Object value) {
            this.previous = previous;
            this.value = value;
        }
    }

    private static final class QueuedEvent {
        private final String type;
        private final Supplier<LuaTable> payload;

        private QueuedEvent(String type, Supplier<LuaTable> payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    private static final class CommandPlan {
        private final GuiPresentationNode node;
        private final Map<String, Object> targets;
        private final Map<String, Object> displayed;
        private final Map<String, GuiAnimationRequest> owners = new LinkedHashMap<>();
        private final Set<String> overridden;
        private boolean geometryChanged;

        private CommandPlan(GuiPresentationNode node) {
            this.node = node;
            targets = new LinkedHashMap<>(node.targets);
            displayed = new LinkedHashMap<>(node.displayed);
            overridden = new LinkedHashSet<>(node.overrides.keySet());
            for (Map.Entry<String, GuiPresentationNode.Track> track : node.tracks.entrySet()) {
                owners.put(track.getKey(), track.getValue().request);
            }
        }

        private void stage(Command command) {
            for (Map.Entry<String, Object> entry : command.values.entrySet()) {
                String property = entry.getKey();
                if (command.owner != null && owners.get(property) != command.owner) {
                    continue;
                }
                Object previous = targets.get(property);
                boolean finished = false;
                if ("finish".equals(command.method)) {
                    if (owners.containsKey(property)) {
                        owners.remove(property);
                        displayed.put(property, previous);
                        finished = true;
                    }
                } else if ("cancel".equals(command.method)) {
                    if (owners.containsKey(property)) {
                        owners.remove(property);
                        targets.put(property, displayed.get(property));
                        overridden.add(property);
                    }
                } else if (!"clearOverride".equals(command.method) || overridden.remove(property)) {
                    Object value = "clearOverride".equals(command.method)
                            ? node.derived.get(property)
                            : entry.getValue();
                    targets.put(property, value);
                    boolean immediate = "set".equals(command.method)
                            || "override".equals(command.method) && command.request == null;
                    GuiTransitionPolicy.Options options = command.policy.forProperty(property);
                    if (immediate || options == null || !node.definition.properties.animatable().contains(property)
                            || options.duration == 0 && options.delay == 0) {
                        displayed.put(property, value);
                        owners.remove(property);
                    } else {
                        owners.put(property, command.request);
                    }
                    if (!"clearOverride".equals(command.method)) {
                        overridden.add(property);
                    }
                }
                if (node.definition.properties.affectsGeometry(property)
                        && (!Objects.equals(previous, targets.get(property)) || finished)) {
                    geometryChanged = true;
                }
            }
        }
    }

    private static final class Command {
        private final GuiPresentationNode node;
        private final String method;
        private final Map<String, Object> values;
        private final GuiTransitionPolicy policy;
        private final GuiAnimationRequest request;
        private final GuiAnimationRequest owner;

        private Command(GuiPresentationNode node, String method, Map<String, Object> values, GuiTransitionPolicy policy,
                GuiAnimationRequest request) {
            this(node, method, values, policy, request, null);
        }

        private Command(GuiPresentationNode node, String method, Map<String, Object> values, GuiTransitionPolicy policy,
                GuiAnimationRequest request, GuiAnimationRequest owner) {
            this.node = node;
            this.method = method;
            this.values = values;
            this.policy = policy;
            this.request = request;
            this.owner = owner;
        }
    }
}
