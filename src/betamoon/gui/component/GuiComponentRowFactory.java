package betamoon.gui.component;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.collection.GuiRowFactory;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.action.GuiActionOrigin;
import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.drawing.GuiPaintBudget;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.style.GuiViewAppearance;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.client.ui.GuiAuthoredNodeParser;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.luaj.vm2.LuaValue;

/**
 * Compiles realized Lua rows into the same component plans and retained
 * elements as an ordinary screen.
 */
public final class GuiComponentRowFactory implements GuiRowFactory {
    /**
     * One candidate's host and prepared resource leases, supplied by the existing
     * owning host.
     */
    public interface Environment extends AutoCloseable {
        GuiPresentationHost host();

        /**
         * Graphics/theme accounting must belong to this candidate's releasable scope.
         */
        GuiComponentPlan.Services services();

        /** Acquire complete resource/theme dependencies before constructing views. */
        default void prepare(GuiComponentPlan plan) {
        }

        default void validate() {
        }

        default boolean needsRefresh() {
            return false;
        }

        default Object refreshStamp() {
            return null;
        }

        default GuiPaintBudget paintBudget() {
            return null;
        }

        /** Release leases once; repeated calls must be harmless. */
        @Override
        void close();
    }

    private final GuiComponentRegistry components;
    private final String owner;
    private final String key;
    private final LuaValue builder;
    private final GuiComponentSchema state;
    private final Set<GuiCapability> capabilities;
    private final Function<Context, Environment> environments;
    private final ToIntFunction<String> textWidth;
    private final DoubleSupplier clock;
    private final Object callbackRevision = new Object();

    public GuiComponentRowFactory(GuiComponentRegistry components, String owner, String key, LuaValue builder,
            GuiComponentSchema state, Set<GuiCapability> capabilities, Function<Context, Environment> environments,
            ToIntFunction<String> textWidth, DoubleSupplier clock) {
        this.components = Objects.requireNonNull(components, "row component registry");
        this.owner = Objects.requireNonNull(owner, "row owner");
        this.key = Objects.requireNonNull(key, "row factory identity");
        if (builder == null || !builder.isfunction()) {
            throw new IllegalArgumentException("A component row factory requires a Lua function");
        }
        this.builder = builder;
        this.state = Objects.requireNonNull(state, "row state schema");
        this.capabilities = Collections.unmodifiableSet(new HashSet<>(capabilities));
        this.environments = Objects.requireNonNull(environments, "row environments");
        this.textWidth = Objects.requireNonNull(textWidth, "row text measurement");
        this.clock = Objects.requireNonNull(clock, "row animation clock");
    }

    @Override
    public Prepared prepare(Context context) {
        if (!GuiPreparationPhase.active()) {
            throw new IllegalStateException("Component rows require Java logical preparation outside callbacks");
        }
        Environment environment = Objects.requireNonNull(environments.apply(context), "prepared row environment");
        CompiledRow previous = context.previous instanceof CompiledRow
                && ((CompiledRow) context.previous).factory == this && !((CompiledRow) context.previous).invalidated
                        ? (CompiledRow) context.previous
                        : null;
        GuiComponentPlan plan = null;
        GuiPresentationTree tree = null;
        GuiPresentationTree.PreparedReload reload = null;
        GuiRowBudget.Reservation reservation = null;
        try {
            GuiAuthoredNode authored;
            try (LuaCallbackScope callback = LuaCallbackScope.binding()) {
                environment.host().bindingContext(callback);
                LuaValue result = ScriptExecution.invokeScoped(owner, "ui.row." + key, builder,
                        GuiReadOnlyValues.fromPublished(record(context)), callback).arg1();
                authored = new GuiAuthoredNodeParser(components).parse(owner, result, key + ".row");
            }
            plan = GuiComponentCompiler.prepare(components, owner, key, authored, environment.host(),
                    new RowServices(environment.services(), key + "/row/" + context.record.key), capabilities,
                    previous == null ? null : previous.plan, state, Collections.emptyMap(), saved(context, "state"));
            plan.inheritActionOrigins(context::actionOrigins);
            environment.prepare(plan);
            reservation = context.budget.reserve(plan.document().presentation.nodes.size() + 1);
            GuiComponentPlan acceptedPlan = plan;
            if (previous == null) {
                tree = plan.createTree(capabilities, textWidth, clock);
                tree.restoreViewState(saved(context, "views"));
                prepareTree(context, tree);
                admit(context, plan, tree);
            } else {
                reload = plan.prepareReloadTree(previous.tree, candidate -> {
                    prepareTree(context, candidate);
                    admit(context, acceptedPlan, candidate);
                });
                tree = reload.candidate();
            }
            CompiledRow row = new CompiledRow(environment, plan, tree);
            plan.onInvalidated(row::expire);
            Candidate candidate = new Candidate(row, reservation, reload);
            return candidate;
        } catch (RuntimeException | Error failure) {
            closeAll(failure, reload == null ? tree : reload, plan, reservation, environment);
            throw failure;
        }
    }

    private static void closeAll(Throwable original, AutoCloseable... resources) {
        Throwable failure = original;
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception | Error cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else if (fatal(cleanup) && !fatal(failure)) {
                    cleanup.addSuppressed(failure);
                    failure = cleanup;
                } else if (failure != cleanup && failure.getSuppressed().length < 16) {
                    failure.addSuppressed(cleanup);
                }
            }
        }
        if (failure != null && failure != original) {
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            throw new IllegalStateException("Row resource cleanup failed", failure);
        }
    }

    private static boolean fatal(Throwable failure) {
        return failure instanceof VirtualMachineError || failure instanceof ThreadDeath;
    }

    /**
     * Rendering access is inherited; lifecycle callbacks and root failures stay
     * local to the row.
     */
    private static final class RowServices implements GuiComponentPlan.Services {
        private final GuiComponentPlan.Services delegate;
        private final String path;

        RowServices(GuiComponentPlan.Services delegate, String path) {
            this.delegate = Objects.requireNonNull(delegate, "row services");
            this.path = path;
        }

        public List<GuiActionOrigin> actionOrigins() {
            return delegate.actionOrigins();
        }

        public GuiRowBudget rowBudget() {
            return delegate.rowBudget();
        }

        public Environment rowEnvironment(Context context, String theme) {
            return delegate.rowEnvironment(context, theme);
        }

        public GuiStyleSheet theme(String owner, GuiViewAppearance appearance) {
            return delegate.theme(owner, appearance);
        }

        public GuiVisualTemplate template(String owner, GuiViewAppearance appearance) {
            return delegate.template(owner, appearance);
        }

        public GuiPaintCompiler.Resources paintResources(String owner) {
            return delegate.paintResources(owner);
        }

        public GraphicsResources rawResources(String owner) {
            return delegate.rawResources(owner);
        }

        public void paintAccepted(String source, GuiPaintProgram[] programs) {
            delegate.paintAccepted(source, programs);
        }

        public void requireHost() {
            delegate.requireHost();
        }

        public void failure(String owner, String source, Throwable failure) {
            delegate.failure(owner, path + "/" + source, failure);
        }
    }

    private static Map<String, Object> record(Context context) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("key", context.record.key);
        value.put("parent", context.record.parent);
        value.put("label", context.record.label);
        value.put("enabled", context.record.enabled);
        value.put("data", context.record.data);
        value.put("depth", context.depth);
        value.put("selected", context.selected);
        value.put("expanded", context.expanded);
        return value;
    }

    private static void prepareTree(Context context, GuiPresentationTree tree) {
        tree.runtime().viewport(0, 0, context.width, context.height);
        tree.update();
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(context.gui.getMinecraft(), context.gui.getFont(), context.width, context.height,
                context.gui.getDisplayWidth(), context.gui.getDisplayHeight());
        GuiContainer mount = new GuiContainer();
        mount.add(tree.root());
        scene.setContent(mount);
        try {
            scene.prepare();
        } finally {
            scene.setContent(null);
            mount.remove(tree.root());
        }
    }

    private static void admit(Context context, GuiComponentPlan plan, GuiPresentationTree tree) {
        plan.state().admitWith(typed -> context.publishState(snapshot(typed, tree.captureViewState())));
        tree.admitViewStateWith(new GuiViewStateAdmission() {
            public Proposal begin(Map<String, Object> views) {
                Proposal proposal = context.reserveState(snapshot(plan.state().snapshot(), views));
                return new Proposal() {
                    public void commit(Map<String, Object> accepted) {
                        proposal.commit(snapshot(plan.state().snapshot(), accepted));
                    }

                    public void close() {
                        proposal.close();
                    }
                };
            }

            public Object revision() {
                return context.stateRevision();
            }

            public boolean pending() {
                return context.statePending();
            }
        });
    }

    private static Map<String, Object> snapshot(Map<String, Object> typed, Map<String, Object> views) {
        if (typed.isEmpty() && views.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("state", typed);
        value.put("views", views);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> saved(Context context, String name) {
        Object value = context.state.get(name);
        if (value == null) {
            return Collections.emptyMap();
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Saved row " + name + " requires a detached record");
        }
        return (Map<String, Object>) value;
    }

    private final class CompiledRow implements Row {
        final GuiComponentRowFactory factory = GuiComponentRowFactory.this;
        final Environment environment;
        final GuiComponentPlan plan;
        final GuiPresentationTree tree;
        final List<Object> revision;
        boolean invalidated;
        boolean closed;
        boolean environmentClosed;

        CompiledRow(Environment environment, GuiComponentPlan plan, GuiPresentationTree tree) {
            this.environment = environment;
            this.plan = plan;
            this.tree = tree;
            revision = Collections.unmodifiableList(Arrays.asList(callbackRevision, plan.callbackRevision()));
        }

        public GuiElement element() {
            return tree.root();
        }

        public Map<String, Object> state() {
            return snapshot(plan.state().snapshot(), tree.captureViewState());
        }

        public boolean hasLiveState() {
            return !invalidated;
        }

        public Object actionRevision() {
            return revision;
        }

        public void update(GuiContext context, int width, int height) {
            if (!invalidated) {
                tree.runtime().viewport(0, 0, width, height);
                tree.update();
            }
        }

        public void sample() {
            if (!invalidated) {
                tree.sample();
            }
        }

        public GuiPaintBudget paintBudget() {
            return environment.paintBudget();
        }

        public boolean needsRefresh() {
            return invalidated || environment.needsRefresh()
                    || plan.requiresRecompile(components, environment.host().snapshot());
        }

        public Object refreshStamp() {
            return invalidated
                    ? Boolean.TRUE
                    : Arrays.asList(plan.compilationInputs(components, environment.host().snapshot()),
                            environment.refreshStamp());
        }

        void expire() {
            invalidated = true;
            closeEnvironment();
        }

        private void closeEnvironment() {
            if (!environmentClosed) {
                environmentClosed = true;
                environment.close();
            }
        }

        public GuiElement compatibleFocus(Row previous, GuiElement focused) {
            return previous instanceof GuiComponentRowFactory.CompiledRow
                    ? tree.compatibleElement(((CompiledRow) previous).tree, focused)
                    : null;
        }

        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeAll(null, tree, plan, this::closeEnvironment);
        }
    }

    private static final class Candidate implements Prepared {
        final CompiledRow row;
        final GuiRowBudget.Reservation reservation;
        final GuiPresentationTree.PreparedReload reload;
        boolean committed;
        boolean closed;

        Candidate(CompiledRow row, GuiRowBudget.Reservation reservation, GuiPresentationTree.PreparedReload reload) {
            this.row = row;
            this.reservation = reservation;
            this.reload = reload;
        }

        public Row candidate() {
            return row;
        }

        public GuiRowBudget.Reservation reservation() {
            return reservation;
        }

        public void validate() {
            row.plan.document();
            row.environment.validate();
            if (reload != null) {
                reload.validate();
            }
        }

        public Row commit() {
            if (closed || committed) {
                throw new IllegalStateException("Prepared component row is already finished");
            }
            if (reload == null) {
                row.tree.activate();
            } else {
                reload.commit();
            }
            committed = true;
            return row;
        }

        public void close() {
            if (!closed) {
                closed = true;
                closeAll(null, reload, committed ? null : row, committed ? null : reservation);
            }
        }
    }
}
