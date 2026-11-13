package betamoon.gui.hud;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.GuiInputOwner;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.gui.graphics.GraphicsTransform;
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
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiChat;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ScaledResolution;
import net.minecraft.src.GuiOptions;
import net.minecraft.src.GuiControls;
import net.minecraft.src.GuiVideoSettings;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaTable;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

/**
 * One retained scene and resource boundary for a stable native client
 * environment.
 */
final class GuiHudHost implements AutoCloseable, GuiInputOwner {
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
    private final Map<GuiHudActivation, Entry> entries = new LinkedHashMap<>();
    private final GuiNotificationQueue notifications = GuiNotificationRuntime.queue();
    private final Map<GuiHudNativePart, Entry> winners = new EnumMap<>(GuiHudNativePart.class);
    private final EnumSet<GuiHudNativePart> visited = EnumSet.noneOf(GuiHudNativePart.class);
    private final EnumSet<GuiHudNativePart> suppressed = EnumSet.noneOf(GuiHudNativePart.class);
    private final EnumSet<GuiHudNativePart> replacementsPainted = EnumSet.noneOf(GuiHudNativePart.class);
    private NativeClientScreenHost engine;
    private GuiScopedSnapshot observation;
    private Map<String, Object> observationValues = Collections.emptyMap();
    private final GuiHudObservationPublisher observations = new GuiHudObservationPublisher();
    private final GuiHudFrameState frame = new GuiHudFrameState();
    private GuiHudDefinition.Context context;
    private Rect safeArea = Rect.EMPTY;
    private Surface painting;
    private boolean paintingAdditive;
    private boolean paintingWorld;
    private boolean worldPainted;
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
    private GuiHudCursor cursor;

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
        if (notifications.size() != 0) {
            return true;
        }
        for (Entry entry : entries.values()) {
            if (!entry.failed && !entry.retired && entry.claim.requested() && entry.definition.available()) {
                return true;
            }
        }
        return false;
    }

    GuiScreenActivation instance(GuiHudActivation claim, GuiHudNativePart part) {
        Entry entry = entries.get(claim);
        if (entry == null || entry.claim != claim || !entry.eligible()
                || entry.notification != null && !entry.notification.live()) {
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
            if (cursor != null && !cursor.ownsGuiInput()) {
                stopInteraction();
            }
            List<Rect> reserved = reservations();
            updateNotificationPauses();
            notifications.advance(client.theWorld, client.thePlayer, notificationPresentationAvailable(),
                    new GuiNotificationLayout(viewport.width, viewport.height, reserved));
            for (GuiNotification notification : notifications.admitted()) {
                reserved.add(notification.bounds());
            }
            safeArea = GuiHudLayout.safeArea(viewport.width, viewport.height, reserved);
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
        if ("minecraft:pause".equals(target)) {
            return worldAvailable() ? GuiHudDefinition.Context.PAUSE : null;
        }
        return target != null || GuiClientUiRuntime.managed(client.currentScreen)
                || worldAvailable() && (client.currentScreen instanceof net.minecraft.src.GuiContainer
                        || client.currentScreen instanceof GuiControls
                        || client.currentScreen instanceof GuiVideoSettings) ? GuiHudDefinition.Context.MENU : null;
    }

    private List<Rect> reservations() {
        List<Rect> occupied = new ArrayList<>();
        occupied.add(Rect.fromPositionAndSize(Math.max(0, viewport.width - 164), 0, 164, 40));
        if (client.gameSettings.showDebugInfo) {
            occupied.add(Rect.fromPositionAndSize(0, 0, Math.min(320, viewport.width), 90));
        }
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
        observations.update(client, worldAvailable());
        observations.layout.publish(GuiHudPartLayout.snapshot(client, viewport.width, viewport.height, worldAvailable(),
                observations.stats.values, reservations()));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldAvailable", worldAvailable());
        values.put("context", context == null ? "unavailable" : context.name);
        values.put("hideGui", client.gameSettings.hideGUI);
        values.put("debug", client.gameSettings.showDebugInfo);
        values.put("viewport", rectangle(Rect.fromPositionAndSize(0, 0, viewport.width, viewport.height)));
        values.put("safeArea", rectangle(safeArea));
        if (observations.player() != null) {
            values.put("player", observations.player());
        }
        if (observation == null || !observationValues.equals(values)) {
            observationValues = Collections.unmodifiableMap(values);
            observation = new GuiScopedSnapshot(values, "native:hud", values);
        }
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
        Map<GuiHudActivation, GuiHudDefinition> requested = new LinkedHashMap<>();
        Map<GuiHudActivation, GuiNotification> notices = new IdentityHashMap<>();
        for (GuiHudActivation activation : registry.activations()) {
            requested.put(activation, registry.getRequired(activation.key));
        }
        for (GuiNotification notification : notifications.admitted()) {
            requested.put(notification.activation, notification.contribution);
            notices.put(notification.activation, notification);
        }
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (requested.get(entry.claim) != entry.definition || notices.get(entry.claim) != entry.notification) {
                entry.close();
                entries.remove(entry.claim);
            }
        }
        for (Map.Entry<GuiHudActivation, GuiHudDefinition> request : requested.entrySet()) {
            if (!entries.containsKey(request.getKey())) {
                entries.put(request.getKey(),
                        new Entry(request.getValue(), request.getKey(), notices.get(request.getKey())));
            }
        }
    }

    private boolean notificationPresentationAvailable() {
        return context != null && (context == GuiHudDefinition.Context.MENU || !client.gameSettings.hideGUI);
    }

    private void updateNotificationPauses() {
        for (Entry entry : entries.values()) {
            if (entry.notification == null || entry.additive == null || entry.additive.document == null) {
                continue;
            }
            GuiElement focused = scene.getFocusedElement();
            boolean within = false;
            for (GuiElement ancestor = focused; ancestor != null; ancestor = ancestor.getParent()) {
                within |= ancestor == entry.additive.document.mount();
            }
            boolean input = entry.additive.document.inputAvailable();
            entry.notification.pause(input && entry.notification.bounds().contains(mouseX, mouseY), input && within);
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
        beginNative(mouseX, mouseY, partialTicks, 0);
    }

    void beginNative(int mouseX, int mouseY, float partialTicks, int counter) {
        beginFrame(mouseX, mouseY, partialTicks);
        if (worldAvailable()) {
            frame.capture(client, counter);
        }
        nativeSeen = true;
        nativeDrawing = true;
    }

    GuiHudFrameState frame() {
        return worldAvailable() ? frame : null;
    }

    ItemStack item(int slot) {
        return worldAvailable() ? observations.copyItem(slot) : null;
    }

    private void beginFrame(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        visited.clear();
        suppressed.clear();
        replacementsPainted.clear();
        captured = false;
        worldPainted = false;
        for (Entry entry : entries.values()) {
            entry.additivePainted = false;
            if (entry.notification != null && entry.additive != null && entry.additive.document != null) {
                double opacity = entry.notification.opacity(GuiNotificationRuntime.millis());
                entry.additive.document.mount().setOpacity(opacity);
                entry.additive.document.mount()
                        .setVisualTransform(opacity >= 1
                                ? GraphicsTransform.IDENTITY
                                : new GraphicsTransform(
                                        (entry.notification.options().corner.right ? 10 : -10) * (1 - opacity), 0, 1,
                                        1));
            }
        }
    }

    private boolean prepared() {
        return valid() && available() && preparedWidth == viewport.width && preparedHeight == viewport.height
                && preparedDisplayWidth == client.displayWidth && preparedDisplayHeight == client.displayHeight
                && preparedGuiScale == client.gameSettings.guiScale && viewport.width > 0 && viewport.height > 0;
    }

    String enterCursorMode(GuiHudActivation claim) {
        if (cursor != null && !cursor.ownsGuiInput()) {
            stopInteraction();
        }
        Entry entry = entries.get(claim);
        if (entry == null || entry.claim != claim || !entry.eligible()) {
            return "not_ready";
        }
        if (!entry.definition.interactive || entry.additive == null) {
            return "not_interactive";
        }
        if (entry.additive.document == null || !entry.additive.document.usable() || !entry.additivePainted
                || entry.additive.regions.isEmpty() || !prepared()) {
            return "not_ready";
        }
        if (client.currentScreen != null) {
            return "native_cursor_available";
        }
        if (cursor != null) {
            return cursor.claim == claim && cursor.ownsGuiInput() ? null : "cursor_busy";
        }
        if (!GuiHudCursor.NATIVE_POINTER.available()) {
            return "mouse_unavailable";
        }
        if (!GuiHudCursor.NATIVE_POINTER.focused() || !client.inGameHasFocus) {
            return "client_unfocused";
        }
        if (ClientInputRuntime.router().captureCount() >= 32) {
            return "capture_capacity";
        }
        cursor = new GuiHudCursor(client, claim, ClientInputRuntime.router(), GuiHudCursor.NATIVE_POINTER, () -> valid()
                && entry.eligible() && entry.additive.document != null && entry.additive.document.usable());
        claim.interactionCleanup(this::stopInteraction);
        return null;
    }

    boolean inputAvailable(GuiHudActivation claim) {
        Entry entry = entries.get(claim);
        return entry != null && entry.claim == claim && entry.additive != null && entry.additive.document != null
                && entry.additive.document.usable() && entry.additive.document.inputAvailable();
    }

    boolean cursorMode(GuiHudActivation claim) {
        return cursor != null && cursor.claim == claim && cursor.ownsGuiInput();
    }

    String partStatus(GuiHudActivation claim, GuiHudNativePart part) {
        Entry entry = entries.get(claim);
        if (entry == null || entry.claim != claim) {
            return "queued";
        }
        if (entry.failed) {
            return "failed";
        }
        if (!entry.eligible() || !prepared()) {
            return "suspended";
        }
        GuiHudDefinition.NativePart request = entry.definition.nativeParts.get(part);
        if (request.hide && visited.contains(part) && suppressed.contains(part)) {
            return "hidden";
        }
        if (!request.hide && winners.get(part) != entry) {
            return "covered";
        }
        if (replacementsPainted.contains(part) && winners.get(part) == entry) {
            return "replaced";
        }
        return "unavailable";
    }

    void stopInteraction(GuiHudActivation claim) {
        if (cursor != null && cursor.claim == claim) {
            stopInteraction();
        }
    }

    private void stopInteraction() {
        GuiHudCursor previous = cursor;
        cursor = null;
        if (previous != null) {
            previous.claim.interactionCleanup(null);
            try {
                previous.close();
            } finally {
                scene.cancelInput();
                for (Entry entry : entries.values()) {
                    if (entry.definition.interactive && entry.additive != null && entry.additive.document != null) {
                        entry.additive.document.suspend("interaction_ended");
                    }
                }
            }
        }
    }

    public boolean ownsGuiInput() {
        if (!prepared() || !GuiHudCursor.NATIVE_POINTER.focused()
                || client.currentScreen == null && (cursor == null || !cursor.ownsGuiInput())) {
            return false;
        }
        for (Entry entry : entries.values()) {
            if (entry.additive != null && entry.additive.document != null && entry.additive.document.inputAvailable()) {
                return true;
            }
        }
        return false;
    }

    private boolean input(Runnable callback) {
        if (cursor != null && !cursor.ownsGuiInput()) {
            stopInteraction();
        }
        if (!ownsGuiInput()) {
            return false;
        }
        List<GuiElement> regions = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.additive != null && entry.additive.inputAvailable()) {
                regions.addAll(entry.additive.regions);
            }
        }
        if (regions.isEmpty()) {
            return false;
        }
        engine.input(permit -> scene.runRegionalInput(regions, callback));
        return true;
    }

    boolean keyboard(char character, int key, boolean down, boolean shift, boolean control) {
        boolean[] handled = {false};
        input(() -> {
            boolean modal = scene.hasModalLayer();
            handled[0] = down ? scene.keyTyped(character, key, shift, control) : scene.keyReleased(key, shift);
            if (down && key == Keyboard.KEY_ESCAPE && !modal && cursor != null) {
                stopInteraction();
                handled[0] = true;
            }
            if (handled[0] && down && key > 0) {
                ClientInputRuntime.router().claimGuiKey(key, this);
            }
        });
        return handled[0];
    }

    boolean mouseEvent(int x, int y, int button, boolean down, int wheel, boolean shift) {
        if (client.displayWidth <= 0 || client.displayHeight <= 0) {
            return false;
        }
        int capturedButton = scene.capturedPointerButton();
        boolean dragging = capturedButton >= 0 && Mouse.isCreated() && Mouse.isButtonDown(capturedButton);
        return mouse(x * viewport.width / client.displayWidth,
                viewport.height - y * viewport.height / client.displayHeight - 1, button, down, wheel, shift, dragging);
    }

    boolean mouse(int x, int y, int button, boolean down, int wheel, boolean shift, boolean dragging) {
        boolean[] handled = {false};
        input(() -> {
            if (button >= 0) {
                handled[0] = down ? scene.mousePressed(x, y, button) : scene.mouseReleased(x, y, button);
                if (handled[0] && down) {
                    ClientInputRuntime.router().claimGuiGesture(button, this);
                }
            } else {
                handled[0] = wheel != 0 && scene.mouseScrolled(x, y, wheel, shift);
                if (scene.capturedPointerButton() >= 0) {
                    handled[0] |= scene.mouseDragged(x, y, dragging);
                }
            }
        });
        return handled[0];
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

    boolean nativeSuppressed(GuiHudNativePart part) {
        return nativeDrawing && prepared() && suppressed.contains(part);
    }

    void clientOverlay(float partialTicks) {
        int x = 0;
        int y = 0;
        if (Mouse.isCreated() && client.displayWidth > 0 && client.displayHeight > 0) {
            x = Mouse.getX() * viewport.width / client.displayWidth;
            y = viewport.height - Mouse.getY() * viewport.height / client.displayHeight - 1;
        }
        clientOverlay(x, y, partialTicks);
    }

    void clientOverlay(int mouseX, int mouseY, float partialTicks) {
        if (!prepared()) {
            return;
        }
        if (!nativeSeen) {
            beginFrame(mouseX, mouseY, partialTicks);
        }
        if (!worldPainted && worldAvailable() && client.currentScreen == null) {
            worldOverlay();
        }
        nativeDrawing = false;
        paintingAdditive = true;
        paintingWorld = false;
        try {
            paintContent();
            paintLayers();
            for (Entry entry : entries.values()) {
                boolean active = entry.eligible() && (entry.additivePainted || entry.nativeApplied());
                if (active && entry.notification != null && entry.additive != null && entry.additive.document != null
                        && entry.additive.document.usable()) {
                    entry.notification.rendered();
                }
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

    void worldOverlay() {
        if (worldPainted || !prepared() || !worldAvailable()) {
            return;
        }
        worldPainted = true;
        paintingAdditive = true;
        paintingWorld = true;
        try {
            paintContent();
            paintLayers();
        } finally {
            paintingAdditive = false;
            paintingWorld = false;
        }
    }

    private void paintLayers() {
        engine.paint(() -> scene.renderLayers(owner -> {
            for (GuiElement ancestor = owner; ancestor != null; ancestor = ancestor.getParent()) {
                for (Entry entry : entries.values()) {
                    if (entry.additive != null && entry.additive.document != null
                            && ancestor == entry.additive.document.mount()) {
                        return entry.worldLayer() == paintingWorld;
                    }
                }
            }
            return !paintingWorld;
        }));
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

    void failed(Throwable failure) {
        if (entries.isEmpty()) {
            LuaApiUtils.warnForScript("<engine-hud>", "UI HUD", "HUD preparation failed", failure);
        }
        preparedWidth = -1;
        stopInteraction();
        for (Entry entry : entries.values()) {
            if (!entry.failed && entry.claim.requested()) {
                entry.fail(entry.definition.key, failure);
            }
        }
        notifications.failAll("overlay_failed");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            stopInteraction();
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
        final GuiNotification notification;
        final Surface additive;
        final Map<GuiHudNativePart, Surface> parts = new EnumMap<>(GuiHudNativePart.class);
        final List<Surface> surfaces;
        boolean failed;
        boolean retired;
        boolean additivePainted;
        GuiScopedSnapshot notificationObservation;
        final Runnable notificationCleanup = this::revokeNotificationInput;

        Entry(GuiHudDefinition definition, GuiHudActivation claim, GuiNotification notification) {
            this.definition = definition;
            this.claim = claim;
            this.notification = notification;
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
            return !retired && !failed && valid() && claim.requested() && definition.available()
                    && (notification == null
                            ? registry.get(definition.key) == definition
                            : notification.admitted && !notification.bounds().isEmpty()
                                    && notificationPresentationAvailable())
                    && context != null && (context == GuiHudDefinition.Context.MENU || worldAvailable())
                    && (definition.layer != GuiHudDefinition.Layer.WORLD || worldAvailable())
                    && (contextEligible(false) || !definition.nativeParts.isEmpty() && contextEligible(true))
                    && (!client.gameSettings.hideGUI || definition.hideGui)
                    && (!client.gameSettings.showDebugInfo || definition.debug);
        }

        boolean worldLayer() {
            return notification == null && definition.layer != GuiHudDefinition.Layer.OVERLAY
                    && (definition.layer == GuiHudDefinition.Layer.WORLD || worldAvailable());
        }

        boolean contextEligible(boolean nativePart) {
            return definition.contexts.contains(context) || definition.behindMenus && worldAvailable()
                    && definition.contexts.contains(GuiHudDefinition.Context.GAMEPLAY) && (nativePart || worldLayer());
        }

        List<Surface> surfaces() {
            return surfaces;
        }

        void update(List<GuiElement> content) {
            if (notification != null) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("id", notification.id);
                metadata.put("key", notification.definition.key);
                metadata.put("corner", notification.options().corner.name);
                metadata.put("urgency", notification.options().urgency.name().toLowerCase(Locale.ROOT));
                notificationObservation = new GuiScopedSnapshot(metadata, "native:notification", metadata);
                if (notification.live() && notification.onEnd == null) {
                    notification.onEnd(notificationCleanup);
                }
            }
            for (Surface surface : surfaces()) {
                boolean selected = surface.presentationAvailable();
                if (selected && surface.document == null && (notification == null || notification.live())) {
                    surface.open();
                }
                if (surface.document == null) {
                    continue;
                }
                surface.document.mount().setVisible(selected);
                if (selected) {
                    if (notification == null || notification.live()) {
                        if (!surface.publishNotificationData()) {
                            continue;
                        }
                        surface.document.tick();
                    }
                    surface.resolveRegions();
                    if (surface.document.usable()) {
                        content.add(surface.document.mount());
                        root.bounds.put(surface.document.mount(),
                                notification != null
                                        ? notification.bounds()
                                        : surface.part == null && definition.safeArea
                                                ? safeArea
                                                : Rect.fromPositionAndSize(0, 0, viewport.width, viewport.height));
                    }
                } else {
                    surface.document.suspend("hud_suspended");
                }
            }
        }

        void revokeNotificationInput() {
            stopInteraction(claim);
            if (additive != null && additive.document != null) {
                scene.cancelInput(additive.document.mount());
                additive.document.suspend("notification_suspended");
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
            stopInteraction(claim);
            claim.publish("refused", "document_failed", null);
            if (notification != null) {
                notifications.failed(notification, "document_failed");
            }
            LuaApiUtils.warnForScript(definition.owner, notification == null ? "UI HUD" : "UI notification",
                    path + ": " + failure.getMessage(), failure);
        }

        @Override
        public void close() {
            if (retired) {
                return;
            }
            retired = true;
            if (notification != null && notification.onEnd == notificationCleanup) {
                notification.onEnd(null);
            }
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
        GuiComponentPlan regionPlan;
        List<GuiElement> regions = Collections.emptyList();
        GuiNotificationOptions appliedOptions;
        GuiScopedSnapshot surfaceObservation;
        Object observationBase;

        Surface(Entry entry, GuiHudNativePart part, String screen) {
            this.entry = entry;
            this.part = part;
            this.screen = screen;
        }

        void open() {
            document = new GuiAttachedDocument(this, entry.definition.owner, screen);
            document.mount().paintWhen(() -> {
                boolean paints = document != null && document.usable()
                        && (part == null ? paintingAdditive && entry.worldLayer() == paintingWorld : painting == this);
                if (paints && part == null) {
                    entry.additivePainted = true;
                }
                return paints;
            });
            try {
                GuiScreenController.OpenResult result = document
                        .open(entry.notification == null ? Collections.emptyMap() : entry.notification.options().data);
                appliedOptions = entry.notification == null ? null : entry.notification.options();
                if (result.instance == null) {
                    throw new IllegalArgumentException("HUD screen refused: " + result.reason);
                }
            } catch (RuntimeException failure) {
                entry.fail(screen, failure);
            }
        }

        boolean publishNotificationData() {
            if (entry.notification == null || appliedOptions == entry.notification.options()) {
                return true;
            }
            try {
                document.page().publish(entry.notification.options().data);
                appliedOptions = entry.notification.options();
                return true;
            } catch (RuntimeException failure) {
                entry.fail(screen + ".data", failure);
                return false;
            }
        }

        void resolveRegions() {
            if (part != null || !entry.definition.interactive || document == null || !document.usable()
                    || document.page().plan() == regionPlan) {
                return;
            }
            try {
                GuiComponentPlan plan = document.page().plan();
                List<GuiElement> resolved = new ArrayList<>();
                for (String id : entry.definition.regions) {
                    resolved.add(document.mount().document().element(plan.elementId(id)));
                }
                regions = Collections.unmodifiableList(resolved);
                regionPlan = plan;
            } catch (RuntimeException failure) {
                regions = Collections.emptyList();
                entry.fail(screen + ".regions", failure);
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
            return entry.eligible() && entry.contextEligible(part != null)
                    && (part == null ? !entry.definition.safeArea || !safeArea.isEmpty() : winners.get(part) == entry);
        }

        public boolean inputAvailable() {
            return presentationAvailable() && part == null && entry.definition.interactive && !regions.isEmpty()
                    && (!entry.worldLayer() || client.currentScreen == null)
                    && !(client.currentScreen instanceof GuiOptions) && !(client.currentScreen instanceof GuiControls)
                    && !(client.currentScreen instanceof GuiVideoSettings)
                    && (entry.notification == null || entry.notification.live()) && entry.additivePainted
                    && GuiHudCursor.NATIVE_POINTER.focused()
                    && (client.currentScreen != null || cursor != null && cursor.ownsGuiInput());
        }

        public boolean actionsAvailable() {
            return presentationAvailable() && (entry.notification == null || entry.notification.live());
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
            if (!entry.failed && !entry.retired) {
                entry.claim.close();
            }
        }

        public Map<String, Object> nativeData() {
            Map<String, Object> values = new LinkedHashMap<>();
            if (observation != null) {
                values.put("hud", scopedObservation().revision());
                values.put("hud.stats", observations.stats.snapshot.revision());
                values.put("hud.hotbar", observations.hotbar.snapshot.revision());
                values.put("hud.layout", observations.layout.snapshot.revision());
            }
            if (entry.notificationObservation != null) {
                values.put("notification", entry.notificationObservation.revision());
            }
            return values;
        }

        public void documentChanged() {
            preparedWidth = -1;
        }

        public GuiOwnedResources resources(String owner, GuiResourceDefinitions definitions) {
            return GuiOwnedResources.overlay(client, viewport, owner, definitions, this::presentationAvailable);
        }

        public void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation) {
            LuaTable hud = scopedObservation().access(scope, "snapshot");
            hud.set("stats", observations.stats.snapshot.access(scope, "stats").get("stats"));
            hud.set("hotbar", observations.hotbar.snapshot.access(scope, "hotbar").get("hotbar"));
            hud.set("layout", observations.layout.snapshot.access(scope, "layout").get("layout"));
            hud.set("key", entry.definition.key);
            value.set("hud", hud);
            if (entry.notificationObservation != null) {
                value.set("notification", entry.notificationObservation.access(scope, "snapshot"));
            }
        }

        private GuiScopedSnapshot scopedObservation() {
            if (surfaceObservation == null || observationBase != observation) {
                Map<String, Object> values = new LinkedHashMap<>(observationValues);
                values.put("layer", entry.worldLayer() ? "world" : "overlay");
                values.put("behindMenu", entry.worldLayer() && client.currentScreen != null);
                surfaceObservation = new GuiScopedSnapshot(values, "native:hud", values);
                observationBase = observation;
            }
            return surfaceObservation;
        }

        public void failure(String owner, String path, Throwable failure) {
            entry.fail(path, failure);
        }
    }
}
