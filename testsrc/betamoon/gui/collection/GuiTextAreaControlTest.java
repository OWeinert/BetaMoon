package betamoon.gui.collection;

import betamoon.gui.collection.GuiCollectionControlTest.Fixture;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.widget.GuiTextAreaView;
import betamoon.gui.widget.GuiStyledView;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiFontFace;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.framework.GuiContext;
import org.lwjgl.input.Keyboard;
import java.util.List;
import betamoon.luaapi.utils.LuaCallbackScope;

/**
 * Public textarea tests use real Lua declarations, retained scenes and row
 * admission.
 */
public final class GuiTextAreaControlTest {
    public static void main(String[] args) {
        keysAndBlur();
        controlled();
        refresh();
        pointer();
        nestedAdmission();
        resizeAndHiddenBars();
        largeBoundedViewport();
        virtualRowDraft();
        System.out.println("Textarea controls passed: Enter/LF, Tab/blur commit, controlled acknowledgement, "
                + "draft refresh, stale input, scrolling and nested edit/commit refusal/recovery.");
    }

    private static void keysAndBlur() {
        try (Fixture f = fixture()) {
            f.open("changes=0;commits=0;return{type='column',width=160,children={"
                    + "{type='textarea',id='area',text='one\\r\\ntwo',height=60,on={"
                    + "change=function(ctx,e)changes=changes+1 end,commit=function()commits=commits+1 end}},"
                    + "{type='button',id='next',text='Next'}}}");
            GuiTextAreaView area = area(f);
            require(area.text().equals("one\ntwo"), "Authored text did not normalize LF");
            f.scene.getContext().requestFocus(area);
            f.key(Keyboard.KEY_RETURN);
            require(area.text().equals("one\ntwo\n") && f.lua.get("commits").toint() == 0
                    && f.scene.getFocusedElement() == area, "Enter committed or left the textarea");
            type(f, 'x');
            f.scene.keyTyped('\t', Keyboard.KEY_TAB, false);
            f.prepare();
            require(f.lua.get("commits").toint() == 1 && area.editor().committed().equals(area.text())
                    && f.scene.getFocusedElement() == f.tree.element(f.plan.elementId("next")),
                    "Tab did not leave and commit the field draft");
        }
    }

    private static void controlled() {
        try (Fixture f = fixture()) {
            f.host.extra.put("text", "base");
            f.open("commits=0;return{type='column',children={{type='textarea',id='area',height=40,"
                    + "bind=function(v)return{text=v.data:get('text')}end,on={change=function(ctx,e)wanted=e.value end,"
                    + "commit=function()commits=commits+1 end}},{type='button',text='Next'}}}");
            GuiTextAreaView area = area(f);
            f.scene.getContext().requestFocus(area);
            type(f, 'x');
            require(area.text().equals("basex") && area.editor().committed().equals("base"),
                    "Controlled draft was lost");
            f.host.extra.put("text", "basex");
            f.host.change();
            f.prepare();
            f.scene.keyTyped('\t', Keyboard.KEY_TAB, false);
            f.prepare();
            require(f.lua.get("commits").toint() == 1 && !area.editor().pending(),
                    "Blur waited for an already acknowledged draft");
            f.scene.getContext().requestFocus(area);
            type(f, 'y');
            f.host.extra.put("text", "external");
            f.host.change();
            f.prepare();
            require(area.text().equals("basexy") && area.editor().conflict(),
                    "External update discarded a dirty draft");
            f.key(Keyboard.KEY_ESCAPE);
            require(area.text().equals("external"), "Cancel did not restore the current source");
        }
    }

    private static void refresh() {
        try (Fixture f = fixture()) {
            f.open("reject=false;return{type='textarea',id='area',width=100,height=40,text='start',"
                    + "on={change=function()return not reject end}}");
            f.scene.getContext().requestFocus(area(f));
            f.key(Keyboard.KEY_RETURN);
            type(f, 'x');
            f.reload();
            GuiTextAreaView area = area(f);
            require(area.text().equals("start\nx"), "Refresh lost the multiline draft");
            f.scene.getContext().requestFocus(area);
            Runnable stale = area.prepareStateRestore(area.captureState());
            type(f, 'y');
            boolean refused = false;
            try {
                stale.run();
            } catch (IllegalStateException expected) {
                refused = true;
            }
            require(refused && area.text().equals("start\nxy"), "Stale restoration overwrote a newer draft");
            f.lua.load("reject=true").call();
            type(f, 'z');
            require(area.text().equals("start\nxy"), "Rejected edit published its candidate");
        }
    }

    private static void pointer() {
        try (Fixture f = fixture()) {
            f.host.extra.put("text", "first\nsecond\nthird\nfourth\nfifth");
            f.open("return{type='textarea',id='area',width=100,height=26,"
                    + "bind=function(v)return{text=v.data:get('text')}end}");
            GuiTextAreaView area = area(f);
            Rect viewport = area.viewport().getBounds();
            f.scene.mousePressed(viewport.getLeft() + 1, viewport.getTop() + 1, 0);
            f.scene.mouseDragged(viewport.getLeft() + 1, viewport.getBottom() + 20, true);
            f.scene.mouseReleased(viewport.getLeft() + 1, viewport.getBottom() + 20, 0);
            f.prepare();
            require(area.editor().selectionEnd() > area.editor().selectionStart() && area.viewport().getScrollY() > 0,
                    "Drag selection did not scroll the document");
            f.host.extra.put("text", "new content");
            f.host.change();
            f.scene.prepare();
            int caret = area.editor().caret();
            f.scene.mousePressed(viewport.getLeft() + 1, viewport.getTop() + 1, 0);
            f.scene.mouseReleased(viewport.getLeft() + 1, viewport.getTop() + 1, 0);
            require(area.editor().caret() == caret, "A stale frame positioned the new document's caret");
            HeadlessGuiFrames.layout(f.scene);
            f.scene.mousePressed(viewport.getLeft() + 1, viewport.getTop() + 1, 0);
            f.scene.mouseReleased(viewport.getLeft() + 1, viewport.getTop() + 1, 0);
            require(area.editor().caret() == 0, "Pointer input did not recover after a fresh frame");
        }
    }

    private static void nestedAdmission() {
        try (Fixture f = fixture()) {
            f.open("changes=0;commits=0;return{type='list',id='outer',width=160,height=80,rowHeight=80,overscan=0,"
                    + "records={{key='r'}},rowState={padding={type='string',default=string.rep('x',15500)}},"
                    + "row=function()return{type='column',children={{type='button',height=20,text='Clear',"
                    + "on={activate=function(ctx)ctx.state:set('padding','')end}},"
                    + "{type='textarea',height=60,on={change=function()changes=changes+1 end,"
                    + "commit=function()commits=commits+1 end}}}}end}");
            GuiStyledView row = (GuiStyledView) f.view("outer").collection().rowElement("r");
            require(row != null, "Initial textarea row exceeded its state allowance");
            GuiTextAreaView area = (GuiTextAreaView) row.getChildren().get(1);
            f.scene.getContext().requestFocus(area);
            for (int i = 0; i < 512; i++) {
                f.scene.keyTyped('\u754c', 0, false);
            }
            f.prepare();
            int accepted = f.lua.get("changes").toint();
            require(accepted > 0 && accepted < 512 && area.text().length() == accepted,
                    "Budget refusal called Lua or failed to preserve the accepted draft");
            Rect clear = row.getChildren().get(0).getBounds();
            f.scene.mousePressed(clear.getLeft() + 3, clear.getTop() + 3, 0);
            f.scene.mouseReleased(clear.getLeft() + 3, clear.getTop() + 3, 0);
            f.prepare();
            require(f.lua.get("commits").toint() == 0, "Over-budget commit called Lua before admission");
            f.scene.getContext().requestFocus(area);
            type(f, 'x');
            require(area.text().length() == accepted + 1 && f.lua.get("changes").toint() == accepted + 1,
                    "Textarea editing did not recover after row capacity was freed");
        }
    }

    private static void resizeAndHiddenBars() {
        try (Fixture f = fixture()) {
            f.host.extra.put("width", 140);
            f.host.extra.put("wrap", false);
            f.open("return{type='textarea',id='area',height=30,transition=false,scrollbars=false,text=string.rep('x',100),"
                    + "bind=function(v)return{width=v.data:get('width'),wrap=v.data:get('wrap')}end}");
            GuiTextAreaView area = area(f);
            f.scene.getContext().requestFocus(area);
            require(area.viewport().getScrollX() > 0, "Keyboard focus did not reveal the unwrapped caret");
            Object layout = area.frame().layout;
            for (int i = 0; i < 5; i++) {
                f.prepare();
            }
            require(area.frame().layout == layout && !area.viewport().getChildren().get(2).isVisible(),
                    "Idle layout was rebuilt or hidden bars became visible");
            f.host.extra.put("width", 80);
            f.host.extra.put("wrap", true);
            f.host.change();
            f.prepare();
            require(area.viewport().getScrollX() == 0 && area.viewport().getScrollY() > 0
                    && area.frame().visibleCaretBounds().getHeight() > 0,
                    "Wrapped resize lost the caret or kept the horizontal offset");
            require(area.text().length() == 100 && area.editor().caret() == 100, "Reflow changed text/caret offsets");
            f.scene.getContext().requestLayout();
            try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
                HeadlessGuiFrames.layout(f.scene);
            }
            f.host.extra.put("width", 0.25);
            f.host.change();
            f.prepare();
            require(area.getWidth() == 0, "Fractional explicit width became the default viewport");
        }
    }

    private static void largeBoundedViewport() {
        try (Fixture f = fixture()) {
            f.open("return{type='textarea',id='area',width=100,height=30,maximumLength=65536,"
                    + "text=string.rep('x\\n',32768)}");
            GuiTextAreaView area = area(f);
            f.scene.getContext().requestFocus(area);
            f.prepare();
            require(area.frame().layout.rows.size() == 32769
                    && area.viewport().getContent().getHeight() <= area.getHeight()
                    && area.viewport().getScrollY() > 200000,
                    "Long text expanded its retained child graph or failed to reveal the end");
            List<?> lines = (List<?>) area.frame().metrics(area.getBounds()).get("lines");
            require(lines.size() <= 4, "Prepared text metrics included offscreen rows");
        }
    }

    private static void virtualRowDraft() {
        try (Fixture f = fixture()) {
            f.open("commits=0;calls=0;records={};for i=1,100 do records[i]={key='r'..i}end;"
                    + "return{type='list',id='outer',width=160,height=40,rowHeight=40,overscan=0,records=records,"
                    + "row=function(row)calls=calls+1;return{type='textarea',text=row.key,"
                    + "on={commit=function()commits=commits+1 end}}end}");
            GuiCollectionView list = f.view("outer");
            GuiTextAreaView first = (GuiTextAreaView) list.collection().rowElement("r1");
            f.scene.getContext().requestFocus(first);
            type(f, 'x');
            f.scene.mouseScrolled(list.getLeft() + 10, list.getTop() + 10, -960, false);
            f.prepare();
            require(list.collection().rowElement("r1") == null, "Scrolling kept an offscreen textarea alive");
            f.scene.mouseScrolled(list.getLeft() + 10, list.getTop() + 10, 960, false);
            f.prepare();
            GuiTextAreaView restored = (GuiTextAreaView) list.collection().rowElement("r1");
            require(restored != first && restored.text().equals("r1x") && restored.editor().caret() == 3,
                    "Virtual row retirement lost the detached textarea draft/selection");
            int calls = f.lua.get("calls").toint();
            for (int i = 0; i < 5; i++) {
                f.prepare();
            }
            require(f.lua.get("calls").toint() == calls, "Idle textarea rows reran Lua factories");
        }
    }

    private static Fixture fixture() {
        Fixture f = new Fixture();
        GuiFontFace font = new GuiFontFace() {
            public int width(GuiContext context, String text) {
                return text.codePointCount(0, text.length()) * 6;
            }

            public int lineHeight(GuiContext context) {
                return 9;
            }

            public void draw(GuiContext context, String text, int x, int y, int color, boolean shadow) {
            }
        };
        f.theme = GuiStyleSheet.MINIMAL.withInline(GuiControlStyle.of("font", font));
        return f;
    }

    private static GuiTextAreaView area(Fixture f) {
        return (GuiTextAreaView) f.tree.element(f.plan.elementId("area"));
    }

    private static void type(Fixture f, char character) {
        f.scene.keyTyped(character, 0, false);
        f.prepare();
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
