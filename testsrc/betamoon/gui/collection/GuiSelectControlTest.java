package betamoon.gui.collection;

import betamoon.gui.collection.GuiCollectionControlTest.Fixture;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiInputLifetime;
import betamoon.gui.style.GuiFontFace;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.control.GuiStatusGeometry;
import betamoon.gui.widget.GuiStyledView;
import betamoon.gui.widget.GuiNumberBoxView;
import betamoon.gui.widget.GuiTextAreaView;
import betamoon.gui.widget.GuiRadioView;
import betamoon.gui.widget.GuiSelectView;
import org.lwjgl.input.Keyboard;

/**
 * Real authored controls, portal dismissal and shared focus/selection
 * contracts.
 */
public final class GuiSelectControlTest {
    public static void main(String[] args) {
        dropdownKeys();
        pointerAndContext();
        controlledAndHidden();
        revokedOwner();
        tabs();
        statusGeometry();
        settingsAndList(160, 120);
        settingsAndList(640, 480);
        System.out.println("Selection controls passed: keyed dropdown/context menu, activation-only changes, "
                + "disabled navigation, outside/Escape/Tab dismissal, opener focus and tabs.");
    }

    private static void dropdownKeys() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;return{type='column',width=160,children={{type='dropdown',id='select',selectedKey='a',"
                    + "options={{key='a',label='Same'},{key='disabled',enabled=false},{key='c',label='Same'}},"
                    + "on={change=function(ctx,e)changes=changes+1;wanted=e.value end}},{type='button',id='next',text='Next'}}}");
            GuiSelectView select = (GuiSelectView) f.tree.element(f.plan.elementId("select"));
            f.scene.getContext().requestFocus(select);
            f.key(Keyboard.KEY_RETURN);
            GuiElement menu = f.scene.getFocusedElement();
            require(menu instanceof GuiRadioView && menu != select, "Enter did not open a scene-owned menu");
            f.key(Keyboard.KEY_DOWN);
            require(f.lua.get("changes").toint() == 0 && select.selectedKey().equals("a"),
                    "Highlighting committed selection");
            f.key(Keyboard.KEY_RETURN);
            require(select.selectedKey().equals("c") && f.lua.get("wanted").tojstring().equals("c")
                    && f.scene.getFocusedElement() == select, "Enter did not commit the keyed choice/restore opener");
            f.key(Keyboard.KEY_RETURN);
            f.key(Keyboard.KEY_ESCAPE);
            require(f.scene.getFocusedElement() == select && f.lua.get("changes").toint() == 1,
                    "Escape changed selection");
            f.key(Keyboard.KEY_RETURN);
            f.key(Keyboard.KEY_TAB);
            require(f.scene.getFocusedElement() == f.tree.element(f.plan.elementId("next")),
                    "Tab failed to dismiss and leave menu");
            f.reload();
            select = (GuiSelectView) f.tree.element(f.plan.elementId("select"));
            require(select.selectedKey().equals("c"), "Refresh lost local dropdown selection");
        }
    }

    private static void pointerAndContext() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;return{type='contextmenu',id='select',text='Actions',width=120,height=30,"
                    + "options={{key='a'},{key='b'}},on={change=function(ctx,e)changes=changes+1 end}}");
            GuiSelectView select = (GuiSelectView) f.tree.element(f.plan.elementId("select"));
            int x = select.getLeft() + 10;
            int y = select.getTop() + 10;
            f.scene.mousePressed(x, y, 1);
            f.scene.mouseReleased(x, y, 1);
            f.prepare();
            GuiRadioView menu = (GuiRadioView) f.scene.getFocusedElement();
            require(menu != select && menu.getLeft() >= 0 && menu.getRight() <= 200,
                    "Context menu did not fit its viewport");
            int optionX = menu.optionBounds(0).getLeft() + 4;
            int optionY = menu.optionBounds(0).getTop() + 4;
            f.scene.mousePressed(optionX, optionY, 0);
            f.scene.mouseReleased(optionX, optionY, 0);
            f.prepare();
            require(select.selectedKey().equals("a") && f.lua.get("changes").toint() == 1,
                    "Clicking the current highlight did not activate selection");
            f.scene.mousePressed(x, y, 1);
            f.scene.mouseReleased(x, y, 1);
            f.prepare();
            require(f.scene.mousePressed(1, 1, 0) && f.scene.mouseReleased(1, 1, 0),
                    "Dismissal gesture leaked to background");
            f.prepare();
            require(f.scene.getFocusedElement() == select && f.lua.get("changes").toint() == 1,
                    "Outside dismissal changed selection or lost opener: " + f.scene.getFocusedElement() + " changes="
                            + f.lua.get("changes"));
        }
    }

    private static void controlledAndHidden() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("key", "a");
            f.host.extra.put("visible", true);
            f.open("wanted='';return{type='column',children={{type='dropdown',id='select',options={{key='a'},{key='b'}},"
                    + "bind=function(v)return{selectedKey=v.data:get('key'),visible=v.data:get('visible')}end,"
                    + "on={change=function(ctx,e)wanted=e.value end}},{type='button',id='next',text='Next'}}}");
            GuiSelectView select = (GuiSelectView) f.tree.element(f.plan.elementId("select"));
            f.scene.getContext().requestFocus(select);
            f.key(Keyboard.KEY_RETURN);
            f.key(Keyboard.KEY_END);
            f.key(Keyboard.KEY_RETURN);
            require(select.selectedKey().equals("a") && f.lua.get("wanted").tojstring().equals("b"),
                    "Controlled dropdown bypassed source acknowledgement");
            f.host.extra.put("key", "b");
            f.host.change();
            f.prepare();
            require(select.selectedKey().equals("b"), "Controlled dropdown acknowledgement failed");
            f.key(Keyboard.KEY_RETURN);
            f.host.extra.put("visible", false);
            f.host.change();
            f.prepare();
            require(f.scene.getFocusedElement() == f.tree.element(f.plan.elementId("next")),
                    "A hidden opener retained its modal menu/focus");
        }
    }

    private static void revokedOwner() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;return{type='dropdown',id='select',selectedKey='a',options={{key='a'},{key='b'}},"
                    + "on={change=function()changes=changes+1 end}}");
            GuiSelectView select = (GuiSelectView) f.tree.element(f.plan.elementId("select"));
            GuiInputLifetime lifetime = new GuiInputLifetime();
            lifetime.activate();
            select.inputLifetime(lifetime);
            f.scene.getContext().requestFocus(select);
            f.key(Keyboard.KEY_RETURN);
            f.key(Keyboard.KEY_END);
            lifetime.close();
            f.scene.keyTyped('\r', Keyboard.KEY_RETURN, false);
            require(f.lua.get("changes").toint() == 0 && select.selectedKey().equals("a"),
                    "A revoked opener retained portal input before the next preparation boundary");
            f.prepare();
        }
    }

    private static void tabs() {
        try (Fixture f = new Fixture()) {
            f.open("wanted='';return{type='tabs',id='tabs',selectedKey='first',options={{key='first'},"
                    + "{key='off',enabled=false},{key='last'}},on={change=function(ctx,e)wanted=e.value end}}");
            GuiRadioView tabs = (GuiRadioView) f.tree.element(f.plan.elementId("tabs"));
            f.scene.getContext().requestFocus(tabs);
            f.key(Keyboard.KEY_RIGHT);
            require(tabs.selectedKey().equals("last") && f.lua.get("wanted").tojstring().equals("last"),
                    "Tabs did not use keyed disabled-option keyboard navigation");
            f.key(Keyboard.KEY_HOME);
            require(tabs.selectedKey().equals("first"), "Tabs Home did not select first enabled option");
        }
    }

    private static void statusGeometry() {
        require(GuiStatusGeometry.segment(0.625, 4, 8) == 1 && GuiStatusGeometry.segment(0.625, 5, 8) == 0
                && GuiStatusGeometry.segment(0.1875, 1, 8) == 0.5,
                "Segmented progress lost its partial/final segment semantics");
        require(GuiStatusGeometry.phase(0) == 0 && GuiStatusGeometry.phase(600) == 0.5
                && GuiStatusGeometry.phase(1200) == 0, "Indeterminate phase is not bounded/repeating");
    }

    private static void settingsAndList(int width, int height) {
        try (Fixture f = new Fixture()) {
            f.theme = GuiStyleSheet.MINIMAL.withInline(GuiControlStyle.of("font", new GuiFontFace() {
                public int width(GuiContext context, String text) {
                    return text.length() * 6;
                }

                public int lineHeight(GuiContext context) {
                    return 9;
                }

                public void draw(GuiContext context, String text, int x, int y, int color, boolean shadow) {
                }
            }));
            f.host.extra.put("width", width - 16);
            f.host.extra.put("height", height - 16);
            f.open("calls=0;commits=0;records={};for i=1,4096 do records[i]={key='r'..i,label='Sample '..i}end;"
                    + "return{type='scroll',id='shell',transition=false,bind=function(v)return{width=v.data:get('width'),height=v.data:get('height')}end,"
                    + "children={{type='column',width=128,children={{type='tabs',id='tabs',selectedKey='settings',options={{key='settings'},{key='list'}}},"
                    + "{type='dropdown',id='select',options={{key='low'},{key='high'}}},{type='numberbox',id='amount'},"
                    + "{type='textarea',id='notes',height=40,on={commit=function()commits=commits+1 end}},"
                    + "{type='list',id='entries',height=60,rowHeight=20,overscan=1,rowState={visited={type='boolean',default=false}},"
                    + "bind=function(v)local revision=v.data:get('version');return{records=records}end,row=function(row)calls=calls+1;"
                    + "return{type='button',bind=function(v)return{text=row.label..tostring(v.state:get('visited'))}end,"
                    + "on={activate=function(ctx)ctx.state:set('visited',true)end}}end}}}}}");
            f.scene.updateEnvironment(null, null, width, height, width, height);
            f.prepare();
            GuiRadioView tabs = (GuiRadioView) f.tree.element(f.plan.elementId("tabs"));
            f.scene.getContext().requestFocus(tabs);
            f.key(Keyboard.KEY_RIGHT);
            require(tabs.selectedKey().equals("list"), "Settings tabs failed at " + width);
            f.key(Keyboard.KEY_TAB);
            GuiSelectView select = (GuiSelectView) f.tree.element(f.plan.elementId("select"));
            require(f.scene.getFocusedElement() == select, "Settings Tab order failed at " + width);
            f.key(Keyboard.KEY_RETURN);
            f.key(Keyboard.KEY_END);
            f.key(Keyboard.KEY_RETURN);
            require(select.selectedKey().equals("high"), "Settings dropdown failed at " + width);
            f.key(Keyboard.KEY_TAB);
            GuiNumberBoxView amount = (GuiNumberBoxView) f.tree.element(f.plan.elementId("amount"));
            require(f.scene.getFocusedElement() == amount, "Numeric settings focus failed at " + width);
            f.scene.keyTyped('\0', Keyboard.KEY_A, false, true);
            for (char value : ".125".toCharArray()) {
                f.scene.keyTyped(value, 0, false);
            }
            f.key(Keyboard.KEY_RETURN);
            require(amount.text().equals("0.125"), "Numeric settings commit failed at " + width);
            f.key(Keyboard.KEY_TAB);
            GuiTextAreaView notes = (GuiTextAreaView) f.tree.element(f.plan.elementId("notes"));
            require(f.scene.getFocusedElement() == notes, "Multiline settings focus failed at " + width);
            f.key(Keyboard.KEY_RETURN);
            f.scene.keyTyped('x', 0, false);
            f.key(Keyboard.KEY_TAB);
            require(notes.text().equals("\nx") && f.lua.get("commits").toint() == 1,
                    "Multiline settings blur failed at " + width);
            GuiCollectionView list = f.view("entries");
            f.scene.getContext().requestFocus(list);
            f.key(Keyboard.KEY_END);
            require(list.collection().selection().equals("r4096") && list.collection().realizedRows() <= 5,
                    "Large list keyboard navigation/realization failed at " + width);
            require(f.scene.getFocusedElement() == list.collection().rowElement("r4096"),
                    "End did not focus the realized custom row at " + width + ": " + f.scene.getFocusedElement());
            f.key(Keyboard.KEY_RETURN);
            f.scene.keyReleased(Keyboard.KEY_RETURN, false);
            f.prepare();
            require(((GuiStyledView) list.collection().rowElement("r4096")).text().endsWith("true"),
                    "Custom row activation did not update state at " + width);
            f.lua.load("table.insert(records,1,table.remove(records,4096))").call();
            f.host.change();
            f.prepare();
            f.scene.getContext().requestFocus(list);
            f.key(Keyboard.KEY_HOME);
            GuiStyledView row = (GuiStyledView) list.collection().rowElement("r4096");
            require(row != null && row.text().endsWith("true"), "Reordering lost keyed list state at " + width + " row="
                    + (row == null ? null : row.text()) + " state=" + list.collection().captureState().get("rows"));
            int calls = f.lua.get("calls").toint();
            for (int frame = 0; frame < 8; frame++) {
                f.prepare();
            }
            require(calls < 40 && f.lua.get("calls").toint() == calls,
                    "Idle/offscreen list work polled Lua at " + width);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
