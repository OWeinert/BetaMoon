package betamoon.gui.presentation;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.gui.presentation.GuiPropertyDescriptor.Stage;
import betamoon.luaapi.client.ui.GuiPropertyValues;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real Lua derivation feeds Java views and remains absent from animation
 * replay.
 */
public final class GuiRetainedTreeTest {
    public static void main(String[] args) {
        typedValues();
        sharedTreeAndSampling();
        atomicRevision();
        preparedActivation();
        stagedRevisions();
        System.out.println("Retained tree passed: typed Java/Lua values, shared views, cached publication, "
                + "Java-only animation, candidate failure isolation and revision cleanup.");
    }

    private static void typedValues() {
        GuiPropertyDescriptor width = GuiPropertyDescriptor.number("width", 600, 0, 16384, Stage.MEASURE);
        require(width.normalize(1200).equals(1200.0), "Menu dimensions inherited legacy panel limits");
        expect(IllegalArgumentException.class, () -> width.normalize(Double.NaN));
        expect(LuaError.class, () -> GuiPropertyValues.read(width, LuaValue.valueOf("600")));
        GuiPropertyDescriptor color = GuiPropertyDescriptor.value("fill", Kind.COLOR, 0xFF000000, Stage.PAINT);
        require(GuiPropertyValues.read(color, LuaValue.valueOf("#55AAFF")).equals(0xFF55AAFF), "Hex color failed");
        require(color.interpolate(0x00000000, 0xFFFFFFFF, 0.5).equals(0x80808080), "ARGB interpolation lost alpha");
        GuiProperties properties = new GuiProperties("panel", Arrays.asList(width, color));
        require(properties.affectsGeometry("width") && !properties.affectsGeometry("fill"),
                "Property stage effects were ignored");
    }

    private static void sharedTreeAndSampling() {
        Model model = new Model();
        Globals lua = JsePlatform.standardGlobals();
        lua.load("calls=0; function bind(v) calls=calls+1; local data=v.data:get('records'); "
                + "assert(data.label=='detached'); data.label='local'; assert(v.data:get('records').label=='detached'); "
                + "return {x=v.state:get('count')} end").call();
        GuiDocumentDefinition definition = definition(lua.get("bind"), 400, "overlay");
        try (GuiPresentationTree tree = tree(definition, model)) {
            View child = (View) tree.element("child");
            require(child.getParent() == tree.root(), "Presentation retained another mutable parent graph");
            require(child.offset == 20 && lua.get("calls").checkint() == 1, "Initial derivation did not publish");
            int publications = child.publications;
            for (int i = 0; i < 20; i++) {
                tree.sample();
            }
            require(child.publications == publications && lua.get("calls").checkint() == 1,
                    "Idle samples re-published values or invoked Lua");
            model.set(60);
            tree.update();
            model.time = 0.1;
            tree.sample();
            require(child.offset > 20 && child.offset < 60, "Java animation did not reach the view");
            require(lua.get("calls").checkint() == 2, "Animation sampling invoked a binding");
            expect(IllegalStateException.class, () -> tree.runtime().geometry(tree.runtime().element("child")));
        }
        require(model.listeners.isEmpty(), "Closing retained views leaked source subscriptions");
    }

    private static void atomicRevision() {
        Model model = new Model();
        GuiPresentationTree original = tree(definition(LuaValue.NIL, 400, "overlay"), model);
        GuiElement root = original.root();
        expect(IllegalArgumentException.class, () -> original.reload(definition(LuaValue.NIL, 400, "unsupported")));
        require(original.root() == root && model.listeners.size() == 1, "Failed factory retired active views");
        expect(LuaError.class, () -> original.reload(definition(LuaValue.NIL, 777, "overlay")));
        require(original.root() == root && model.listeners.size() == 1,
                "Failed candidate publication retired active views");
        GuiPresentationTree replacement = original.reload(definition(LuaValue.NIL, 800, "overlay"));
        expect(IllegalStateException.class, original::root);
        require(replacement.root() != root && ((View) root).closed == 1 && model.listeners.size() == 1,
                "Successful revision did not retire exactly one old tree");
        replacement.close();
        replacement.close();
        require(model.listeners.isEmpty(), "Revision close retained subscriptions");

        Globals lua = JsePlatform.standardGlobals();
        LuaValue invalid = lua.load("return function(v) return {width=-1} end").call();
        expect(LuaError.class, () -> tree(definition(invalid, 400, "overlay"), model));
        require(model.listeners.isEmpty(), "Rejected initial binding retained a runtime");
    }

    private static GuiPresentationTree tree(GuiDocumentDefinition definition, Model model) {
        return new GuiPresentationTree(definition, new Host(model), node -> {
            if ("unsupported".equals(node.type)) {
                throw new IllegalArgumentException(node.path + ": unsupported view");
            }
            return new View();
        }, Collections.emptySet(), text -> text.length() * 6, () -> model.time);
    }

    private static void stagedRevisions() {
        Model model = new Model();
        GuiPresentationTree original = tree(definition(LuaValue.NIL, 400, "overlay"), model);
        GuiElement root = original.root();
        GuiPresentationTree rolledBack;
        try (GuiPresentationTree.PreparedReload first = original.prepareReload(definition(LuaValue.NIL, 800, "overlay"),
                candidate -> {
                })) {
            rolledBack = first.candidate();
            require(original.root() == root && model.listeners.size() == 2,
                    "Preparing one row retired its predecessor before the complete batch validated");
            expect(IllegalStateException.class, rolledBack::activate);
            expect(LuaError.class, () -> original.prepareReload(definition(LuaValue.NIL, 777, "overlay"), candidate -> {
            }));
            require(original.root() == root && model.listeners.size() == 2,
                    "A later failed candidate invalidated the old batch or leaked observers");
        }
        require(original.root() == root && model.listeners.size() == 1, "Batch rollback closed the predecessor");
        expect(IllegalStateException.class, rolledBack::root);
        GuiPresentationTree replacement;
        try (GuiPresentationTree.PreparedReload first = original.prepareReload(definition(LuaValue.NIL, 800, "overlay"),
                candidate -> {
                });
                GuiPresentationTree.PreparedReload competing = original
                        .prepareReload(definition(LuaValue.NIL, 900, "overlay"), candidate -> {
                        })) {
            replacement = first.commit();
            expect(IllegalStateException.class, original::root);
            expect(IllegalStateException.class, first::commit);
            expect(IllegalStateException.class, competing::commit);
        }
        require(model.listeners.size() == 1 && ((View) replacement.root()).closed == 0,
                "A stale competing commit affected the chosen successor or retained subscriptions");
        replacement.close();
        require(model.listeners.isEmpty(), "Committed batch retained a source observer after close");
    }

    private static void preparedActivation() {
        Model model = new Model();
        Globals lua = JsePlatform.standardGlobals();
        LuaValue callback = lua.load("events=0; return function(ctx,event) events=events+1 end").call();
        GuiDocumentDefinition base = definition(LuaValue.NIL, 400, "overlay");
        GuiPresentationDefinition presentation = new GuiPresentationDefinition(base.presentation.nodes, callback,
                new java.util.LinkedHashSet<>(Arrays.asList("open", "close")), base.owner);
        GuiDocumentDefinition definition = new GuiDocumentDefinition(base.name, base.owner, base.width, base.height,
                null, null, Collections.emptyList(), presentation, true);
        GuiPresentationTree prepared = tree(definition, model);
        prepared.sample();
        require(lua.get("events").checkint() == 0, "Preparation executed an activation callback");
        prepared.close();
        require(lua.get("events").checkint() == 0, "An unactivated candidate executed close");
        try (GuiPresentationTree active = tree(definition, model)) {
            active.activate();
            active.update();
            require(lua.get("events").checkint() == 1, "Activation did not deliver open at an update boundary");
        }
        require(lua.get("events").checkint() == 2, "Active close was not delivered exactly once");
    }

    private static GuiDocumentDefinition definition(LuaValue binding, double width, String type) {
        GuiProperties properties = new GuiProperties(type,
                Arrays.asList(GuiPropertyDescriptor.number("width", width, 0, 16384, Stage.MEASURE),
                        GuiPropertyDescriptor.number("x", 20, -10000, 10000, Stage.HIT, Stage.PAINT)));
        List<GuiNodeDefinition> nodes = Arrays.asList(
                new GuiNodeDefinition("root", "root", type, -1, null, null, properties, LuaValue.NIL,
                        GuiTransitionPolicy.DEFAULT, false),
                new GuiNodeDefinition("child", "root.child", "text", 0, null, null, properties, binding,
                        GuiTransitionPolicy.DEFAULT, false));
        GuiPresentationDefinition presentation = new GuiPresentationDefinition(nodes, LuaValue.NIL,
                Collections.emptySet(), "retained_tree_test.lua");
        return new GuiDocumentDefinition("test:retained", "retained_tree_test.lua", 800, 600, null, null,
                Collections.emptyList(), presentation, true);
    }

    private static final class View extends GuiContainer implements GuiViewAdapter {
        private double offset;
        private int publications;
        private int closed;

        @Override
        public GuiElement element() {
            return this;
        }

        @Override
        public void validateValues(Map<String, Object> values) {
            if (((Number) values.get("width")).doubleValue() == 777) {
                throw new IllegalArgumentException("Rejected prepared view fixture");
            }
        }

        @Override
        public void publish(GuiPresentationNode node) {
            offset = node.number("x", 0);
            publications++;
            setVisualTransform(new GraphicsTransform(offset, 0, 1, 1));
        }

        @Override
        public void close() {
            closed++;
        }
    }

    private static final class Model {
        private double count = 20;
        private double time;
        private final List<GuiPresentationHost.Observer> listeners = new ArrayList<>();

        private void set(double next) {
            double previous = count;
            count = next;
            for (GuiPresentationHost.Observer listener : new ArrayList<>(listeners)) {
                listener.changed("state:count", previous, next);
            }
        }
    }

    private static final class Host implements GuiPresentationHost {
        private final Model model;

        private Host(Model model) {
            this.model = model;
        }

        @Override
        public GuiPresentationSources snapshot() {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("count", model.count);
            return new GuiPresentationSources(state,
                    Collections.singletonMap("records", Collections.singletonMap("label", "detached")),
                    Collections.emptyMap());
        }

        @Override
        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources values) {
            return Collections.emptyMap();
        }

        @Override
        public List<AutoCloseable> observe(Observer observer) {
            model.listeners.add(observer);
            return Collections.singletonList(() -> model.listeners.remove(observer));
        }

        @Override
        public LuaTable context(LuaCallbackScope scope) {
            return new LuaTable();
        }

        @Override
        public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
            return new Host(model);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Expected " + type.getName(), failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
