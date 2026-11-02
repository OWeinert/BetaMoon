package betamoon.gui.collection;

import betamoon.gui.collection.GuiCollectionControlTest.Fixture;
import betamoon.gui.control.GuiNumericEntry;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.widget.GuiNumberBoxView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import org.luaj.vm2.LuaValue;

public final class GuiNumberBoxControlTest {
    public static void main(String[] args) {
        decimalPolicy();
        commitAndReject();
        controlledAndRefresh();
        changingRangeAndStaleInput();
        rowRetentionAndReservations();
        admissionAndStaleRestore();
        System.out.println("Numeric entry passed: partial drafts, finite decimal policy, explicit stepping, "
                + "Enter/blur commit, controlled acknowledgement, virtual retention and complete state admission.");
    }

    private static void decimalPolicy() {
        GuiNumericEntry plain = new GuiNumericEntry(-100, 100, 0);
        require(plain.parse("+.125") == 0.125 && plain.parse("1e-2") == 0.01,
                "Unstepped decimal/scientific input was rounded");
        require(new GuiNumericEntry(0, 1, 0.1).parse(".35") == 0.4, "Explicit decimal midpoint did not snap upward");
        require(GuiNumericEntry.format(1000).equals("1000") && GuiNumericEntry.format(1.0E-300).length() < 128,
                "Numeric formatting is unreadable or exceeds the bounded draft");
        for (String invalid : new String[]{"", "-", ".", "1e-", "NaN", "Infinity", "0x1p0", "101", "1e999", "1e-999"}) {
            boolean refused = false;
            try {
                plain.parse(invalid);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            require(refused, "Accepted an invalid numeric draft: " + invalid);
        }
        boolean refused = false;
        try {
            new GuiNumericEntry(0, 100, 1.0E-8);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        require(refused, "An unsupported positive step silently changed precision");
    }

    private static void commitAndReject() {
        try (Fixture f = new Fixture()) {
            f.open("changes=0;reject=false;return{type='column',children={{type='numberbox',id='number',"
                    + "minimum=-100,maximum=100,value=2.5,on={change=function(ctx,e)changes=changes+1;wanted=e.value;"
                    + "return not reject end}},{type='button',id='next',text='Next'}}}");
            GuiNumberBoxView box = box(f);
            draft(f, box, "-");
            require(box.text().equals("-") && f.lua.get("changes").toint() == 0,
                    "An incomplete draft triggered a numeric change");
            f.key(Keyboard.KEY_RETURN);
            require(box.interaction("invalid") && box.text().equals("-") && number(box) == 2.5,
                    "An invalid commit changed the accepted number or discarded its draft");
            draft(f, box, ".125");
            f.key(Keyboard.KEY_RETURN);
            require(number(box) == 0.125 && box.text().equals("0.125") && !box.interaction("invalid")
                    && f.lua.get("changes").toint() == 1 && f.lua.get("wanted").isnumber(),
                    "A valid Enter commit did not propose and normalize a number");
            f.lua.set("reject", LuaValue.TRUE);
            draft(f, box, "3e1");
            f.key(Keyboard.KEY_RETURN);
            require(number(box) == 0.125 && box.text().equals("3e1") && box.interaction("invalid"),
                    "A rejected numeric proposal changed the accepted value or raw draft");
            f.key(Keyboard.KEY_ESCAPE);
            require(box.text().equals("0.125") && !box.interaction("invalid"),
                    "Escape failed to restore the accepted number");
            f.lua.set("reject", LuaValue.FALSE);
            draft(f, box, "4.75");
            f.scene.keyTyped('\t', Keyboard.KEY_TAB, false);
            f.prepare();
            require(number(box) == 4.75 && f.scene.getFocusedElement() == f.tree.element(f.plan.elementId("next")),
                    "Tab failed to leave and commit numeric entry");
            require(box.templateValues(true).get("text").equals("4.75")
                    && box.templateValues(true).get("value").equals(4.75),
                    "Template props lost draft or accepted number");
        }
        try (Fixture f = new Fixture()) {
            f.open("return{type='numberbox',id='number',maximum=1,step=.1}");
            GuiNumberBoxView box = box(f);
            draft(f, box, ".35");
            f.key(Keyboard.KEY_RETURN);
            require(number(box) == 0.4, "Explicit stepping was not applied on commit");
            draft(f, box, "1.1");
            f.key(Keyboard.KEY_RETURN);
            require(number(box) == 0.4 && box.interaction("invalid"), "Out-of-range input was silently clamped");
        }
    }

    private static void controlledAndRefresh() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("amount", 1.0);
            f.open("changes=0;return{type='numberbox',id='number',bind=function(v)return{value=v.data:get('amount')}end,"
                    + "on={change=function(ctx,e)changes=changes+1;wanted=e.value end}}");
            GuiNumberBoxView box = box(f);
            draft(f, box, "2e0");
            f.key(Keyboard.KEY_RETURN);
            require(number(box) == 1 && box.text().equals("2") && box.editor().pending(),
                    "A controlled number bypassed acknowledgement");
            f.key(Keyboard.KEY_RETURN);
            require(f.lua.get("changes").toint() == 1, "Pending numeric commit ran twice");
            f.host.extra.put("amount", 2.0);
            f.host.change();
            f.prepare();
            require(number(box) == 2 && !box.editor().pending() && !box.interaction("invalid"),
                    "Normalized numeric acknowledgement became a conflict");
            draft(f, box, "3e-");
            f.host.extra.put("amount", 4.0);
            f.host.change();
            f.prepare();
            require(number(box) == 4 && box.text().equals("3e-") && box.editor().conflict(),
                    "A different source update discarded the in-progress draft");
            f.reload();
            box = box(f);
            require(box.text().equals("3e-") && number(box) == 4, "Controlled refresh lost the draft/source split");
            f.scene.getContext().requestFocus(box);
            f.key(Keyboard.KEY_ESCAPE);
            require(box.text().equals("4"), "Escape did not restore the latest numeric source");
        }
        try (Fixture f = new Fixture()) {
            f.open("return{type='numberbox',id='number',value=5}");
            GuiNumberBoxView box = box(f);
            draft(f, box, "7.25");
            f.key(Keyboard.KEY_RETURN);
            draft(f, box, "8e-");
            f.reload();
            box = box(f);
            require(number(box) == 7.25 && box.text().equals("8e-"),
                    "Local refresh lost committed value or partial draft");
        }
    }

    private static void changingRangeAndStaleInput() {
        try (Fixture f = new Fixture()) {
            f.host.extra.put("maximum", 100.0);
            f.open("return{type='numberbox',id='number',bind=function(v)return{maximum=v.data:get('maximum')}end}");
            GuiNumberBoxView box = box(f);
            draft(f, box, "12.5");
            f.key(Keyboard.KEY_RETURN);
            f.host.extra.put("maximum", 10.0);
            f.host.change();
            f.prepare();
            require(number(box) == 10 && box.text().equals("10"), "Range narrowing lost a clean local value");
            draft(f, box, "9e-");
            f.host.extra.put("maximum", 5.0);
            f.host.change();
            f.prepare();
            require(number(box) == 5 && box.text().equals("9e-") && box.editor().conflict(),
                    "Range narrowing silently discarded an in-progress numeric draft");
        }
        try (Fixture f = new Fixture()) {
            f.host.extra.put("amount", 12.5);
            f.open("return{type='numberbox',id='number',bind=function(v)return{value=v.data:get('amount')}end}");
            GuiNumberBoxView box = box(f);
            f.scene.getContext().requestFocus(box);
            f.host.extra.put("amount", 64.0);
            f.host.change();
            f.scene.prepare();
            int caret = box.editor().caret();
            f.scene.mousePressed(box.getLeft() + 2, box.getTop() + 3, 0);
            f.scene.mouseReleased(box.getLeft() + 2, box.getTop() + 3, 0);
            require(box.editor().caret() == caret, "Stale pointer input positioned text that was not displayed");
            HeadlessGuiFrames.layout(f.scene);
            f.scene.mousePressed(box.getLeft() + 2, box.getTop() + 3, 0);
            f.scene.mouseReleased(box.getLeft() + 2, box.getTop() + 3, 0);
            require(box.editor().caret() == 0, "Numeric pointer positioning did not recover on the new frame");
        }
    }

    private static void rowRetentionAndReservations() {
        try (Fixture f = new Fixture()) {
            f.open("reject=false;return{type='list',id='rows',width=160,height=40,rowHeight=40,overscan=0,records={"
                    + "{key='a'},{key='b'},{key='c'},{key='d'}},rowState={padding={type='string',default=string.rep('x',100)}},"
                    + "row=function()return{type='numberbox',value=1,on={change=function()return not reject end}}end}");
            GuiCollectionView rows = f.view("rows");
            GuiNumberBoxView box = (GuiNumberBoxView) rows.collection().rowElement("a");
            draft(f, box, "5.5");
            f.key(Keyboard.KEY_RETURN);
            require(number(box) == 5.5, "Nested accepted reservations rolled the committed value back");
            f.lua.set("reject", LuaValue.TRUE);
            draft(f, box, "6e0");
            f.key(Keyboard.KEY_RETURN);
            require(box.text().equals("6e0") && box.interaction("invalid"), "Nested rejection discarded the draft");
            f.scene.mouseScrolled(rows.getLeft() + 10, rows.getTop() + 10, -960, false);
            f.prepare();
            require(rows.collection().rowElement("a") == null, "Numeric row did not retire");
            f.scene.mouseScrolled(rows.getLeft() + 10, rows.getTop() + 10, 960, false);
            f.prepare();
            box = (GuiNumberBoxView) rows.collection().rowElement("a");
            require(number(box) == 5.5 && box.text().equals("6e0") && box.interaction("invalid"),
                    "Retiring a numeric row lost its local value/invalid draft");
        }
    }

    private static void admissionAndStaleRestore() {
        int[] callbacks = {0};
        GuiNumberBoxView box = new GuiNumberBoxView(GuiStyleSheet.MINIMAL, GuiDefaultTemplate.INSTANCE,
                (event, value) -> {
                    callbacks[0]++;
                    return true;
                }).configure(new LinkedHashMap<>());
        boolean[] refuse = {false};
        box.admitStateWith(state -> {
            if (refuse[0]) {
                throw new GuiViewStateAdmission.Refusal(new IllegalArgumentException("capacity fixture"));
            }
            return GuiViewStateAdmission.UNRESTRICTED;
        });
        GuiScene scene = new GuiScene();
        scene.setContent(box);
        scene.updateEnvironment(null, null, 200, 100, 200, 100);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        scene.getContext().requestFocus(box);
        scene.keyTyped('\0', Keyboard.KEY_A, false, true);
        scene.keyTyped('5', 0, false);
        Map<String, Object> previous = box.captureState();
        Runnable restore = box.prepareStateRestore(previous);
        refuse[0] = true;
        scene.keyTyped('\0', Keyboard.KEY_RETURN, false);
        require(callbacks[0] == 0 && number(box) == 0 && box.text().equals("5"),
                "Refused numeric commit called Lua or changed its accepted state");
        refuse[0] = false;
        scene.keyTyped('\0', Keyboard.KEY_RETURN, false);
        require(callbacks[0] == 1 && number(box) == 5 && !box.interaction("invalid"),
                "Numeric commit failed to recover");
        boolean stale = false;
        try {
            restore.run();
        } catch (IllegalStateException expected) {
            stale = true;
        }
        require(stale, "A stale numeric restore overwrote a later commit");
        box.close();
    }

    private static GuiNumberBoxView box(Fixture f) {
        return (GuiNumberBoxView) f.tree.element(f.plan.elementId("number"));
    }

    private static double number(GuiNumberBoxView box) {
        return ((Number) box.captureState().get("value")).doubleValue();
    }

    private static void draft(Fixture f, GuiNumberBoxView box, String text) {
        f.scene.getContext().requestFocus(box);
        f.scene.keyTyped('\0', Keyboard.KEY_A, false, true);
        for (int i = 0; i < text.length(); i++) {
            f.scene.keyTyped(text.charAt(i), 0, false);
        }
        f.prepare();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
