package betamoon.gui.menu;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.instrumentation.agent.BetaMoonAgent;
import betamoon.gui.GuiBetaMoonIngameMenu;
import betamoon.gui.GuiBetaMoonMainMenu;
import betamoon.gui.GuiMenuBuiltinViews;
import betamoon.gui.GuiWorldCreation;
import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDocumentMount;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputLifetime;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.interop.NativeClientScreenHost;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.screen.GuiActionDefinition;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiHostElementBudget;
import betamoon.gui.screen.GuiNativeScreenPage;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.screen.GuiScreenAttachment;
import betamoon.gui.screen.GuiScreenController;
import betamoon.gui.screen.GuiScreenDefinition;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.client.ui.GuiMenuLuaAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.teleport.TeleportRuntime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiIngameMenu;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiOptions;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.GuiSelectWorld;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * One shared scene on the actual native menu; never substitutes an unknown
 * modded subclass.
 */
public final class GuiMenuRuntime {
    private static final GuiMenuRegistry REGISTRY = new GuiMenuRegistry();
    private static Context current;

    public static GuiMenuRegistry registry() {
        return REGISTRY;
    }

    public static String target(GuiScreen screen) {
        if (screen == null) {
            return null;
        }
        Class<?> type = screen.getClass();
        if (type == GuiMainMenu.class || type == GuiBetaMoonMainMenu.class) {
            return "minecraft:main";
        }
        if (type == GuiIngameMenu.class || type == GuiBetaMoonIngameMenu.class) {
            return "minecraft:pause";
        }
        if (type == GuiOptions.class) {
            return "minecraft:options";
        }
        if (type == GuiSelectWorld.class) {
            return "minecraft:world_select";
        }
        return type == GuiWorldCreation.class ? "betamoon:world_create" : null;
    }

    public static boolean manual(GuiScreen screen) {
        return screen instanceof GuiBetaMoonMainMenu || screen instanceof GuiBetaMoonIngameMenu;
    }

    public static void initialized(Minecraft client, GuiScreen screen, List<?> buttons) {
        if (target(screen) == null || client == null) {
            return;
        }
        if (current != null && current.screen == screen && current.client == client) {
            current.buttons = buttons;
            current.layoutDirty = true;
            return;
        }
        if (current != null) {
            current.close();
        }
        current = new Context(client, screen, buttons);
    }

    public static void tick(Minecraft client) {
        Context context = current;
        if (context == null) {
            return;
        }
        if (context.client != client || client.currentScreen != context.screen) {
            context.close();
            if (current == context) {
                current = null;
            }
            return;
        }
        context.tick();
    }

    public static void closed(GuiScreen screen) {
        if (current != null && current.screen == screen) {
            Context previous = current;
            current = null;
            previous.close();
        }
    }

    public static void changing(Minecraft client, GuiScreen next) {
        if (current != null && current.client == client && current.screen != next) {
            closed(current.screen);
        }
    }

    /**
     * Called from the native base-button paint seam, or the engine's manual
     * fallback adapters.
     */
    public static boolean suppressNative(GuiScreen screen) {
        return current != null && current.screen == screen && current.replacementReady();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void nativePaint(GuiScreen screen, List buttons, Runnable painter) {
        if (!suppressNative(screen)) {
            painter.run();
            return;
        }
        List saved = new ArrayList(buttons);
        buttons.clear();
        try {
            painter.run();
        } finally {
            buttons.clear();
            buttons.addAll(saved);
        }
    }

    public static void paint(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        Context context = current;
        if (context == null || context.screen != screen || context.closed) {
            return;
        }
        boolean suppressed = context.replacementReady();
        Runnable drawing = () -> {
            for (Entry entry : new ArrayList<>(context.entries.values())) {
                if (entry.visible && entry.page != null) {
                    entry.page.sampleAttached();
                }
            }
            context.scene.render(mouseX, mouseY, partialTicks);
        };
        if (context.host == null || !context.host.session().active()) {
            drawing.run();
        } else {
            context.host.paint(drawing);
        }
        if (context.winner != null && context.winner.usable()) {
            context.winner.painted = true;
        }
        if (suppressed && !context.replacementReady()) {
            for (Object value : context.buttons) {
                if (value instanceof GuiButton) {
                    ((GuiButton) value).drawButton(context.client, mouseX, mouseY);
                }
            }
        }
    }

    public static boolean keyboard(GuiScreen screen) {
        Context context = active(screen);
        if (context == null) {
            return false;
        }
        boolean[] consumed = new boolean[1];
        context.input(() -> {
            boolean down = Keyboard.getEventKeyState();
            int key = Keyboard.getEventKey();
            consumed[0] = down
                    ? context.scene.keyTyped(Keyboard.getEventCharacter(), key, shift())
                    : context.scene.keyReleased(key, shift());
            if (!consumed[0] && context.replacementReady()) {
                if (down && key == Keyboard.KEY_ESCAPE) {
                    context.winner.claim.close();
                }
                consumed[0] = true;
            }
            if (consumed[0] && down && key > 0) {
                ClientInputRuntime.router().claimGuiKey(key, screen);
            }
        });
        return consumed[0];
    }

    public static boolean mouse(GuiScreen screen) {
        Context context = active(screen);
        if (context == null) {
            return false;
        }
        boolean[] consumed = new boolean[1];
        context.input(() -> {
            int x = Mouse.getEventX() * screen.width / context.client.displayWidth;
            int y = screen.height - Mouse.getEventY() * screen.height / context.client.displayHeight - 1;
            int button = Mouse.getEventButton();
            if (button >= 0) {
                boolean down = Mouse.getEventButtonState();
                consumed[0] = down
                        ? context.scene.mousePressed(x, y, button)
                        : context.scene.mouseReleased(x, y, button);
                if (context.replacementReady()) {
                    consumed[0] = true;
                }
                if (consumed[0] && down) {
                    ClientInputRuntime.router().claimGuiGesture(button, screen);
                }
            } else {
                int wheel = Mouse.getEventDWheel();
                if (wheel != 0) {
                    consumed[0] = context.scene.mouseScrolled(x, y, wheel, shift());
                }
                int captured = context.scene.capturedPointerButton();
                if (captured >= 0) {
                    consumed[0] |= context.scene.mouseDragged(x, y, Mouse.isButtonDown(captured));
                }
                consumed[0] |= context.replacementReady();
            }
        });
        return consumed[0];
    }

    private static Context active(GuiScreen screen) {
        return current != null && current.screen == screen && current.client.currentScreen == screen && !current.closed
                ? current
                : null;
    }

    private static boolean shift() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static final class Context implements AutoCloseable {
        final Minecraft client;
        final GuiScreen screen;
        final String target;
        final GuiScene scene = new GuiScene();
        final GuiRowBudget rows = new GuiRowBudget();
        final GuiHostElementBudget budget = new GuiHostElementBudget(rows);
        final Map<String, Entry> entries = new LinkedHashMap<>();
        final MenuRoot root = new MenuRoot();
        final GuiMenuBuiltinViews engine;
        final Object world;
        final Object player;
        List<?> buttons;
        NativeClientScreenHost host;
        GuiMenuLayout layout;
        Entry winner;
        boolean closed;
        boolean layoutDirty = true;
        long cycle = Long.MIN_VALUE;
        long registryRevision = -1;

        Context(Minecraft client, GuiScreen screen, List<?> buttons) {
            this.client = client;
            this.screen = screen;
            this.buttons = buttons;
            target = target(screen);
            world = client.theWorld;
            player = client.thePlayer;
            engine = target.equals("minecraft:main") || target.equals("minecraft:pause")
                    ? new GuiMenuBuiltinViews(client, screen, target.equals("minecraft:main"))
                    : null;
            budget.prepare(this, 128).commit();
            if (engine != null) {
                root.add(engine);
            }
            scene.setContent(root);
            scene.updateEnvironment(client, client.fontRenderer, screen.width, screen.height, client.displayWidth,
                    client.displayHeight);
            if (engine != null) {
                engine.prepare();
            }
            scene.prepare();
        }

        void tick() {
            long next = ClientInputRuntime.router().cycle();
            if (BetaMoonAgent.isRegistered() && cycle == next && registryRevision == REGISTRY.revision()
                    && !layoutDirty) {
                return;
            }
            cycle = next;
            registryRevision = REGISTRY.revision();
            if (host != null && !host.session().active()) {
                host.close();
                host = null;
            }
            boolean requested = false;
            for (GuiMenuActivation claim : REGISTRY.activations()) {
                GuiMenuDefinition definition = REGISTRY.get(claim.key);
                requested |= definition != null && definition.target.equals(target);
            }
            if (!requested && host != null) {
                host.close();
                host = null;
            }
            if (requested && host == null && NativeDiscoveryRuntime.bindingsIfAvailable() != null) {
                host = new NativeClientScreenHost(client, screen, "<engine-menu>");
                host.initialize();
            }
            try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
                scene.updateEnvironment(client, client.fontRenderer, screen.width, screen.height, client.displayWidth,
                        client.displayHeight);
                scene.runActions();
                if (closed || client.currentScreen != screen) {
                    return;
                }
                reconcile();
                for (Entry entry : new ArrayList<>(entries.values())) {
                    if (entry.visible && !entry.failed) {
                        entry.controller.tick();
                        if (entry.failed) {
                            entry.close();
                            continue;
                        }
                        GuiScreenActivation activation = entry.controller.current();
                        if (activation == null || !activation.active() || entry.page == null
                                || !entry.page.attachedUsable()) {
                            entry.fail("document_unavailable");
                        } else {
                            entry.claim.publish("active", null, activation);
                        }
                    }
                    if (closed || client.currentScreen != screen) {
                        return;
                    }
                }
                if (engine != null) {
                    engine.prepare();
                }
                scene.prepare();
                layoutDirty = false;
            }
        }

        private void reconcile() {
            Map<String, GuiMenuDefinition> definitions = REGISTRY.snapshot();
            Map<String, GuiMenuActivation> claims = new LinkedHashMap<>();
            for (GuiMenuActivation claim : REGISTRY.activations()) {
                GuiMenuDefinition definition = definitions.get(claim.key);
                if (definition != null && definition.target.equals(target)) {
                    claims.put(claim.key, claim);
                }
            }
            for (Entry entry : new ArrayList<>(entries.values())) {
                if (entry.failed) {
                    entry.close();
                }
                if (claims.get(entry.definition.key) != entry.claim
                        || definitions.get(entry.definition.key) != entry.definition) {
                    entry.close();
                    entries.remove(entry.definition.key);
                }
            }
            for (GuiMenuActivation claim : claims.values()) {
                if (!entries.containsKey(claim.key)) {
                    entries.put(claim.key, new Entry(this, definitions.get(claim.key), claim));
                }
            }
            List<Rect> reserved = new ArrayList<>();
            if (buttons.size() > 1024) {
                for (Entry entry : entries.values()) {
                    entry.fail("native_controls_capacity");
                }
            } else {
                for (Object value : buttons) {
                    if (value instanceof GuiButton) {
                        GuiButton button = (GuiButton) value;
                        reserved.add(GuiNativeMenuActions.bounds(button));
                    }
                }
            }
            if (target.equals("minecraft:world_select")) {
                reserved.add(Rect.fromPositionAndSize(0, 32, screen.width, Math.max(0, screen.height - 96)));
            }
            if (target.equals("betamoon:world_create")) {
                reserved.add(Rect.fromPositionAndSize(screen.width / 2 - 100, 40, 200, 128));
            }
            if (engine != null) {
                reserved.addAll(engine.reservations(screen.width, screen.height));
            }
            layout = new GuiMenuLayout(screen.width, screen.height, reserved, engine == null ? 0 : 60);
            List<Entry> replacements = new ArrayList<>();
            for (Entry entry : entries.values()) {
                entry.visible = false;
                entry.refusal = null;
                if (entry.definition.replacement && !entry.failed) {
                    replacements.add(entry);
                }
            }
            replacements.sort(Comparator.comparingInt((Entry value) -> value.definition.priority).reversed()
                    .thenComparing(value -> value.definition.key));
            winner = replacements.isEmpty() ? null : replacements.get(0);
            List<GuiElement> order = new ArrayList<>();
            if (winner != null) {
                winner.visible = true;
                winner.bounds = layout.content;
                order.add(winner.mount);
            }
            for (String region : new String[]{"actions", "footer"}) {
                List<GuiMenuDefinition> ordered;
                try {
                    ordered = GuiMenuOrder.order(definitions, target, region, true);
                } catch (IllegalArgumentException invalid) {
                    for (Entry entry : entries.values()) {
                        if (entry.definition.region.equals(region)) {
                            entry.refusal = "ordering_anchor_unavailable";
                        }
                    }
                    continue;
                }
                List<Entry> group = new ArrayList<>();
                for (GuiMenuDefinition definition : ordered) {
                    Entry entry = entries.get(definition.key);
                    if (entry != null && !entry.failed) {
                        group.add(entry);
                    }
                }
                Rect bounds = layout.region(region);
                int available = bounds.getHeight() - Math.max(0, group.size() - 1) * 4;
                int height = group.isEmpty() ? 0 : available / group.size();
                for (int index = 0; index < group.size(); index++) {
                    Entry entry = group.get(index);
                    if (height < 20 || bounds.getWidth() < 24) {
                        entry.refusal = "region_capacity";
                    } else {
                        entry.visible = true;
                        entry.bounds = Rect.fromPositionAndSize(bounds.getLeft(),
                                bounds.getTop() + index * (height + 4), bounds.getWidth(), height);
                        order.add(entry.mount);
                    }
                }
            }
            for (Entry entry : entries.values()) {
                entry.mount.setVisible(entry.visible);
                if (!entry.visible && !entry.failed) {
                    entry.controller.suspend("menu_suspended");
                    entry.claim.publish(entry.refusal == null ? "suspended" : "refused",
                            entry.refusal == null ? "replacement_priority" : entry.refusal, entry.controller.current());
                }
            }
            if (engine != null) {
                order.add(engine);
            }
            root.reconcileChildren(order);
        }

        boolean replacementReady() {
            return !closed && client.currentScreen == screen && winner != null && winner.painted && winner.usable();
        }

        void input(Runnable callback) {
            if (host == null || !host.session().active()) {
                callback.run();
            } else {
                host.input(permit -> callback.run());
            }
            if (!closed && client.currentScreen == screen) {
                try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
                    scene.runActions();
                    for (Entry entry : new ArrayList<>(entries.values())) {
                        if (entry.visible && entry.page != null && entry.page.attachedUsable()) {
                            entry.page.flush();
                        }
                    }
                    scene.prepare();
                }
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            scene.cancelPointerCapture();
            for (Entry entry : new ArrayList<>(entries.values())) {
                entry.close();
                if (entry.claim.requested()) {
                    entry.claim.publish("queued", null, null);
                }
            }
            entries.clear();
            scene.setContent(null);
            if (host != null) {
                host.close();
            }
            rows.close();
        }

        private final class MenuRoot extends GuiContainer {
            @Override
            protected void arrangeChildren(GuiContext context) {
                for (Entry entry : entries.values()) {
                    if (entry.visible) {
                        entry.mount.arrange(context, entry.bounds);
                    }
                }
                if (engine != null) {
                    engine.arrange(context, getBounds());
                }
            }
        }
    }

    private static final class Entry implements GuiScreenController.Host, GuiScreenAttachment, AutoCloseable {
        final Context context;
        final GuiMenuDefinition definition;
        final GuiMenuActivation claim;
        final GuiDocumentMount mount = new GuiDocumentMount();
        final GuiScreenController controller = new GuiScreenController(GuiClientUiRuntime.screens(), this);
        GuiNativeScreenPage page;
        final Map<Object, GuiHostElementBudget.Claim> capacity = new IdentityHashMap<>();
        String refusal;
        Rect bounds = Rect.EMPTY;
        boolean visible;
        boolean painted;
        boolean failed;
        boolean retired;

        Entry(Context context, GuiMenuDefinition definition, GuiMenuActivation claim) {
            this.context = context;
            this.definition = definition;
            this.claim = claim;
            GuiInputLifetime input = new GuiInputLifetime(() -> usable());
            input.activate();
            mount.inputLifetime(input);
            mount.paintWhen(this::usable);
            mount.onFailure(error -> failure(definition.owner, definition.key + ".paint", error));
            try {
                GuiScreenDefinition screen = GuiClientUiRuntime.screens().getRequired(definition.screen);
                if (!screen.owner.equals(definition.owner)) {
                    throw new IllegalArgumentException("Menu screen belongs to another owner");
                }
                if (definition.replacement && !definition.target.equals("minecraft:main")
                        && !definition.target.equals("minecraft:pause")) {
                    fail("target_replacement_unavailable");
                } else {
                    GuiScreenController.OpenResult opened = controller.open(definition.owner, definition.screen,
                            Collections.emptyMap(), Collections.emptyMap());
                    if (opened.instance == null) {
                        fail(opened.reason);
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException failure) {
                fail("screen_unavailable");
            }
        }

        boolean usable() {
            return !retired && !failed && !mount.failed() && visible && claim.requested() && definition.available()
                    && context.client.currentScreen == context.screen && !context.closed
                    && (page == null || page.attachedUsable());
        }

        void fail(String reason) {
            failed = true;
            painted = false;
            claim.publish("refused", reason, null);
        }

        public long environment() {
            return context.client.theWorld == context.world && context.client.thePlayer == context.player ? 1 : 2;
        }

        public boolean available() {
            return context.host != null && context.client.fontRenderer != null && context.client.renderEngine != null;
        }

        public boolean worldAvailable() {
            Minecraft client = context.client;
            return client.theWorld != null && !client.theWorld.multiplayerWorld && client.thePlayer != null
                    && client.thePlayer.health > 0 && client.thePlayer.worldObj == client.theWorld;
        }

        public boolean paused() {
            return context.client.isGamePaused || context.screen.doesGuiPauseGame() || TeleportRuntime.frozen();
        }

        public boolean canOpen() {
            return !retired && claim.requested() && context.client.currentScreen == context.screen;
        }

        public boolean canNavigate() {
            return false;
        }

        public Object screenIdentity() {
            return context.client.currentScreen;
        }

        public boolean current(GuiScreenActivation activation) {
            return canOpen() && visible && !failed;
        }

        public GuiScreenController.Page prepare(GuiScreenActivation activation, GuiScreenController.History history) {
            GuiNativeScreenPage prepared = new GuiNativeScreenPage(context.client, activation,
                    GuiClientUiRuntime.screens(), GuiClientUiRuntime.components(), history, this);
            if (page == null) {
                page = prepared;
            }
            return prepared;
        }

        public void display(GuiScreenActivation activation) {
            // The actual native menu remains the host of this registered document.
        }

        public void fallback() {
            fail("document_closed");
        }

        public void failure(String owner, String path, Throwable failure) {
            fail("document_failed");
            LuaApiUtils.warnForScript(owner, "UI menu", path + ": " + failure.getMessage(), failure);
        }

        public GuiScreen nativeScreen() {
            return context.screen;
        }

        public GuiScene scene() {
            return context.scene;
        }

        public GuiRowBudget rowBudget() {
            return context.rows;
        }

        public boolean inputAvailable() {
            return !retired && !failed && visible && claim.requested() && definition.available() && !context.closed
                    && context.client.currentScreen == context.screen;
        }

        public void prepare(Object document, GuiComponentPlan plan) {
            capacity.put(document, context.budget.prepare(document, plan.rowElementAllowance()));
        }

        public void attach(GuiPresentationTree document) {
            mount.document(document);
        }

        public void replace(GuiPresentationTree document) {
            attach(document);
            painted = false;
        }

        public void commit(Object document) {
            GuiHostElementBudget.Claim pending = capacity.get(document);
            if (pending == null) {
                throw new IllegalStateException("Attached document has no capacity proposal");
            }
            pending.commit();
            capacity.remove(document);
        }

        public void abort(Object document) {
            GuiHostElementBudget.Claim pending = capacity.get(document);
            if (pending != null) {
                pending.close();
                capacity.remove(document);
            }
        }

        public void release(Object document) {
            abort(document);
            context.budget.remove(document);
        }

        public void detach() {
            mount.document(null);
            page = null;
            painted = false;
        }

        public void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation) {
            value.set("menu",
                    GuiMenuLuaAccess.context(definition.owner, scope, definition.target, definition.key,
                            name -> usable() && GuiNativeMenuActions.enabled(definition.target, name, context.buttons),
                            name -> request(activation, name)));
        }

        private GuiActionQueue.Submission request(GuiScreenActivation activation, String command) {
            if (!GuiNativeMenuActions.commands(definition.target).containsKey(command)) {
                throw new IllegalArgumentException("Unsupported native menu action: " + command);
            }
            GuiActionDefinition action = new GuiActionDefinition("menu_" + command, GuiActionDefinition.Scope.CLIENT,
                    GuiComponentSchema.EMPTY, new VarArgFunction() {
                        public Varargs invoke(Varargs arguments) {
                            if (!usable()) {
                                throw new IllegalStateException("Menu action host expired");
                            }
                            GuiNativeMenuActions.invoke(context.screen, definition.target, command, context.buttons);
                            return LuaValue.NIL;
                        }
                    });
            return controller.actions().submit(activation, action, Collections.emptyMap());
        }

        @Override
        public void close() {
            if (!retired) {
                retired = true;
                controller.reset("menu_detached");
                for (Object document : new ArrayList<>(capacity.keySet())) {
                    release(document);
                }
            }
        }
    }

    private GuiMenuRuntime() {
    }
}
