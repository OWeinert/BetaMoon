package betamoon.gui.component;

import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import betamoon.luaapi.client.ui.GuiAuthoredNodeParser;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.interop.NativeCallbackTable;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Authored components compile into actual shared controls; no alternative Lua
 * tree/input engine is used.
 */
public final class GuiComponentTest {
    private static final String AUTHOR = "component-author.lua";
    private static final String SCREEN = "component-screen.lua";

    public static void main(String[] args) {
        schemasAndCycles();
        sharedInstances();
        try (PaintFixture fixture = new PaintFixture()) {
            fixture.check();
        }
        derivedProps();
        System.out
                .println("Components passed: schemas/cycles, shared controls, scoped IDs/exports, caller-owned slots, "
                        + "isolated keyed state, transactional events, builder reuse, refresh and dependency cleanup.");
    }

    private static void schemasAndCycles() {
        Globals lua = JsePlatform.standardGlobals();
        GuiComponentSchema schema = GuiComponentParser.schema(lua.load("return {n={type='integer',min=1,max=3}, "
                + "tint={type='color',default='#55AAFF'}, payload={type='data',default={title='12',rows={1,2}}}}")
                .call(), "test.schema", false);
        expect(() -> schema.normalize(Collections.emptyMap()));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("n", 2);
        require(schema.normalize(values).get("tint").equals(0xFF55AAFF), "Color schema coerced its typed baseline");
        values.put("n", 1.5);
        expect(() -> schema.normalize(values));
        GuiComponentRegistry registry = new GuiComponentRegistry();
        registry.add(GuiComponentParser.parse(AUTHOR,
                lua.load("return {key='test:a',dependencies={'test:b'},build=function() return {type='panel'} end}")
                        .call()));
        expect(() -> registry.add(GuiComponentParser.parse(AUTHOR,
                lua.load("return {key='test:b',dependencies={'test:a'},build=function() return {type='panel'} end}")
                        .call())));
        require(registry.get("test:b") == null, "Rejected dependency cycle replaced the registry candidate");
        expect(() -> GuiComponentParser.parse(AUTHOR,
                lua.load("return {key='test:invalid',paint=function()return{}end,rawDraw=function()end}").call()));
        expect(() -> GuiComponentParser.schema(
                lua.load("local t={};for i=1,65 do t['field'..i]={type='string'} end;return t").call(), "test.tooMany",
                false));
        expect(() -> GuiComponentParser.schema(
                lua.load("return {payload={type='data',default=string.rep(string.char(228,184,173),400000)}}").call(),
                "test.utf8", false));
        ScriptResourceTracker.unload(AUTHOR);
    }

    private static void sharedInstances() {
        Globals lua = JsePlatform.standardGlobals();
        GuiComponentRegistry registry = new GuiComponentRegistry();
        LuaValue declaration = lua.load("buildCalls=0; bindCalls=0; fail=false; return {key='test:quest', "
                + "props={title={type='string',default='Quest'}}, state={count={type='integer',default=0,min=0,max=100}}, "
                + "events={chosen={count={type='integer',min=0,max=100}}}, slots={tail={max=2}}, exports={confirm='choose',readout='readout'}, "
                + "build=function(props,slots) buildCalls=buildCalls+1; return {type='column',gap=2,children={"
                + "{type='button',id='choose',text=props.title,on={activate=function(ctx,event) savedState=ctx.state; "
                + "local n=ctx.state:get('count')+1; ctx.state:set('count',n); "
                + "if fail then ctx.gui:element('readout'):set({text='leaked'}); error('component failure fixture') end; "
                + "ctx:emit('chosen',{count=n}) end}}, {type='text',id='readout',bind=function(v) bindCalls=bindCalls+1; "
                + "return {text=tostring(v.state:get('count'))} end}, {slot='tail'}}} end}").call();
        registry.add(GuiComponentParser.parse(AUTHOR, declaration));
        GuiAuthoredNode root = new GuiAuthoredNodeParser(registry).parse(SCREEN,
                lua.load("return {type='column',id='page',width=200,height=200,gap=8,children={"
                        + "{component='test:quest',id='first',key='first',props={title='First'},on={chosen=function(ctx,event)ctx.state:set('selected',event.payload.count)end},"
                        + "slots={tail={{type='button',id='tailButton',text='Tail',on={activate=function(ctx)ctx.state:set('selected',ctx.state:get('selected')+10)end}}}}},"
                        + "{component='test:quest',id='second',key='second',props={title='Second'},on={chosen=function(ctx,event)ctx.state:set('selected',event.payload.count)end}}}}")
                        .call(),
                "screen.root");
        Host host = new Host();
        List<Throwable> failures = new ArrayList<>();
        GuiComponentPlan.Services services = services(failures);
        GuiComponentPlan plan = GuiComponentCompiler.prepare(registry, SCREEN, "test:component_screen", root, host,
                services, EnumSet.allOf(GuiCapability.class), null);
        GuiPresentationTree tree = plan.createTree(EnumSet.allOf(GuiCapability.class), text -> text.length() * 6,
                () -> 0);
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 240, 320, 240);
        scene.setDocument(tree);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(lua.get("buildCalls").checkint() == 2 && lua.get("bindCalls").checkint() == 2,
                "Component instances did not build/bind once");
        String first = plan.elementId("first.confirm");
        String second = plan.elementId("second.confirm");
        require(!first.equals(second), "Two component uses shared a local element identity");
        click(scene, tree.element(first));
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(text(tree, plan, "first").equals("1") && text(tree, plan, "second").equals("0"),
                "Local state crossed component instances");
        require(host.selected == 1 && lua.get("bindCalls").checkint() == 3,
                "Typed event payload or dirty-only local binding failed");
        require(!lua.load("return pcall(function()savedState:set('count',99)end)").call().toboolean(),
                "Retained component context remained mutable");
        for (GuiNodeDefinition node : plan.document().presentation.nodes) {
            if ("Tail".equals(node.properties.baseline().get("text"))) {
                click(scene, tree.element(node.id));
                break;
            }
        }
        require(host.selected == 11, "Content-slot handler used component state instead of its author's caller state");
        require(!plan.requiresRecompile(registry, host.snapshot()),
                "Ordinary local state rebuilt an unrelated component body");
        List<GuiAuthoredNode> reversed = new ArrayList<>(root.children);
        Collections.reverse(reversed);
        GuiAuthoredNode reordered = new GuiAuthoredNode(root.owner, root.type, root.component, root.slot,
                root.slotIndex, root.id, root.key, root.properties, root.props, root.slots, reversed, root.handlers,
                root.binding, root.transition, root.explicitTransition);
        GuiComponentPlan next = GuiComponentCompiler.prepare(registry, SCREEN, "test:component_screen", reordered, host,
                services, EnumSet.allOf(GuiCapability.class), plan);
        GuiPresentationTree replacement = next.reloadTree(tree, scene::prepareDetached);
        plan.close();
        plan = next;
        tree = replacement;
        scene.setDocument(tree);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(lua.get("buildCalls").checkint() == 2 && text(tree, plan, "first").equals("1"),
                "Keyed reorder reran unchanged builders or reset state");
        require(host.observers.size() == 2, "Compatible refresh retained old source subscriptions");
        lua.set("fail", LuaValue.TRUE);
        click(scene, tree.element(plan.elementId("first.confirm")));
        require(failures.size() == 1 && text(tree, plan, "first").equals("1"),
                "Failing event leaked staged state or visual commands");
        lua.set("fail", LuaValue.FALSE);
        click(scene, tree.element(plan.elementId("second.confirm")));
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(text(tree, plan, "second").equals("1"), "One component failure disabled an unrelated instance");
        plan.onInvalidated(() -> scene.setDocument(null));
        ScriptResourceTracker.unload(AUTHOR);
        require(scene.getContent() == null && host.observers.isEmpty() && registry.get("test:quest") == null,
                "Dependency unload left live views, definitions or source subscriptions");
        tree.close();
        plan.close();
        ScriptResourceTracker.unload(SCREEN);
    }

    public static final class PaintFixture implements AutoCloseable {
        public final Globals lua = JsePlatform.standardGlobals();
        public final GuiScene scene = new GuiScene();
        public final List<Throwable> failures = new ArrayList<>();
        public final GuiComponentRegistry registry = new GuiComponentRegistry();
        public final GuiPresentationTree tree;
        public final GuiComponentPlan plan;
        private final Host host = new Host();

        public PaintFixture() {
            registry.add(GuiComponentParser.parse(AUTHOR,
                    lua.load("paintCalls=0; inputCalls=0; return {key='test:radial', "
                            + "size={width=120,height=120}, props={amount={type='number',default=0.75,min=0,max=1,animated=true}}, "
                            + "state={clicks={type='integer',default=0,min=0,max=100}}, events={chosen={count={type='integer',min=0,max=100}}}, "
                            + "exports={control='root'}, focusable=true, paint=function(v)paintCalls=paintCalls+1; local n=v.state:get('clicks'); return {"
                            + "{op='ring',x=60,y=60,inner=34,outer=50,sweep={source='property',property='amount',factor=360},color=n==0 and '#FF55AAFF' or '#FF88FF55'},"
                            + "{op='hitPolygon',name='diamond',vertices={60,10,110,60,60,110,10,60}}} end, "
                            + "input=function(ctx,e)inputCalls=inputCalls+1; if e.type=='pointer_down' then assert(e.region=='diamond');return {consume=true,capture=true,focus=true} end; "
                            + "if e.type=='pointer_up' then local n=ctx.state:get('clicks')+1; ctx.state:set('clicks',n);ctx:emit('chosen',{count=n});return {consume=true,release=true} end; return true end}")
                            .call()));
            GuiAuthoredNode root = new GuiAuthoredNodeParser(registry).parse(SCREEN,
                    lua.load("return {type='row',width=248,height=120,gap=8,children={"
                            + "{component='test:radial',id='first',key='first',on={chosen=function(ctx,e)ctx.state:set('selected',e.payload.count)end}},"
                            + "{component='test:radial',id='second',key='second'}}}").call(),
                    "screen.radial");
            plan = GuiComponentCompiler.prepare(registry, SCREEN, "test:radial_screen", root, host, services(failures),
                    EnumSet.allOf(GuiCapability.class), null);
            tree = plan.createTree(EnumSet.allOf(GuiCapability.class), text -> text.length() * 6, () -> 0);
            scene.setDocument(tree);
            scene.updateEnvironment(null, null, 320, 240, 320, 240);
        }

        public GuiElement first() {
            return tree.element(plan.elementId("first.control"));
        }

        public void check() {
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
            require(lua.get("paintCalls").checkint() == 2, "Two custom components did not prepare independently");
            GuiElement element = first();
            require(!scene.mousePressed(element.getLeft() + 12, element.getTop() + 12, 0),
                    "Irregular region captured its rectangular corner");
            click(scene, element);
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
            require(host.selected == 1 && lua.get("paintCalls").checkint() == 3,
                    "Custom input did not emit its schema event or invalidated unrelated paint");
            for (int index = 0; index < 10; index++) {
                scene.prepare();
                HeadlessGuiFrames.layout(scene);
            }
            require(lua.get("paintCalls").checkint() == 3, "Idle custom component polled Lua paint");
            require(scene.mousePressed(element.getLeft() + 60, element.getTop() + 60, 0),
                    "Custom region did not capture");
            plan.onInvalidated(() -> scene.setDocument(null));
            int calls = lua.get("inputCalls").checkint();
            ScriptResourceTracker.unload(AUTHOR);
            require(scene.mouseReleased(element.getLeft() + 60, element.getTop() + 60, 0),
                    "Unload lost owned release consumption");
            require(lua.get("inputCalls").checkint() == calls && failures.isEmpty(),
                    "Unload invoked expired custom input or reported a fault");
        }

        public void close() {
            plan.close();
            tree.close();
            ScriptResourceTracker.unload(AUTHOR);
            ScriptResourceTracker.unload(SCREEN);
        }
    }

    private static void derivedProps() {
        Globals lua = JsePlatform.standardGlobals();
        GuiComponentRegistry registry = new GuiComponentRegistry();
        registry.add(
                GuiComponentParser.parse(AUTHOR,
                        lua.load("buildCalls=0; return {key='test:derived',props={title={type='string',default='0'}},"
                                + "build=function(p)buildCalls=buildCalls+1;return {type='button',text=p.title}end}")
                                .call()));
        GuiAuthoredNode root = new GuiAuthoredNodeParser(registry).parse(SCREEN, lua.load(
                "return {component='test:derived',key='same',bind=function(v)return {props={title=tostring(v.state:get('selected'))}} end}")
                .call(), "derived.root");
        Host host = new Host();
        List<Throwable> failures = new ArrayList<>();
        GuiComponentPlan plan = GuiComponentCompiler.prepare(registry, SCREEN, "test:derived", root, host,
                services(failures), EnumSet.allOf(GuiCapability.class), null);
        GuiPresentationTree tree = plan.createTree(EnumSet.allOf(GuiCapability.class), String::length, () -> 0);
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 240, 320, 240);
        scene.setDocument(tree);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(!plan.requiresRecompile(registry, host.snapshot()), "Unchanged component props requested recompile");
        host.selected = 7;
        require(plan.requiresRecompile(registry, host.snapshot()),
                "Observed prop dependency did not request recompile");
        GuiComponentPlan next = GuiComponentCompiler.prepare(registry, SCREEN, "test:derived", root, host,
                services(failures), EnumSet.allOf(GuiCapability.class), plan);
        GuiPresentationTree replacement = next.reloadTree(tree, scene::prepareDetached);
        plan.close();
        scene.setDocument(replacement);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(lua.get("buildCalls").checkint() == 2 && !next.requiresRecompile(registry, host.snapshot())
                && failures.isEmpty(), "Changed prop did not rebuild exactly once");
        next.close();
        replacement.close();
        ScriptResourceTracker.unload(AUTHOR);
        ScriptResourceTracker.unload(SCREEN);
    }

    private static String text(GuiPresentationTree tree, GuiComponentPlan plan, String instance) {
        return String.valueOf(tree.runtime().element(plan.elementId(instance + ".readout")).get("text"));
    }

    private static GuiComponentPlan.Services services(List<Throwable> failures) {
        return new GuiComponentPlan.Services() {
            public GuiPaintCompiler.Resources paintResources(String owner) {
                return new GuiPaintCompiler.Resources() {
                    public GuiFontFace font(String key) {
                        return GuiFontFace.NATIVE;
                    }

                    public GuiDrawable image(String key) {
                        throw new IllegalArgumentException("Unknown image: " + key);
                    }
                };
            }

            public void requireHost() {
            }

            public void failure(String owner, String path, Throwable failure) {
                failures.add(failure);
            }
        };
    }

    private static void click(GuiScene scene, GuiElement element) {
        int x = element.getLeft() + Math.max(1, element.getWidth() / 2);
        int y = element.getTop() + Math.max(1, element.getHeight() / 2);
        scene.mousePressed(x, y, 0);
        scene.mouseReleased(x, y, 0);
    }

    private static final class Host implements GuiPresentationHost {
        final List<Observer> observers = new ArrayList<>();
        int selected;
        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.singletonMap("selected", selected), Collections.emptyMap(),
                    Collections.emptyMap());
        }

        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources values) {
            return Collections.emptyMap();
        }

        public List<AutoCloseable> observe(Observer observer) {
            observers.add(observer);
            return Collections.singletonList(() -> observers.remove(observer));
        }

        public LuaTable context(LuaCallbackScope scope) {
            LuaTable context = new NativeCallbackTable(scope);
            LuaTable state = new LuaTable();
            state.set("get", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    scope.requireActive();
                    return LuaValue.valueOf(selected);
                }
            });
            state.set("set", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    scope.requireUiStateMutable();
                    int before = selected;
                    selected = arguments.arg(3).checkint();
                    for (Observer observer : new ArrayList<>(observers)) {
                        observer.changed("state:selected", before, selected);
                    }
                    return NIL;
                }
            });
            context.set("state", state);
            return context;
        }

        public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
            return this;
        }
    }

    private static void expect(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException | LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid component contract was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
