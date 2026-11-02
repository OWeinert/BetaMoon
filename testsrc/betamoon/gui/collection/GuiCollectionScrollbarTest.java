package betamoon.gui.collection;

import betamoon.gui.collection.GuiCollectionControlTest.Fixture;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.gui.widget.GuiStyledView;
import org.lwjgl.input.Keyboard;

/**
 * Shared scrollbar gestures keep virtual rows bounded and table headers
 * aligned.
 */
public final class GuiCollectionScrollbarTest {
    public static void main(String[] args) {
        largeWindowDrag();
        horizontalTable();
        smallTable();
        initialHorizontal();
        controlledHorizontal();
        nestedHorizontalAdmission();
        System.out.println("Collection scrollbars passed: bounded 4096-record dragging, shrink cancellation, "
                + "pinned/aligned table headers, horizontal ownership/refresh and nested refusal/recovery.");
    }

    private static void largeWindowDrag() {
        try (Fixture f = new Fixture()) {
            f.open("calls=0;records={};for i=1,4096 do records[i]={key='r'..i}end;return{type='list',id='list',"
                    + "width=100,height=60,overscan=0,bind=function(v)local version=v.data:get('version');return{records=records}end,"
                    + "row=function(row)calls=calls+1;return{type='text',text=row.key}end}");
            GuiCollectionView list = f.view("list");
            require(list.viewport().getChildren().get(1).isVisible() && list.collection().getHeight() == 60,
                    "Virtual content was arranged at its full logical extent or has no scrollbar");
            int x = list.viewport().getRight() - 2;
            int top = list.viewport().getTop();
            f.scene.mousePressed(x, top + 3, 0);
            f.scene.mouseDragged(x, top + 48, true);
            f.prepare();
            f.scene.mouseReleased(x, top + 48, 0);
            require(list.collection().scrollOffset() > 70000 && list.collection().realizedRows() <= 4
                    && f.budget.liveRows() <= 4 && f.lua.get("calls").toint() <= 7,
                    "Scrollbar drag expanded the full model or failed to publish its accepted virtual offset");
            int calls = f.lua.get("calls").toint();
            for (int i = 0; i < 5; i++) {
                f.prepare();
            }
            require(f.lua.get("calls").toint() == calls, "Idle scrollbar synchronization reran Lua factories");
            f.scene.getContext().requestFocus(list);
            f.key(Keyboard.KEY_END);
            f.scene.mousePressed(x, top + 50, 0);
            f.lua.load("for i=11,#records do records[i]=nil end").call();
            f.host.change();
            f.prepare();
            int accepted = list.collection().scrollOffset();
            f.scene.mouseDragged(x, top + 5, true);
            f.scene.mouseReleased(x, top + 5, 0);
            f.prepare();
            require(accepted == 140 && list.collection().scrollOffset() == accepted && f.budget.liveRows() <= 3,
                    "Model shrink retained stale thumb capture or failed to clamp its window");
        }
    }

    private static void horizontalTable() {
        try (Fixture f = new Fixture()) {
            f.open("columns={{key='left',width=100},{key='right',width=100}};records={};for i=1,20 do "
                    + "records[i]={key='r'..i,data={left='L'..i,right='R'..i}}end;scrolls=0;"
                    + "return{type='table',id='table',width=100,height=60,overscan=0,"
                    + "bind=function(v)local version=v.data:get('version');return{columns=columns,records=records}end,"
                    + "on={scroll=function(ctx,e)scrolls=scrolls+1;wanted=e.value.scrollX end}}");
            GuiTableView table = table(f);
            int top = table.header().getTop();
            require(table.viewport().getChildren().get(2).isVisible(),
                    "Wide columns did not expose the shared horizontal bar");
            f.scene.mouseScrolled(table.getLeft() + 10, table.getTop() + 30, -120, true);
            f.prepare();
            require(table.viewport().getScrollX() == 15 && table.collection().scrollOffset() == 0
                    && f.lua.get("wanted").toint() == 15, "Shift-wheel changed the wrong table axis");
            f.scene.mouseScrolled(table.getLeft() + 10, table.getTop() + 30, -120, false);
            f.prepare();
            require(table.header().getTop() == top && table.collection().scrollOffset() == 15,
                    "Vertical scrolling moved the pinned header");
            assertAligned(table);
            f.scene.getContext().requestFocus(table.collection().rowElement("r1"));
            f.prepare();
            require(table.collection().scrollOffset() == 0 && table.viewport().getScrollX() == 15,
                    "Focus did not reveal a row clipped by the pinned header or discarded the horizontal position");
            f.scene.getContext().requestLayout();
            try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
                HeadlessGuiFrames.layout(f.scene);
                boolean refused = false;
                try {
                    table.viewport().publishScrollPosition(0, 0);
                } catch (RuntimeException expected) {
                    refused = true;
                }
                require(refused, "Read-only painting gained authority to publish UI state");
            }
            f.scene.getContext().requestFocus(table);
            f.key(Keyboard.KEY_RIGHT);
            require(table.viewport().getScrollX() == 31, "Table keyboard navigation did not move horizontally");
            f.key(Keyboard.KEY_DOWN);
            require(table.viewport().getScrollX() == 31, "Row focus reveal discarded the user's horizontal position");
            f.reload();
            table = table(f);
            require(table.viewport().getScrollX() == 31,
                    "Horizontal table position did not survive compatible refresh");
            assertAligned(table);
            f.lua.load("columns={{key='left'}}").call();
            f.host.change();
            f.prepare();
            require(table.viewport().getScrollX() == 0 && table.header().getChildren().get(0).getWidth() == 100,
                    "Column shrink failed to clamp horizontal state or reflow the header in the accepted frame");
            GuiStyledView row = (GuiStyledView) table.collection()
                    .rowElement(table.collection().visibleRows().get(0).record.key);
            GuiStyledView cells = (GuiStyledView) row.getChildren().get(0);
            require(cells.getChildren().get(0).getWidth() == 100, "Column shrink retained old cell widths");
        }
    }

    private static void smallTable() {
        try (Fixture f = new Fixture()) {
            f.open("return{type='table',id='table',width=100,height=10,headerHeight=80,gap=5,"
                    + "columns={{key='a'}},records={{key='a'},{key='b'}}}");
            GuiTableView table = table(f);
            table.viewport().setScrollPosition(0, 1000);
            f.prepare();
            require(table.viewport().getScrollY() == 39 && table.collection().scrollOffset() == 39
                    && table.collection().getHeight() == 0 && table.collection().getTop() == table.getBottom(),
                    "A header taller than its viewport produced unreachable scrollbar metrics");
        }
    }

    private static void initialHorizontal() {
        try (Fixture f = new Fixture()) {
            f.open("return{type='table',id='table',width=100,height=60,scrollX=45,scrollbars=false,"
                    + "columns={{key='a',width=100},{key='b',width=100}},records={{key='row'}}}");
            GuiTableView table = table(f);
            require(table.viewport().getScrollX() == 45 && !table.viewport().getChildren().get(2).isVisible(),
                    "Initial horizontal position or hidden collection bars were ignored");
            table.viewport().setScrollPosition(0, 0);
            f.prepare();
            f.reload();
            require(table(f).viewport().getScrollX() == 0,
                    "A retained zero horizontal position was replaced by the declaration seed");
        }
    }

    private static void controlledHorizontal() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("horizontal", 0);
            f.open("return{type='table',id='table',width=100,height=60,columns={{key='a',width=100},{key='b',width=100}},"
                    + "records={{key='row'}},bind=function(v)return{scrollX=v.data:get('horizontal')}end,"
                    + "on={scroll=function(ctx,e)wanted=e.value.scrollX end}}");
            GuiTableView table = table(f);
            f.scene.getContext().requestFocus(table);
            f.key(Keyboard.KEY_RIGHT);
            require(table.viewport().getScrollX() == 0 && f.lua.get("wanted").toint() == 16,
                    "Controlled horizontal state moved without acknowledgement");
            f.host.extra.put("horizontal", 40);
            f.host.change();
            f.prepare();
            f.reload();
            require(table(f).viewport().getScrollX() == 40, "Controlled horizontal state was lost during refresh");
        }
    }

    private static void nestedHorizontalAdmission() {
        try (Fixture f = new Fixture()) {
            f.open("scrolls=0;return{type='list',id='outer',width=160,height=80,rowHeight=80,overscan=0,records={{key='row'}},"
                    + "rowState={padding={type='string',default=string.rep('x',16350)}},row=function()return{type='column',children={"
                    + "{type='button',height=20,text='Clear',on={activate=function(ctx)ctx.state:set('padding','')end}},"
                    + "{type='table',height=60,columns={{key='a',width=150},{key='b',width=150}},records={{key='r'}},"
                    + "on={scroll=function()scrolls=scrolls+1 end}}}}end}");
            GuiStyledView row = (GuiStyledView) f.view("outer").collection().rowElement("row");
            GuiTableView table = (GuiTableView) row.getChildren().get(1);
            f.scene.getContext().requestFocus(table);
            f.key(Keyboard.KEY_RIGHT);
            require(table.viewport().getScrollX() == 0 && f.lua.get("scrolls").toint() == 0,
                    "Nested horizontal movement ran Lua before containing-row admission");
            GuiElement clear = row.getChildren().get(0);
            f.scene.mousePressed(clear.getLeft() + 3, clear.getTop() + 3, 0);
            f.scene.mouseReleased(clear.getLeft() + 3, clear.getTop() + 3, 0);
            f.prepare();
            f.scene.getContext().requestFocus(table);
            f.key(Keyboard.KEY_RIGHT);
            require(table.viewport().getScrollX() == 16 && f.lua.get("scrolls").toint() == 1,
                    "Nested horizontal movement failed to recover after capacity was freed");
        }
    }

    private static void assertAligned(GuiTableView table) {
        GuiStyledView header = table.header();
        String key = table.collection().visibleRows().get(table.collection().scrollOffset() / 20).record.key;
        GuiStyledView row = (GuiStyledView) table.collection().rowElement(key);
        GuiStyledView cells = (GuiStyledView) row.getChildren().get(0);
        require(header.getChildren().get(1).getLeft() == cells.getChildren().get(1).getLeft(),
                "Horizontal header and cell geometry diverged");
    }

    private static GuiTableView table(Fixture f) {
        return (GuiTableView) f.view("table");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
