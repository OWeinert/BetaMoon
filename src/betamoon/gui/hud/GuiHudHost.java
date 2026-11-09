package betamoon.gui.hud;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.gui.interop.GuiOwnedResources;
import betamoon.gui.interop.NativeClientScreenHost;
import betamoon.gui.inventory.GuiInventoryRuntime;
import betamoon.gui.menu.GuiMenuRuntime;
import betamoon.gui.screen.GuiAttachedDocument;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiHostElementBudget;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.screen.GuiScreenController;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.client.ui.GuiScopedSnapshot;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.teleport.TeleportRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiChat;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ScaledResolution;
import org.luaj.vm2.LuaTable;

/**
 * One retained scene and resource boundary for a stable native client
 * environment.
 */
final class GuiHudHost implements AutoCloseable {
    final Minecraft client;
    private final GuiHudRegistry registry;
    private final Object nativeScreen;
    private final Object world;
    private final Object player;
    private final GuiScreen viewport = new GuiScreen();
    private final GuiScene scene = new GuiScene();
    private final GuiRowBudget rows = new GuiRowBudget();
    private final GuiHostElementBudget budget = new GuiHostElementBudget(rows);
    private final Root root = new Root();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<GuiHudNativePart, Entry> winners = new EnumMap<>(GuiHudNativePart.class);
    private final EnumSet<GuiHudNativePart> visited = EnumSet.noneOf(GuiHudNativePart.class);
    private final EnumSet<GuiHudNativePart> suppressed = EnumSet.noneOf(GuiHudNativePart.class);
    private final EnumSet<GuiHudNativePart> replacementsPainted = EnumSet.noneOf(GuiHudNativePart.class);
    private NativeClientScreenHost engine;
    private GuiScopedSnapshot observation;
    private GuiHudDefinition.Context context;
    private Rect safeArea = Rect.EMPTY;
    private Surface painting;
    private boolean paintingAdditive;
    private boolean closed;
    private boolean nativeSeen;
    private boolean captured;
    boolean nativeDrawing;
    private int preparedWidth = -1;
    private int preparedHeight = -1;
    private int preparedDisplayWidth = -1;
    private int preparedDisplayHeight = -1;
    private int preparedGuiScale = -1;
    private int mouseX;
    private int mouseY;
    private float partialTicks;

    GuiHudHost(Minecraft client, GuiHudRegistry registry) {
        this.client = client;
        this.registry = registry;
        nativeScreen = client.currentScreen;
        world = client.theWorld;
        player = client.thePlayer;
        engine = NativeClientScreenHost.prepareOverlay(client, viewport, "<engine-hud>", this::valid);
        try {
            engine.initialize();
            budget.prepare(this, 128).commit();
            scene.setContent(root);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    boolean valid() {
        return !closed && client.currentScreen == nativeScreen && client.theWorld == world
                && client.thePlayer == player;
    }

    boolean available() {
        return engine != null && engine.session().active();
    }

    boolean hasLiveClaims() {
        for (Entry entry : entries.values()) {
            if (!entry.failed && !entry.retired && entry.claim.requested() && entry.definition.available()) {
                return true;
            }
        }
        return false;
    }

    GuiScreenActivation instance(GuiHudActivation claim, GuiHudNativePart part) {
        Entry entry = entries.get(claim.key);
        if (entry == null || entry.claim != claim || !entry.eligible()) {
            return null;
        }
        Surface surface = part == null ? entry.additive : entry.parts.get(part);
        return surface == null || surface.document == null || !surface.document.usable()
                ? null
                : surface.document.controller().current();
    }

    private boolean worldAvailable() {
        return client.theWorld != null && !client.theWorld.multiplayerWorld && client.thePlayer != null
                && client.thePlayer.worldObj == client.theWorld && client.thePlayer.health > 0;
    }

    void tick() {
        try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
            ScaledResolution resolution = new ScaledResolution(client.gameSettings, client.displayWidth,
                    client.displayHeight);
            viewport.width = resolution.getScaledWidth();
            viewport.height = resolution.getScaledHeight();
            scene.updateEnvironment(client, client.fontRenderer, viewport.width, viewport.height, client.displayWidth,
                    client.displayHeight);
            context = visibilityContext();
            safeArea = GuiHudLayout.safeArea(viewport.width, viewport.height, reservations());
            engine.observe(this::observe);
            scene.runActions();
            if (!valid()) {
                return;
            }
            reconcile();
            chooseReplacements();
            List<GuiElement> content = new ArrayList<>();
            root.bounds.clear();
            for (Entry entry : ordered()) {
                if (!valid()) {
                    return;
                }
                if (entry.failed) {
                    entry.close();
                } else {
                    entry.update(content);
                }
            }
            root.reconcileChildren(content);
            scene.prepare();
            preparedWidth = viewport.width;
            preparedHeight = viewport.height;
            preparedDisplayWidth = client.displayWidth;
            preparedDisplayHeight = client.displayHeight;
            preparedGuiScale = client.gameSettings.guiScale;
        }
    }

    private GuiHudDefinition.Context visibilityContext() {
        if (client.currentScreen == null) {
            return worldAvailable() ? GuiHudDefinition.Context.GAMEPLAY : null;
        }
        if (client.currentScreen.getClass() == GuiChat.class) {
            return worldAvailable() ? GuiHudDefinition.Context.CHAT : null;
        }
        if (GuiInventoryRuntime.supported(client.currentScreen)) {
            return worldAvailable() ? GuiHudDefinition.Context.INVENTORY : null;
        }
        String target = GuiMenuRuntime.target(client.currentScreen);
        if ("minecraft:options".equals(target)) {
            return null;
        }
        if ("minecraft:pause".equals(target)) {
            return worldAvailable() ? GuiHudDefinition.Context.PAUSE : null;
        }
        return target != null || GuiClientUiRuntime.managed(client.currentScreen)
                ? GuiHudDefinition.Context.MENU
                : null;
    }

    private List<Rect> reservations() {
        List<Rect> occupied = new ArrayList<>();
        occupied.add(Rect.fromPositionAndSize(Math.max(0, viewport.width - 164), 0, 164, 40));
        if (worldAvailable()) {
            occupied.add(Rect.fromPositionAndSize(Math.max(0, viewport.width / 2 - 96),
                    Math.max(0, viewport.height - 48), 192, 48));
            int chatHeight = context == GuiHudDefinition.Context.CHAT ? 188 : 98;
            occupied.add(Rect.fromPositionAndSize(0, Math.max(0, viewport.height - 48 - chatHeight),
                    Math.min(324, viewport.width), chatHeight));
        }
        if (context == GuiHudDefinition.Context.MENU) {
            occupied.add(Rect.fromPositionAndSize(0, Math.max(0, viewport.height - 64), viewport.width, 64));
        }
        return occupied;
    }

    private void observe() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldAvailable", worldAvailable());
        values.put("context", context == null ? "unavailable" : context.name);
        values.put("hideGui", client.gameSettings.hideGUI);
        values.put("debug", client.gameSettings.showDebugInfo);
        values.put("viewport", rectangle(Rect.fromPositionAndSize(0, 0, viewport.width, viewport.height)));
        values.put("safeArea", rectangle(safeArea));
        if (worldAvailable()) {
            Map<String, Object> actor = new LinkedHashMap<>();
            actor.put("health", client.thePlayer.health);
            actor.put("armor", client.thePlayer.getPlayerArmorValue());
            actor.put("air", client.thePlayer.air);
            actor.put("selectedSlot", client.thePlayer.inventory.currentItem + 1);
            actor.put("onGround", client.thePlayer.onGround);
            actor.put("x", client.thePlayer.posX);
            actor.put("y", client.thePlayer.posY);
            actor.put("z", client.thePlayer.posZ);
            actor.put("motionX", client.thePlayer.motionX);
            actor.put("motionY", client.thePlayer.motionY);
            actor.put("motionZ", client.thePlayer.motionZ);
            values.put("player", actor);
        }
        observation = new GuiScopedSnapshot(values, "native:hud", values);
    }

    private static Map<String, Object> rectangle(Rect rectangle) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("x", rectangle.getLeft());
        values.put("y", rectangle.getTop());
        values.put("width", rectangle.getWidth());
        values.put("height", rectangle.getHeight());
        return values;
    }

    private void reconcile() {
        Map<String, GuiHudActivation> requested = new LinkedHashMap<>();
        for (GuiHudActivation activation : registry.activations()) {
            requested.put(activation.key, activation);
        }
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (requested.get(entry.definition.key) != entry.claim
                    || registry.get(entry.definition.key) != entry.definition) {
                entry.close();
                entries.remove(entry.definition.key);
            }
        }
        for (GuiHudActivation claim : requested.values()) {
            if (!entries.containsKey(claim.key)) {
                entries.put(claim.key, new Entry(registry.getRequired(claim.key), claim));
            }
        }
    }

    private List<Entry> ordered() {
        List<Entry> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparingInt((Entry entry) -> entry.definition.priority)
                .thenComparingLong(entry -> entry.claim.sequence));
        return result;
    }

    private void chooseReplacements() {
        winners.clear();
        for (Entry entry : ordered()) {
            if (entry.eligible()) {
                for (Map.Entry<GuiHudNativePart, GuiHudDefinition.NativePart> part : entry.definition.nativeParts
                        .entrySet()) {
                    if (part.getValue().replace != null) {
                        winners.put(part.getKey(), entry);
                    }
                }
            }
        }
    }

    void beginNative(int mouseX, int mouseY, float partialTicks) {
        beginFrame(mouseX, mouseY, partialTicks);
        nativeSeen = true;
        nativeDrawing = true;
    }

    private void beginFrame(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        visited.clear();
        suppressed.clear();
        replacementsPainted.clear();
        captured = false;
        for (Entry entry : entries.values()) {
            entry.additivePainted = false;
        }
    }

    private boolean prepared() {
        return valid() && available() && preparedWidth == viewport.width && preparedHeight == viewport.height
                && preparedDisplayWidth == client.displayWidth && preparedDisplayHeight == client.displayHeight
                && preparedGuiScale == client.gameSettings.guiScale && viewport.width > 0 && viewport.height > 0;
    }

    boolean nativePart(GuiHudNativePart part) {
        if (!nativeDrawing || !prepared()) {
            return false;
        }
        if (!visited.add(part)) {
            return suppressed.contains(part);
        }
        boolean hidden = false;
        for (Entry entry : entries.values()) {
            GuiHudDefinition.NativePart requested = entry.definition.nativeParts.get(part);
            hidden |= entry.eligible() && requested != null && requested.hide;
        }
        Entry winner = winners.get(part);
        Surface replacement = winner == null ? null : winner.parts.get(part);
        if (replacement != null && replacement.presentationAvailable() && replacement.document != null
                && replacement.document.usable()) {
            painting = replacement;
            try {
                paintContent();
                if (replacement.document.usable()) {
                    hidden = true;
                    replacementsPainted.add(part);
                }
            } finally {
                painting = null;
            }
        }
        if (hidden) {
            suppressed.add(part);
        }
        return hidden;
    }

    void clientOverlay(int mouseX, int mouseY, float partialTicks) {
        if (!prepared()) {
            return;
        }
        if (!nativeSeen) {
            beginFrame(mouseX, mouseY, partialTicks);
        }
        nativeDrawing = false;
        paintingAdditive = true;
        try {
            paintContent();
            engine.paint(scene::renderLayers);
            for (Entry entry : entries.values()) {
                boolean active = entry.eligible() && (entry.additivePainted || entry.nativeApplied());
                if (active) {
                    entry.claim.publish("active", null,
                            entry.additive == null || entry.additive.document == null
                                    ? null
                                    : entry.additive.document.controller().current());
                } else if (!entry.failed && entry.claim.requested()) {
                    entry.claim.publish("suspended", entry.eligible() ? "native_part_unavailable" : "visibility", null);
                }
            }
        } finally {
            paintingAdditive = false;
            nativeSeen = false;
        }
    }

    private void paintContent() {
        if (!captured) {
            for (Entry entry : entries.values()) {
                for (Surface surface : entry.surfaces()) {
                    if (surface.document != null && surface.document.usable()) {
                        surface.document.page().sampleAttached();
                    }
                }
            }
        }
        engine.paint(() -> {
            if (captured) {
                scene.renderDisplayedContent();
            } else {
                scene.renderContent(mouseX, mouseY, partialTicks);
                captured = true;
            }
        });
    }

    void worldTick() {
        for (Entry entry : new ArrayList<>(entries.values())) {
            for (Surface surface : entry.surfaces()) {
                if (surface.document != null && surface.document.usable()) {
                    surface.document.controller().worldTick();
                }
            }
        }
    }

    void failed(Throwable failure) {
        preparedWidth = -1;
        for (Entry entry : entries.values()) {
            if (!entry.failed && entry.claim.requested()) {
                entry.fail(entry.definition.key, failure);
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            for (Entry entry : new ArrayList<>(entries.values())) {
                entry.close();
                if (entry.claim.requested() && !entry.failed) {
                    entry.claim.publish("queued", null, null);
                }
            }
        } finally {
            entries.clear();
            winners.clear();
            root.bounds.clear();
            root.clear();
            scene.setContent(null);
            rows.close();
            if (engine != null) {
                engine.close();
            }
            engine = null;
            observation = null;
            painting = null;
        }
    }

    private final class Root extends GuiContainer {
        final Map<GuiElement, Rect> bounds = new IdentityHashMap<>();

        @Override
        protected void arrangeChildren(GuiContext context) {
            for (GuiElement child : getChildren()) {
                child.arrange(context, bounds.getOrDefault(child, Rect.EMPTY));
            }
        }
    }

    private final class Entry implements AutoCloseable {
        final GuiHudDefinition definition;
        final GuiHudActivation claim;
        final Surface additive;
        final Map<GuiHudNativePart, Surface> parts = new EnumMap<>(GuiHudNativePart.class);
        final List<Surface> surfaces;
        boolean failed;
        boolean retired;
        boolean additivePainted;

        Entry(GuiHudDefinition definition, GuiHudActivation claim) {
            this.definition = definition;
            this.claim = claim;
            additive = definition.screen == null ? null : new Surface(this, null, definition.screen);
            for (Map.Entry<GuiHudNativePart, GuiHudDefinition.NativePart> part : definition.nativeParts.entrySet()) {
                if (part.getValue().replace != null) {
                    parts.put(part.getKey(), new Surface(this, part.getKey(), part.getValue().replace));
                }
            }
            List<Surface> views = new ArrayList<>();
            if (additive != null) {
                views.add(additive);
            }
            views.addAll(parts.values());
            surfaces = Collections.unmodifiableList(views);
        }

        boolean eligible() {
            return !retired && !failed && valid() && claim.requested() && definition.available() && context != null
                    && (context == GuiHudDefinition.Context.MENU || worldAvailable())
                    && definition.contexts.contains(context) && (!client.gameSettings.hideGUI || definition.hideGui)
                    && (!client.gameSettings.showDebugInfo || definition.debug);
        }

        List<Surface> surfaces() {
            return surfaces;
        }

        void update(List<GuiElement> content) {
            for (Surface surface : surfaces()) {
                boolean selected = surface.presentationAvailable();
                if (selected && surface.document == null) {
                    surface.open();
                }
                if (surface.document == null) {
                    continue;
                }
                surface.document.mount().setVisible(selected);
                if (selected) {
                    surface.document.tick();
                    if (surface.document.usable()) {
                        content.add(surface.document.mount());
                        root.bounds.put(surface.document.mount(),
                                surface.part == null && definition.safeArea
                                        ? safeArea
                                        : Rect.fromPositionAndSize(0, 0, viewport.width, viewport.height));
                    }
                } else {
                    surface.document.suspend("hud_suspended");
                }
            }
        }

        boolean nativeApplied() {
            for (Map.Entry<GuiHudNativePart, GuiHudDefinition.NativePart> part : definition.nativeParts.entrySet()) {
                if (part.getValue().hide && visited.contains(part.getKey())
                        || winners.get(part.getKey()) == this && replacementsPainted.contains(part.getKey())) {
                    return true;
                }
            }
            return false;
        }

        void fail(String path, Throwable failure) {
            failed = true;
            claim.publish("refused", "document_failed", null);
            LuaApiUtils.warnForScript(definition.owner, "UI HUD", path + ": " + failure.getMessage(), failure);
        }

        @Override
        public void close() {
            if (retired) {
                return;
            }
            retired = true;
            for (Surface surface : surfaces()) {
                if (surface.document != null) {
                    surface.document.close();
                    surface.document = null;
                }
            }
        }
    }

    private final class Surface implements GuiAttachedDocument.Host {
        final Entry entry;
        final GuiHudNativePart part;
        final String screen;
        GuiAttachedDocument document;

        Surface(Entry entry, GuiHudNativePart part, String screen) {
            this.entry = entry;
            this.part = part;
            this.screen = screen;
        }

        void open() {
            document = new GuiAttachedDocument(this, entry.definition.owner, screen);
            document.mount().paintWhen(() -> {
                boolean paints = document != null && document.usable()
                        && (part == null ? paintingAdditive : painting == this);
                if (paints && part == null) {
                    entry.additivePainted = true;
                }
                return paints;
            });
            try {
                GuiScreenController.OpenResult result = document.open();
                if (result.instance == null) {
                    throw new IllegalArgumentException("HUD screen refused: " + result.reason);
                }
            } catch (RuntimeException failure) {
                entry.fail(screen, failure);
            }
        }

        public Minecraft client() {
            return client;
        }

        public GuiScreen screen() {
            return viewport;
        }

        public GuiScene scene() {
            return scene;
        }

        public GuiRowBudget rowBudget() {
            return rows;
        }

        public GuiHostElementBudget elementBudget() {
            return budget;
        }

        public boolean available() {
            return GuiHudHost.this.available();
        }

        public boolean attached() {
            return !entry.retired && valid() && entry.claim.requested();
        }

        public boolean presentationAvailable() {
            return entry.eligible()
                    && (part == null ? !entry.definition.safeArea || !safeArea.isEmpty() : winners.get(part) == entry);
        }

        public boolean inputAvailable() {
            return presentationAvailable() && part == null && entry.definition.interactive
                    && client.currentScreen != null;
        }

        public long environment() {
            return valid() ? 1 : 2;
        }

        public boolean worldAvailable() {
            return GuiHudHost.this.worldAvailable();
        }

        public boolean paused() {
            return client.isGamePaused || TeleportRuntime.frozen();
        }

        public void closed() {
            if (!entry.failed) {
                entry.claim.close();
            }
        }

        public Map<String, Object> nativeData() {
            return observation == null
                    ? Collections.emptyMap()
                    : Collections.singletonMap("hud", observation.revision());
        }

        public void documentChanged() {
            preparedWidth = -1;
        }

        public GuiOwnedResources resources(String owner, GuiResourceDefinitions definitions) {
            return GuiOwnedResources.overlay(client, viewport, owner, definitions, this::presentationAvailable);
        }

        public void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation) {
            LuaTable hud = observation.access(scope, "snapshot");
            hud.set("key", entry.definition.key);
            value.set("hud", hud);
        }

        public void failure(String owner, String path, Throwable failure) {
            entry.fail(path, failure);
        }
    }
}
