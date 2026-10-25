package betamoon.gui.presentation;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Runs the production presentation engine with no tile, inventory, player,
 * world or Minecraft client.
 */
public final class GuiPresentationHostTest {
    private GuiPresentationHostTest() {
    }

    public static void main(String[] args) {
        independentInstances();
        callbackAuthority();
        revisionLifecycle();
        snapshotOwnership();
        System.out.println("Worldless presentation passed: isolated instances, observed state, pure sampling, "
                + "callback authority, atomic reload, stale handles and subscription cleanup.");
    }

    private static void independentInstances() {
        Globals lua = JsePlatform.standardGlobals();
        GuiDocumentDefinition definition = definition(lua, "test:worldless", "return {width=v.state:get('amount')}");
        Model first = new Model();
        Model second = new Model();
        try (GuiPresentationRuntime left = runtime(definition, first);
                GuiPresentationRuntime right = runtime(definition, second)) {
            left.update();
            right.update();
            first.set("amount", 72.0);
            left.update();
            right.update();
            near(left.element("panel").number("width", 0), 24);
            near(((Number) left.element("panel").target("width")).doubleValue(), 72);
            near(right.element("panel").number("width", 0), 24);
            int evaluations = left.evaluationCount("panel");
            first.time = 0.5;
            left.sample();
            near(left.element("panel").number("width", 0), 48);
            first.time = 1;
            left.sample();
            near(left.element("panel").number("width", 0), 72);
            require(left.evaluationCount("panel") == evaluations, "Sampling invoked a Lua binding");
            first.set("unobserved", 9.0);
            left.update();
            require(left.evaluationCount("panel") == evaluations, "Unobserved state invalidated a binding");
            first.set("amount", 72.0);
            left.update();
            require(left.evaluationCount("panel") == evaluations, "Unchanged state invalidated a binding");
            require(first.observers.size() == 1 && second.observers.size() == 1, "Instances shared subscriptions");
        }
        require(first.observers.isEmpty() && second.observers.isEmpty(), "Closed instances retained subscriptions");
    }

    private static void callbackAuthority() {
        Globals lua = JsePlatform.standardGlobals();
        Model model = new Model();
        lua.set("attemptGameplay", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaCallbackScope.requireGameplayWrite();
                model.gameplayWrites++;
                return LuaValue.NONE;
            }
        });
        GuiDocumentDefinition definition = definition(lua, "test:authority",
                "local ok=pcall(attemptGameplay); assert(not ok); " + "return {width=v.state:get('amount')}");
        lua.load("function command(ctx) savedState=ctx.state; savedElement=ctx.gui:element('panel'); "
                + "ctx.state:set('amount',48); local ok=pcall(attemptGameplay); assert(not ok) end").call();
        try (GuiPresentationRuntime runtime = runtime(definition, model)) {
            runtime.update();
            model.event();
            runtime.update();
            near(((Number) model.state.get("amount")).doubleValue(), 48);
            runtime.update();
            near(((Number) runtime.element("panel").target("width")).doubleValue(), 48);
            expectLua(() -> lua.load("savedState:set('amount',96)").call());
            expectLua(() -> lua.load("savedElement:set{width=96}").call());
            require(model.gameplayWrites == 0, "Presentation acquired gameplay authority");
        }
        expectLua(() -> lua.load("savedElement:get('width')").call());
    }

    private static void revisionLifecycle() {
        Globals lua = JsePlatform.standardGlobals();
        Model model = new Model();
        GuiDocumentDefinition definition = definition(lua, "test:revision", "return {width=v.state:get('amount')}");
        GuiPresentationRuntime original = runtime(definition, model);
        original.update();
        lua.load("function command(ctx) oldHandle=ctx.gui:element('panel') end").call();
        model.event();
        original.update();
        model.set("amount", 72.0);
        original.update();
        model.time = 0.25;
        original.sample();
        near(original.element("panel").number("width", 0), 36);

        GuiDocumentDefinition invalid = definition(lua, "test:revision", "return {width=-1}");
        expectLua(() -> original.reload(invalid));
        require(model.observers.size() == 1, "Rejected revision leaked a subscription");
        near(lua.load("return oldHandle:get('width')").call().checkdouble(), 36);
        GuiDocumentDefinition wrongHost = definition(lua, "other:document", "return {width=24}");
        expectLua(() -> original.reload(wrongHost));
        require(model.observers.size() == 1, "Incompatible document acquired subscriptions");

        GuiDocumentDefinition replacement = definition(lua, "test:revision", "return {width=v.state:get('amount')}");
        try (GuiPresentationRuntime current = original.reload(replacement)) {
            require(model.observers.size() == 1, "Visual refresh retained the old revision subscription");
            near(current.element("panel").number("width", 0), 36);
            expectLua(() -> original.element("panel"));
            expectLua(() -> lua.load("oldHandle:get('width')").call());
            model.time = 0.5;
            current.sample();
            near(current.element("panel").number("width", 0), 48);
            near(((Number) model.state.get("amount")).doubleValue(), 72);
        }
        original.close();
        require(model.observers.isEmpty(), "Repeated close retained subscriptions");
    }

    private static void snapshotOwnership() {
        Model model = new Model();
        GuiPresentationSources snapshot = new GuiPresentationSources(model.state, Collections.emptyMap(),
                Collections.emptyMap());
        model.set("amount", 48.0);
        near(((Number) snapshot.state.get("amount")).doubleValue(), 24);
        try {
            snapshot.state.put("amount", 96.0);
            throw new AssertionError("A source snapshot was mutable");
        } catch (UnsupportedOperationException expected) {
            // A host must not mutate a batch that the engine is currently deriving.
        }
    }

    private static GuiPresentationRuntime runtime(GuiDocumentDefinition definition, Model model) {
        Host host = new Host(definition.name, model);
        GuiPresentationLayout.validateDefinition(definition, host);
        return new GuiPresentationRuntime(definition, host, String::length, () -> model.time);
    }

    private static GuiDocumentDefinition definition(Globals lua, String name, String bindingBody) {
        LuaValue declaration = lua.load("return {x=8,y=8,width=24,height=24}").call();
        GuiDocumentDefinition.PanelElement panel = new GuiDocumentDefinition.PanelElement(8, 8, 0, GuiAnchor.TOP_LEFT,
                null, Collections.emptyList(), null, 24, 24, 0xFFFFFF);
        GuiProperties properties = new GuiProperties("panel", panel, declaration);
        GuiTransitionPolicy transition = properties
                .transition(lua.load("return {default={duration=1,easing='linear'}}").call());
        LuaValue binding = lua.load("return function(v) " + bindingBody + " end").call();
        GuiNodeDefinition node = new GuiNodeDefinition("panel", "elements[1]", "panel", -1, panel, null, properties,
                binding, transition, true);
        LuaValue event = lua
                .load("return function(ctx,e) if e.type=='interaction' and command then command(ctx) end end").call();
        GuiPresentationDefinition presentation = new GuiPresentationDefinition(Collections.singletonList(node), event,
                new LinkedHashSet<>(Arrays.asList("open", "change", "interaction", "reload", "close")), null);
        return new GuiDocumentDefinition(name, null, 160, 100, null, null, Collections.singletonList(panel),
                presentation);
    }

    private static void expectLua(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected a Lua refusal");
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 1e-9, "Expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Model {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<GuiPresentationHost.Observer> observers = new ArrayList<>();
        private double time;
        private int gameplayWrites;

        private Model() {
            state.put("amount", 24.0);
            state.put("unobserved", 0.0);
        }

        private void set(String name, Object value) {
            if (!state.containsKey(name)) {
                throw new LuaError("Unknown local state field: " + name);
            }
            Object previous = state.put(name, value);
            if (!Objects.equals(previous, value)) {
                for (GuiPresentationHost.Observer observer : new ArrayList<>(observers)) {
                    observer.changed("state:" + name, previous, value);
                }
            }
        }

        private void event() {
            for (GuiPresentationHost.Observer observer : new ArrayList<>(observers)) {
                observer.event("interaction", LuaTable::new);
            }
        }
    }

    private static final class Host implements GuiPresentationHost {
        private final String name;
        private final Model model;

        private Host(String name, Model model) {
            this.name = name;
            this.model = model;
        }

        @Override
        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(model.state, Collections.emptyMap(), Collections.emptyMap());
        }

        @Override
        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
            return Collections.emptyMap();
        }

        @Override
        public List<AutoCloseable> observe(Observer observer) {
            model.observers.add(observer);
            return Collections.singletonList(() -> model.observers.remove(observer));
        }

        @Override
        public LuaTable context(LuaCallbackScope scope) {
            LuaTable context = new LuaTable();
            LuaTable state = new LuaTable();
            state.set("set", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    scope.requireUiStateMutable();
                    int offset = args.arg1() == state ? 1 : 0;
                    model.set(args.arg(offset + 1).checkjstring(), args.arg(offset + 2).checkdouble());
                    return LuaValue.NONE;
                }
            });
            context.set("state", state);
            return context;
        }

        @Override
        public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
            if (!name.equals(replacement.name)) {
                throw new LuaError("A revision must preserve its host document identity.");
            }
            return new Host(name, model);
        }
    }
}
