package betamoon.gui.component;

import betamoon.gui.widget.GuiBuiltInViews;
import java.util.function.Function;
import java.util.Arrays;
import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationRuntime;
import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.luaapi.client.ui.GuiAuthoredNodeParser;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Bounded read-only expansion into the existing presentation/view graph, with
 * stable scoped keyed identities.
 */
public final class GuiComponentCompiler {
    private final GuiComponentRegistry registry;
    private final Set<GuiCapability> capabilities;
    private final GuiComponentPlan previous;
    private final GuiComponentPlan plan;
    private final List<GuiNodeDefinition> nodes = new ArrayList<>();
    private final Set<String> identities = new LinkedHashSet<>();
    private final Set<String> expanding = new LinkedHashSet<>();
    private final GuiComponentLimits.Values declarationBudget = new GuiComponentLimits.Values(4 * 1024 * 1024, 262144);

    private GuiComponentCompiler(GuiComponentRegistry registry, Set<GuiCapability> capabilities,
            GuiComponentPlan previous, GuiComponentPlan plan) {
        this.registry = registry;
        this.capabilities = capabilities;
        this.previous = previous;
        this.plan = plan;
    }

    public static GuiComponentPlan prepare(GuiComponentRegistry registry, String owner, String key,
            GuiAuthoredNode root, GuiPresentationHost host, GuiComponentPlan.Services services,
            Set<GuiCapability> capabilities, GuiComponentPlan previous) {
        GuiComponentPlan plan = new GuiComponentPlan(owner, key, host, services);
        try {
            GuiComponentScope scope = new GuiComponentScope("root", owner, null, null, null, null, plan.state,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            plan.scopes.put(scope.path, scope);
            GuiComponentCompiler compiler = new GuiComponentCompiler(registry, capabilities, previous, plan);
            compiler.expand(root, scope, -1, "root/node", 0);
            plan.finish(compiler.nodes);
            return plan;
        } catch (RuntimeException | Error failure) {
            plan.close();
            throw failure;
        }
    }

    private int expand(GuiAuthoredNode authored, GuiComponentScope scope, int parent, String path, int depth) {
        if (depth >= 32 || nodes.size() >= 4096) {
            throw new IllegalArgumentException(path + ": expansion exceeds 32 levels/4096 nodes");
        }
        scope.requireActive();
        if (authored.slot != null) {
            return slot(authored, scope, parent, path, depth);
        }
        if (authored.component != null) {
            return component(authored, scope, parent, path, depth);
        }
        int index = add(authored, scope, parent, path, authored.type, authored.properties, binding(authored, scope),
                authored.transition, null);
        children(authored.children, scope, index, path, depth + 1);
        return index;
    }

    private void children(List<GuiAuthoredNode> children, GuiComponentScope scope, int parent, String path, int depth) {
        for (int index = 0; index < children.size(); index++) {
            GuiAuthoredNode child = children.get(index);
            String key = child.key != null ? child.key : child.id != null ? child.id : Integer.toString(index + 1);
            expand(child, scope, parent, path + "/" + key, depth);
        }
    }

    private int component(GuiAuthoredNode authored, GuiComponentScope caller, int parent, String path, int depth) {
        GuiComponentDefinition definition = registry.getRequired(authored.component);
        definition.requireActive();
        if (!capabilities.containsAll(definition.requirements)) {
            throw new IllegalArgumentException(
                    path + ": host does not support component capabilities " + definition.requirements);
        }
        if (!expanding.add(definition.key)) {
            throw new IllegalArgumentException(path + ": recursive component expansion: " + definition.key);
        }
        try {
            dependency(definition, new LinkedHashSet<>());
            String stateKey = path + "@" + definition.key;
            Derivation derivation = derive(authored, caller, path, definition);
            int wrapper = add(authored, caller, parent, path, "overlay", derivation.geometry, LuaValue.NIL,
                    derivation.transition, null);
            plan.state.create(stateKey, definition.state, previous == null ? null : previous.state);
            GuiComponentScope scope = new GuiComponentScope(stateKey, definition.owner, definition, caller, caller,
                    stateKey, plan.state, derivation.props, authored.slots, authored.handlers);
            if (plan.scopes.put(stateKey, scope) != null) {
                throw new IllegalArgumentException(path + ": duplicate keyed component identity");
            }
            int root;
            if (!definition.build.isnil()) {
                GuiAuthoredNode built = build(definition, scope, authored.slots);
                root = expand(built, scope, wrapper, stateKey + "/body", depth + 1);
            } else {
                GuiProperties properties = leafProperties(authored, definition, derivation.props);
                root = add(authored, scope, wrapper, stateKey + "/paint", definition.key, properties, LuaValue.NIL,
                        derivation.transition, definition);
            }
            if (root >= 0 && !scope.ids.containsKey("root")) {
                scope.ids.put("root", nodes.get(root).id);
            }
            if (authored.id != null) {
                for (Map.Entry<String, String> export : definition.exports.entrySet()) {
                    String alias = authored.id + "." + export.getKey();
                    if (caller.ids.put(alias, scope.resolve(export.getValue())) != null) {
                        throw new IllegalArgumentException(path + ": duplicate exported reference: " + alias);
                    }
                }
            }
            return wrapper;
        } finally {
            expanding.remove(definition.key);
        }
    }

    private void dependency(GuiComponentDefinition definition, Set<String> seen) {
        if (!seen.add(definition.key)) {
            return;
        }
        definition.requireActive();
        plan.dependencies.put(definition.key, definition);
        for (String key : definition.dependencies) {
            dependency(registry.getRequired(key), seen);
        }
    }

    private GuiAuthoredNode build(GuiComponentDefinition definition, GuiComponentScope scope,
            Map<String, List<GuiAuthoredNode>> slots) {
        GuiAuthoredNode retained = previous == null ? null : previous.builtRoots.get(scope.path);
        if (retained != null && previous.dependencies.get(definition.key) == definition
                && Objects.equals(previous.builtProps.get(scope.path), scope.props)
                && Objects.equals(previous.builtSlots.get(scope.path), slots)) {
            cacheBuild(scope, retained, slots);
            return retained;
        }
        Map<String, Object> tokens = new LinkedHashMap<>();
        for (Map.Entry<String, List<GuiAuthoredNode>> slot : slots.entrySet()) {
            List<Object> placements = new ArrayList<>();
            for (int index = 0; index < slot.getValue().size(); index++) {
                Map<String, Object> token = new LinkedHashMap<>();
                token.put("slot", slot.getKey());
                token.put("index", index + 1);
                placements.add(token);
            }
            tokens.put(slot.getKey(), placements);
        }
        try (LuaCallbackScope callback = LuaCallbackScope.binding()) {
            plan.host.bindingContext(callback);
            LuaValue result = ScriptExecution
                    .invokeScoped(definition.owner, "ui.build." + scope.path, definition.build,
                            LuaValue.varargsOf(
                                    new LuaValue[]{scope.propsView, GuiReadOnlyValues.fromPublished(tokens)}),
                            callback)
                    .arg1();
            definition.requireActive();
            GuiAuthoredNode built = new GuiAuthoredNodeParser(registry).parse(definition.owner, result,
                    scope.path + ".build");
            cacheBuild(scope, built, slots);
            return built;
        }
    }

    private void cacheBuild(GuiComponentScope scope, GuiAuthoredNode built, Map<String, List<GuiAuthoredNode>> slots) {
        plan.builtRoots.put(scope.path, built);
        plan.builtProps.put(scope.path, scope.props);
        plan.builtSlots.put(scope.path, slots);
    }

    private int slot(GuiAuthoredNode authored, GuiComponentScope component, int parent, String path, int depth) {
        List<GuiAuthoredNode> content = component.slots.get(authored.slot);
        if (content == null || component.eventScope == null) {
            throw new IllegalArgumentException(path + ": content placement refers to an undeclared slot");
        }
        if (authored.slotIndex > content.size()) {
            throw new IllegalArgumentException(path + ": slot placement index exceeds content count");
        }
        GuiComponentScope caller = component.eventScope;
        GuiComponentScope placement = new GuiComponentScope(path + "/slot." + authored.slot, caller.owner,
                caller.definition, component, caller.eventScope, caller.stateKey, plan.state, caller.props,
                caller.slots, caller.subscriptions);
        plan.scopes.put(placement.path, placement);
        int first = -1;
        int start = authored.slotIndex == 0 ? 0 : authored.slotIndex - 1;
        int end = authored.slotIndex == 0 ? content.size() : authored.slotIndex;
        for (int index = start; index < end; index++) {
            GuiAuthoredNode child = content.get(index);
            String key = child.key != null ? child.key : child.id != null ? child.id : Integer.toString(index + 1);
            int result = expand(child, placement, parent, placement.path + "/" + key, depth);
            if (first < 0) {
                first = result;
            }
        }
        return first;
    }

    private int add(GuiAuthoredNode authored, GuiComponentScope scope, int parent, String path, String type,
            GuiProperties properties, LuaValue binding, GuiTransitionPolicy transition, GuiComponentDefinition leaf) {
        if (nodes.size() >= 4096) {
            throw new IllegalArgumentException(path + ": expanded node budget exceeded");
        }
        String id = authored.id == null || leaf != null ? path : scope.path + "#" + authored.id;
        if (!identities.add(id)) {
            throw new IllegalArgumentException(path + ": duplicate scoped element ID: " + id);
        }
        if (authored.id != null && leaf == null && scope.ids.put(authored.id, id) != null) {
            throw new IllegalArgumentException(path + ": duplicate local ID: " + authored.id);
        }
        int index = nodes.size();
        declarationBudget.text(id);
        declarationBudget.text(path);
        declarationBudget.add(properties.baseline());
        nodes.add(new GuiNodeDefinition(id, path, type, parent, null, null, properties, binding, transition,
                authored.explicitTransition));
        plan.views.put(id, new GuiComponentPlan.View(scope, authored, leaf));
        return index;
    }

    private LuaValue binding(GuiAuthoredNode authored, GuiComponentScope scope) {
        if (authored.binding.isnil()) {
            return LuaValue.NIL;
        }
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                LuaTable view = arguments.arg1().checktable();
                scope.bindingView(view);
                return ScriptExecution.invoke(scope.owner, "ui.bind." + scope.path, authored.binding, view);
            }
        };
    }

    private GuiProperties leafProperties(GuiAuthoredNode authored, GuiComponentDefinition definition,
            Map<String, Object> props) {
        List<GuiPropertyDescriptor> descriptors = new ArrayList<>(authored.properties.descriptors().values());
        Map<String, Object> defaults = new LinkedHashMap<>(GuiBuiltInViews.properties("overlay").baseline());
        for (Map.Entry<String, GuiComponentSchema.Field> field : definition.props.fields().entrySet()) {
            if (defaults.containsKey(field.getKey())) {
                throw new IllegalArgumentException(
                        definition.key + ": painted component prop collides with a layout property: " + field.getKey());
            }
            descriptors.add(field.getValue().descriptor);
            defaults.put(field.getKey(), props.get(field.getKey()));
        }
        // Wrapper geometry owns placement; the paint leaf fills that rectangle.
        defaults.put("x", 0.0);
        defaults.put("y", 0.0);
        defaults.put("fill", true);
        return new GuiProperties(definition.key, descriptors).withDefaults(defaults);
    }

    private Derivation derive(GuiAuthoredNode authored, GuiComponentScope caller, String path,
            GuiComponentDefinition definition) {
        Map<String, Object> props = definition.props.normalize(authored.props);
        Map<String, Object> hints = new LinkedHashMap<>(authored.properties.baseline());
        if (((Number) hints.get("width")).doubleValue() == 0) {
            hints.put("width", definition.size.width);
        }
        if (((Number) hints.get("height")).doubleValue() == 0) {
            hints.put("height", definition.size.height);
        }
        hints.put("minWidth", Math.max(((Number) hints.get("minWidth")).doubleValue(), definition.size.minimumWidth));
        hints.put("minHeight",
                Math.max(((Number) hints.get("minHeight")).doubleValue(), definition.size.minimumHeight));
        GuiProperties geometry = authored.properties.withDefaults(hints);
        GuiTransitionPolicy transition = authored.transition;
        if (!authored.binding.isnil()) {
            Map<String, Object> reads = new LinkedHashMap<>();
            try (LuaCallbackScope callback = LuaCallbackScope.binding()) {
                LuaTable view = plan.host.bindingContext(callback);
                view.set("props", caller.propsView);
                view.set("state",
                        recordedAccess(callback,
                                caller.stateKey == null ? plan.sources.state : plan.state.values(caller.stateKey),
                                name -> caller.stateKey == null
                                        ? "state:" + name
                                        : "state:" + GuiComponentStateStore.source(caller.stateKey, name),
                                reads));
                view.set("data", recordedAccess(callback, plan.sources.data, name -> "data:" + name, reads));
                view.set("interaction", new VarArgFunction() {
                    public Varargs invoke(Varargs arguments) {
                        callback.requireActive();
                        int offset = arguments.arg1() == view ? 1 : 0;
                        String name = arguments.arg(offset + 1).checkjstring();
                        if (!Arrays.asList("hovered", "pressed", "focused", "selected", "disabled", "invalid")
                                .contains(name)) {
                            throw new LuaError("Unknown UI interaction: " + name);
                        }
                        String id = authored.id == null ? path : caller.path + "#" + authored.id;
                        if (!arguments.arg(offset + 2).isnil()) {
                            String local = arguments.arg(offset + 2).checkjstring();
                            GuiComponentScope earlier = previous == null ? null : previous.scopes.get(caller.path);
                            id = caller.ids.containsKey(local)
                                    ? caller.resolve(local)
                                    : earlier == null ? caller.path + "#" + local : earlier.resolve(local);
                        }
                        boolean value = previous != null && previous.interaction(id, name);
                        reads.put("interaction:" + id + ":" + name, value);
                        return LuaValue.valueOf(value);
                    }
                });
                Varargs output = ScriptExecution.invoke(caller.owner, "ui.props." + path, authored.binding, view);
                LuaValue result = output.arg1();
                if (!result.istable()) {
                    throw new LuaError(path + ": component binding requires a property record");
                }
                Map<String, Object> values = new LinkedHashMap<>(geometry.baseline());
                for (LuaValue key : result.checktable().keys()) {
                    if (key.type() != LuaValue.TSTRING) {
                        throw new LuaError(path + ": binding keys must be strings");
                    }
                    String name = key.tojstring();
                    if ("props".equals(name)) {
                        props = definition.props.patch(props, GuiAuthoredNodeParser.readDeclaredProps(definition.props,
                                result.get(key), path + ".props"));
                    } else {
                        values.put(name, geometry.read(name, result.get(key)));
                    }
                }
                geometry = geometry.withDefaults(values);
                if (!output.arg(2).isnil()) {
                    transition = GuiAuthoredNodeParser.transition(geometry, definition, output.arg(2));
                }
            }
            plan.derivationReads.put(path, Collections.unmodifiableMap(reads));
        }
        return new Derivation(props, geometry, transition);
    }

    private static LuaTable recordedAccess(LuaCallbackScope scope, Map<String, Object> values,
            Function<String, String> key, Map<String, Object> reads) {
        LuaTable access = new LuaTable();
        access.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                String name = arguments.arg(arguments.arg1() == access ? 2 : 1).checkjstring();
                if (!values.containsKey(name)) {
                    throw new LuaError("Unknown published UI field: " + name);
                }
                reads.put(key.apply(name), values.get(name));
                return GuiPresentationRuntime.lua(values.get(name));
            }
        });
        return access;
    }

    private static final class Derivation {
        final Map<String, Object> props;
        final GuiProperties geometry;
        final GuiTransitionPolicy transition;
        Derivation(Map<String, Object> props, GuiProperties geometry, GuiTransitionPolicy transition) {
            this.props = props;
            this.geometry = geometry;
            this.transition = transition;
        }
    }
}
