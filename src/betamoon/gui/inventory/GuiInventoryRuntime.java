package betamoon.gui.inventory;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.graphics.GraphicsStateGuard;
import betamoon.gui.interop.NativeClientScreenHost;
import betamoon.gui.screen.GuiAttachedDocument;
import betamoon.gui.screen.GuiHostElementBudget;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.screen.GuiScreenController;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.presentation.GuiCapability;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.client.ui.GuiInventoryLuaAccess;
import betamoon.luaapi.client.ui.GuiScopedSnapshot;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.teleport.TeleportRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiInventory;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Presentation attached to the real player inventory; native item transactions
 * remain untouched.
 */
public final class GuiInventoryRuntime {
    private static final GuiInventoryRegistry REGISTRY = new GuiInventoryRegistry();
    private static Context current;
    private static WeakReference<GuiScreen> rejected = new WeakReference<>(null);
    private static long rejectedRevision;

    public static GuiInventoryRegistry registry() {
        return REGISTRY;
    }

    public static boolean supported(GuiScreen screen) {
        return screen != null && screen.getClass() == GuiInventory.class;
    }

    public static void initialized(Minecraft client, GuiScreen screen) {
        if (current != null && current.client == client && current.screen == screen) {
            current.displayedWidth = -1;
            current.preparedWidth = -1;
        }
    }

    public static void changing(Minecraft client, GuiScreen next) {
        if (current != null && current.client == client && current.screen != next) {
            closeCurrent();
        }
    }

    public static void closed(GuiScreen screen) {
        if (current != null && current.screen == screen) {
            closeCurrent();
        }
    }

    private static void closeCurrent() {
        Context previous = current;
        current = null;
        if (previous != null) {
            previous.close();
        }
    }

    public static void tick(Minecraft client) {
        if (current != null && (current.client != client || client.currentScreen != current.screen
                || client.theWorld != current.world || client.thePlayer != current.player
                || !current.host.session().active())) {
            closeCurrent();
        }
        if (client == null || !supported(client.currentScreen)) {
            rejected.clear();
            return;
        }
        if (rejected.get() == client.currentScreen && rejectedRevision == REGISTRY.revision()) {
            return;
        }
        if (current == null && !REGISTRY.activations().isEmpty()
                && NativeDiscoveryRuntime.bindingsIfAvailable() != null) {
            try {
                current = new Context(client, (GuiInventory) client.currentScreen);
            } catch (IllegalArgumentException | IllegalStateException failure) {
                rejected = new WeakReference<>(client.currentScreen);
                rejectedRevision = REGISTRY.revision();
                for (GuiInventoryActivation claim : REGISTRY.activations()) {
                    claim.publish("refused", "native_schema_unavailable", null);
                }
                return;
            }
        }
        if (current != null) {
            current.tick();
        }
    }

    public static void worldTick(Minecraft client, World world) {
        Context context = active(client == null ? null : client.currentScreen);
        if (context != null && context.client == client && context.world == world && context.selected != null
                && context.selected.document != null && context.selected.eligible()
                && context.selected.document.usable()) {
            context.selected.document.controller().worldTick();
        }
    }

    /**
     * Geometry is installed before GuiContainer computes any native panel origin or
     * slot coordinates.
     */
    public static void beforeDraw(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        Context context = active(screen);
        if (context != null) {
            context.beforeDraw(mouseX, mouseY, partialTicks);
        }
    }

    public static void afterDraw(GuiScreen screen) {
        Context context = active(screen);
        if (context != null) {
            context.afterDraw();
            if (REGISTRY.activations().isEmpty() && context.geometry.installed() == null) {
                closeCurrent();
            }
        }
    }

    public static void failedDraw(GuiScreen screen) {
        Context context = active(screen);
        if (context != null) {
            context.displayedWidth = -1;
        }
    }

    public static boolean background(GuiScreen screen) {
        Context context = active(screen);
        if (context == null || context.frameEntry == null || context.geometry.installed() == null) {
            return false;
        }
        context.paintBackground();
        return true;
    }

    public static boolean foreground(GuiScreen screen) {
        Context context = active(screen);
        if (context == null || context.frameEntry == null || context.geometry.installed() == null) {
            return false;
        }
        context.paintLabel();
        return true;
    }

    /**
     * The verified native cursor-stack reads occur after slots/hover and before
     * cursor/tooltips.
     */
    public static void beforeCursor(InventoryPlayer inventory) {
        Context context = current;
        if (context != null && !context.closed && context.client.currentScreen == context.screen
                && context.client.thePlayer != null && context.client.thePlayer.inventory == inventory
                && context.frameEntry != null && !context.layersPainted) {
            context.paintLayers();
        }
    }

    public static boolean suppressHover(GuiScreen screen) {
        Context context = active(screen);
        return context != null && context.frameEntry != null && context.scene.hasModalLayer();
    }

    public static boolean mouse(GuiScreen screen) {
        Context context = active(screen);
        if (context == null) {
            return false;
        }
        int button = Mouse.getEventButton();
        boolean[] handled = {false};
        context.input(() -> {
            int x = Mouse.getEventX() * screen.width / context.client.displayWidth;
            int y = screen.height - Mouse.getEventY() * screen.height / context.client.displayHeight - 1;
            boolean ready = context.inputReady();
            if (button >= 0) {
                boolean down = Mouse.getEventButtonState();
                handled[0] = down
                        ? ready && context.scene.mousePressed(x, y, button)
                        : context.scene.mouseReleased(x, y, button);
                handled[0] |= !context.nativeInputReady();
                if (handled[0] && down) {
                    ClientInputRuntime.router().claimGuiGesture(button, screen);
                }
            } else if (ready) {
                int wheel = Mouse.getEventDWheel();
                if (wheel != 0) {
                    handled[0] = context.scene.mouseScrolled(x, y, wheel, shift());
                }
                int captured = context.scene.capturedPointerButton();
                if (captured >= 0) {
                    handled[0] |= context.scene.mouseDragged(x, y, Mouse.isButtonDown(captured));
                }
            }
        });
        return handled[0];
    }

    public static boolean keyboard(GuiScreen screen) {
        Context context = active(screen);
        if (context == null || !context.inputReady()) {
            return false;
        }
        int key = Keyboard.getEventKey();
        if (Keyboard.getEventKeyState() && key == Keyboard.KEY_ESCAPE && !context.scene.hasModalLayer()) {
            return false;
        }
        boolean[] handled = {false};
        context.input(() -> {
            boolean down = Keyboard.getEventKeyState();
            handled[0] = down
                    ? context.scene.keyTyped(Keyboard.getEventCharacter(), key, shift())
                    : context.scene.keyReleased(key, shift());
            if (handled[0] && down && key > 0) {
                ClientInputRuntime.router().claimGuiKey(key, screen);
            }
        });
        return handled[0];
    }

    private static Context active(GuiScreen screen) {
        return current != null && !current.closed && current.screen == screen && current.client.currentScreen == screen
                ? current
                : null;
    }

    private static boolean shift() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static final class Context implements AutoCloseable {
        final Minecraft client;
        final GuiInventory screen;
        final GuiScene scene = new GuiScene();
        final GuiRowBudget rows = new GuiRowBudget();
        final GuiHostElementBudget budget = new GuiHostElementBudget(rows);
        final Root root = new Root();
        final Map<String, Entry> entries = new LinkedHashMap<>();
        GuiInventoryNativeState geometry;
        NativeClientScreenHost host;
        Object world;
        Object player;
        Entry selected;
        Entry frameEntry;
        Rect panel = Rect.EMPTY;
        boolean closed;
        boolean conflicted;
        boolean backgroundPainted;
        boolean layersPainted;
        int preparedWidth = -1;
        int preparedHeight = -1;
        int displayedWidth = -1;
        int displayedHeight = -1;
        int mouseX;
        int mouseY;
        float partialTicks;

        Context(Minecraft client, GuiInventory screen) {
            this.client = client;
            this.screen = screen;
            world = client.theWorld;
            player = client.thePlayer;
            geometry = new GuiInventoryNativeState(client, screen);
            budget.prepare(this, 128).commit();
            host = new NativeClientScreenHost(client, screen, "<engine-inventory>");
            try {
                host.initialize();
                scene.setContent(root);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void tick() {
            if (conflicted) {
                return;
            }
            try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
                if (!geometry.matches()) {
                    conflict();
                    return;
                }
                scene.updateEnvironment(client, client.fontRenderer, screen.width, screen.height, client.displayWidth,
                        client.displayHeight);
                scene.runActions();
                if (closed || client.currentScreen != screen) {
                    return;
                }
                reconcile();
                if (selected != null && selected.document != null) {
                    selected.document.tick();
                    if (closed || client.currentScreen != screen) {
                        return;
                    }
                    if (!selected.failed && !selected.document.usable()) {
                        selected.fail("document_unavailable");
                    }
                    if (!selected.failed && selected.definition.backgroundImage != null) {
                        selected.background = selected.document.page()
                                .preparedDrawable(selected.definition.backgroundImage);
                    }
                }
                if (selected != null && !selected.failed && !selected.definition.preview.isEmpty()) {
                    host.session().prepare(() -> GuiInventoryPreview.prepare(client));
                }
                scene.getContext().getRenderer().preparePackagedTexture("/gui/inventory.png");
                scene.prepare();
                preparedWidth = screen.width;
                preparedHeight = screen.height;
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                if (selected != null) {
                    selected.failure(selected.definition.owner, selected.definition.key + ".update", failure);
                }
            }
        }

        private void reconcile() {
            Map<String, GuiInventoryActivation> requested = new LinkedHashMap<>();
            for (GuiInventoryActivation claim : REGISTRY.activations()) {
                requested.put(claim.key, claim);
            }
            for (Entry entry : new ArrayList<>(entries.values())) {
                if (requested.get(entry.definition.key) != entry.claim
                        || REGISTRY.get(entry.definition.key) != entry.definition) {
                    entry.close();
                    entries.remove(entry.definition.key);
                }
            }
            for (GuiInventoryActivation claim : requested.values()) {
                if (!entries.containsKey(claim.key)) {
                    entries.put(claim.key, new Entry(this, REGISTRY.getRequired(claim.key), claim));
                }
            }
            List<Entry> eligible = new ArrayList<>();
            for (Entry entry : entries.values()) {
                if (entry.failed) {
                    entry.close();
                } else if (!entry.worldAvailable()) {
                    entry.claim.publish("suspended", "world_unavailable", null);
                } else {
                    try {
                        entry.layout.panel(screen.width, screen.height);
                        eligible.add(entry);
                    } catch (IllegalArgumentException tiny) {
                        entry.claim.publish("suspended", "viewport_capacity", null);
                    }
                }
            }
            eligible.sort(Comparator.comparingInt((Entry value) -> value.definition.priority).reversed()
                    .thenComparing(value -> value.definition.key));
            Entry previous = selected;
            selected = eligible.isEmpty() ? null : eligible.get(0);
            if (previous != selected) {
                displayedWidth = -1;
            }
            List<GuiElement> visible = new ArrayList<>();
            for (Entry entry : entries.values()) {
                boolean winner = entry == selected && !entry.failed;
                if (winner && entry.document == null && entry.definition.screen != null) {
                    entry.openDocument();
                }
                if (entry.document != null) {
                    entry.document.mount().setVisible(winner);
                    if (winner) {
                        visible.add(entry.document.mount());
                    } else {
                        entry.document.suspend("inventory_suspended");
                    }
                }
                if (!winner && !entry.failed && eligible.contains(entry)) {
                    entry.claim.publish("suspended", "replacement_priority",
                            entry.document == null ? null : entry.document.controller().current());
                }
            }
            root.reconcileChildren(visible);
        }

        void beforeDraw(int mouseX, int mouseY, float partialTicks) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTicks = partialTicks;
            backgroundPainted = false;
            layersPainted = false;
            frameEntry = null;
            if (conflicted || preparedWidth != screen.width || preparedHeight != screen.height || selected == null
                    || !selected.eligible() || selected.document != null && !selected.document.usable()) {
                geometry.restore();
                return;
            }
            try {
                if (!geometry.matches()) {
                    conflict();
                    geometry.restore();
                    return;
                }
                panel = selected.layout.panel(screen.width, screen.height);
                geometry.install(selected.layout);
                frameEntry = selected;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                selected.failure(selected.definition.owner, selected.definition.key + ".geometry", failure);
                geometry.restore();
            }
        }

        void afterDraw() {
            displayedWidth = screen.width;
            displayedHeight = screen.height;
            if (frameEntry != null && frameEntry.eligible() && backgroundPainted && layersPainted) {
                frameEntry.claim.publish("active", null,
                        frameEntry.document == null ? null : frameEntry.document.controller().current());
            }
        }

        void paintBackground() {
            Entry entry = frameEntry;
            try {
                paint(() -> {
                    GuiInventoryProfile profile = entry.definition;
                    if (profile.background.equals("native")) {
                        scene.getContext().getRenderer().drawPackagedTexture("/gui/inventory.png", panel, 0, 0,
                                176.0F / 256, 166.0F / 256, 255);
                    } else if (profile.background.equals("panel")) {
                        scene.getContext().getRenderer().drawPopupPanel(panel);
                    }
                    if (entry.background != null) {
                        entry.background.draw(scene.getContext(), panel);
                    }
                    Rect preview = entry.layout.preview;
                    if (!preview.isEmpty()) {
                        GuiInventoryPreview.draw(client,
                                Rect.fromPositionAndSize(panel.getLeft() + preview.getLeft(),
                                        panel.getTop() + preview.getTop(), preview.getWidth(), preview.getHeight()),
                                mouseX, mouseY, screen.width, screen.height);
                    }
                    if (entry.document != null) {
                        entry.document.page().sampleAttached();
                    }
                    scene.renderContent(mouseX, mouseY, partialTicks);
                    if (profile.slotFrames) {
                        paintSlotFrames(entry.layout);
                    }
                });
                backgroundPainted = entry.eligible();
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                entry.failure(entry.definition.owner, entry.definition.key + ".paint", failure);
            }
            if (!backgroundPainted) {
                try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthMask(false);
                    scene.getContext().getRenderer().drawPopupPanel(panel);
                    paintSlotFrames(entry.layout);
                }
            }
        }

        private void paintSlotFrames(GuiInventoryLayout layout) {
            for (GuiInventoryProfile.Position slot : layout.slots) {
                int x = panel.getLeft() + slot.x;
                int y = panel.getTop() + slot.y;
                scene.getContext().getRenderer().drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
                scene.getContext().getRenderer().drawRect(x, y, x + 17, y + 17, 0xFFFFFFFF);
                scene.getContext().getRenderer().drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
            }
        }

        void paintLayers() {
            layersPainted = true;
            if (!backgroundPainted || frameEntry == null || !frameEntry.eligible()) {
                return;
            }
            try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
                GL11.glTranslatef(-panel.getLeft(), -panel.getTop(), 0);
                paint(scene::renderLayers);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                frameEntry.failure(frameEntry.definition.owner, frameEntry.definition.key + ".layers", failure);
            }
        }

        void paintLabel() {
            if (frameEntry.definition.craftingLabel) {
                GuiInventoryProfile.Position crafting = frameEntry.layout.slots.get(1);
                client.fontRenderer.drawString("Crafting", crafting.x - 2, crafting.y - 10, 0x404040);
            }
        }

        private void paint(Runnable painter) {
            if (host != null && host.session().active()) {
                host.paint(painter);
            }
        }

        boolean inputReady() {
            return nativeInputReady() && frameEntry != null && frameEntry == selected && frameEntry.eligible()
                    && backgroundPainted && layersPainted;
        }

        boolean nativeInputReady() {
            return displayedWidth == screen.width && displayedHeight == screen.height
                    && (conflicted || geometry.matches()) && (geometry.installed() == null
                            || frameEntry != null && frameEntry.eligible() && backgroundPainted && layersPainted);
        }

        void input(Runnable input) {
            if (host != null && host.session().active()) {
                host.input(permit -> input.run());
            } else {
                input.run();
            }
            if (!closed && client.currentScreen == screen) {
                try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
                    scene.runActions();
                    if (selected != null && selected.document != null && selected.document.usable()) {
                        selected.document.page().flush();
                    }
                    scene.prepare();
                }
            }
        }

        void conflict() {
            conflicted = true;
            for (Entry entry : entries.values()) {
                entry.fail("geometry_conflict");
                entry.close();
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                geometry.restore();
                for (Entry entry : new ArrayList<>(entries.values())) {
                    entry.close();
                    if (entry.claim.requested()) {
                        entry.claim.publish("queued", null, null);
                    }
                }
            } finally {
                entries.clear();
                root.clear();
                scene.setContent(null);
                rows.close();
                if (host != null) {
                    host.close();
                }
                host = null;
                geometry = null;
                selected = null;
                frameEntry = null;
                world = null;
                player = null;
            }
        }

        private final class Root extends GuiContainer {
            @Override
            protected void arrangeChildren(GuiContext context) {
                for (GuiElement child : getChildren()) {
                    child.arrange(context, getBounds());
                }
            }
        }
    }

    private static final class Entry implements GuiAttachedDocument.Host, AutoCloseable {
        final Context context;
        final GuiInventoryProfile definition;
        final GuiInventoryActivation claim;
        GuiInventoryLayout layout;
        GuiAttachedDocument document;
        GuiDrawable background;
        GuiScopedSnapshot publishedLayout;
        int publishedWidth = -1;
        int publishedHeight = -1;
        boolean publishedDisplayed;
        boolean failed;
        boolean retired;

        Entry(Context context, GuiInventoryProfile definition, GuiInventoryActivation claim) {
            this.context = context;
            this.definition = definition;
            this.claim = claim;
            try {
                layout = context.geometry.propose(definition);
            } catch (IllegalArgumentException invalid) {
                fail("invalid_geometry");
            }
        }

        void openDocument() {
            document = new GuiAttachedDocument(this, definition.owner, definition.screen);
            try {
                GuiScreenController.OpenResult result = document.open();
                if (result.instance == null) {
                    fail(result.reason);
                }
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                fail("screen_unavailable");
            }
        }

        boolean eligible() {
            return attached() && !failed && context.selected == this && worldAvailable();
        }

        void fail(String reason) {
            failed = true;
            claim.publish("refused", reason, null);
        }

        public Minecraft client() {
            return context.client;
        }

        public GuiScreen screen() {
            return context.screen;
        }

        public GuiScene scene() {
            return context.scene;
        }

        public GuiRowBudget rowBudget() {
            return context.rows;
        }

        public GuiHostElementBudget elementBudget() {
            return context.budget;
        }

        public Set<GuiCapability> capabilities() {
            return EnumSet.of(GuiCapability.NATIVE_INVENTORY);
        }

        public boolean available() {
            return context.host != null && context.host.session().active();
        }

        public boolean attached() {
            return !retired && !context.closed && claim.requested() && definition.available()
                    && context.client.currentScreen == context.screen;
        }

        public boolean inputAvailable() {
            return eligible();
        }

        public long environment() {
            return context.client.theWorld == context.world && context.client.thePlayer == context.player ? 1 : 2;
        }

        public boolean worldAvailable() {
            Minecraft client = context.client;
            return client.theWorld != null && !client.theWorld.multiplayerWorld && client.thePlayer != null
                    && client.thePlayer.worldObj == client.theWorld && client.thePlayer.health > 0;
        }

        public boolean paused() {
            return context.client.isGamePaused || TeleportRuntime.frozen();
        }

        public void closed() {
            fail("document_closed");
        }

        public void documentChanged() {
            context.displayedWidth = -1;
        }

        public void failure(String owner, String path, Throwable failure) {
            fail("document_failed");
            LuaApiUtils.warnForScript(owner, "UI inventory", path + ": " + failure.getMessage(), failure);
        }

        public void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation) {
            value.set("inventory", GuiInventoryLuaAccess.context(scope, snapshot()));
        }

        private GuiScopedSnapshot snapshot() {
            boolean displayed = context.displayedWidth == context.screen.width
                    && context.displayedHeight == context.screen.height && context.frameEntry == this && eligible()
                    && context.backgroundPainted && context.layersPainted;
            if (publishedLayout != null && publishedWidth == context.screen.width
                    && publishedHeight == context.screen.height && publishedDisplayed == displayed) {
                return publishedLayout;
            }
            Rect panel = layout.panel(context.screen.width, context.screen.height);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("panel", rect(panel));
            result.put("preview", rect(layout.preview));
            result.put("displayed", displayed);
            List<Map<String, Object>> slots = new ArrayList<>();
            for (GuiInventorySchema.Slot slot : GuiInventorySchema.SLOTS) {
                GuiInventoryProfile.Position position = layout.slots.get(slot.nativeIndex);
                Map<String, Object> values = rect(Rect.fromPositionAndSize(position.x, position.y, 16, 16));
                values.put("id", slot.id);
                values.put("group", slot.group.name);
                values.put("index", slot.index);
                slots.add(values);
            }
            result.put("slots", slots);
            publishedWidth = context.screen.width;
            publishedHeight = context.screen.height;
            publishedDisplayed = displayed;
            publishedLayout = new GuiScopedSnapshot(result);
            return publishedLayout;
        }

        private static Map<String, Object> rect(Rect rect) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("x", rect.getLeft());
            value.put("y", rect.getTop());
            value.put("width", rect.getWidth());
            value.put("height", rect.getHeight());
            return value;
        }

        @Override
        public void close() {
            if (!retired) {
                retired = true;
                if (document != null) {
                    document.close();
                }
                background = null;
                publishedLayout = null;
            }
        }
    }

    private GuiInventoryRuntime() {
    }
}
