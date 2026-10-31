package betamoon.gui.collection;

import betamoon.gui.component.GuiComponentCompiler;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.luaapi.client.ui.GuiAuthoredNodeParser;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Parsed Lua grids use the Java layout and preflight complete reactive layout
 * changes.
 */
public final class GuiGridControlTest {
    public static void main(String[] arguments) {
        Globals lua = JsePlatform.standardGlobals();
        GuiComponentRegistry components = new GuiComponentRegistry();
        Host host = new Host();
        host.publish(2, 0, 10);
        LuaValue authored = lua.load("calls=0; return {type='grid',id='grid',fill=true,gap=5,"
                + "bind=function(v) calls=calls+1; return {columns=v.data:get('columns'),"
                + "minimumCellWidth=v.data:get('cell')} end,children={"
                + "{type='button',id='a',text='A',transition=false,bind=function(v)return{height=v.data:get('height')}end},"
                + "{type='button',id='b',text='B',height=20},"
                + "{type='button',id='c',text='C',transition=false,bind=function(v)return{height=v.data:get('height'),"
                + "visibility=v.data:get('visibility')}end}," + "{type='button',id='d',text='D',height=5}}}").call();
        GuiComponentPlan.Services services = new GuiComponentPlan.Services() {
            public GuiPaintCompiler.Resources paintResources(String owner) {
                throw new IllegalArgumentException("This grid fixture has no custom graphics");
            }

            public void requireHost() {
            }

            public void failure(String owner, String path, Throwable failure) {
                throw new AssertionError(failure);
            }
        };
        try (GuiComponentPlan plan = GuiComponentCompiler.prepare(components, "grid-control-test.lua", "test:grid",
                new GuiAuthoredNodeParser(components).parse("grid-control-test.lua", authored, "root"), host, services,
                Collections.emptySet(), null);
                GuiPresentationTree tree = plan.createTree(Collections.emptySet(), text -> text.length() * 6,
                        () -> 0)) {
            GuiScene scene = new GuiScene();
            scene.updateEnvironment(null, null, 205, 120, 205, 120);
            scene.setDocument(tree);
            prepare(scene);
            GuiElement a = tree.element(plan.elementId("a"));
            GuiElement b = tree.element(plan.elementId("b"));
            GuiElement c = tree.element(plan.elementId("c"));
            GuiElement grid = tree.root();
            require(a.getWidth() == 100 && b.getLeft() == 105 && c.getTop() == 25,
                    "Parsed fixed columns did not use shared row measurement/remainder geometry");
            host.publish(0, 60, 30);
            prepare(scene);
            require(a.getWidth() == 65 && c.getRight() == 205,
                    "Reactive responsive columns failed to reflow the existing shared tree");
            scene.updateEnvironment(null, null, 130, 120, 130, 120);
            prepare(scene);
            require(a.getWidth() == 63 && b.getLeft() == 68 && c.getTop() == 35,
                    "Responsive resize disagreed with the Java grid geometry: " + a.getWidth() + "/" + b.getLeft() + "/"
                            + c.getTop());
            Size measured = grid.getMeasuredSize();
            scene.getContext().requestFocus(b);
            host.publish(0, 60, 10500);
            prepare(scene);
            require(a.getHeight() == 30 && c.getHeight() == 30 && grid.getMeasuredSize().equals(measured)
                    && scene.getFocusedElement() == b,
                    "A refused combined grid overflow published properties, measurement caches or lost focus");
            int calls = lua.get("calls").checkint();
            for (int frame = 0; frame < 5; frame++) {
                prepare(scene);
                tree.sample();
            }
            require(lua.get("calls").checkint() == calls, "Idle grid replay retried Lua after a refused layout");
            host.publish(2, 60, 20);
            prepare(scene);
            require(a.getWidth() == 63, "Conflicting grid modes replaced the accepted layout");
            host.publish(1, 0, 20);
            prepare(scene);
            require(a.getWidth() == 130 && b.getTop() == 25 && scene.getFocusedElement() == b,
                    "A valid grid update did not recover after a rejected layout");
            host.publish(0, 60, 10500, "collapsed");
            prepare(scene);
            require(c.isCollapsed() && a.getHeight() == 10500,
                    "Grid preflight measured a prospectively collapsed child as participating");
            host.publish(0, 60, 10500);
            prepare(scene);
            require(c.isCollapsed() && scene.getFocusedElement() == b,
                    "Rejected uncollapse changed live visibility or keyboard focus");
            host.publish(2, 0, 20);
            prepare(scene);
            require(!c.isCollapsed() && c.getHeight() == 20, "A valid uncollapse did not restore grid participation");
        }
        require(host.observers.isEmpty(), "Closing a parsed grid retained source subscriptions");
        growingRow(lua, components, host, services);
        System.out.println("Grid controls passed: parsed fixed/responsive layout, reactive reflow/resize, complete "
                + "overflow rejection, cache/focus retention, idle replay and recovery.");
    }

    private static void growingRow(Globals lua, GuiComponentRegistry components, Host host,
            GuiComponentPlan.Services services) {
        LuaValue declaration = lua.load("local children={}; for i=1,6 do children[i]={type='button',height=20} end; "
                + "return {type='row',width=130,children={{type='spacer',width=40,height=1},"
                + "{type='grid',id='grid',grow=1,columns=0,minimumCellWidth=60,children=children}}}").call();
        try (GuiComponentPlan plan = GuiComponentCompiler.prepare(components, "grid-control-test.lua", "test:grow_grid",
                new GuiAuthoredNodeParser(components).parse("grid-control-test.lua", declaration, "root"), host,
                services, Collections.emptySet(), null);
                GuiPresentationTree tree = plan.createTree(Collections.emptySet(), text -> text.length() * 6,
                        () -> 0)) {
            GuiScene scene = new GuiScene();
            scene.updateEnvironment(null, null, 130, 200, 130, 200);
            scene.setDocument(tree);
            prepare(scene);
            GuiElement grid = tree.element(plan.elementId("grid"));
            require(grid.getWidth() == 90 && tree.root().getMeasuredSize().getHeight() == 120,
                    "A growing responsive grid measured before its sibling's width allocation");
        }
    }

    private static void prepare(GuiScene scene) {
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
    }

    private static final class Host implements GuiPresentationHost {
        final GuiPublishedData data = new GuiPublishedData();
        final List<Observer> observers = new ArrayList<>();

        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.emptyMap(), data.snapshot(), Collections.emptyMap());
        }

        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
            return Collections.emptyMap();
        }

        public List<AutoCloseable> observe(Observer observer) {
            observers.add(observer);
            return Collections.singletonList(() -> observers.remove(observer));
        }

        public LuaTable context(LuaCallbackScope scope) {
            return new LuaTable();
        }

        public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
            return this;
        }

        void publish(int columns, int cell, int height) {
            publish(columns, cell, height, "visible");
        }

        void publish(int columns, int cell, int height, String visibility) {
            Map<String, Object> before = data.snapshot();
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("columns", columns);
            next.put("cell", cell);
            next.put("height", height);
            next.put("visibility", visibility);
            data.publish(next);
            for (String key : next.keySet()) {
                for (Observer observer : new ArrayList<>(observers)) {
                    observer.changed("data:" + key, before.get(key), next.get(key));
                }
            }
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
