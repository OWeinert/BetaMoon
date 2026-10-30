package betamoon.gui.component;

import java.util.function.Function;
import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.drawing.GuiPaintView;
import betamoon.gui.drawing.GuiRawDrawing;
import betamoon.gui.drawing.GuiRawView;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationRuntime;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiViewAdapter;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.widget.GuiStyledView;
import betamoon.luaapi.client.ui.GuiLuaPaintSource;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.ToIntFunction;
import java.util.function.Supplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Per-instance compiled component scopes, views and dependency cleanup using
 * the shared presentation tree.
 */
public final class GuiComponentPlan implements AutoCloseable {
    public interface Services {
        default GuiStyleSheet theme(String owner) {
            return GuiStyleSheet.MENU;
        }

        default GuiVisualTemplate template(String owner, String type) {
            return GuiDefaultTemplate.INSTANCE;
        }

        GuiPaintCompiler.Resources paintResources(String owner);

        default GraphicsResources rawResources(String owner) {
            return new GraphicsResources() {
            };
        }

        void requireHost();

        void failure(String owner, String path, Throwable failure);
    }

    static final class View {
        final GuiComponentScope scope;
        final GuiAuthoredNode authored;
        final GuiComponentDefinition leaf;
        View(GuiComponentScope scope, GuiAuthoredNode authored, GuiComponentDefinition leaf) {
            this.scope = scope;
            this.authored = authored;
            this.leaf = leaf;
        }
    }

    final String owner;
    final String key;
    final GuiComponentStateStore state = new GuiComponentStateStore();
    final Map<String, View> views = new LinkedHashMap<>();
    final Map<String, GuiComponentScope> scopes = new LinkedHashMap<>();
    final Map<String, GuiComponentDefinition> dependencies = new LinkedHashMap<>();
    final Map<String, GuiAuthoredNode> builtRoots = new LinkedHashMap<>();
    final Map<String, Map<String, Object>> builtProps = new LinkedHashMap<>();
    final Map<String, Map<String, List<GuiAuthoredNode>>> builtSlots = new LinkedHashMap<>();
    final Map<String, Map<String, Object>> derivationReads = new LinkedHashMap<>();
    GuiPresentationSources sources;
    GuiPresentationHost host;
    GuiPresentationRuntime runtime;
    final Services services;
    private GuiComponentDocument document;
    private final Map<String, GuiViewAdapter> adapters = new LinkedHashMap<>();
    private final Map<String, ScriptResourceTracker.Cleanup> cleanups = new LinkedHashMap<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private final Map<PaintBinding, GuiPaintProgram> paintCosts = new LinkedHashMap<>();
    private final Set<PaintBinding> paints = new LinkedHashSet<>();
    private final GuiComponentEvents events;
    private boolean active = true;
    private Runnable invalidated = () -> {
    };

    GuiComponentPlan(String owner, String key, GuiPresentationHost host, Services services) {
        this.owner = owner;
        this.key = key;
        this.host = host;
        this.services = services;
        sources = host.snapshot();
        events = new GuiComponentEvents(this);
    }

    void finish(List<GuiNodeDefinition> nodes) {
        long raw = views.values().stream().filter(view -> view.leaf != null && !view.leaf.rawDraw.isnil()).count();
        if (raw > 32) {
            throw new IllegalArgumentException("UI document exceeds 32 raw drawing contributions");
        }
        state.seal();
        LuaValue callback = new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return events.handle(arguments.arg1().checktable(), arguments.arg(2).checktable());
            }
        };
        document = new GuiComponentDocument(key, owner,
                new GuiPresentationDefinition(nodes, callback, Collections.singleton("control"), owner), this);
        Set<String> owners = new LinkedHashSet<>();
        owners.add(owner);
        for (GuiComponentDefinition dependency : dependencies.values()) {
            owners.add(dependency.owner);
        }
        for (String source : owners) {
            ScriptResourceTracker.Cleanup cleanup = () -> {
                Runnable notify = invalidated;
                try {
                    close();
                } finally {
                    notify.run();
                }
            };
            cleanups.put(source, cleanup);
            ScriptExecution.withOwner(source, () -> {
                ScriptResourceTracker.track(cleanup);
                return null;
            });
        }
        GuiPresentationHost.Observer listener = new GuiPresentationHost.Observer() {
            public void changed(String source, Object previous, Object value) {
                for (PaintBinding paint : new ArrayList<>(paints)) {
                    if (paint.reads.contains(source)) {
                        paint.view.invalidatePaint();
                    }
                }
            }

            public void event(String type, Supplier<LuaTable> payload) {
            }
        };
        subscriptions.addAll(host.observe(listener));
        subscriptions.add(state.observe(listener));
    }

    public GuiDocumentDefinition document() {
        requireActive();
        return document;
    }

    public GuiComponentStateStore state() {
        requireActive();
        return state;
    }

    public void onInvalidated(Runnable callback) {
        invalidated = callback;
    }

    public String elementId(String exportedOrLocalId) {
        requireActive();
        return scopes.get("root").resolve(exportedOrLocalId);
    }

    public boolean requiresRecompile(GuiComponentRegistry registry, GuiPresentationSources current) {
        requireActive();
        for (Map.Entry<String, GuiComponentDefinition> dependency : dependencies.entrySet()) {
            if (registry.get(dependency.getKey()) != dependency.getValue()) {
                return true;
            }
        }
        for (Map<String, Object> reads : derivationReads.values()) {
            for (Map.Entry<String, Object> read : reads.entrySet()) {
                Object value;
                if (read.getKey().startsWith("state:")) {
                    String key = read.getKey().substring(6);
                    value = current.state.containsKey(key) ? current.state.get(key) : state.snapshot().get(key);
                } else if (read.getKey().startsWith("data:")) {
                    value = current.data.get(read.getKey().substring(5));
                } else {
                    int split = read.getKey().lastIndexOf(':');
                    GuiViewAdapter view = adapters.get(read.getKey().substring(12, split));
                    value = view != null && view.interaction(read.getKey().substring(split + 1));
                }
                if (!Objects.equals(read.getValue(), value)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean interaction(String id, String name) {
        GuiViewAdapter view = adapters.get(id);
        return view != null && view.interaction(name);
    }

    public GuiPresentationTree createTree(Set<GuiCapability> capabilities, ToIntFunction<String> textWidth,
            DoubleSupplier clock) {
        requireActive();
        GuiPresentationTree tree = new GuiPresentationTree(document, new GuiComponentHost(host, this), this::createView,
                capabilities, textWidth, clock);
        runtime = tree.runtime();
        return tree;
    }

    public GuiPresentationTree reloadTree(GuiPresentationTree previous, Consumer<GuiPresentationTree> prepare) {
        requireActive();
        return previous.reload(document, this::createView, candidate -> {
            runtime = candidate.runtime();
            prepare.accept(candidate);
        });
    }

    private GuiViewAdapter createView(GuiNodeDefinition node) {
        View metadata = views.get(node.id);
        if (metadata == null) {
            throw new IllegalArgumentException("Missing compiled component view: " + node.id);
        }
        GuiViewAdapter result;
        if (metadata.leaf == null) {
            result = new GuiStyledView(node.type, services.theme(metadata.scope.owner),
                    services.template(metadata.scope.owner, node.type), (event, value) -> available(metadata.scope)
                            && accepted(runtime.dispatchUiEvent(node.id, event, value)));
        } else {
            PaintBinding paint = new PaintBinding(node, metadata);
            paints.add(paint);
            GuiPaintView.InputHandler input = (event, region) -> {
                if (!available(metadata.scope)) {
                    return GuiPaintView.Decision.IGNORE;
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", event.getType().name().toLowerCase(Locale.ROOT));
                payload.put("x", event.getMouseX() - paint.view.getLeft());
                payload.put("y", event.getMouseY() - paint.view.getTop());
                payload.put("button", event.getButton());
                payload.put("key", event.getKeyCode());
                payload.put("character", String.valueOf(event.getTypedChar()));
                payload.put("shift", event.isShiftDown());
                payload.put("region", region);
                LuaValue decision = runtime.dispatchUiEvent(node.id, "input", payload);
                return inputDecision(decision, node.path);
            };
            if (!metadata.leaf.rawDraw.isnil()) {
                GuiRawDrawing raw = new GuiRawDrawing(metadata.scope.owner, node.path, metadata.leaf.rawDraw,
                        host::bindingContext, () -> {
                            requireActive();
                            metadata.scope.requireActive();
                            services.requireHost();
                        }, services.rawResources(metadata.scope.owner),
                        failure -> services.failure(metadata.scope.owner, node.path, failure));
                paint.view = new GuiRawView(paint, input,
                        failure -> services.failure(metadata.scope.owner, node.path, failure), metadata.leaf.focusable,
                        raw, () -> rawData(metadata.scope));
            } else {
                paint.view = new GuiPaintView(paint, input,
                        failure -> services.failure(metadata.scope.owner, node.path, failure), metadata.leaf.focusable);
            }
            result = paint.view;
        }
        adapters.put(node.id, result);
        return result;
    }

    private Map<String, ?> rawData(GuiComponentScope scope) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("model", sources.data);
        values.put("state", scope.stateKey == null ? sources.state : state.values(scope.stateKey));
        return values;
    }

    boolean available(GuiComponentScope scope) {
        return active && scope.available();
    }

    void requireActive() {
        if (!active) {
            throw new IllegalStateException("Component instance plan has closed");
        }
    }

    void disable(GuiComponentScope scope) {
        scope.disabled = true;
        for (Map.Entry<String, GuiViewAdapter> entry : adapters.entrySet()) {
            if (views.get(entry.getKey()).scope == scope) {
                entry.getValue().element().setEnabled(false);
            }
        }
    }

    LuaTable eventContext(GuiComponentScope scope, LuaCallbackScope callback, LuaTable outer,
            GuiComponentScope.Emitter emitter) {
        LuaTable context = host.context(callback);
        context.set("gui", outer.get("gui"));
        scope.eventContext(context, callback, emitter);
        return context;
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        active = false;
        List<Throwable> failures = new ArrayList<>();
        for (GuiViewAdapter adapter : new ArrayList<>(adapters.values())) {
            try {
                adapter.element().setVisible(false);
                adapter.close();
            } catch (Exception failure) {
                if (failures.size() < 16) {
                    failures.add(failure);
                }
            }
        }
        if (runtime != null) {
            try {
                runtime.close();
            } catch (RuntimeException failure) {
                failures.add(failure);
            }
            runtime = null;
        }
        for (AutoCloseable subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception failure) {
                if (failures.size() < 16) {
                    failures.add(failure);
                }
            }
        }
        for (Map.Entry<String, ScriptResourceTracker.Cleanup> cleanup : cleanups.entrySet()) {
            ScriptResourceTracker.untrack(cleanup.getKey(), cleanup.getValue());
        }
        cleanups.clear();
        subscriptions.clear();
        adapters.clear();
        paints.clear();
        paintCosts.clear();
        views.clear();
        scopes.clear();
        dependencies.clear();
        builtRoots.clear();
        builtProps.clear();
        builtSlots.clear();
        derivationReads.clear();
        document = null;
        state.close();
        host = null;
        invalidated = () -> {
        };
        if (!failures.isEmpty()) {
            IllegalStateException failure = new IllegalStateException("Component instance cleanup failed",
                    failures.get(0));
            for (int index = 1; index < failures.size(); index++) {
                failure.addSuppressed(failures.get(index));
            }
            services.failure(owner, key + ".cleanup", failure);
        }
    }

    private static boolean accepted(LuaValue value) {
        return value.isnil() || value != LuaValue.FALSE;
    }

    static GuiPaintView.Decision inputDecision(LuaValue decision, String path) {
        if (decision.isnil() || decision == LuaValue.FALSE) {
            return GuiPaintView.Decision.IGNORE;
        }
        if (decision == LuaValue.TRUE) {
            return GuiPaintView.Decision.CONSUME;
        }
        LuaDeclarationValues.fields(decision, path + ".input result", "consume", "capture", "release", "focus");
        return new GuiPaintView.Decision(LuaDeclarationValues.bool(decision.get("consume"), "consume", false),
                LuaDeclarationValues.bool(decision.get("capture"), "capture", false),
                LuaDeclarationValues.bool(decision.get("release"), "release", false),
                LuaDeclarationValues.bool(decision.get("focus"), "focus", false));
    }

    private final class PaintBinding implements GuiPaintView.Source {
        final GuiNodeDefinition node;
        final View metadata;
        final Set<String> reads = new LinkedHashSet<>();
        GuiPaintView view;

        PaintBinding(GuiNodeDefinition node, View metadata) {
            this.node = node;
            this.metadata = metadata;
        }

        @Override
        public void accept(GuiPaintProgram program) {
            int commands = program.commands();
            int vertices = program.vertices();
            int hitVertices = program.hitVertices();
            int expressions = program.expressions();
            int text = program.textUnits();
            for (Map.Entry<PaintBinding, GuiPaintProgram> entry : paintCosts.entrySet()) {
                if (entry.getKey() == this) {
                    continue;
                }
                GuiPaintProgram other = entry.getValue();
                commands += other.commands();
                vertices += other.vertices();
                hitVertices += other.hitVertices();
                expressions += other.expressions();
                text += other.textUnits();
            }
            if (commands > 4096 || vertices > 65536 || hitVertices > 16384 || expressions > 32768 || text > 131072) {
                throw new IllegalArgumentException("UI document exceeds its prepared drawing budget");
            }
            paintCosts.put(this, program);
        }

        public GuiPaintProgram prepare(int width, int height, Map<String, Object> properties) {
            requireActive();
            metadata.scope.requireActive();
            LuaValue builder = metadata.leaf.rawDraw.isnil() ? metadata.leaf.paint : metadata.leaf.hit;
            if (builder.isnil()) {
                return new GuiPaintProgram.Builder().build();
            }
            Set<String> attempted = new LinkedHashSet<>();
            GuiLuaPaintSource source = new GuiLuaPaintSource(metadata.scope.owner, node.path, builder,
                    new GuiPaintCompiler(node.properties, services.paintResources(metadata.scope.owner)), callback -> {
                        LuaTable context = host.bindingContext(callback);
                        context.set("state", sourceAccess(callback,
                                metadata.scope.stateKey == null ? sources.state : state.values(metadata.scope.stateKey),
                                name -> metadata.scope.stateKey == null
                                        ? "state:" + name
                                        : "state:" + GuiComponentStateStore.source(metadata.scope.stateKey, name),
                                attempted));
                        context.set("data", sourceAccess(callback, sources.data, name -> "data:" + name, attempted));
                        return context;
                    });
            try {
                GuiPaintProgram program = source.prepare(width, height, properties);
                reads.clear();
                reads.addAll(attempted);
                return program;
            } catch (RuntimeException | Error failure) {
                reads.addAll(attempted);
                throw failure;
            }
        }
    }

    static LuaTable sourceAccess(LuaCallbackScope scope, Map<String, Object> values, Function<String, String> key,
            Set<String> reads) {
        LuaTable access = new LuaTable();
        access.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                String name = arguments.arg(arguments.arg1() == access ? 2 : 1).checkjstring();
                if (!values.containsKey(name)) {
                    throw new LuaError("Unknown published UI field: " + name);
                }
                reads.add(key.apply(name));
                return GuiReadOnlyValues.fromPublished(Collections.singletonMap("value", values.get(name)))
                        .get("value");
            }
        });
        return access;
    }
}
