package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentCompiler;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.component.GuiComponentStateStore;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.drawing.GuiPaintBudget;
import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.style.GuiThemeDefinition;
import betamoon.gui.style.GuiViewAppearance;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiPreparedTemplate;
import betamoon.luaapi.client.ui.GuiTemplateSource;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.GuiLayer;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.interop.GuiOwnedResources;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.client.ui.GuiLuaData;
import betamoon.luaapi.client.ui.GuiModelLuaAccess;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.client.ui.GuiScreenLuaAccess;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.interop.NativeCallbackTable;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.ScriptExecution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ScaledResolution;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Native composition owns resource revisions, while the controller owns
 * navigation/admission.
 */
public final class GuiNativeScreenPage implements GuiScreenController.Page {
    private final Minecraft client;
    private final GuiScreenActivation activation;
    private final GuiScreenRegistry screens;
    private final GuiComponentRegistry components;
    private final GuiManagedScreen screen;
    private final GuiNativeScreenPage parent;
    private GuiLayer layer;
    private final long started = System.nanoTime();
    private Revision current;
    private Object rejectedStamp;
    private Object attemptedStamp;
    private Throwable pendingFailure;
    private boolean invalidated;
    private boolean closed;
    private int ticks;

    public GuiNativeScreenPage(Minecraft client, GuiScreenActivation activation, GuiScreenRegistry screens,
            GuiComponentRegistry components, GuiScreenController.History history) {
        this.client = client;
        this.activation = activation;
        this.screens = screens;
        this.components = components;
        parent = activation.parent == null ? null : (GuiNativeScreenPage) activation.parent.page;
        screen = parent == null ? new GuiManagedScreen() : parent.screen;
        if (parent == null) {
            screen.page(this);
        }
        updateEnvironment();
        Revision candidate = new Revision(activation.definition(), null, activation.initialState,
                activation.initialData, history == null ? Collections.emptyMap() : history.state);
        boolean complete = false;
        try {
            candidate.tree = candidate.plan.createTree(candidate.capabilities(), client.fontRenderer::getStringWidth,
                    this::clock);
            candidate.tree.root().inputLifetime(activation.input);
            screen.scene().prepareDetached(candidate.tree);
            current = candidate;
            complete = true;
        } finally {
            if (!complete) {
                candidate.close();
                if (parent == null) {
                    screen.page(null);
                }
            }
        }
    }

    public GuiManagedScreen screen() {
        return screen;
    }

    GuiScreenActivation activation() {
        return activation;
    }

    GuiPresentationTree tree() {
        return current.tree;
    }

    public GuiComponentPlan plan() {
        return current.plan;
    }

    public Map<String, Object> data() {
        return current.data.snapshot();
    }

    public void publish(Map<String, Object> changes) {
        current.data.publish(changes);
    }

    boolean pauseGame() {
        return current.definition.pauseGame && activation.controller.host().worldAvailable();
    }

    boolean usable() {
        return !closed && !invalidated && pendingFailure == null && activation.active()
                && activation.environment == activation.controller.host().environment();
    }

    void input(Runnable input) {
        current.main().host().input(permit -> input.run());
    }

    void observe(Runnable update) {
        current.main().host().observe(update);
    }

    void paint(Runnable paint) {
        current.main().host().paint(paint);
    }

    void renderFailed(Throwable failure) {
        if (pendingFailure == null) {
            pendingFailure = failure;
        }
    }

    void nativeClosed() {
        activation.controller.externalClosed(activation);
    }

    private double clock() {
        return (System.nanoTime() - started) / 1e9;
    }

    private void updateEnvironment() {
        ScaledResolution resolution = new ScaledResolution(client.gameSettings, client.displayWidth,
                client.displayHeight);
        screen.scene().updateEnvironment(client, client.fontRenderer, resolution.getScaledWidth(),
                resolution.getScaledHeight(), client.displayWidth, client.displayHeight);
    }

    @Override
    public void activate() {
        if (parent == null) {
            screen.scene().setDocument(current.tree);
        } else {
            layer = screen.scene().openLayer(current.tree, true);
            layer.onClosed(() -> activation.controller.externalClosed(activation));
            if (!activation.active() || !parent.usable()) {
                layer.close();
                throw new IllegalStateException("Dialog host closed during attachment");
            }
        }
        observe(() -> screen.scene().prepare());
    }

    @Override
    public void update() {
        if (closed || invalidated) {
            throw new IllegalStateException("Screen component dependency expired");
        }
        if (pendingFailure != null) {
            throw new IllegalStateException("Screen rendering/callback failed", pendingFailure);
        }
        updateEnvironment();
        current.updateResources();
        if (++ticks % current.definition.updateInterval == 0 && current.definition.callbacks.containsKey("update")) {
            current.emit("update");
        }
        if (parent == null) {
            observe(() -> screen.scene().prepare());
        }
    }

    @Override
    public void flush() {
        if (parent == null && usable()) {
            observe(() -> screen.scene().prepare());
        }
    }

    @Override
    public boolean needsRefresh(GuiScreenDefinition definition) {
        boolean model = current.plan.requiresRecompile(components, current.snapshot());
        boolean images = current.needsResourceRevision();
        boolean themes = current.themesChanged();
        if (definition == current.definition && !model && !images && !themes) {
            return false;
        }
        attemptedStamp = Arrays.asList(definition, current.plan.compilationInputs(components, current.snapshot()),
                current.resourceStamp(), current.themeStamp());
        return !attemptedStamp.equals(rejectedStamp);
    }

    @Override
    public void refresh(GuiScreenDefinition definition) {
        if (definition.context != current.definition.context) {
            rejectedStamp = attemptedStamp;
            throw new IllegalArgumentException("Screen context changes require reopening");
        }
        Revision candidate = null;
        try {
            candidate = new Revision(definition, current, Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap());
            Revision prepared = candidate;
            candidate.tree = candidate.plan.reloadTree(current.tree, tree -> {
                tree.root().inputLifetime(activation.input);
                screen.scene().prepareDetached(tree);
            });
            Revision previous = current;
            current = candidate;
            if (parent == null) {
                screen.scene().setDocument(current.tree);
            } else {
                layer.replace(current.tree);
            }
            previous.close();
            rejectedStamp = null;
        } catch (RuntimeException | Error failure) {
            rejectedStamp = attemptedStamp;
            if (candidate != null && candidate != current) {
                candidate.close();
            }
            throw failure;
        }
    }

    @Override
    public String focusedElement() {
        GuiElement focused = screen.scene().getFocusedElement();
        if (focused == null) {
            return null;
        }
        for (GuiNodeDefinition node : current.plan.document().presentation.nodes) {
            if (current.tree.element(node.id) == focused) {
                return node.id;
            }
        }
        return null;
    }

    @Override
    public void restoreFocus(String id) {
        if (id == null) {
            return;
        }
        try {
            screen.scene().getContext().requestFocus(current.tree.element(id));
        } catch (IllegalArgumentException incompatible) {
            /* The new tree chooses its ordinary initial focus. */ }
    }

    @Override
    public Object execute(GuiActionDefinition action, Map<String, Object> payload) {
        boolean world = action.scope == GuiActionDefinition.Scope.WORLD;
        try (LuaCallbackScope scope = new LuaCallbackScope(world, true)) {
            LuaTable context = current.context(scope);
            if (world) {
                scope.nativeEntry(NativeEventAccess.entry(scope, client, client.theWorld, client.thePlayer));
                context.set("world", LuaWorldActionAccess.create(scope, client.theWorld, 0, 0, 0));
                context.set("player", LuaEntityActionAccess.create(scope, client.thePlayer, client.thePlayer));
            } else {
                scope.nativeEntry(current.main().host().actionEntry(scope));
            }
            LuaValue result = ScriptExecution
                    .invoke(activation.owner(), "ui.action." + action.name, action.handler,
                            LuaValue.varargsOf(new LuaValue[]{context, GuiReadOnlyValues.fromPublished(payload)}))
                    .arg1();
            return GuiLuaData.read(result, "action result");
        }
    }

    @Override
    public void close(String reason, boolean notify) {
        if (closed) {
            return;
        }
        closed = true;
        try {
            LuaValue callback = current == null
                    ? LuaValue.NIL
                    : current.definition.callbacks.getOrDefault("close", LuaValue.NIL);
            if (notify && !callback.isnil() && current.definition.available()) {
                try (LuaCallbackScope scope = new LuaCallbackScope(false, false)) {
                    LuaTable context = current.context(scope);
                    LuaTable event = new LuaTable();
                    event.set("type", "close");
                    event.set("reason", reason);
                    ScriptExecution.invoke(activation.owner(), "ui.close", callback,
                            LuaValue.varargsOf(new LuaValue[]{context, event}));
                } catch (RuntimeException failure) {
                    activation.controller.host().failure(activation.owner(), activation.key + ".close", failure);
                }
            }
        } finally {
            try {
                if (current != null) {
                    current.close();
                }
            } finally {
                current = null;
                if (parent == null) {
                    screen.scene().setDocument(null);
                    screen.page(null);
                } else if (layer != null) {
                    layer.close();
                    layer = null;
                }
                pendingFailure = null;
                rejectedStamp = null;
                attemptedStamp = null;
            }
        }
    }

    private final class Revision implements GuiPresentationHost, GuiComponentPlan.Services, AutoCloseable {
        final GuiScreenDefinition definition;
        final GuiScreenData data;
        final Map<String, GuiOwnedResources> resources = new LinkedHashMap<>();
        final List<Observer> observers = new ArrayList<>();
        GuiComponentPlan plan;
        GuiPresentationTree tree;
        boolean retired;
        boolean candidateInvalidated;
        final Map<String, GuiThemeDefinition> themeDefinitions = new LinkedHashMap<>();
        final Map<String, GuiStyleSheet> preparedThemes = new LinkedHashMap<>();
        final GuiPaintBudget paintBudget = new GuiPaintBudget();

        Revision(GuiScreenDefinition definition, Revision previous, Map<String, Object> initialState,
                Map<String, Object> initialData, Map<String, Object> restoredState) {
            this.definition = definition;
            data = previous == null
                    ? new GuiScreenData(definition.data, initialData)
                    : GuiScreenData.refresh(definition.data, previous.data.snapshot());
            boolean complete = false;
            try {
                resources.put(definition.owner,
                        new GuiOwnedResources(client, screen, definition.owner, definition.resources));
                plan = GuiComponentCompiler.prepare(components, definition.owner, definition.key, definition.root, this,
                        this, capabilities(), previous == null ? null : previous.plan, definition.state, initialState,
                        restoredState);
                plan.onInvalidated(() -> {
                    if (current == this) {
                        invalidated = true;
                        activation.controller.dependencyExpired(activation);
                    } else {
                        candidateInvalidated = true;
                    }
                });
                for (GuiViewAppearance appearance : plan.appearances()) {
                    String reference = appearance.theme == null ? definition.theme : appearance.theme;
                    if (!preset(reference)) {
                        GuiThemeDefinition theme = GuiClientUiRuntime.themes().getRequired(reference);
                        themeDefinitions.put(reference, theme);
                        plan.dependOnOwner(theme.owner);
                    }
                }
                if (plan.owners().size() > 32) {
                    throw new IllegalArgumentException("Screen exceeds 32 resource owners");
                }
                for (String owner : plan.owners()) {
                    if (!resources.containsKey(owner)) {
                        resources.put(owner, new GuiOwnedResources(client, screen, owner, definition.resources));
                    }
                }
                if (candidateInvalidated) {
                    throw new IllegalStateException("Theme/component owner expired during preparation");
                }
                complete = true;
            } finally {
                if (!complete) {
                    close();
                }
            }
        }

        Set<GuiCapability> capabilities() {
            EnumSet<GuiCapability> values = EnumSet.of(GuiCapability.INPUT, GuiCapability.MODAL_LAYERS,
                    GuiCapability.PREPARED_DRAWING, GuiCapability.RAW_DRAWING);
            if (activation.controller.host().worldAvailable()) {
                values.add(GuiCapability.WORLD_DATA);
            }
            if (!values.containsAll(definition.requirements)) {
                throw new IllegalArgumentException("Screen requires unavailable host capabilities");
            }
            return values;
        }

        GuiOwnedResources main() {
            return resources.get(definition.owner);
        }

        void updateResources() {
            for (GuiOwnedResources owner : resources.values()) {
                owner.update();
            }
        }

        boolean needsResourceRevision() {
            for (GuiOwnedResources owner : resources.values()) {
                if (owner.needsRevision()) {
                    return true;
                }
            }
            return false;
        }

        Map<String, Long> resourceStamp() {
            Map<String, Long> values = new LinkedHashMap<>();
            for (Map.Entry<String, GuiOwnedResources> entry : resources.entrySet()) {
                if (entry.getValue().needsRevision()) {
                    values.put(entry.getKey(), entry.getValue().imageRevision());
                }
            }
            return values;
        }

        void emit(String type) {
            for (Observer observer : new ArrayList<>(observers)) {
                observer.event(type, LuaTable::new);
            }
        }

        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.emptyMap(), data.snapshot(), Collections.emptyMap());
        }

        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
            return Collections.emptyMap();
        }

        public List<AutoCloseable> observe(Observer observer) {
            observers.add(observer);
            return Arrays.asList(data.observe(observer), () -> observers.remove(observer));
        }

        public LuaTable context(LuaCallbackScope scope) {
            scope.nativeEntry(main().host().queryEntry(scope));
            LuaTable context = new NativeCallbackTable(scope);
            if (plan != null) {
                GuiComponentStateStore state = plan.state();
                context.set("state",
                        GuiModelLuaAccess.create(scope, definition.state,
                                () -> state.values(GuiComponentPlan.SCREEN_STATE),
                                changes -> state.set(GuiComponentPlan.SCREEN_STATE, changes), definition.owner));
            }
            context.set("data",
                    GuiModelLuaAccess.create(scope, definition.data, data::snapshot, data::publish, definition.owner));
            context.set("screen",
                    GuiScreenLuaAccess.instance(definition.owner, activation, scope, screens::getRequired));
            if (activation.environment == activation.controller.host().environment() && client.theWorld != null) {
                context.set("world", LuaWorldActionAccess.create(scope, client.theWorld, 0, 0, 0));
                if (client.thePlayer != null) {
                    context.set("player", LuaEntityActionAccess.create(scope, client.thePlayer, client.thePlayer));
                }
            }
            return context;
        }

        public LuaTable bindingContext(LuaCallbackScope scope) {
            scope.nativeEntry(main().host().queryEntry(scope));
            return new NativeCallbackTable(scope);
        }

        public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
            return this;
        }

        public GuiPaintCompiler.Resources paintResources(String owner) {
            return required(owner).painting();
        }

        public GraphicsResources rawResources(String owner) {
            return required(owner);
        }

        GuiOwnedResources required(String owner) {
            GuiOwnedResources value = resources.get(owner);
            if (value == null) {
                throw new IllegalStateException("Rendering resources were not prepared for " + owner);
            }
            return value;
        }

        private String themeReference(GuiViewAppearance appearance) {
            return appearance.theme == null ? definition.theme : appearance.theme;
        }

        private boolean preset(String reference) {
            return reference.equals("menu") || reference.equals("minimal") || reference.equals("container_legacy");
        }

        public GuiStyleSheet theme(String owner, GuiViewAppearance appearance) {
            String reference = themeReference(appearance);
            String key = owner + "|" + reference;
            GuiStyleSheet base = preparedThemes.get(key);
            if (base == null) {
                base = preset(reference)
                        ? GuiThemeDefinition.preset(reference)
                        : themeDefinitions.get(reference).prepare(required(owner).painting());
                preparedThemes.put(key, base);
            }
            return appearance.appearance.style.values.isEmpty()
                    ? base
                    : base.withInline(appearance.appearance.style.prepare(required(owner).painting()));
        }

        public GuiVisualTemplate template(String owner, GuiViewAppearance appearance) {
            String reference = themeReference(appearance);
            GuiThemeDefinition theme = themeDefinitions.get(reference);
            String name = appearance.appearance.template == null
                    ? appearance.node.type
                    : appearance.appearance.template;
            GuiThemeDefinition.Template template = theme == null ? null : theme.templates.get(name);
            if (template == null) {
                if (appearance.appearance.template != null) {
                    throw new IllegalArgumentException("Unknown template in theme: " + name);
                }
                return GuiDefaultTemplate.INSTANCE;
            }
            GuiTemplateSource source = new GuiTemplateSource(appearance.node.path, theme, template,
                    appearance.node.properties, required(template.owner).painting(), paintBudget,
                    scope -> scope.nativeEntry(required(template.owner).host().queryEntry(scope)));
            return new GuiPreparedTemplate(template.parts, source,
                    failure -> failure(owner, appearance.node.path, failure));
        }

        boolean themesChanged() {
            for (Map.Entry<String, GuiThemeDefinition> entry : themeDefinitions.entrySet()) {
                if (GuiClientUiRuntime.themes().get(entry.getKey()) != entry.getValue()) {
                    return true;
                }
            }
            return false;
        }

        Map<String, GuiThemeDefinition> themeStamp() {
            Map<String, GuiThemeDefinition> result = new LinkedHashMap<>();
            for (String reference : themeDefinitions.keySet()) {
                result.put(reference, GuiClientUiRuntime.themes().get(reference));
            }
            return result;
        }

        public void paintAccepted(String path, GuiPaintProgram[] programs) {
            paintBudget.accept(path, programs);
        }

        public Set<String> events() {
            return new LinkedHashSet<>(Arrays.asList("control", "open", "reload", "update"));
        }

        public LuaValue event(LuaTable context, LuaTable event) {
            String type = event.get("type").checkjstring();
            String name = type.equals("reload") ? "refresh" : type;
            LuaValue handler = definition.callbacks.getOrDefault(name, LuaValue.NIL);
            if (handler.isnil()) {
                return LuaValue.NIL;
            }
            if (name.equals("refresh")) {
                event.set("type", "refresh");
            }
            return handler.call(context, event);
        }

        public void requireHost() {
            if (candidateInvalidated || !usable() || client.currentScreen != screen) {
                throw new IllegalStateException("Screen graphics attachment expired");
            }
        }

        public void failure(String owner, String path, Throwable failure) {
            if (path.equals("root") && current == this && !closed) {
                pendingFailure = failure;
            }
            activation.controller.host().failure(owner, path, failure);
        }

        @Override
        public void close() {
            if (retired) {
                return;
            }
            retired = true;
            try {
                if (plan != null) {
                    plan.close();
                }
            } finally {
                try {
                    if (tree != null) {
                        tree.close();
                    }
                } finally {
                    for (GuiOwnedResources value : new ArrayList<>(resources.values())) {
                        try {
                            value.close();
                        } catch (RuntimeException failure) {
                            activation.controller.host().failure(definition.owner, definition.key + ".resources",
                                    failure);
                        }
                    }
                    resources.clear();
                    data.close();
                    observers.clear();
                    themeDefinitions.clear();
                    preparedThemes.clear();
                    paintBudget.clear();
                }
            }
        }
    }
}
