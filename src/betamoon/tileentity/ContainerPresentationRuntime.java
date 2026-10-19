package betamoon.tileentity;

import betamoon.BetaMoonCommon;
import betamoon.data.DataField;
import betamoon.gui.animation.GuiAnimationRequest;
import betamoon.gui.animation.GuiAnimationValueError;
import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.presentation.GuiGeometry;
import betamoon.gui.presentation.GuiGeometry.Bounds;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptErrors;
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
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Coordinates source batches, pure derivation, atomic geometry, events and per-screen ownership. */
public final class ContainerPresentationRuntime implements AutoCloseable {
    private final ContainerGuiDefinition definition;
    private final LuaTileEntity tile;
    private final ContainerControlRuntime controls;
    private final ToIntFunction<String> textWidth;
    private final DoubleSupplier clock;
    private final List<ContainerGuiNode> nodes = new ArrayList<>();
    private final Map<ContainerGuiNode, Integer> indices = new IdentityHashMap<>();
    private List<GuiGeometry> displayedGeometry;
    private final Map<String, ContainerGuiNode> ids = new LinkedHashMap<>();
    private final Map<ContainerGuiDefinition.Element, ContainerGuiNode> elements = new IdentityHashMap<>();
    private final Map<String, Change> changes = new LinkedHashMap<>();
    private final Map<String, Object> interactions = new LinkedHashMap<>();
    private final List<QueuedEvent> events = new ArrayList<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private final Map<String, Object> nativeData = new LinkedHashMap<>();
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
    private boolean layoutDirty;
    private int nextRequest = 1;
    private Bounds viewport = new Bounds(-1e9, -1e9, 2e9, 2e9);

    public ContainerPresentationRuntime(ContainerGuiDefinition definition, LuaTileEntity tile,
            ContainerControlRuntime controls, ToIntFunction<String> textWidth) {
        this(definition, tile, controls, textWidth, () -> System.nanoTime() / 1e9);
    }

    public ContainerPresentationRuntime(ContainerGuiDefinition definition, LuaTileEntity tile,
            ContainerControlRuntime controls, ToIntFunction<String> textWidth, DoubleSupplier clock) {
        this.definition = definition;
        this.tile = tile;
        this.controls = controls;
        this.textWidth = textWidth;
        this.clock = clock;
        if (definition.presentation == null) {
            throw new IllegalArgumentException("A compiled presentation definition is required.");
        }
        for (ContainerGuiNodeDefinition nodeDefinition : definition.presentation.nodes) {
            ContainerGuiNode node = new ContainerGuiNode(nodeDefinition);
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
        for (ContainerGuiNode node : nodes) {
            nativeBaseline(node);
            node.derived.putAll(node.baseline);
            node.targets.putAll(node.baseline);
            node.displayed.putAll(node.baseline);
        }
        subscriptions.add(controls.session().observe((key, previous, value) -> change("state:" + key, previous, value)));
        subscriptions.add(tile.observeGuiData((key, previous, value) -> {
            TileEntityDefinition.Field field = definition.container.tileEntity.fields.get(key);
            if (field != null) {
                change((field.sync ? "data:" : "native:") + key, previous, value);
            }
        }));
        if (definition.presentation.listens("control")) {
            subscriptions.add(controls.observeControls(notification -> queue("control", () -> {
            LuaTable event = new LuaTable();
            event.set("control", notification.control);
            event.set("phase", notification.phase);
            event.set("accepted", LuaValue.valueOf(notification.accepted));
            event.set("proposed", lua(notification.proposed));
            event.set("value", lua(notification.value));
            event.set("previous", lua(notification.previous));
            event.set("input", notification.input);
            return event;
            })));
        }
    }

    public ContainerGuiNode node(ContainerGuiDefinition.Element element) {
        return elements.get(element);
    }

    public ContainerGuiNode element(String id) {
        requireActive();
        ContainerGuiNode node = ids.get(id);
        if (node == null) {
            throw new LuaError("Unknown GUI element ID: " + id);
        }
        return node;
    }

    /** Internal diagnostics only: immutable snapshots do not expose mutable render state to Lua. */
    public Map<String, Object> debugSnapshot() {
        sample();
        Map<String, Object> result = new LinkedHashMap<>();
        for (ContainerGuiNode node : nodes) {
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
            for (Map.Entry<String, ContainerGuiNode.Override> entry : node.overrides.entrySet()) {
                Map<String, Object> override = new LinkedHashMap<>();
                override.put("value", entry.getValue().value);
                override.put("held", entry.getValue().held);
                overrides.put(entry.getKey(), Collections.unmodifiableMap(override));
            }
            values.put("overrides", Collections.unmodifiableMap(overrides));
            Map<String, Object> tracks = new LinkedHashMap<>();
            for (Map.Entry<String, ContainerGuiNode.Track> entry : node.tracks.entrySet()) {
                ContainerGuiNode.Track track = entry.getValue();
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

    /** Called on the UI update/input boundary, including while world simulation is paused. */
    public void update() {
        requireActive();
        if (delivering) {
            return;
        }
        if (!callbacksValid()) {
            ownerInvalidated = true;
            events.clear();
            for (ContainerGuiNode node : nodes) {
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
        for (ContainerGuiNode node : nodes) {
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

    /** Java-only sampling; it cannot evaluate a binding or invoke an event callback. */
    public void sample() {
        double now = clock.getAsDouble();
        for (ContainerGuiNode node : nodes) {
            if (!node.tracks.isEmpty()) {
                displayedGeometry = null;
                node.sample(now);
            }
        }
    }

    public GuiGeometry geometry(ContainerGuiDefinition.Element element) {
        return geometry(node(element));
    }

    public GuiGeometry geometry(ContainerGuiNode node) {
        if (displayedGeometry == null) {
            displayedGeometry = geometries(Collections.emptyMap(), false);
        }
        return displayedGeometry.get(indices.get(node));
    }

    public int parentTint(ContainerGuiNode node) {
        int parent = node.definition.parent;
        return parent < 0 ? 0xFFFFFF : geometry(nodes.get(parent)).tint;
    }

    public void pointerPosition(double x, double y) {
        for (ContainerGuiNode node : nodes) {
            interaction(node, "hovered", geometry(node).contains(x, y));
        }
    }

    public void interaction(ContainerGuiDefinition.Element element, String name, boolean value) {
        interaction(node(element), name, value);
    }

    private void interaction(ContainerGuiNode node, String name, boolean value) {
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
        state = controls.session().snapshot();
        data.clear();
        nativeData.clear();
        for (TileEntityDefinition.Field field : definition.container.tileEntity.fields.values()) {
            nativeData.put(field.name, tile.getDataValue(field.name));
            if (field.sync && (field.schema.type == DataField.Type.INTEGER || field.schema.type == DataField.Type.BOOLEAN)) {
                data.put(field.name, nativeData.get(field.name));
            }
        }
    }

    private void nativeBaseline(ContainerGuiNode node) {
        ContainerGuiNativeBaseline.resolve(node, definition.container, nativeData::get, state::get);
    }

    private void evaluate(ContainerGuiNode node) {
        Set<String> reads = new LinkedHashSet<>();
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            Map<String, Object> output = Collections.emptyMap();
            GuiTransitionPolicy policy = node.definition.transition;
            if (!node.definition.binding.isnil()) {
                node.evaluations++;
                Varargs result = ScriptExecution.invoke(definition.presentation.callbackOwner, "gui.bind." + label(node),
                        node.definition.binding, view(node, scope, reads));
                output = node.definition.properties.read(result.arg1());
                LuaValue dynamicPolicy = result.arg(2);
                if (!dynamicPolicy.isnil()) {
                    policy = node.definition.properties.transition(dynamicPolicy);
                }
            }
            node.pending = candidate(node, output, policy, reads);
            node.bindingFailed = false;
        } catch (Throwable error) {
            node.pending = null;
            node.bindingFailed = true;
            node.dependencies.addAll(reads);
            node.suspended = !recoverable(error);
            diagnostic(node, error);
        }
    }

    private ContainerGuiNode.Candidate candidate(ContainerGuiNode node, Map<String, Object> output,
            GuiTransitionPolicy policy, Set<String> dependencies) {
        Map<String, Object> derived = new LinkedHashMap<>(node.baseline);
        derived.putAll(output);
        Map<String, ContainerGuiNode.Override> overrides = new LinkedHashMap<>(node.overrides);
        overrides.entrySet().removeIf(entry -> !entry.getValue().held
                && !Objects.equals(node.derived.get(entry.getKey()), derived.get(entry.getKey())));
        Map<String, Object> effective = new LinkedHashMap<>(derived);
        for (Map.Entry<String, ContainerGuiNode.Override> entry : overrides.entrySet()) {
            effective.put(entry.getKey(), entry.getValue().value);
        }
        return new ContainerGuiNode.Candidate(new LinkedHashMap<>(output), derived, effective, overrides,
                new LinkedHashSet<>(dependencies), policy);
    }

    private LuaTable view(ContainerGuiNode node, LuaCallbackScope scope, Set<String> reads) {
        LuaTable view = new LuaTable();
        view.set("state", observed(scope, reads, "state", state));
        view.set("data", observed(scope, reads, "data", data));
        view.set("interaction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int offset = arguments.arg1() == view ? 1 : 0;
                String name = arguments.arg(offset + 1).checkjstring();
                ContainerGuiNode target = arguments.arg(offset + 2).isnil() ? node
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
        Map<ContainerGuiNode, Map<String, Object>> geometry = new IdentityHashMap<>();
        List<ContainerGuiNode> appearance = new ArrayList<>();
        for (ContainerGuiNode node : nodes) {
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
                for (ContainerGuiNode node : nodes) {
                    if (geometry.containsKey(node)) {
                        commit(node, initial, timestamp);
                    }
                }
                layoutDirty = false;
            } catch (LuaError invalid) {
                for (ContainerGuiNode node : geometry.keySet()) {
                    node.dependencies.addAll(node.pending.dependencies);
                    diagnostic(node, invalid);
                }
                layoutDirty = false;
            }
        }
        for (ContainerGuiNode node : appearance) {
            commit(node, initial, timestamp);
        }
    }

    private void commit(ContainerGuiNode node, boolean initial, double timestamp) {
        ContainerGuiNode.Candidate candidate = node.pending;
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
        }
        if (!changed.isEmpty()) {
            GuiAnimationRequest request = initial ? null : request(node, changed.keySet());
            node.retarget(changed, node.policy, request, timestamp, initial, false, "binding_changed");
        }
        node.pending = null;
    }

    private boolean affectsGeometry(ContainerGuiNode node, Map<String, Object> candidate) {
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

    private ContainerGuiLayout layout() {
        return new ContainerGuiLayout(definition, nodes, textWidth, viewport);
    }

    private List<GuiGeometry> geometries(Map<ContainerGuiNode, Map<String, Object>> candidates, boolean target) {
        return layout().geometries(candidates, target);
    }

    private void validateLayout(Map<ContainerGuiNode, Map<String, Object>> candidates) {
        layout().validate(candidates);
    }

    private GuiAnimationRequest request(ContainerGuiNode node, Set<String> properties) {
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

    private LuaTable requestEvent(ContainerGuiNode node, GuiAnimationRequest request, boolean completed, String reason) {
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
        return "open".equals(event) || "reload".equals(event) ? 0 : "change".equals(event) ? 1
                : event.startsWith("animation") ? 3 : 2;
    }

    private void invokeEvent(QueuedEvent event) {
        commands = new ArrayList<>();
        try (LuaCallbackScope scope = new LuaCallbackScope(false, !closing)) {
            LuaTable context = controls.guiContext(scope);
            context.set("gui", guiAccess(scope));
            LuaTable payload = event.payload.get();
            payload.set("type", event.type);
            ScriptExecution.invoke(definition.presentation.callbackOwner, "gui.onEvent." + event.type, definition.presentation.onEvent,
                    LuaValue.varargsOf(new LuaValue[]{context, payload}));
            commitCommands();
        } catch (Throwable error) {
            if (commands != null) {
                for (Command command : commands) {
                    if (command.request != null) {
                        command.request.cancel("callback_error");
                    }
                }
            }
            eventFailed = true;
            report("onEvent", error);
        } finally {
            commands = null;
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

    private LuaTable nodeHandle(ContainerGuiNode node) {
        LuaTable handle = new LuaTable();
        for (String method : Arrays.asList("set", "animate", "override", "clearOverride", "cancel", "finish",
                "refresh", "get", "target")) {
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
                                ? "animate".equals(method) ? node.definition.explicitTransition
                                        ? node.definition.transition : GuiTransitionPolicy.DEFAULT
                                : GuiTransitionPolicy.IMMEDIATE
                                : node.definition.properties.transition(options);
                        GuiAnimationRequest request = "animate".equals(method)
                                || "override".equals(method) && !options.isnil() && options != FALSE
                                ? request(node, values.keySet()) : null;
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

    private Object stagedValue(ContainerGuiNode node, String property) {
        Object value = node.get(property);
        for (Command command : commands) {
            if (command.node == node && command.values.containsKey(property) && "set".equals(command.method)) {
                value = command.values.get(property);
            }
        }
        return value;
    }

    private LuaTable requestHandle(ContainerGuiNode node, GuiAnimationRequest request) {
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

    private Set<String> selection(ContainerGuiNode node, LuaValue value) {
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
        Map<ContainerGuiNode, Map<String, Object>> candidates = new IdentityHashMap<>();
        Map<ContainerGuiNode, CommandPlan> plans = new IdentityHashMap<>();
        for (Command command : commands) {
            CommandPlan plan = plans.computeIfAbsent(command.node, CommandPlan::new);
            plan.stage(command);
            candidates.put(command.node, plan.targets);
        }
        Set<ContainerGuiNode> geometry = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<ContainerGuiNode, Map<String, Object>> geometryCandidates = new IdentityHashMap<>();
        for (Map.Entry<ContainerGuiNode, Map<String, Object>> entry : candidates.entrySet()) {
            if (plans.get(entry.getKey()).geometryChanged) {
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
        for (ContainerGuiNode node : plans.keySet()) {
            if (node.pending != null) {
                node.pending = candidate(node, node.pending.bindingValues, node.pending.policy,
                        node.pending.dependencies);
            }
        }
        layoutDirty = true;
        displayedGeometry = null;
    }

    private void applyCommand(Command command, double now) {
        ContainerGuiNode node = command.node;
        if ("refresh".equals(command.method)) {
            node.suspended = false;
            node.dirty = true;
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        if (command.owner != null && "cancel".equals(command.method)) {
            command.owner.cancel("explicit_cancel");
        }
        for (Map.Entry<String, Object> property : command.values.entrySet()) {
            String name = property.getKey();
            if (command.owner != null) {
                ContainerGuiNode.Track track = node.tracks.get(name);
                if (track == null || track.request != command.owner) {
                    continue;
                }
            }
            if ("clearOverride".equals(command.method)) {
                if (node.overrides.remove(name) != null) {
                    values.put(name, node.derived.get(name));
                }
            } else if ("finish".equals(command.method)) {
                ContainerGuiNode.Track track = node.tracks.remove(name);
                if (track != null) {
                    node.displayed.put(name, track.to);
                    track.request.property(name, true, null);
                }
            } else if ("cancel".equals(command.method)) {
                ContainerGuiNode.Track track = node.tracks.remove(name);
                if (track != null) {
                    Object displayed = node.get(name);
                    ContainerGuiNode.Override previous = node.overrides.get(name);
                    node.overrides.put(name, new ContainerGuiNode.Override(displayed, previous != null && previous.held));
                    node.targets.put(name, displayed);
                    track.request.property(name, false, "explicit_cancel");
                }
            } else {
                values.put(name, property.getValue());
                node.overrides.put(name, new ContainerGuiNode.Override(property.getValue(),
                        "override".equals(command.method)));
            }
        }
        if (!values.isEmpty()) {
            GuiAnimationRequest request = command.request == null && !"set".equals(command.method)
                    ? request(node, values.keySet()) : command.request;
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
        return !ownerInvalidated && ScriptResourceTracker.ownerGeneration(definition.presentation.callbackOwner)
                == definition.presentation.ownerGeneration;
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

    private static void requireInteraction(ContainerGuiNode node, String name) {
        if ("hovered".equals(name)) {
            return;
        }
        if (node.definition.element instanceof ContainerGuiDefinition.ControlElement
                && Arrays.asList("pressed", "focused", "disabled", "selected", "invalid").contains(name)) {
            ContainerGuiDefinition.ControlElement control = (ContainerGuiDefinition.ControlElement) node.definition.element;
            if (!"selected".equals(name) || control.type == ContainerControlDefinition.Type.TOGGLE
                    || control.type == ContainerControlDefinition.Type.CHOICE) {
                return;
            }
        }
        throw new LuaError("Unsupported interaction " + name + " on " + label(node));
    }

    private static String interactionKey(ContainerGuiNode node, String name) {
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

    private void diagnostic(ContainerGuiNode node, Throwable error) {
        String detail = error.getMessage() == null ? error.toString() : error.getMessage();
        String category = detail.replaceAll("[-+]?[0-9]+(?:\\.[0-9]+)?", "#");
        if (node.diagnostics.size() < 32 && node.diagnostics.add(category)) {
            report("element " + label(node) + " bind", error);
        } else if (node.suppressedDiagnostics < Integer.MAX_VALUE) {
            node.suppressedDiagnostics++;
        }
    }

    private void report(String callback, Throwable error) {
        String message = "GUI " + definition.name + " / " + callback + ": "
                + (error.getMessage() == null ? error.toString() : error.getMessage());
        LuaScriptErrors.add(definition.presentation.callbackOwner, message);
        BetaMoonCommon.LOGGER.warning(message);
    }

    private static String label(ContainerGuiNode node) {
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
        return LuaValue.valueOf(String.valueOf(value));
    }

    /** Validates a new visual revision before detaching this screen's active presentation. */
    public ContainerPresentationRuntime reload(ContainerGuiDefinition replacement) {
        requireActive();
        if (replacement.container != definition.container) {
            throw new LuaError("Presentation refresh cannot replace the active container schema.");
        }
        ContainerPresentationRuntime candidate = new ContainerPresentationRuntime(replacement, tile, controls,
                textWidth, clock);
        candidate.preparing = true;
        candidate.viewport = viewport;
        try {
            candidate.update();
            if (candidate.nodes.stream().anyMatch(node -> node.bindingFailed || node.pending != null)) {
                throw new LuaError("Presentation refresh has invalid bindings or final geometry.");
            }
            sample();
            candidate.nextRequest = nextRequest;
            for (ContainerGuiNode next : candidate.nodes) {
                ContainerGuiNode previous = next.definition.id == null ? null : ids.get(next.definition.id);
                if (previous == null || !previous.definition.type.equals(next.definition.type)) {
                    continue;
                }
                for (Map.Entry<String, ContainerGuiNode.Override> entry : previous.overrides.entrySet()) {
                    String property = entry.getKey();
                    if (next.definition.properties.supports(property)
                            && (entry.getValue().held
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
                Map<String, ContainerGuiNode.Track> retained = new LinkedHashMap<>();
                for (Map.Entry<String, ContainerGuiNode.Track> entry : previous.tracks.entrySet()) {
                    if (next.targets.containsKey(entry.getKey()) && !changing.containsKey(entry.getKey())) {
                        retained.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!retained.isEmpty()) {
                    GuiAnimationRequest request = candidate.request(next, retained.keySet());
                    for (Map.Entry<String, ContainerGuiNode.Track> entry : retained.entrySet()) {
                        ContainerGuiNode.Track track = entry.getValue();
                        next.tracks.put(entry.getKey(), new ContainerGuiNode.Track(track.from, track.to, track.start,
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
            dispose(false);
            candidate.preparing = false;
            candidate.queue("reload", LuaTable::new);
            return candidate;
        } catch (Throwable error) {
            candidate.dispose(false);
            throw error;
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
        if (notifyClose && !eventFailed && definition.presentation.listens("close") && callbacksValid()) {
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
        for (ContainerGuiNode node : nodes) {
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
        private final ContainerGuiNode node;
        private final Map<String, Object> targets;
        private final Map<String, Object> displayed;
        private final Map<String, GuiAnimationRequest> owners = new LinkedHashMap<>();
        private final Set<String> overridden;
        private boolean geometryChanged;

        private CommandPlan(ContainerGuiNode node) {
            this.node = node;
            targets = new LinkedHashMap<>(node.targets);
            displayed = new LinkedHashMap<>(node.displayed);
            overridden = new LinkedHashSet<>(node.overrides.keySet());
            for (Map.Entry<String, ContainerGuiNode.Track> track : node.tracks.entrySet()) {
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
                    Object value = "clearOverride".equals(command.method) ? node.derived.get(property) : entry.getValue();
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
        private final ContainerGuiNode node;
        private final String method;
        private final Map<String, Object> values;
        private final GuiTransitionPolicy policy;
        private final GuiAnimationRequest request;
        private final GuiAnimationRequest owner;

        private Command(ContainerGuiNode node, String method, Map<String, Object> values,
                GuiTransitionPolicy policy, GuiAnimationRequest request) {
            this(node, method, values, policy, request, null);
        }

        private Command(ContainerGuiNode node, String method, Map<String, Object> values,
                GuiTransitionPolicy policy, GuiAnimationRequest request, GuiAnimationRequest owner) {
            this.node = node;
            this.method = method;
            this.values = values;
            this.policy = policy;
            this.request = request;
            this.owner = owner;
        }
    }
}
