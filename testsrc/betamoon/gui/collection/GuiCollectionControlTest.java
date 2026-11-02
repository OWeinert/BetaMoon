package betamoon.gui.collection;

import betamoon.gui.component.GuiComponentCompiler;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.component.GuiComponentRowFactory;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.widget.GuiStyledView;
import betamoon.luaapi.client.ui.GuiAuthoredNodeParser;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/**
 * Parsed collections exercise real retained row controls, keyboard input and
 * refresh.
 */
public final class GuiCollectionControlTest {
    private static final String OWNER = "collection-controls.lua";

    public static void main(String[] arguments) {
        defaultRows();
        customRows();
        controlledRefresh();
        nestedRows();
        declarationBudgets();
        nestedRefusal();
        tables();
        tableReplacementRefusal();
        controlledSort();
        nestedTableAdmission();
        System.out.println(
                "Collection controls passed: parsed list/tree/table, stable sort/filter/header alignment, 4096-record Lua virtualization, "
                        + "keyed editors, keyboard/reveal, disabled rows, stale gestures, model refusal and refresh.");
    }

    private static void defaultRows() {
        try (Fixture f = new Fixture()) {
            f.open("records={}; for i=1,100 do records[i]={key='r'..i,label='Row '..i,enabled=i~=2} end; "
                    + "changes=0; activations=0; return {type='list',id='list',width=160,height=60,overscan=0,"
                    + "bind=function(v)local version=v.data:get('version');return{records=records}end,"
                    + "on={change=function() changes=changes+1 end,activate=function() activations=activations+1 end}}");
            GuiCollectionView list = f.view("list");
            require(list.collection().realizedRows() == 3, "Default list realized offscreen rows");
            f.scene.getContext().requestFocus(list);
            f.key(Keyboard.KEY_DOWN);
            require("r1".equals(list.collection().selection()), "Down did not select the first key");
            f.key(Keyboard.KEY_DOWN);
            require("r3".equals(list.collection().selection()), "Navigation did not skip a disabled row");
            require(f.scene.getFocusedElement() == list.collection().rowElement("r3"),
                    "Replacement focus overrode keyboard navigation");
            f.key(Keyboard.KEY_END);
            require("r100".equals(list.collection().selection()) && list.collection().scrollOffset() == 1940,
                    "End did not reveal the final key");
            f.key(Keyboard.KEY_HOME);
            GuiElement row = list.collection().rowElement("r3");
            f.scene.mousePressed(row.getLeft() + 5, row.getTop() + 5, 0);
            f.lua.load("records[2],records[3]=records[3],records[2]").call();
            f.host.change();
            f.prepare();
            f.scene.mouseReleased(row.getLeft() + 5, row.getTop() + 5, 0);
            require(f.lua.get("activations").toint() == 0, "A moved row retained its old pointer gesture");
            GuiElement retained = list.collection().rowElement("r1");
            f.lua.load("records[2].key=records[1].key").call();
            f.host.change();
            f.prepare();
            require(list.collection().rowElement("r1") == retained && list.collection().model().records.size() == 100,
                    "A malformed record list replaced the last usable model");
        }
        try (Fixture f = new Fixture()) {
            f.open("return {type='tree',id='tree',width=160,height=60,overscan=0,"
                    + "records={{key='root'},{key='child',parent='root'},{key='leaf',parent='child'}},"
                    + "on={expand=function(ctx,e) lastExpansion=e.value end}}");
            GuiCollectionView tree = f.view("tree");
            require(tree.collection().visibleRows().size() == 1, "Tree did not begin collapsed");
            f.scene.getContext().requestFocus(tree);
            f.key(Keyboard.KEY_DOWN);
            f.key(Keyboard.KEY_RIGHT);
            require(tree.collection().visibleRows().size() == 2
                    && f.lua.get("lastExpansion").get("expanded").toboolean(),
                    "Right did not expand the selected branch");
            f.key(Keyboard.KEY_DOWN);
            f.key(Keyboard.KEY_RIGHT);
            f.key(Keyboard.KEY_DOWN);
            f.key(Keyboard.KEY_LEFT);
            require("child".equals(tree.collection().selection()), "Left did not select the parent key");
            f.key(Keyboard.KEY_LEFT);
            require(tree.collection().visibleRows().size() == 2, "Left did not collapse the selected branch");
        }
    }

    private static void customRows() {
        try (Fixture f = new Fixture()) {
            f.open("calls=0; records={}; for i=1,4096 do records[i]={key='r'..i,label='Row '..i} end; "
                    + "return {type='list',id='list',width=160,height=40,overscan=0,"
                    + "records=records,rowState={draft={type='string',default=''}},row=function(row) calls=calls+1; "
                    + "assert(not pcall(function()row.key='bad'end)); "
                    + "return {type='textbox',id='editor',maximumLength=65536,"
                    + "bind=function(v)return{text=v.state:get('draft')}end,"
                    + "on={change=function(ctx,e)ctx.state:set('draft',e.value)end}} end}");
            GuiCollectionView list = f.view("list");
            require(f.lua.get("calls").toint() == 2 && f.budget.liveRows() == 2,
                    "Public Lua list did not bound 4096 records to its two-row window");
            GuiStyledView editor = (GuiStyledView) list.collection().rowElement("r1");
            f.scene.getContext().requestFocus(editor);
            f.scene.keyTyped('x', Keyboard.KEY_X, false);
            f.prepare();
            list.collection().setScrollOffset(80);
            f.prepare();
            require(list.collection().rowElement("r1") == null, "Offscreen custom row was retained");
            list.collection().setScrollOffset(0);
            f.prepare();
            editor = (GuiStyledView) list.collection().rowElement("r1");
            require(editor.text().equals("x"), "A remounted keyed row lost its typed/editor draft");
            int calls = f.lua.get("calls").toint();
            for (int i = 0; i < 10; i++) {
                f.prepare();
                list.sample();
            }
            require(f.lua.get("calls").toint() == calls, "Idle preparation or animation replay called row Lua");
            f.scene.getContext().requestFocus(editor);
            f.scene.keyTyped('y', Keyboard.KEY_Y, false);
            f.prepare();
            f.reload();
            list = f.view("list");
            require(((GuiStyledView) list.collection().rowElement("r1")).text().equals("xy"),
                    "Visual refresh lost keyed row state");
        }
    }

    private static void controlledRefresh() {
        try (Fixture f = new Fixture()) {
            f.open("return {type='list',id='list',width=160,height=40,records={{key='a'},{key='b'}},"
                    + "bind=function(v)return{selectedKey=v.data:get('selected')}end,"
                    + "on={change=function(ctx,e)wanted=e.value end}}");
            GuiCollectionView list = f.view("list");
            f.scene.getContext().requestFocus(list);
            f.key(Keyboard.KEY_DOWN);
            require("a".equals(list.collection().selection()) && "b".equals(f.lua.get("wanted").tojstring()),
                    "Controlled selection accepted an unacknowledged local value");
            f.host.selected = "b";
            f.host.change();
            f.prepare();
            f.reload();
            require("b".equals(f.view("list").collection().selection()),
                    "A retained selection overrode its current controlled value");
        }
    }

    private static void nestedRows() {
        try (Fixture f = new Fixture()) {
            f.open("return {type='list',id='outer',width=160,height=40,rowHeight=40,overscan=0,"
                    + "records={{key='group'},{key='other'}},row=function(row)return {type='list',id='inner',"
                    + "height=40,overscan=0,records={{key='a'},{key='b'}}}end}");
            GuiVirtualCollection outer = f.view("outer").collection();
            GuiCollectionView inner = (GuiCollectionView) outer.rowElement("group");
            require(f.budget.liveRows() == 3, "Nested collections did not share the host row budget");
            f.scene.getContext().requestFocus(inner);
            f.key(Keyboard.KEY_DOWN);
            f.key(Keyboard.KEY_DOWN);
            require("b".equals(inner.collection().selection()), "Nested collection rejected a valid selection");
            outer.setScrollOffset(40);
            f.prepare();
            outer.setScrollOffset(0);
            f.prepare();
            inner = (GuiCollectionView) outer.rowElement("group");
            require("b".equals(inner.collection().selection()), "Nested collection lost dormant keyed selection");
        }
    }

    private static void declarationBudgets() {
        Globals lua = JsePlatform.standardGlobals();
        LuaValue authored = lua.load("local large=string.rep('x',700000);local children={}for i=1,7 do "
                + "children[i]={type='list',row=function()return{type='text'}end,"
                + "rowState={draft={type='data',default={payload=large}}}}end return{type='column',children=children}")
                .call();
        new GuiAuthoredNodeParser(new GuiComponentRegistry()).parse(OWNER, authored.get("children").get(1), "one");
        try {
            new GuiAuthoredNodeParser(new GuiComponentRegistry()).parse(OWNER, authored, "root");
        } catch (IllegalArgumentException | LuaError expected) {
            return;
        }
        throw new AssertionError("Collection row schemas bypassed the complete declaration budget");
    }

    private static void nestedRefusal() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;return {type='list',id='outer',width=160,height=80,rowHeight=80,overscan=0,"
                    + "rowState={padding={type='string',default=string.rep('x',16100)}},records={{key='group'}},"
                    + "row=function()return {type='column',children={"
                    + "{type='button',height=20,text='Clear',on={activate=function(ctx)ctx.state:set('padding','')end}},"
                    + "{type='list',height=40,overscan=0,records={{key=string.rep('k',255)}},"
                    + "on={change=function()changes=changes+1 end}}}}end}");
            GuiStyledView row = (GuiStyledView) f.view("outer").collection().rowElement("group");
            GuiCollectionView inner = (GuiCollectionView) row.getChildren().get(1);
            f.scene.getContext().requestFocus(inner);
            f.key(Keyboard.KEY_DOWN);
            require(inner.collection().selection() == null && f.lua.get("changes").toint() == 0,
                    "Nested selection called Lua or changed state before containing-row admission");
            GuiElement clear = row.getChildren().get(0);
            f.scene.mousePressed(clear.getLeft() + 5, clear.getTop() + 5, 0);
            f.scene.mouseReleased(clear.getLeft() + 5, clear.getTop() + 5, 0);
            f.prepare();
            f.scene.getContext().requestFocus(inner);
            f.key(Keyboard.KEY_DOWN);
            require(inner.collection().selection() != null && f.lua.get("changes").toint() == 1,
                    "Nested selection did not recover after the containing row freed state capacity");
        }
    }

    private static void tables() {
        try (Fixture f = new Fixture()) {
            f.open("columns={{key='title',label='Name'},{key='score',width=40}};"
                    + "records={{key='a',label='Alfa',data={title='A',score=3}},"
                    + "{key='b',data={title='B',score=1}},{key='c',data={title='C',score=1}},"
                    + "{key='d',data={title='D'}},{key='e',data={title='E',score=false}},"
                    + "{key='f',data={title='F',score='one'}}};return {type='table',id='table',"
                    + "width=160,height=80,overscan=0,bind=function(v)local version=v.data:get('version');"
                    + "return{records=records,columns=columns,filter=v.data:get('filter')}end}");
            GuiTableView table = (GuiTableView) f.view("table");
            GuiVirtualCollection rows = table.collection();
            require(rows.realizedRows() == 3, "Table header did not reserve body viewport height");
            GuiStyledView header = (GuiStyledView) table.getChildren().get(1);
            GuiElement score = header.getChildren().get(1);
            GuiStyledView first = (GuiStyledView) rows.rowElement("a");
            GuiStyledView cells = (GuiStyledView) first.getChildren().get(0);
            require(score.getWidth() == 40 && cells.getChildren().get(1).getLeft() == score.getLeft(),
                    "Header and cells disagreed on shared column widths");
            click(f, score);
            require(rows.scrollOffset() == 0 && order(rows).equals("d,e,b,c,a,f"),
                    "Primitive ascending sort or stable equal-key ordering failed");
            click(f, score);
            require(order(rows).equals("f,a,b,c,e,d"), "Descending sort was not stable");
            rows.setSelection("a");
            f.prepare();
            f.host.filter = "alfa";
            f.host.change();
            f.prepare();
            require(order(rows).equals("a") && rows.model().records.size() == 6 && rows.selection().equals("a"),
                    "Filtering removed full-model identity or selection");
            f.host.filter = "missing";
            f.host.change();
            f.prepare();
            require(rows.realizedRows() == 0 && rows.selection().equals("a"), "Filtered selection was pruned");
            f.host.filter = "";
            f.host.change();
            f.prepare();
            f.reload();
            table = (GuiTableView) f.view("table");
            rows = table.collection();
            require(order(rows).equals("f,a,b,c,e,d"), "Unbound table sort did not survive refresh");
            header = (GuiStyledView) table.getChildren().get(1);
            GuiElement accepted = header.getChildren().get(1);
            f.lua.load("columns[2].key='title'").call();
            f.host.change();
            f.prepare();
            require(header.getChildren().get(1) == accepted && order(rows).equals("f,a,b,c,e,d"),
                    "Invalid column metadata replaced the accepted table");
            f.lua.load("columns[2]=nil").call();
            f.host.change();
            f.prepare();
            require(header.getChildren().size() == 1
                    && ((GuiStyledView) ((GuiStyledView) rows.rowElement("a")).getChildren().get(0)).getChildren()
                            .size() == 1,
                    "Changing the column count broke accepted row updates or header/body replacement");
        }
        try (Fixture f = new Fixture()) {
            f.open("return{type='table',id='table',width=160,height=60,overscan=0,columns={{key='score'}},"
                    + "records={{key='a',label='Alfa',data={score=3}},{key='b',data={score=1}}},"
                    + "bind=function(v)return{filter=v.data:get('filter')}end,"
                    + "rowState={draft={type='string',default=''}},row=function()return{type='textbox',"
                    + "bind=function(v)return{text=v.state:get('draft')}end,"
                    + "on={change=function(ctx,e)ctx.state:set('draft',e.value)end}}end}");
            GuiTableView table = (GuiTableView) f.view("table");
            f.scene.getContext().requestFocus(table.collection().rowElement("a"));
            f.scene.keyTyped('x', Keyboard.KEY_X, false);
            f.prepare();
            f.host.filter = "b";
            f.host.change();
            f.prepare();
            require(table.collection().rowElement("a") == null, "Filter retained an excluded row graph");
            f.host.filter = "";
            f.host.change();
            f.prepare();
            require(((GuiStyledView) table.collection().rowElement("a")).text().equals("x"),
                    "Filtered-out table row lost its keyed draft");
        }
    }

    private static void tableReplacementRefusal() {
        try (Fixture f = new Fixture()) {
            f.open("fail=false;columns={{key='score'}};return{type='table',id='table',width=160,height=60,"
                    + "overscan=0,records={{key='a',data={score=1}}},"
                    + "bind=function(v)local version=v.data:get('version');return{columns=columns}end,"
                    + "row=function()if fail then error('replacement fixture')end return{type='text',text='Row'}end}");
            GuiTableView table = (GuiTableView) f.view("table");
            GuiStyledView header = (GuiStyledView) table.getChildren().get(1);
            GuiElement previous = table.collection().rowElement("a");
            f.lua.load("fail=true;columns={{key='score'},{key='title'}}").call();
            f.host.change();
            f.prepare();
            require(f.failures.size() == 1 && table.collection().rowElement("a") == previous
                    && header.getChildren().size() == 1,
                    "A failed row replacement exposed candidate headers or retired the accepted body");
            f.prepare();
            require(f.failures.size() == 1, "A failed table replacement retried factories while idle");
            f.failures.clear();
            f.lua.set("fail", LuaValue.FALSE);
            table.collection().refreshRows();
            f.prepare();
            require(header.getChildren().size() == 2 && table.collection().rowElement("a") != previous,
                    "A corrected table could not publish its header and row replacement together");
        }
    }

    private static void controlledSort() {
        try (Fixture f = new Fixture()) {
            f.open("return{type='table',id='table',width=160,height=60,columns={{key='score'}},"
                    + "records={{key='a',data={score=2}},{key='b',data={score=1}}},"
                    + "bind=function(v)return{sortColumn=v.data:get('sortColumn'),sortDirection=v.data:get('sortDirection')}end,"
                    + "on={sort=function(ctx,e)wantedSort=e.value end}}");
            GuiTableView table = (GuiTableView) f.view("table");
            GuiStyledView header = (GuiStyledView) table.getChildren().get(1);
            click(f, header.getChildren().get(0));
            require(order(table.collection()).equals("a,b")
                    && f.lua.get("wantedSort").get("direction").tojstring().equals("ascending"),
                    "Controlled sort accepted a local proposal without acknowledgement");
            f.host.sortColumn = "score";
            f.host.sortDirection = "ascending";
            f.host.change();
            f.prepare();
            require(order(table.collection()).equals("b,a"), "Acknowledged controlled sort did not reorder rows");
            f.reload();
            require(order(f.view("table").collection()).equals("b,a"),
                    "Controlled sort was overwritten during refresh");
        }
    }

    private static void nestedTableAdmission() {
        try (Fixture f = new Fixture()) {
            f.open("sorts=0;return{type='list',id='outer',width=160,height=80,rowHeight=80,overscan=0,records={{key='group'}},"
                    + "rowState={padding={type='string',default=string.rep('x',16100)}},row=function()return{type='column',children={"
                    + "{type='button',height=20,text='Clear',on={activate=function(ctx)ctx.state:set('padding','')end}},"
                    + "{type='table',height=60,overscan=0,columns={{key=string.rep('k',255)}},records={{key='a'}},"
                    + "on={sort=function()sorts=sorts+1 end}}}}end}");
            GuiStyledView row = (GuiStyledView) f.view("outer").collection().rowElement("group");
            GuiTableView table = (GuiTableView) row.getChildren().get(1);
            GuiStyledView header = (GuiStyledView) table.getChildren().get(1);
            require(f.budget.liveElements() >= 70,
                    "Nested table headers escaped their containing-row element allowance");
            click(f, header.getChildren().get(0));
            require(f.lua.get("sorts").toint() == 0 && !table.captureState().containsKey("sortColumn"),
                    "An over-budget nested sort called Lua or changed accepted local state");
            click(f, row.getChildren().get(0));
            click(f, header.getChildren().get(0));
            require(f.lua.get("sorts").toint() == 1 && table.captureState().containsKey("sortColumn"),
                    "Nested sort did not recover after containing-row state capacity was freed");
        }
    }

    private static void click(Fixture f, GuiElement element) {
        f.scene.mousePressed(element.getLeft() + 3, element.getTop() + 3, 0);
        f.scene.mouseReleased(element.getLeft() + 3, element.getTop() + 3, 0);
        f.prepare();
    }

    private static String order(GuiVirtualCollection rows) {
        List<String> keys = new ArrayList<>();
        for (GuiCollectionModel.Row row : rows.visibleRows()) {
            keys.add(row.record.key);
        }
        return String.join(",", keys);
    }

    static final class Fixture implements AutoCloseable {
        final Globals lua = JsePlatform.standardGlobals();
        final GuiComponentRegistry registry = new GuiComponentRegistry();
        final GuiRowBudget budget = new GuiRowBudget();
        final Host host = new Host();
        final GuiScene scene = new GuiScene();
        final List<Throwable> failures = new ArrayList<>();
        final GuiComponentPlan.Services services = new GuiComponentPlan.Services() {
            public GuiRowBudget rowBudget() {
                return budget;
            }

            public GuiPaintCompiler.Resources paintResources(String owner) {
                throw new IllegalArgumentException("No paint");
            }

            public void requireHost() {
            }

            public void failure(String owner, String path, Throwable failure) {
                failures.add(failure);
            }

            public GuiComponentRowFactory.Environment rowEnvironment(GuiRowFactory.Context context, String theme) {
                return new GuiComponentRowFactory.Environment() {
                    public GuiPresentationHost host() {
                        return host;
                    }

                    public GuiComponentPlan.Services services() {
                        return services;
                    }

                    public void close() {
                    }
                };
            }
        };
        LuaValue authored;
        GuiComponentPlan plan;
        GuiPresentationTree tree;

        void open(String source) {
            authored = lua.load(source).call();
            plan = compile(null);
            tree = plan.createTree(Collections.emptySet(), s -> s.length() * 6, () -> 0);
            scene.updateEnvironment(null, null, 200, 120, 200, 120);
            scene.setDocument(tree);
            prepare();
        }

        GuiComponentPlan compile(GuiComponentPlan previous) {
            return GuiComponentCompiler.prepare(registry, OWNER, "test:collections",
                    new GuiAuthoredNodeParser(registry).parse(OWNER, authored, "root"), host, services,
                    Collections.emptySet(), previous);
        }

        void reload() {
            GuiComponentPlan next = compile(plan);
            GuiPresentationTree replacement = next.reloadTree(tree, candidate -> scene.prepareDetached(candidate));
            plan.close();
            plan = next;
            tree = replacement;
            scene.setDocument(tree);
            prepare();
        }

        GuiCollectionView view(String id) {
            return (GuiCollectionView) tree.element(plan.elementId(id));
        }

        void key(int key) {
            scene.keyTyped('\0', key, false);
            prepare();
        }

        void prepare() {
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
        }

        public void close() {
            if (tree != null) {
                tree.close();
            }
            if (plan != null) {
                plan.close();
            }
            require(budget.reservedElements() == 0 && host.observers.isEmpty(),
                    "Collections leaked rows or subscriptions");
            budget.close();
            require(failures.isEmpty(), "Collection preparation failed: " + failures);
        }
    }

    static final class Host implements GuiPresentationHost {
        final List<Observer> observers = new ArrayList<>();
        final Map<String, Object> extra = new LinkedHashMap<>();
        int version;
        String selected = "a";
        String filter = "";
        String sortColumn = "";
        String sortDirection = "none";
        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.emptyMap(), data(), Collections.emptyMap());
        }

        Map<String, Object> data() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("version", version);
            result.put("selected", selected);
            result.put("filter", filter);
            result.put("sortColumn", sortColumn);
            result.put("sortDirection", sortDirection);
            result.putAll(extra);
            return result;
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

        public GuiPresentationHost forDefinition(GuiDocumentDefinition definition) {
            return this;
        }

        void change() {
            int before = version++;
            for (Observer observer : new ArrayList<>(observers)) {
                observer.changed("data:version", before, version);
                observer.changed("data:selected", null, selected);
                observer.changed("data:filter", null, filter);
                observer.changed("data:sortColumn", null, sortColumn);
                observer.changed("data:sortDirection", null, sortDirection);
                for (Map.Entry<String, Object> entry : extra.entrySet()) {
                    observer.changed("data:" + entry.getKey(), null, entry.getValue());
                }
            }
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
