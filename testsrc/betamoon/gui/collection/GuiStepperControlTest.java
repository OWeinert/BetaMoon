package betamoon.gui.collection;

import betamoon.gui.collection.GuiCollectionControlTest.Fixture;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.widget.GuiStyledView;
import java.util.Collections;
import java.util.Set;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.style.GuiVisualTemplate;
import org.luaj.vm2.LuaValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;

/**
 * Numeric stepper contracts use the production Lua parser, scene and shared
 * Java control.
 */
public final class GuiStepperControlTest {
    public static void main(String[] args) {
        pointerAndKeyboard();
        intrinsicAndDecimalEndpoint();
        controlledAndRefresh();
        displayedFrameAdmission();
        admissionAndTemplates();
        System.out.println("Steppers passed: decimal values, distinct pointer targets/cancel, keyboard bounds, "
                + "controlled acknowledgement, retained refresh, template values and pre-event admission.");
    }

    private static void pointerAndKeyboard() {
        try (Fixture f = new Fixture()) {
            f.open("limit=1;changes=0;return{type='stepper',id='stepper',width=120,height=20,text='Amount ',"
                    + "minimum=0,maximum=1,step=0.1,value=0.2,bind=function(v)local version=v.data:get('version');"
                    + "return{maximum=limit}end,on={change=function(ctx,e)changes=changes+1;wanted=e.value end}}");
            GuiStyledView stepper = view(f);
            click(f, stepper.getLeft() + 3);
            require(value(stepper) == 0.1, "Decrement did not publish a decimal step");
            click(f, stepper.getRight() - 3);
            f.key(Keyboard.KEY_UP);
            require(value(stepper) == 0.3 && stepper.text().equals("Amount 0.3")
                    && f.lua.get("wanted").todouble() == 0.3, "Decimal stepping or formatting drifted");
            int calls = f.lua.get("changes").toint();
            int y = stepper.getTop() + 5;
            f.scene.mousePressed(stepper.getRight() - 3, y, 0);
            f.scene.mouseDragged(stepper.getLeft() + 3, y, true);
            f.scene.mouseReleased(stepper.getLeft() + 3, y, 0);
            f.scene.mousePressed(stepper.getLeft() + 60, y, 0);
            f.scene.mouseReleased(stepper.getRight() - 3, y, 0);
            f.scene.mousePressed(stepper.getRight() - 3, y, 0);
            f.scene.cancelPointerCapture();
            f.scene.mouseReleased(stepper.getRight() - 3, y, 0);
            require(f.lua.get("changes").toint() == calls, "A center/crossed/cancelled gesture activated stepping");
            f.scene.mousePressed(stepper.getRight() - 3, y, 0);
            f.lua.set("limit", 0.5);
            f.host.change();
            f.prepare();
            f.scene.mouseReleased(stepper.getRight() - 3, y, 0);
            require(value(stepper) == 0.3 && f.lua.get("changes").toint() == calls,
                    "An intervening range publication retained the old pressed operation");
            f.key(Keyboard.KEY_PRIOR);
            require(value(stepper) == 0.5, "Page Up did not clamp ten steps to the upper endpoint");
            calls = f.lua.get("changes").toint();
            f.key(Keyboard.KEY_UP);
            require(f.lua.get("changes").toint() == calls, "An unchanged endpoint emitted change");
            f.key(Keyboard.KEY_HOME);
            require(value(stepper) == 0, "Home did not reach the minimum");
            f.key(Keyboard.KEY_END);
            require(value(stepper) == 0.5, "End did not reach the final available step");
            f.key(Keyboard.KEY_NEXT);
            require(value(stepper) == 0, "Page Down did not decrement by ten steps");
            f.lua.set("limit", -1);
            f.host.change();
            f.prepare();
            f.key(Keyboard.KEY_UP);
            require(value(stepper) == 0.1, "An invalid numeric binding replaced the accepted range");
        }
    }

    private static void intrinsicAndDecimalEndpoint() {
        try (Fixture f = new Fixture()) {
            f.open("return{type='stepper',id='stepper',text='Amount ',maximum=0.35,step=0.1,value=0.35}");
            GuiStyledView stepper = view(f);
            require(stepper.getWidth() >= stepper.style(0).font().width(f.scene.getContext(), stepper.text()) + 48,
                    "Intrinsic measurement omitted the side targets or center text padding");
            f.scene.getContext().requestFocus(stepper);
            f.key(Keyboard.KEY_DOWN);
            require(value(stepper) == 0.3, "Decimal midpoint maximum skipped its preceding step");
            f.key(Keyboard.KEY_UP);
            require(value(stepper) == 0.35, "The clipped decimal endpoint became unreachable");
        }
    }

    private static void controlledAndRefresh() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("amount", 0.2);
            f.open("return{type='stepper',id='stepper',width=120,maximum=1,step=0.1,"
                    + "bind=function(v)return{value=v.data:get('amount')}end,"
                    + "on={change=function(ctx,e)wanted=e.value end}}");
            GuiStyledView stepper = view(f);
            f.scene.getContext().requestFocus(stepper);
            f.key(Keyboard.KEY_UP);
            require(stepper.text().equals("0.2") && f.lua.get("wanted").todouble() == 0.3,
                    "Controlled stepper moved without acknowledgement");
            f.host.extra.put("amount", 0.35);
            f.host.change();
            f.prepare();
            require(stepper.text().equals("0.4") && stepper.templateValues(false).get("value").equals(0.4),
                    "Controlled publication or template values bypassed decimal quantization");
            f.reload();
            stepper = view(f);
            require(stepper.text().equals("0.4"), "Refresh overwrote a controlled value");
            int x = stepper.getRight() - 3;
            int y = stepper.getTop() + 5;
            f.scene.mousePressed(x, y, 0);
            f.host.extra.put("amount", 0.7);
            f.host.change();
            f.prepare();
            f.host.extra.put("amount", 0.35);
            f.host.change();
            f.prepare();
            f.scene.mouseReleased(x, y, 0);
            require(f.lua.get("wanted").todouble() == 0.3,
                    "A value changed away and back revived a stale pressed operation");
        }
        try (Fixture f = new Fixture()) {
            f.open("reject=false;return{type='stepper',id='stepper',width=120,maximum=1,step=0.1,value=0.2,"
                    + "on={change=function()return not reject end}}");
            GuiStyledView stepper = view(f);
            f.scene.getContext().requestFocus(stepper);
            f.key(Keyboard.KEY_UP);
            f.reload();
            stepper = view(f);
            require(value(stepper) == 0.3, "Refresh lost the unbound local value");
            f.scene.getContext().requestFocus(stepper);
            f.lua.set("reject", LuaValue.TRUE);
            f.key(Keyboard.KEY_UP);
            require(value(stepper) == 0.3, "A rejected change moved the local value");
        }
    }

    private static void displayedFrameAdmission() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("amount", 0.2);
            f.open("changes=0;return{type='stepper',id='stepper',width=120,step=0.1,"
                    + "bind=function(v)return{value=v.data:get('amount')}end,"
                    + "on={change=function()changes=changes+1 end}}");
            GuiStyledView stepper = view(f);
            int x = stepper.getRight() - 3;
            int y = stepper.getTop() + 5;
            f.host.extra.put("amount", 0.6);
            f.host.change();
            f.scene.prepare();
            f.scene.mousePressed(x, y, 0);
            f.scene.mouseReleased(x, y, 0);
            require(f.lua.get("changes").toint() == 0, "A press stepped a value not yet displayed");
            HeadlessGuiFrames.layout(f.scene);
            f.scene.mousePressed(x, y, 0);
            f.scene.mouseReleased(x, y, 0);
            require(f.lua.get("changes").toint() == 1, "Stepping did not recover after the accepted frame");
        }
    }

    private static void admissionAndTemplates() {
        int[] callbacks = {0};
        boolean[] refuse = {true};
        GuiStyledView stepper = new GuiStyledView("stepper", GuiStyleSheet.MINIMAL, GuiDefaultTemplate.INSTANCE,
                (event, value) -> {
                    callbacks[0]++;
                    return true;
                }).configure(values());
        stepper.admitStateWith(state -> {
            if (refuse[0]) {
                throw new GuiViewStateAdmission.Refusal(new IllegalArgumentException("capacity fixture"));
            }
            return GuiViewStateAdmission.UNRESTRICTED;
        });
        GuiScene scene = new GuiScene();
        scene.setContent(stepper);
        scene.updateEnvironment(null, null, 200, 100, 200, 100);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        scene.getContext().requestFocus(stepper);
        scene.keyTyped('\0', Keyboard.KEY_UP, false);
        require(value(stepper) == 0 && callbacks[0] == 0 && stepper.interaction("invalid"),
                "Refused complete state ran the event or changed the accepted value");
        refuse[0] = false;
        scene.keyTyped('\0', Keyboard.KEY_UP, false);
        require(value(stepper) == 0.1 && callbacks[0] == 1 && !stepper.interaction("invalid"),
                "An admitted step failed to recover");
        stepper.close();
        boolean rejected = false;
        try {
            new GuiStyledView("stepper", GuiStyleSheet.MINIMAL, new GuiVisualTemplate() {
                public Set<String> parts() {
                    return Collections.emptySet();
                }

                public void paint(GuiContext context, GuiStyledView view, GuiControlStyle style) {
                }
            }, (event, value) -> true);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "A template without increment/decrement parts was accepted");
    }

    private static Map<String, Object> values() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("width", 120);
        values.put("step", 0.1);
        return values;
    }

    private static GuiStyledView view(Fixture f) {
        return (GuiStyledView) f.tree.element(f.plan.elementId("stepper"));
    }

    private static double value(GuiStyledView view) {
        return ((Number) view.captureState().get("value")).doubleValue();
    }

    private static void click(Fixture f, int x) {
        int y = view(f).getTop() + 5;
        f.scene.mousePressed(x, y, 0);
        f.scene.mouseReleased(x, y, 0);
        f.prepare();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
