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
        System.out.println(
                "Screen navigation passed: deferred admission, invalid preparation retention, detached Back history, "
                        + "close/cancel, stale requests, native replacement and unload fallback precedence.");
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
        boolean failPrepare;
        Runnable duringPrepare;
        final GuiComponentRegistry components = new GuiComponentRegistry();
        final GuiScreenRegistry registry = new GuiScreenRegistry();
        final GuiScreenController controller = new GuiScreenController(registry, this);
        final List<Throwable> failures = new ArrayList<>();
        Fixture() {
            Globals lua = JsePlatform.standardGlobals();
            for (String key : new String[]{"test:first", "test:second"}) {
                registry.add(GuiScreenParser.parse(OWNER, lua.load("return {key='" + key
                        + "',state={count={type='integer',default=0,min=0,max=100}},root={type='button',id='ok',text='Continue'}}")
                        .call(), components));
            }
        }

        GuiScreenActivation open(String key) {
            return controller.open(OWNER, key, Collections.emptyMap(), Collections.emptyMap()).instance;
        }

        public long environment() {
            return environment;
        }

        public boolean available() {
            return true;
        }

        public boolean worldAvailable() {
            return false;
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
