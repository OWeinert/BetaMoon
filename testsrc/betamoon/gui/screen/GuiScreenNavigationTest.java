package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentCompiler;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiLayer;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.screen.GuiScreenActivation.Status;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luaapi.interop.NativeCallbackTable;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/**
 * Navigation owns activation/history only; pages in this test use the real
 * retained compiler.
 */
public final class GuiScreenNavigationTest {
    private static final String OWNER = "navigation-test.lua";
    public static void main(String[] arguments) {
        prepareAndHistory();
        invalidation();
        dialogs();
        sharedActions();
        worldTransitions();
        System.out.println(
                "Screen navigation passed: deferred admission, invalid preparation retention, detached Back history, "
                        + "close/cancel, stale requests, native replacement and unload fallback precedence.");
    }

    private static void worldTransitions() {
        String owner = "world-transition-test.lua";
        try {
            Fixture fixture = transitionFixture("{replace='test:second',state={count=4},data={message='Welcome'}}");
            GuiScreenActivation source = fixture.controller.current();
            source.state(Collections.singletonMap("count", 77));
            GuiScreenActivation dialog = source.dialog("test:first", Collections.emptyMap(),
                    Collections.emptyMap()).instance;
            fixture.controller.tick();
            GuiActionTicket oldAction = source.request("apply", Collections.emptyMap()).ticket;
            enterWorld(fixture);
            fixture.controller.tick();
            require(source.terminal() && dialog.terminal() && fixture.controller.current() == null
                    && oldAction.status() == GuiActionTicket.Status.CANCELLED && !source.input.active()
                    && fixture.prepared == fixture.disposed,
                    "Root transition retained old dialogs, authority, actions or prepared resources");
            fixture.controller.tick();
            GuiScreenActivation target = fixture.controller.current();
            require(target != null && target != source && target.key.equals("test:second")
                    && ((Number) target.state().get("count")).intValue() == 4
                    && target.page.data().get("message").equals("Welcome") && fixture.failures.isEmpty(),
                    "Fresh world replacement did not validate explicit initialization");
            int prepared = fixture.prepared;
            fixture.controller.tick();
            require(fixture.prepared == prepared && target.back(), "Stable environment repeated its replacement");
            fixture.controller.tick();
            require(fixture.controller.current() == null && fixture.nativeScreen == fixture.menu,
                    "Back after replacement restored obsolete history");

            Fixture self = transitionFixture("{replace='test:first'}");
            enterWorld(self);
            self.controller.tick();
            self.controller.tick();
            GuiScreenActivation freshSelf = self.controller.current();
            self.controller.tick();
            require(freshSelf != null && self.prepared == 2 && self.disposed == 1,
                    "Self-key world replacement looped or retained its predecessor");
            self.controller.reset("fixture_closed");

            Fixture nativeClose = transitionFixture("{replace='test:second'}");
            GuiScreenActivation closing = nativeClose.controller.current();
            enterWorld(nativeClose);
            nativeClose.controller.externalClosed(closing);
            nativeClose.nativeScreen = null;
            nativeClose.controller.tick();
            require(nativeClose.controller.current() != null && nativeClose.prepared == 2,
                    "Native close after an observed root change discarded the detached handoff");
            nativeClose.controller.reset("fixture_closed");

            Fixture protectedScreen = transitionFixture("{replace='test:second'}");
            enterWorld(protectedScreen);
            protectedScreen.controller.tick();
            Object recovery = new Object();
            protectedScreen.nativeScreen = recovery;
            protectedScreen.controller.tick();
            require(protectedScreen.controller.current() == null && protectedScreen.nativeScreen == recovery
                    && protectedScreen.prepared == protectedScreen.disposed,
                    "World policy displaced a native/protected screen");

            Fixture explicit = transitionFixture("{replace='test:second'}");
            explicit.controller.current().close();
            enterWorld(explicit);
            explicit.controller.tick();
            explicit.controller.tick();
            require(explicit.controller.current() == null && explicit.prepared == 1,
                    "Explicit close armed a later world policy");

            Fixture lateClose = transitionFixture("{replace='test:second'}");
            Object oldScreen = lateClose.nativeScreen;
            enterWorld(lateClose);
            lateClose.controller.tick();
            lateClose.controller.nativeScreenClosed(oldScreen);
            lateClose.nativeScreen = null;
            lateClose.controller.tick();
            require(lateClose.controller.current() == null && lateClose.prepared == 1,
                    "Closing an already retired handoff screen revived its page");

            Fixture ordinary = transitionFixture("{replace='test:second'}");
            ordinary.controller.externalClosed(ordinary.controller.current());
            ordinary.nativeScreen = null;
            enterWorld(ordinary);
            ordinary.controller.tick();
            require(ordinary.controller.current() == null && ordinary.prepared == 1,
                    "Ordinary native close retained a dormant world-transition request");

            for (String policy : new String[]{"'close'", "{replace='test:missing'}", "{replace='foreign:screen'}"}) {
                Fixture rejected = transitionFixture(policy);
                if (policy.contains("foreign")) {
                    rejected.registry.add(GuiScreenParser.parse(
                            "foreign-transition-owner", JsePlatform.standardGlobals()
                                    .load("return {key='foreign:screen',root={type='text'}}").call(),
                            rejected.components));
                }
                enterWorld(rejected);
                rejected.controller.tick();
                rejected.controller.tick();
                require(rejected.controller.current() == null && rejected.nativeScreen == rejected.menu
                        && rejected.prepared == rejected.disposed,
                        "Unavailable/default world replacement retained a blank old screen or prepared resources");
            }

            Fixture changedAgain = transitionFixture("{replace='test:second'}");
            enterWorld(changedAgain);
            changedAgain.controller.tick();
            changedAgain.environment++;
            changedAgain.controller.tick();
            require(changedAgain.controller.current() == null && changedAgain.nativeScreen == changedAgain.menu,
                    "A second environment change dispatched the first change's policy");

            Fixture duringPrepare = transitionFixture("{replace='test:second'}");
            enterWorld(duringPrepare);
            duringPrepare.controller.tick();
            duringPrepare.duringPrepare = () -> duringPrepare.environment++;
            duringPrepare.controller.tick();
            require(duringPrepare.controller.current() == null && duringPrepare.nativeScreen == duringPrepare.menu
                    && duringPrepare.prepared == duringPrepare.disposed,
                    "Root change during preflight retained the old blank screen or candidate");

            Fixture failedPrepare = transitionFixture("{replace='test:second'}");
            enterWorld(failedPrepare);
            failedPrepare.controller.tick();
            failedPrepare.failPrepare = true;
            failedPrepare.controller.tick();
            require(failedPrepare.controller.current() == null && failedPrepare.nativeScreen == failedPrepare.menu,
                    "Failed replacement preflight retained a dead managed page");

            Fixture dead = transitionFixture("{replace='test:second'}");
            dead.environment++;
            dead.controller.tick();
            dead.controller.tick();
            require(dead.controller.current() == null && dead.prepared == 1,
                    "Unavailable player/world acquired replacement authority");

            Fixture unloaded = transitionFixture("{replace='test:second'}");
            enterWorld(unloaded);
            unloaded.controller.tick();
            ScriptResourceTracker.unload(owner);
            unloaded.controller.tick();
            require(unloaded.controller.current() == null && unloaded.nativeScreen == unloaded.menu,
                    "Owner unload retained a detached replacement request");
        } finally {
            ScriptResourceTracker.unload(owner);
            ScriptResourceTracker.unload("foreign-transition-owner");
        }
    }

    private static Fixture transitionFixture(String policy) {
        Fixture fixture = new Fixture("world-transition-test.lua", null);
        fixture.worldTransitions = true;
        Globals lua = JsePlatform.standardGlobals();
        fixture.registry.add(GuiScreenParser.parse(fixture.owner, lua.load("return {key='test:first',worldTransition="
                + policy
                + ",state={count={type='integer',default=0}},actions={apply={scope='client',handler=function() end}},"
                + "root={type='button',id='ok',text='Source'}}").call(), fixture.components));
        fixture.registry.add(GuiScreenParser.parse(fixture.owner,
                lua.load("return {key='test:second',context='world',state={count={type='integer',default=0}},"
                        + "data={message={type='string',default=''}},root={type='text',text='Destination'}}").call(),
                fixture.components));
        fixture.open("test:first");
        fixture.controller.tick();
        return fixture;
    }

    private static void enterWorld(Fixture fixture) {
        fixture.worldAvailable = true;
        fixture.environment++;
    }

    private static void sharedActions() {
        GuiActionQueue queue = new GuiActionQueue(() -> 0);
        List<Fixture> hosts = new ArrayList<>();
        List<GuiActionTicket> tickets = new ArrayList<>();
        try {
            for (int owner = 0; owner < 4; owner++) {
                for (int document = 0; document < 2; document++) {
                    Fixture fixture = new Fixture("shared-action-" + owner, queue);
                    hosts.add(fixture);
                    GuiScreenActivation activation = fixture.open("test:first");
                    fixture.controller.tick();
                    for (int index = 0; index < 16; index++) {
                        GuiActionQueue.Submission submission = activation.request("apply", Collections.emptyMap());
                        require(submission.ticket != null, "Shared controller refused before its aggregate allowance");
                        tickets.add(submission.ticket);
                    }
                    require(activation.request("apply", Collections.emptyMap()).reason.equals("action_queue_full"),
                            "Shared activation exceeded sixteen pending actions");
                }
                Fixture extra = new Fixture("shared-action-" + owner, queue);
                hosts.add(extra);
                GuiScreenActivation activation = extra.open("test:first");
                extra.controller.tick();
                require(activation.request("apply", Collections.emptyMap()).reason.equals("action_queue_full"),
                        "Another controller multiplied its owner's action capacity");
            }
            Fixture overflow = new Fixture("shared-overflow", queue);
            hosts.add(overflow);
            GuiScreenActivation activation = overflow.open("test:first");
            overflow.controller.tick();
            require(queue.pending() == 128
                    && activation.request("apply", Collections.emptyMap()).reason.equals("action_queue_full"),
                    "Another owner/controller multiplied the client-wide action capacity");
            for (Fixture fixture : hosts) {
                fixture.controller.tick();
                fixture.controller.worldTick();
                require(fixture.executed == 0, "An individual shared controller granted its own dispatch slice");
            }
            queue.dispatch(GuiActionDefinition.Scope.CLIENT);
            require(queue.pending() == 120, "Shared controllers exceeded one eight-callback logical slice");
            for (int owner = 0; owner < 4; owner++) {
                require(hosts.get(owner * 3).executed == 2 && hosts.get(owner * 3 + 1).executed == 0,
                        "Client-wide action service lost owner fairness or FIFO order");
            }
            hosts.get(0).controller.reset("host_detached");
            require(queue.pending() == 106 && tickets.get(0).status() == GuiActionTicket.Status.SUCCEEDED
                    && tickets.get(2).status() == GuiActionTicket.Status.CANCELLED,
                    "Detaching one shared controller changed completed results or another host's capacity");
            queue.cancelAll("client_replaced");
            require(queue.pending() == 0 && tickets.get(16).status() == GuiActionTicket.Status.CANCELLED,
                    "Client replacement retained shared queue entries");
        } finally {
            for (Fixture fixture : hosts) {
                fixture.controller.reset("fixture_closed");
            }
        }
    }

    private static void dialogs() {
        Fixture fixture = new Fixture();
        GuiScreenActivation root = fixture.open("test:first");
        fixture.controller.tick();
        Page rootPage = (Page) root.page;
        rootPage.scene.getContext().requestFocus(rootPage.element("ok"));
        root.state(Collections.singletonMap("count", 9));
        GuiScreenActivation dialog = root.dialog("test:second", Collections.singletonMap("count", 3),
                Collections.emptyMap()).instance;
        require(dialog.status() == Status.QUEUED && fixture.nativeScreen == root,
                "Dialog acquired a native screen before preparation");
        fixture.controller.tick();
        Page dialogPage = (Page) dialog.page;
        require(dialog.active() && fixture.nativeScreen == root && dialogPage.scene == rootPage.scene,
                "Dialog did not share the parent's native screen and scene");
        require(((Number) dialog.state().get("count")).intValue() == 3
                && ((Number) root.state().get("count")).intValue() == 9, "Dialog state leaked into its parent");
        require(rootPage.scene.getFocusedElement() == dialogPage.element("ok"), "Dialog did not acquire modal focus");
        require(dialog.push("test:first", Collections.emptyMap(), Collections.emptyMap()).reason
                .equals("dialog_navigation_unavailable"), "Dialog replaced native navigation");
        rootPage.scene.keyTyped('\0', Keyboard.KEY_ESCAPE, false);
        require(dialog.status() == Status.CLOSED && root.active()
                && rootPage.scene.getFocusedElement() == rootPage.element("ok"),
                "Escape did not close the dialog and restore its eligible opener");

        GuiScreenActivation outer = root.dialog("test:second", Collections.emptyMap(), Collections.emptyMap()).instance;
        fixture.controller.tick();
        GuiScreenActivation inner = outer.dialog("test:first", Collections.emptyMap(), Collections.emptyMap()).instance;
        fixture.controller.tick();
        require(inner.active() && inner.close() && !inner.input.active(),
                "Closing a dialog retained input eligibility until the next tick");
        fixture.controller.tick();
        require(inner.status() == Status.CLOSED && outer.active(), "Closing a nested dialog retired its parent");
        GuiScreenActivation pending = outer.dialog("test:first", Collections.emptyMap(),
                Collections.emptyMap()).instance;
        require(root.close() && !outer.active(), "Closing a root left descendant actions available");
        fixture.controller.tick();
        require(outer.status() == Status.CLOSED && pending.terminal() && fixture.nativeScreen == fixture.menu
                && fixture.prepared == fixture.disposed, "Root close leaked a dialog or prepared page");
        ScriptResourceTracker.unload(OWNER);
    }

    private static void prepareAndHistory() {
        Fixture fixture = new Fixture();
        Object menu = fixture.nativeScreen;
        GuiScreenActivation first = fixture.open("test:first");
        require(first.status() == Status.QUEUED && fixture.nativeScreen == menu && fixture.prepared == 0,
                "Open acquired native/input ownership before the logical preparation boundary");
        fixture.controller.tick();
        require(first.active() && fixture.controller.current() == first && fixture.prepared == 1,
                "Queued screen did not activate once");
        first.state(Collections.singletonMap("count", 7));
        fixture.failPrepare = true;
        GuiScreenActivation bad = first.push("test:second", Collections.emptyMap(), Collections.emptyMap()).instance;
        fixture.controller.tick();
        require(bad.status() == Status.FAILED && first.active() && fixture.nativeScreen == first,
                "Invalid prepared page retired the usable screen");
        fixture.failPrepare = false;
        GuiScreenActivation second = first.push("test:second", Collections.emptyMap(), Collections.emptyMap()).instance;
        fixture.controller.tick();
        require(second.active() && first.status() == Status.CLOSED && !first.close(),
                "Push retained an active predecessor handle");
        require(second.back(), "Back did not queue a valid predecessor");
        fixture.controller.tick();
        GuiScreenActivation restored = fixture.controller.current();
        require(restored != first && restored.active() && restored.definition().key.equals("test:first")
                && ((Number) restored.state().get("count")).intValue() == 7,
                "Back reused a closed/native page or lost detached state");
        require(restored.close() && !restored.close(), "Close was not idempotent");
        fixture.controller.tick();
        require(restored.status() == Status.CLOSED && fixture.controller.current() == null
                && fixture.nativeScreen == fixture.menu, "Close did not return to a safe native fallback");
        GuiScreenActivation cancelled = fixture.open("test:first");
        require(cancelled.close(), "Pending open could not be cancelled");
        fixture.controller.tick();
        require(cancelled.status() == Status.CANCELLED && fixture.nativeScreen == fixture.menu,
                "Cancelled open took screen ownership");
        ScriptResourceTracker.unload(OWNER);
    }

    private static void invalidation() {
        Fixture fixture = new Fixture();
        fixture.nativeScreen = new Object();
        require(fixture.controller.open(OWNER, "test:first", Collections.emptyMap(), Collections.emptyMap()).reason
                .equals("screen_busy"), "Unknown protected native screen was replaced");
        fixture.nativeScreen = fixture.menu;
        GuiScreenActivation pending = fixture.open("test:first");
        fixture.environment++;
        fixture.controller.tick();
        require(pending.status() == Status.CANCELLED && fixture.prepared == 0,
                "Old-environment open prepared native resources");
        GuiScreenActivation current = fixture.open("test:first");
        fixture.controller.tick();
        GuiScreenActivation stale = current.replace("test:second", Collections.emptyMap(),
                Collections.emptyMap()).instance;
        current.revision++;
        fixture.controller.tick();
        require(stale.status() == Status.CANCELLED && fixture.controller.current() == current,
                "Stale navigation affected a successor revision");
        GuiScreenActivation replaced = current.push("test:second", Collections.emptyMap(),
                Collections.emptyMap()).instance;
        Object recovery = new Object();
        fixture.nativeScreen = recovery;
        fixture.controller.externalClosed(current);
        fixture.controller.tick();
        require(replaced.status() == Status.CANCELLED && fixture.nativeScreen == recovery,
                "Queued navigation displaced native recovery");
        fixture.nativeScreen = fixture.menu;
        GuiScreenActivation unloaded = fixture.open("test:first");
        fixture.controller.tick();
        ScriptResourceTracker.unload(OWNER);
        require(unloaded.status() == Status.CLOSED, "Owner unload left an active screen");
        fixture.nativeScreen = recovery;
        fixture.controller.tick();
        require(fixture.nativeScreen == recovery, "Deferred owner fallback displaced a newer native screen");
        Fixture reentrant = new Fixture();
        GuiScreenActivation candidate = reentrant.open("test:first");
        reentrant.duringPrepare = () -> ScriptResourceTracker.unload(OWNER);
        reentrant.controller.tick();
        require(candidate.status() == Status.CLOSED && reentrant.nativeScreen == reentrant.menu
                && reentrant.disposed == 1,
                "Unload during preparation revived the activation or leaked its prepared page");
    }

    private static final class Fixture implements GuiScreenController.Host {
        final Object menu = new Object();
        Object nativeScreen = menu;
        long environment;
        int prepared;
        int disposed;
        int executed;
        boolean failPrepare;
        boolean worldAvailable;
        boolean worldTransitions;
        Runnable duringPrepare;
        final GuiComponentRegistry components = new GuiComponentRegistry();
        final GuiScreenRegistry registry = new GuiScreenRegistry();
        final GuiScreenController controller;
        final String owner;
        final List<Throwable> failures = new ArrayList<>();
        Fixture() {
            this(OWNER, null);
        }

        Fixture(String owner, GuiActionQueue queue) {
            this.owner = owner;
            controller = queue == null
                    ? new GuiScreenController(registry, this)
                    : new GuiScreenController(registry, this, queue);
            Globals lua = JsePlatform.standardGlobals();
            for (String key : new String[]{"test:first", "test:second"}) {
                registry.add(GuiScreenParser.parse(owner,
                        lua.load("return {key='" + key + "',state={count={type='integer',default=0,min=0,max=100}},"
                                + "actions={apply={scope='client',handler=function() end}},"
                                + "root={type='button',id='ok',text='Continue'}}").call(),
                        components));
            }
        }

        GuiScreenActivation open(String key) {
            return controller.open(owner, key, Collections.emptyMap(), Collections.emptyMap()).instance;
        }

        public long environment() {
            return environment;
        }

        public boolean available() {
            return true;
        }

        public boolean worldAvailable() {
            return worldAvailable;
        }

        public boolean supportsWorldTransition() {
            return worldTransitions;
        }

        public boolean paused() {
            return false;
        }

        public boolean canOpen() {
            return nativeScreen == menu;
        }

        public Object screenIdentity() {
            return nativeScreen;
        }

        public boolean current(GuiScreenActivation activation) {
            return nativeScreen == activation.root();
        }

        public GuiScreenController.Page prepare(GuiScreenActivation activation, GuiScreenController.History history) {
            if (failPrepare) {
                throw new IllegalArgumentException("preparation fixture");
            }
            prepared++;
            Page page = new Page(this, activation, history);
            if (duringPrepare != null) {
                duringPrepare.run();
            }
            return page;
        }

        public void display(GuiScreenActivation activation) {
            Object previous = nativeScreen;
            nativeScreen = activation;
            if (previous instanceof GuiScreenActivation) {
                controller.externalClosed((GuiScreenActivation) previous);
            }
        }

        public void fallback() {
            nativeScreen = menu;
        }

        public void failure(String owner, String path, Throwable failure) {
            failures.add(failure);
        }
    }

    private static final class Page
            implements
                GuiScreenController.Page,
                GuiPresentationHost,
                GuiComponentPlan.Services {
        final Fixture fixture;
        final GuiComponentPlan plan;
        final GuiPresentationTree tree;
        final GuiScene scene;
        final GuiScreenActivation activation;
        GuiLayer layer;
        boolean closed;
        final GuiScreenData data;
        Page(Fixture fixture, GuiScreenActivation activation, GuiScreenController.History history) {
            this.fixture = fixture;
            this.activation = activation;
            scene = activation.parent == null ? new GuiScene() : ((Page) activation.parent.page).scene;
            data = new GuiScreenData(activation.definition.data, activation.initialData);
            plan = GuiComponentCompiler.prepare(fixture.components, activation.owner(), activation.definition.key,
                    activation.definition.root, this, this, EnumSet.allOf(GuiCapability.class), null,
                    activation.definition.state, activation.initialState,
                    history == null ? Collections.emptyMap() : history.state);
            tree = plan.createTree(EnumSet.allOf(GuiCapability.class), String::length, () -> 0);
            tree.root().inputLifetime(activation.input);
            scene.updateEnvironment(null, null, 320, 240, 320, 240);
            scene.prepareDetached(tree);
        }

        public GuiElement element(String id) {
            return tree.element(plan.elementId(id));
        }

        public GuiComponentPlan plan() {
            return plan;
        }

        public Map<String, Object> data() {
            return data.snapshot();
        }

        public void publish(Map<String, Object> changes) {
            data.publish(changes);
        }

        public String focusedElement() {
            return "ok";
        }

        public void restoreFocus(String id) {
            require(id.equals("ok"), "Focus descriptor changed");
        }

        public void activate() {
            if (activation.parent == null) {
                scene.setDocument(tree);
            } else {
                layer = scene.openLayer(tree, true);
                layer.onClosed(() -> fixture.controller.externalClosed(activation));
            }
            scene.prepare();
        }

        public void update() {
            scene.prepare();
        }

        public boolean needsRefresh(GuiScreenDefinition definition) {
            return false;
        }

        public void refresh(GuiScreenDefinition definition) {
            throw new AssertionError("Unexpected refresh");
        }

        public Object execute(GuiActionDefinition action, Map<String, Object> payload) {
            fixture.executed++;
            return null;
        }

        public void close(String reason, boolean notify) {
            if (closed) {
                return;
            }
            closed = true;
            fixture.disposed++;
            plan.close();
            tree.close();
            data.close();
            if (layer != null) {
                layer.close();
            } else {
                scene.setDocument(null);
            }
        }

        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.emptyMap(), data.snapshot(), Collections.emptyMap());
        }

        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
            return Collections.emptyMap();
        }

        public List<AutoCloseable> observe(Observer observer) {
            return Collections.singletonList(data.observe(observer));
        }

        public LuaTable context(LuaCallbackScope scope) {
            return new NativeCallbackTable(scope);
        }

        public GuiPresentationHost forDefinition(GuiDocumentDefinition definition) {
            return this;
        }

        public GuiPaintCompiler.Resources paintResources(String owner) {
            return new GuiPaintCompiler.Resources() {
                public GuiFontFace font(String key) {
                    return GuiFontFace.NATIVE;
                }

                public GuiDrawable image(String key) {
                    throw new IllegalArgumentException("Missing image");
                }
            };
        }

        public void requireHost() {
        }

        public void failure(String owner, String path, Throwable failure) {
            fixture.failures.add(failure);
        }
    }
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
