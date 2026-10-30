package betamoon.gui.collection;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.component.GuiComponentCompiler;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.component.GuiComponentStateStore;
import betamoon.luaapi.client.ui.GuiAuthoredNodeParser;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.GuiSharedControlsTest;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.drawing.GuiPaintView;
import betamoon.gui.graphics.GraphicsExpression;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.screen.GuiActionDefinition;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiActionTicket;
import betamoon.gui.screen.GuiActionTicket.Status;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.widget.GuiBuiltInViews;
import betamoon.gui.widget.GuiStyledView;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.lwjgl.input.Keyboard;

/**
 * User input and complete binding/command batches share the dormant row budget
 * before callbacks/publication.
 */
public final class GuiControlAdmissionTest {
    public static void main(String[] arguments) {
        localEditorProposals();
        typedEventRejection();
        callbackFailureOrigins();
        customCaptureRefusal();
        bindingAndCommandBatches();
        System.out.println("Control admission passed: rejected edit/commit isolation, draft rollback and recovery, "
                + "complete binding/command budget checks, idle replay and unchanged accepted views.");
    }

    private static void localEditorProposals() {
        List<String> events = new ArrayList<>();
        try (GuiPresentationTree tree = GuiSharedControlsTest.document(GuiStyleSheet.MINIMAL, events, LuaValue.NIL)) {
            Budget budget = new Budget(tree, 15500);
            GuiScene scene = scene(tree);
            GuiStyledView textbox = (GuiStyledView) tree.element("textbox");
            scene.getContext().requestFocus(textbox);
            int edits = 0;
            while (!textbox.interaction("invalid") && edits < 1500) {
                scene.keyTyped('x', Keyboard.KEY_X, false);
                edits++;
            }
            require(edits > 100 && edits < 1500 && textbox.text().length() < 1500,
                    "The row budget did not reject a draft below the control's independent maximumLength");
            String accepted = textbox.text();
            int beforeEvents = events.size();
            long beforeRevision = budget.model.revision();
            scene.keyTyped('x', Keyboard.KEY_X, false);
            require(textbox.text().equals(accepted) && events.size() == beforeEvents
                    && budget.model.revision() == beforeRevision,
                    "An oversized edit changed the draft/cache or invoked a semantic callback");
            scene.keyTyped('\0', Keyboard.KEY_RETURN, false);
            require(textbox.editor().committed().equals("seed") && events.size() == beforeEvents,
                    "An oversized committed/draft pair invoked Apply before validating its row state");
            while (textbox.text().length() > 8) {
                scene.keyTyped('\0', Keyboard.KEY_BACK, false);
            }
            scene.keyTyped('\0', Keyboard.KEY_RETURN, false);
            require(!textbox.interaction("invalid") && textbox.editor().committed().equals(textbox.text())
                    && events.get(events.size() - 1).equals("textbox:commit"),
                    "Reducing the local draft did not restore valid commit admission");
            require(budget.model.rowState("row").equals(budget.state(tree.captureViewState())),
                    "Accepted input left different live and dormant control state");
        }
    }

    private static void bindingAndCommandBatches() {
        Globals lua = JsePlatform.standardGlobals();
        lua.load("calls=0; commands=''; " + "function first(v) calls=calls+1; return {text=v.data:get('first')} end; "
                + "function second(v) calls=calls+1; return {text=v.data:get('second')} end; "
                + "function flag(v) calls=calls+1; return {selected=v.data:get('flag')} end; "
                + "function onEvent(ctx,event) if event.type=='control' then "
                + "ctx.gui:element('first'):set{text=commands}; "
                + "ctx.gui:element('second'):set{text=commands} end end").call();
        List<GuiNodeDefinition> nodes = Arrays.asList(
                node("root", "column", -1, values("width", 180, "height", 192, "gap", 5), LuaValue.NIL),
                node("first", "textbox", 0, values("height", 24), lua.get("first")),
                node("second", "textbox", 0, values("height", 24), lua.get("second")),
                node("flag", "checkbox", 0, values("text", "Flag", "height", 24), lua.get("flag")));
        String owner = "control-admission-fixture.lua";
        GuiDocumentDefinition definition = new GuiDocumentDefinition("test:control_admission", owner, 320, 200, null,
                null, Collections.emptyList(),
                new GuiPresentationDefinition(nodes, lua.get("onEvent"), Collections.singleton("control"), owner),
                true);
        Host host = new Host();
        host.publish("", "", false);
        try (GuiPresentationTree tree = new GuiPresentationTree(definition, host,
                new GuiBuiltInViews(GuiStyleSheet.MINIMAL, node -> (event, value) -> true), Collections.emptySet(),
                text -> text.length() * 6, () -> 0)) {
            Budget budget = new Budget(tree, 9000);
            GuiScene scene = scene(tree);
            GuiStyledView first = (GuiStyledView) tree.element("first");
            GuiStyledView second = (GuiStyledView) tree.element("second");
            GuiStyledView flag = (GuiStyledView) tree.element("flag");
            long revision = budget.model.revision();
            host.publish(repeat(2000), repeat(2000), true);
            scene.prepare();
            require(first.text().equals("") && second.text().equals("") && !flag.selected()
                    && tree.runtime().element("first").target("text").equals("")
                    && tree.runtime().element("second").target("text").equals("")
                    && budget.model.revision() == revision,
                    "A combined binding overflow partly published geometry, appearance or row cache state");
            int calls = lua.get("calls").checkint();
            int attempts = budget.attempts;
            for (int frame = 0; frame < 10; frame++) {
                scene.prepare();
                tree.sample();
            }
            require(lua.get("calls").checkint() == calls && budget.attempts == attempts,
                    "Unchanged rejected bindings retried Lua/admission during idle or frame replay");
            String smaller = repeat(1000);
            host.publish(smaller, smaller, true);
            scene.prepare();
            require(first.text().equals(smaller) && second.text().equals(smaller) && flag.selected()
                    && budget.model.rowState("row").equals(budget.state(tree.captureViewState())),
                    "A valid later source batch did not recover the accepted views/cache together");

            lua.set("commands", repeat(2000));
            revision = budget.model.revision();
            tree.runtime().dispatchUiEvent("flag", "change", true);
            scene.prepare();
            require(first.text().equals(smaller) && second.text().equals(smaller)
                    && tree.runtime().element("first").target("text").equals(smaller)
                    && tree.runtime().element("second").target("text").equals(smaller)
                    && budget.model.revision() == revision,
                    "An over-budget event command batch partly changed explicit targets or the row cache");
            lua.set("commands", repeat(500));
            tree.runtime().dispatchUiEvent("flag", "change", true);
            scene.prepare();
            require(first.text().equals(repeat(500)) && second.text().equals(repeat(500))
                    && budget.model.rowState("row").equals(budget.state(tree.captureViewState())),
                    "A valid command batch failed to publish the same complete control/cache snapshot");
            first.textOffset(300);
            LuaTable shorter = new LuaTable();
            shorter.set("text", "short");
            tree.runtime().invokeElement("first", "set", shorter, LuaValue.NIL);
            scene.prepare();
            require(first.text().equals("short") && first.textOffset() == 5
                    && budget.model.rowState("row").equals(budget.state(tree.captureViewState())),
                    "A shorter bound value left different projected and published caret viewport state");
            shorter.set("text", "tiny");
            shorter.set("maximumLength", 300);
            tree.runtime().invokeElement("first", "set", shorter, LuaValue.NIL);
            scene.prepare();
            require(first.editor().maximumLength() == 300 && first.textOffset() == 0
                    && budget.model.rowState("row").equals(budget.state(tree.captureViewState())),
                    "Changed editor constraints did not reset the projected and published viewport together");
            try {
                tree.reload(definition);
                throw new AssertionError("Refresh silently dropped an owned row's state admission");
            } catch (IllegalStateException expected) {
                require(tree.element("first") == first && host.observers.size() == 1,
                        "Refused unguarded refresh retired accepted controls or retained candidate subscriptions");
            }
        }
        require(host.observers.isEmpty(), "Closed guarded bindings retained host subscriptions");
    }

    private static void typedEventRejection() {
        Globals lua = JsePlatform.standardGlobals();
        Actions actions = new Actions();
        lua.set("submit", actions.submit);
        GuiComponentRegistry registry = new GuiComponentRegistry();
        GuiComponentSchema schema = GuiComponentParser
                .schema(lua.load("return {mirror={type='string',default=''}}").call(), "state", false);
        LuaValue authored = lua.load("calls=0; return {type='textbox',id='box',width=180,height=24,maximumLength=65536,"
                + "on={change=function(ctx,event) calls=calls+1; submit(); "
                + "ctx.state:set('mirror',event.value) end}}").call();
        Host host = new Host();
        List<Throwable> failures = new ArrayList<>();
        GuiComponentPlan.Services services = new GuiComponentPlan.Services() {
            public GuiPaintCompiler.Resources paintResources(String owner) {
                throw new IllegalArgumentException("This control fixture has no prepared custom painting");
            }

            public void requireHost() {
            }

            public void failure(String owner, String path, Throwable failure) {
                failures.add(failure);
            }
        };
        try (GuiComponentPlan plan = GuiComponentCompiler.prepare(registry, "typed-control-admission.lua",
                "test:typed_control",
                new GuiAuthoredNodeParser(registry).parse("typed-control-admission.lua", authored, "root"), host,
                services, Collections.emptySet(), null, schema, Collections.emptyMap(), Collections.emptyMap());
                GuiPresentationTree tree = plan.createTree(Collections.emptySet(), text -> text.length() * 6,
                        () -> 0)) {
            Budget budget = new Budget(tree, 9000, plan.state());
            GuiScene scene = scene(tree);
            GuiStyledView textbox = (GuiStyledView) tree.element(plan.elementId("box"));
            scene.getContext().requestFocus(textbox);
            int edits = 0;
            while (!textbox.interaction("invalid") && edits < 6000) {
                scene.keyTyped('x', Keyboard.KEY_X, false);
                actions.queue.dispatch(Scope.CLIENT);
                edits++;
            }
            require(edits > 1000 && edits < 6000 && textbox.isEnabled() && failures.isEmpty(),
                    "Combined typed/editor admission disabled its component instead of refusing the edit");
            require(actions.last.status() == Status.CANCELLED && "origin_expired".equals(actions.last.reason())
                    && actions.completed == edits - 1,
                    "A recoverably refused typed callback retained its deferred action");
            require(textbox.text().equals(plan.state().get(GuiComponentPlan.SCREEN_STATE, "mirror"))
                    && budget.model.rowState("row").equals(budget.state(tree.captureViewState())),
                    "Refused typed event left different live state, editor text or dormant cache");
            String accepted = textbox.text();
            scene.keyTyped('\0', Keyboard.KEY_BACK, false);
            actions.queue.dispatch(Scope.CLIENT);
            require(!textbox.interaction("invalid") && textbox.text().length() == accepted.length() - 1
                    && textbox.text().equals(plan.state().get(GuiComponentPlan.SCREEN_STATE, "mirror"))
                    && failures.isEmpty(), "A valid edit after typed-state refusal did not recover its event scope");
            require(actions.last.status() == Status.SUCCEEDED, "A recovered callback could no longer submit intent");
        }
        require(host.observers.isEmpty(), "A guarded component left subscriptions after close");
    }

    private static final class Actions implements GuiActionQueue.Activation {
        final GuiActionQueue queue = new GuiActionQueue(() -> 0);
        final GuiActionDefinition action = new GuiActionDefinition("apply", Scope.CLIENT, GuiComponentSchema.EMPTY,
                new ZeroArgFunction() {
                    public LuaValue call() {
                        return NIL;
                    }
                });
        GuiActionTicket last;
        int completed;
        final LuaValue submit = new ZeroArgFunction() {
            public LuaValue call() {
                GuiActionQueue.Submission request = queue.submit(Actions.this, action, Collections.emptyMap());
                require(request.ticket != null, "Callback intent was refused: " + request.reason);
                last = request.ticket;
                return NIL;
            }
        };

        public String owner() {
            return "typed-control-admission.lua";
        }

        public long revision() {
            return 0;
        }

        public boolean active() {
            return true;
        }

        public String unavailable(Scope scope) {
            return null;
        }

        public Object execute(GuiActionDefinition definition, Map<String, Object> payload) {
            completed++;
            return null;
        }

        public void failed(String name, Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    private static void callbackFailureOrigins() {
        for (String result : Arrays.asList("error('failure fixture')", "return 3", "return true")) {
            Globals lua = JsePlatform.standardGlobals();
            Actions actions = new Actions();
            lua.set("submit", actions.submit);
            GuiComponentRegistry registry = new GuiComponentRegistry();
            List<Throwable> failures = new ArrayList<>();
            GuiComponentPlan.Services services = new GuiComponentPlan.Services() {
                public GuiPaintCompiler.Resources paintResources(String owner) {
                    throw new IllegalArgumentException("This callback fixture has no custom painting");
                }

                public void requireHost() {
                }

                public void failure(String owner, String path, Throwable failure) {
                    failures.add(failure);
                }
            };
            LuaValue authored = lua.load("return {type='button',id='button',width=80,height=24,"
                    + "on={activate=function() submit(); " + result + " end}}").call();
            try (GuiComponentPlan plan = GuiComponentCompiler.prepare(registry, "origin-callback-fixture.lua",
                    "test:origin_callback",
                    new GuiAuthoredNodeParser(registry).parse("origin-callback-fixture.lua", authored, "root"),
                    new Host(), services, Collections.emptySet(), null);
                    GuiPresentationTree tree = plan.createTree(Collections.emptySet(), text -> text.length() * 6,
                            () -> 0)) {
                GuiScene scene = scene(tree);
                GuiElement button = tree.element(plan.elementId("button"));
                scene.mousePressed(button.getLeft() + 2, button.getTop() + 2, 0);
                scene.mouseReleased(button.getLeft() + 2, button.getTop() + 2, 0);
                actions.queue.dispatch(Scope.CLIENT);
                boolean succeeded = "return true".equals(result);
                require(actions.last != null
                        && actions.last.status() == (succeeded ? Status.SUCCEEDED : Status.CANCELLED)
                        && failures.size() == (succeeded ? 0 : 1),
                        "Swallowed callback failure/invalid result retained intent or cancelled a successful callback");
            }
        }
    }

    private static void customCaptureRefusal() {
        int[] cancelled = {0};
        int[] released = {0};
        List<Throwable> failures = new ArrayList<>();
        GuiNodeDefinition leaf = node("leaf", "panel", -1, values("width", 80, "height", 80), LuaValue.NIL);
        GuiDocumentDefinition definition = new GuiDocumentDefinition("test:custom_state_refusal",
                "custom-state-refusal.lua", 320, 200, null, null, Collections.emptyList(),
                new GuiPresentationDefinition(Collections.singletonList(leaf), LuaValue.NIL, Collections.emptySet(),
                        "custom-state-refusal.lua"),
                true);
        try (GuiPresentationTree tree = new GuiPresentationTree(definition, new Host(),
                node -> new GuiPaintView(
                        (width, height,
                                properties) -> new GuiPaintProgram.Builder().hitRectangle("target",
                                        GraphicsExpression.constant(0), GraphicsExpression.constant(0),
                                        GraphicsExpression.constant(80), GraphicsExpression.constant(80)).build(),
                        (event, region) -> {
                            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                                return new GuiPaintView.Decision(true, true, false, true);
                            }
                            if (event.getType() == GuiInputEvent.Type.POINTER_DRAG) {
                                throw new GuiViewStateAdmission.Refusal(new IllegalArgumentException("Budget fixture"));
                            }
                            if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                                cancelled[0]++;
                            }
                            if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
                                released[0]++;
                            }
                            return GuiPaintView.Decision.CONSUME;
                        }, failures::add, true),
                Collections.emptySet(), text -> text.length() * 6, () -> 0)) {
            GuiScene scene = scene(tree);
            GuiElement target = tree.element("leaf");
            int x = target.getLeft() + 3;
            int y = target.getTop() + 3;
            require(scene.mousePressed(x, y, 0), "Custom fixture did not own its pointer gesture");
            scene.mouseDragged(x + 1, y + 1, true);
            require(cancelled[0] == 1 && !((GuiPaintView) target).interaction("pressed") && scene.mouseReleased(x, y, 0)
                    && released[0] == 0, "Refused custom input retained capture or delivered a stale pointer release");
            scene.mousePressed(x, y, 0);
            scene.mouseReleased(x, y, 0);
            require(released[0] == 1 && failures.isEmpty(), "Custom input did not recover after a state refusal");
        }
    }

    private static GuiScene scene(GuiPresentationTree tree) {
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 200, 320, 200);
        scene.setDocument(tree);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        return scene;
    }

    private static GuiNodeDefinition node(String id, String type, int parent, Map<String, Object> values,
            LuaValue binding) {
        return new GuiNodeDefinition(id, id, type, parent, null, null,
                GuiBuiltInViews.properties(type).withDefaults(values), binding, GuiTransitionPolicy.IMMEDIATE, false);
    }

    private static final class Budget {
        final GuiCollectionModel model = new GuiCollectionModel();
        final String padding;
        final GuiComponentStateStore typed;
        int attempts;
        Budget(GuiPresentationTree tree, int padding) {
            this(tree, padding, null);
        }

        Budget(GuiPresentationTree tree, int padding, GuiComponentStateStore typed) {
            this.padding = repeat(padding);
            this.typed = typed;
            model.publish(Collections
                    .singletonList(new GuiCollectionModel.Record("row", null, "Row", true, Collections.emptyMap())));
            model.publishRowState("row", state(tree.captureViewState()));
            if (typed != null) {
                typed.admitWith(candidate -> {
                    Map<String, Object> combined = state(tree.captureViewState());
                    combined.put("state", candidate);
                    try {
                        model.publishRowState("row", combined);
                    } catch (IllegalArgumentException refused) {
                        throw new GuiViewStateAdmission.Refusal(refused);
                    }
                });
            }
            tree.admitViewStateWith(prospective -> {
                attempts++;
                GuiCollectionModel.StateReservation reservation = model.reserveRowState("row", state(prospective));
                return new GuiViewStateAdmission.Proposal() {
                    public void commit(Map<String, Object> accepted) {
                        model.publishRowState("row", state(accepted));
                        reservation.commit();
                    }

                    public void close() {
                        reservation.close();
                    }
                };
            });
        }

        Map<String, Object> state(Map<String, Object> views) {
            Map<String, Object> combined = values("padding", padding, "views", views);
            if (typed != null) {
                combined.put("state", typed.snapshot());
            }
            return combined;
        }
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

        void publish(String first, String second, boolean flag) {
            Map<String, Object> previous = data.snapshot();
            data.publish(values("first", first, "second", second, "flag", flag));
            for (String name : data.snapshot().keySet()) {
                for (Observer observer : new ArrayList<>(observers)) {
                    observer.changed("data:" + name, previous.get(name), data.snapshot().get(name));
                }
            }
        }
    }

    private static Map<String, Object> values(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return values;
    }

    private static String repeat(int length) {
        char[] text = new char[length];
        Arrays.fill(text, 'x');
        return new String(text);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
