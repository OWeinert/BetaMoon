package betamoon.gui.collection;

import betamoon.gui.collection.GuiCollectionControlTest.Fixture;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.widget.GuiScrollControl;
import betamoon.gui.widget.GuiStyledView;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.lwjgl.input.Keyboard;

/**
 * Actual authored scroll trees share native viewport input, admitted local
 * state and retained child editors.
 */
public final class GuiScrollControlTest {
    public static void main(String[] arguments) {
        measuredContent();
        controlledOffsets();
        reactiveGrid();
        containingRowAdmission();
        measuredOverflow();
        clippedCollections();
        explicitSmallViewport();
        System.out.println("Scroll controls passed: authored child mounting, measured axes, keyboard/focus, "
                + "controlled proposals, retained positions/editors, prospective grid rejection and nested state refusal/recovery.");
    }

    private static void measuredContent() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;return{type='scroll',id='scroll',width=100,height=60,direction='both',"
                    + "contentWidth=240,contentHeight=180,on={change=function(ctx,e)changes=changes+1;lastScroll=e.value end},"
                    + "children={{type='column',id='content',width=240,height=180,children={"
                    + "{type='textbox',id='edit',text='draft',height=20},"
                    + "{type='spacer',height=120},{type='button',id='last',text='Last',height=20}}}}}");
            GuiScrollControl control = scroll(f, "scroll");
            GuiStyledView editor = (GuiStyledView) f.tree.element(f.plan.elementId("edit"));
            require(f.tree.element(f.plan.elementId("content")).getParent() == control.childContainer(),
                    "Authored children escaped the actual retained scroll content mount");
            f.scene.getContext().requestFocus(control);
            f.key(Keyboard.KEY_DOWN);
            require(control.viewport().getScrollY() == 16 && f.lua.get("changes").toint() == 1,
                    "Authored viewport keyboard input did not propose and apply its offset");
            f.scene.getContext().requestFocus(editor);
            f.prepare();
            f.scene.keyTyped('x', Keyboard.KEY_X, false);
            f.prepare();
            require(editor.text().equals("draftx"), "Scroll composition lost child editor behavior");
            GuiElement last = f.tree.element(f.plan.elementId("last"));
            f.scene.getContext().requestFocus(last);
            f.prepare();
            require(control.viewport().getScrollY() == 100, "Child focus did not reveal measured scroll content");
            f.scene.getContext().requestFocus(control);
            f.key(Keyboard.KEY_RIGHT);
            require(control.viewport().getScrollX() == 16,
                    "Horizontal keyboard input did not reach the shared viewport");
            require(((Number) control.templateValues(false).get("scrollX")).intValue() == 16
                    && ((Number) control.templateValues(true).get("scrollY")).intValue() == 100,
                    "Viewport templates did not receive accepted unbound offsets");
            f.authored.set("scrollbars", LuaValue.FALSE);
            f.reload();
            control = scroll(f, "scroll");
            require(control.viewport().getScrollX() == 16 && control.viewport().getScrollY() == 100
                    && ((GuiStyledView) f.tree.element(f.plan.elementId("edit"))).text().equals("draftx"),
                    "Compatible scroll refresh lost offsets or the retained child editor");
            require(!control.viewport().getChildren().get(1).isVisible()
                    && !control.viewport().getChildren().get(2).isVisible(),
                    "Hiding default bars left their tracks or input handles visible");
            f.scene.getContext().requestFocus(control);
            f.key(Keyboard.KEY_RIGHT);
            require(control.viewport().getScrollX() == 32, "Hiding bars disabled viewport keyboard navigation");
            Map<String, Object> state = control.captureState();
            Runnable stale = control.prepareStateRestore(state);
            control.viewport().setScrollPosition(0, 0);
            try {
                stale.run();
                throw new AssertionError("A stale restore discarded newer scroll intent");
            } catch (IllegalStateException expected) {
            }
            require(control.captureState().containsKey("scrollY"),
                    "An interacted zero offset was lost from dormant state");
        }
    }

    private static void controlledOffsets() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("position", 0);
            f.open("return{type='scroll',id='scroll',width=100,height=40,"
                    + "bind=function(v)return{scrollY=v.data:get('position')}end,"
                    + "on={change=function(ctx,e)wanted=e.value.scrollY end},"
                    + "children={{type='column',height=200,children={{type='text',text='Content'}}}}}");
            GuiScrollControl control = scroll(f, "scroll");
            f.scene.getContext().requestFocus(control);
            f.key(Keyboard.KEY_NEXT);
            require(control.viewport().getScrollY() == 0 && f.lua.get("wanted").toint() == 40,
                    "Controlled offset moved without source acknowledgement");
            f.host.extra.put("position", 60);
            f.host.change();
            f.prepare();
            require(control.viewport().getScrollY() == 60, "Acknowledged offset was not applied");
            f.reload();
            require(scroll(f, "scroll").viewport().getScrollY() == 60,
                    "Retained local offsets overwrote the controlled position during refresh");
        }
    }

    private static void reactiveGrid() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("cell", 50);
            f.host.extra.put("extent", 0);
            f.open("return{type='scroll',id='scroll',width=105,height=45,"
                    + "bind=function(v)return{contentHeight=v.data:get('extent')}end,children={{type='grid',columns=0,"
                    + "bind=function(v)return{minimumCellWidth=v.data:get('cell')}end,children={"
                    + "{type='button',id='a',text='A',height=20},{type='button',id='b',text='B',height=20},"
                    + "{type='button',id='c',text='C',height=20},{type='button',id='d',text='D',height=20}}}}}");
            GuiElement a = f.tree.element(f.plan.elementId("a"));
            GuiElement b = f.tree.element(f.plan.elementId("b"));
            require(a.getTop() == b.getTop(), "Responsive child grid did not measure at viewport width");
            f.host.extra.put("cell", 100);
            f.host.change();
            f.prepare();
            require(b.getTop() - a.getTop() == 20, "Reactive child measurement did not reflow inside the viewport");
            f.scene.getContext().requestFocus(scroll(f, "scroll"));
            f.key(Keyboard.KEY_END);
            require(scroll(f, "scroll").viewport().getScrollY() == 35,
                    "Measured grid height did not define the scroll limit");
            int oldHeight = a.getHeight();
            f.host.extra.put("cell", 0);
            f.host.change();
            f.prepare();
            require(a.getHeight() == oldHeight && b.getTop() - a.getTop() == 20,
                    "An invalid responsive layout replaced accepted scrolled geometry");
        }
    }

    private static void containingRowAdmission() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;return{type='list',id='outer',width=160,height=80,rowHeight=80,overscan=0,records={{key='row'}},"
                    + "rowState={padding={type='string',default=string.rep('x',16330)}},row=function()return{type='column',children={"
                    + "{type='button',height=20,text='Clear',on={activate=function(ctx)ctx.state:set('padding','')end}},"
                    + "{type='scroll',id='scroll',height=60,on={change=function()changes=changes+1 end},"
                    + "children={{type='column',height=200,children={{type='text',text='Content'}}}}}}}end}");
            GuiStyledView row = (GuiStyledView) f.view("outer").collection().rowElement("row");
            GuiScrollControl control = (GuiScrollControl) row.getChildren().get(1);
            f.scene.getContext().requestFocus(control);
            f.key(Keyboard.KEY_NEXT);
            require(control.viewport().getScrollY() == 0 && f.lua.get("changes").toint() == 0,
                    "Nested scroll crossed its containing-row state limit before calling Lua");
            GuiElement clear = row.getChildren().get(0);
            f.scene.mousePressed(clear.getLeft() + 3, clear.getTop() + 3, 0);
            f.scene.mouseReleased(clear.getLeft() + 3, clear.getTop() + 3, 0);
            f.prepare();
            f.scene.getContext().requestFocus(control);
            f.key(Keyboard.KEY_NEXT);
            require(control.viewport().getScrollY() == 60 && f.lua.get("changes").toint() == 1,
                    "Nested scroll did not recover after the containing row freed state capacity");
        }
    }

    private static void measuredOverflow() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("gap", 0);
            f.open("local children={};for i=1,18 do children[i]={type='button',height=900,text='Row'}end;"
                    + "return{type='scroll',id='scroll',width=100,height=60,children={{type='column',id='content',"
                    + "bind=function(v)return{gap=v.data:get('gap')}end,children=children}}}");
            GuiScrollControl control = scroll(f, "scroll");
            f.scene.getContext().requestFocus(control);
            f.key(Keyboard.KEY_END);
            require(control.viewport().getScrollY() == 16140, "Bounded measured canvas had the wrong extent");
            GuiStyledView content = (GuiStyledView) f.tree.element(f.plan.elementId("content"));
            int before = content.getChildren().get(1).getTop() - content.getChildren().get(0).getTop();
            f.host.extra.put("gap", 20);
            f.host.change();
            f.prepare();
            require(control.viewport().getScrollY() == 16140
                    && content.getChildren().get(1).getTop() - content.getChildren().get(0).getTop() == before,
                    "Oversized reactive scroll content was silently truncated or replaced accepted geometry");
        }
    }

    private static void clippedCollections() {
        try (Fixture f = new Fixture()) {
            f.open("topCalls=0;bottomCalls=0;return{type='scroll',id='scroll',width=100,height=60,children={{"
                    + "type='column',height=300,children={"
                    + "{type='list',height=40,overscan=0,records={{key='top'}},row=function()topCalls=topCalls+1;return{type='text',text='Top'}end},"
                    + "{type='spacer',height=200},"
                    + "{type='list',height=40,overscan=0,records={{key='bottom'}},row=function()bottomCalls=bottomCalls+1;return{type='text',text='Bottom'}end}}}}}");
            GuiScrollControl control = scroll(f, "scroll");
            require(f.lua.get("topCalls").toint() == 1 && f.lua.get("bottomCalls").toint() == 0
                    && f.budget.liveRows() == 1,
                    "A fully clipped collection realized rows before entering its ancestor viewport");
            control.viewport().setScrollPosition(0, 230);
            f.prepare();
            require(f.lua.get("topCalls").toint() == 1 && f.lua.get("bottomCalls").toint() == 1
                    && f.budget.liveRows() == 1,
                    "Viewport scrolling retained fully clipped rows or failed to realize newly visible rows");
        }
    }

    private static void explicitSmallViewport() {
        try (Fixture f = new Fixture()) {
            f.open("return{type='scroll',id='scroll',width=8.6,height=5.4,contentHeight=100,"
                    + "children={{type='text',text='Content'}}}");
            GuiScrollControl control = scroll(f, "scroll");
            require(control.getWidth() == 9 && control.getHeight() == 5,
                    "Explicit fractional viewport dimensions were replaced by intrinsic minimums");
        }
    }

    private static GuiScrollControl scroll(Fixture f, String id) {
        return (GuiScrollControl) f.tree.element(f.plan.elementId(id));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
