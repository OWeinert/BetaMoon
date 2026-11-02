package betamoon.gui.collection;

import betamoon.gui.collection.GuiCollectionControlTest.Fixture;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.widget.GuiRadioView;
import betamoon.gui.widget.GuiScrollControl;
import betamoon.gui.widget.GuiStyledView;
import org.lwjgl.input.Keyboard;

/**
 * Radio selection exercises real parsed controls, displayed frames and nested
 * state admission.
 */
public final class GuiRadioControlTest {
    public static void main(String[] args) {
        keyboardAndKeyedRefresh();
        controlledSelection();
        displayedInput();
        nestedAdmission();
        focusReveal();
        acknowledgedFocusReveal();
        horizontalGeometry();
        emptyAndFractional();
        System.out.println("Radio controls passed: keyed/disabled options, keyboard wrapping, retained/controlled "
                + "selection, displayed-frame input, nested refusal/recovery and selected-region focus reveal.");
    }

    private static void keyboardAndKeyedRefresh() {
        try (Fixture f = new Fixture()) {
            f.open("options={{key='a',label='Same'},{key='b',enabled=false},{key='c',label='Same'}};changes=0;"
                    + "return{type='radio',id='radio',width=150,selectedKey='a',"
                    + "bind=function(v)local version=v.data:get('version');return{options=options}end,"
                    + "on={change=function(ctx,e)changes=changes+1;wanted=e.value end}}");
            GuiRadioView radio = radio(f);
            f.scene.getContext().requestFocus(radio);
            f.key(Keyboard.KEY_DOWN);
            require(radio.selectedKey().equals("c"), "Navigation did not skip a disabled option");
            f.key(Keyboard.KEY_DOWN);
            require(radio.selectedKey().equals("a"), "Arrow navigation did not wrap");
            f.key(Keyboard.KEY_END);
            require(radio.selectedKey().equals("c"), "End did not select the last enabled option");
            f.key(Keyboard.KEY_HOME);
            int calls = f.lua.get("changes").toint();
            click(f, radio.optionBounds(1));
            require(f.lua.get("changes").toint() == calls, "Disabled pointer input changed selection");
            Rect first = radio.optionBounds(0);
            Rect last = radio.optionBounds(2);
            f.scene.mousePressed(first.getLeft() + 3, first.getTop() + 3, 0);
            f.scene.mouseReleased(last.getLeft() + 3, last.getTop() + 3, 0);
            require(radio.selectedKey().equals("a"), "A crossed gesture selected another option");
            f.scene.mousePressed(last.getLeft() + 3, last.getTop() + 3, 0);
            f.lua.load("options[1],options[3]=options[3],options[1]").call();
            f.host.change();
            f.prepare();
            f.scene.mouseReleased(last.getLeft() + 3, last.getTop() + 3, 0);
            f.reload();
            radio = radio(f);
            require(radio.selectedKey().equals("a") && f.lua.get("changes").toint() == calls,
                    "Reorder/refresh lost keyed selection or retained a stale press");
            f.lua.load("options={{key='c'},{key='b',enabled=false}}").call();
            f.host.change();
            f.prepare();
            require(radio.selectedKey().isEmpty(), "Removing the selected key retained a stale value");
            f.scene.getContext().requestFocus(radio);
            f.key(Keyboard.KEY_DOWN);
            require(radio.selectedKey().equals("c"), "Cleared selection could not recover");
            f.lua.load("options={{key='c'},{key='c'}}").call();
            f.host.change();
            f.prepare();
            require(radio.selectedKey().equals("c") && radio.options().options.size() == 2,
                    "Duplicate option keys replaced the accepted options");
        }
    }

    private static void controlledSelection() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("choice", "a");
            f.open("return{type='radio',id='radio',width=150,options={{key='a'},{key='b'}},"
                    + "bind=function(v)return{selectedKey=v.data:get('choice')}end,"
                    + "on={change=function(ctx,e)wanted=e.value end}}");
            GuiRadioView radio = radio(f);
            f.scene.getContext().requestFocus(radio);
            f.key(Keyboard.KEY_DOWN);
            require(radio.selectedKey().equals("a") && f.lua.get("wanted").tojstring().equals("b")
                    && radio.captureState().isEmpty(), "Controlled radio moved without acknowledgement");
            f.host.extra.put("choice", "b");
            f.host.change();
            f.prepare();
            f.reload();
            require(radio(f).selectedKey().equals("b") && radio(f).templateValues(false).get("selectedKey").equals("b"),
                    "Controlled refresh or template selection was stale");
        }
        try (Fixture f = new Fixture()) {
            f.open("return{type='radio',id='radio',options={{key='a'},{key='b'}},selectedKey='a',"
                    + "on={change=function()return false end}}");
            f.scene.getContext().requestFocus(radio(f));
            f.key(Keyboard.KEY_DOWN);
            require(radio(f).selectedKey().equals("a"), "Rejected local selection was published");
        }
    }

    private static void displayedInput() {
        try (Fixture f = new Fixture()) {
            f.open("options={{key='a'},{key='b'}};changes=0;return{type='radio',id='radio',width=140,"
                    + "bind=function(v)local version=v.data:get('version');return{options=options}end,"
                    + "on={change=function()changes=changes+1 end}}");
            GuiRadioView radio = radio(f);
            Rect before = radio.optionBounds(0);
            f.lua.load("options[1],options[2]=options[2],options[1]").call();
            f.host.change();
            f.scene.prepare();
            f.scene.mousePressed(before.getLeft() + 3, before.getTop() + 3, 0);
            f.scene.mouseReleased(before.getLeft() + 3, before.getTop() + 3, 0);
            require(f.lua.get("changes").toint() == 0, "A press selected an option not yet displayed");
            HeadlessGuiFrames.layout(f.scene);
            click(f, radio.optionBounds(0));
            require(radio.selectedKey().equals("b"), "Displayed input did not recover after the new frame");
        }
    }

    private static void nestedAdmission() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;return{type='list',id='outer',width=160,height=80,rowHeight=80,overscan=0,records={{key='r'}},"
                    + "rowState={padding={type='string',default=string.rep('x',16300)}},row=function()return{type='column',children={"
                    + "{type='button',height=20,text='Clear',on={activate=function(ctx)ctx.state:set('padding','')end}},"
                    + "{type='radio',height=60,options={{key='a'},{key=string.rep('k',255)}},selectedKey='a',"
                    + "on={change=function()changes=changes+1 end}}}}end}");
            GuiStyledView row = (GuiStyledView) f.view("outer").collection().rowElement("r");
            require(row != null, "Nested radio fixture exceeded its initial budget");
            GuiRadioView radio = (GuiRadioView) row.getChildren().get(1);
            f.scene.getContext().requestFocus(radio);
            f.key(Keyboard.KEY_END);
            require(radio.selectedKey().equals("a") && f.lua.get("changes").toint() == 0,
                    "An over-budget keyed selection ran Lua or published local state");
            GuiElement clear = row.getChildren().get(0);
            click(f, clear.getBounds());
            f.scene.getContext().requestFocus(radio);
            f.key(Keyboard.KEY_END);
            require(radio.selectedKey().length() == 255 && f.lua.get("changes").toint() == 1,
                    "Nested radio selection did not recover after space was freed");
        }
    }

    private static void focusReveal() {
        try (Fixture f = new Fixture()) {
            f.open("options={};for i=1,10 do options[i]={key='r'..i}end;return{type='scroll',id='scroll',width=100,height=40,"
                    + "children={{type='radio',id='radio',width=100,options=options,selectedKey='r1'}}}");
            GuiRadioView radio = radio(f);
            GuiScrollControl scroll = (GuiScrollControl) f.tree.element(f.plan.elementId("scroll"));
            f.scene.getContext().requestFocus(radio);
            f.key(Keyboard.KEY_END);
            require(radio.selectedKey().equals("r10") && scroll.viewport().getScrollY() == 160
                    && radio.optionBounds(9).getBottom() <= scroll.viewport().getBottom(),
                    "Keyboard selection did not reveal its option through the shared viewport");
            f.key(Keyboard.KEY_HOME);
            require(scroll.viewport().getScrollY() == 0, "Home did not reveal the first radio option");
        }
    }

    private static void acknowledgedFocusReveal() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("choice", "r1");
            f.open("options={};for i=1,10 do options[i]={key='r'..i}end;return{type='scroll',id='scroll',width=100,height=40,"
                    + "children={{type='radio',id='radio',width=100,options=options,"
                    + "bind=function(v)return{selectedKey=v.data:get('choice')}end}}}");
            GuiRadioView radio = radio(f);
            GuiScrollControl scroll = (GuiScrollControl) f.tree.element(f.plan.elementId("scroll"));
            f.scene.getContext().requestFocus(radio);
            f.key(Keyboard.KEY_END);
            require(scroll.viewport().getScrollY() == 0,
                    "A pending controlled proposal scrolled before acknowledgement");
            f.host.extra.put("choice", "r10");
            f.host.change();
            f.prepare();
            require(scroll.viewport().getScrollY() == 160, "Acknowledged selection did not reveal its option");
        }
    }

    private static void horizontalGeometry() {
        try (Fixture f = new Fixture()) {
            f.open("return{type='radio',id='radio',width=150,orientation='horizontal',gap=2,"
                    + "options={{key='a'},{key='b'},{key='c'}}}");
            GuiRadioView radio = radio(f);
            require(radio.optionBounds(2).getRight() == radio.getRight()
                    && radio.optionBounds(0).getRight() + 2 == radio.optionBounds(1).getLeft(),
                    "Horizontal option cells disagreed about boundaries or gap");
            click(f, radio.optionBounds(2));
            require(radio.selectedKey().equals("c") && radio.templateOptions().get(2).get("focusTarget").equals(true),
                    "Horizontal hit regions and template bounds disagreed");
        }
    }

    private static void emptyAndFractional() {
        try (Fixture f = new Fixture()) {
            f.open("return{type='radio',id='radio',options={}}");
            require(!radio(f).isFocusable(), "An empty group retained a focus stop");
        }
        try (Fixture f = new Fixture()) {
            f.open("return{type='radio',id='radio',width=0.25,height=0.25,options={{key='a'}}}");
            require(radio(f).getWidth() == 0 && radio(f).getHeight() == 0,
                    "Fractional explicit bounds became intrinsic dimensions");
        }
    }

    private static GuiRadioView radio(Fixture f) {
        return (GuiRadioView) f.tree.element(f.plan.elementId("radio"));
    }

    private static void click(Fixture f, Rect bounds) {
        f.scene.mousePressed(bounds.getLeft() + 3, bounds.getTop() + 3, 0);
        f.scene.mouseReleased(bounds.getLeft() + 3, bounds.getTop() + 3, 0);
        f.prepare();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
