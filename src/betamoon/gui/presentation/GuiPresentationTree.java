package betamoon.gui.presentation;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiElement;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.ToIntFunction;
import java.util.function.Consumer;

/**
 * Relates reactive nodes to the one retained parent graph; owns adapters and
 * revision cleanup.
 */
public final class GuiPresentationTree implements AutoCloseable {
    private static final String[] INTERACTIONS = {"hovered", "pressed", "focused", "selected", "disabled", "invalid"};
    @FunctionalInterface
    public interface Factory {
        GuiViewAdapter create(GuiNodeDefinition definition);
    }

    private final GuiDocumentDefinition definition;
    private final List<GuiViewAdapter> views = new ArrayList<>();
    private final Map<String, GuiViewAdapter> ids = new LinkedHashMap<>();
    private final Map<String, GuiNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<GuiElement, String> viewIds = new IdentityHashMap<>();
    private final Factory factory;
    private final Set<GuiCapability> capabilities;
    private final long[] published;
    private GuiPresentationRuntime runtime;
    private boolean closed;

    public GuiPresentationTree(GuiDocumentDefinition definition, GuiPresentationHost host, Factory factory,
            Set<GuiCapability> capabilities, ToIntFunction<String> textWidth, DoubleSupplier clock) {
        this(definition, factory, capabilities);
        try {
            runtime = new GuiPresentationRuntime(definition, host, textWidth, clock);
            runtime.setViewValidator(this::validateValues);
            runtime.prepareInitial();
            publish();
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    private GuiPresentationTree(GuiDocumentDefinition definition, Factory factory, Set<GuiCapability> capabilities) {
        this.definition = definition;
        this.factory = factory;
        this.capabilities = Collections.unmodifiableSet(new HashSet<>(capabilities));
        validate(definition, capabilities);
        published = new long[definition.presentation.nodes.size()];
        Arrays.fill(published, -1);
        Map<GuiElement, Boolean> unique = new IdentityHashMap<>();
        try {
            for (GuiNodeDefinition node : definition.presentation.nodes) {
                GuiViewAdapter view = factory.create(node);
                if (view == null || view.element() == null) {
                    throw new IllegalArgumentException(node.path + ": view factory returned no element");
                }
                if (view.element().getParent() != null || unique.put(view.element(), Boolean.TRUE) != null) {
                    throw new IllegalArgumentException(node.path + ": factory must create an unattached unique view");
                }
                views.add(view);
                ids.put(node.id, view);
                definitions.put(node.id, node);
                viewIds.put(view.element(), node.id);
                if (node.parent >= 0) {
                    GuiElement parent = views.get(node.parent).element();
                    if (!(parent instanceof GuiContainer)) {
                        throw new IllegalArgumentException(node.path + ": parent does not accept children");
                    }
                    ((GuiContainer) parent).add(view.element());
                }
            }
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    public GuiElement root() {
        requireActive();
        return views.get(0).element();
    }

    public GuiElement element(String id) {
        requireActive();
        GuiViewAdapter view = ids.get(id);
        if (view == null) {
            throw new IllegalArgumentException("Unknown retained element: " + id);
        }
        return view.element();
    }

    public GuiPresentationRuntime runtime() {
        requireActive();
        return runtime;
    }

    /**
     * Bounded detached snapshots let a keyed row release its actual control graph
     * while offscreen.
     */
    public Map<String, Object> captureViewState() {
        requireActive();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, GuiViewAdapter> view : ids.entrySet()) {
            Map<String, Object> state = view.getValue().captureState();
            if (!state.isEmpty()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", definitions.get(view.getKey()).type);
                entry.put("state", state);
                snapshot.put(view.getKey(), entry);
            }
        }
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(snapshot);
        return detached.snapshot();
    }

    /** All compatible state validates before any candidate view changes. */
    public void restoreViewState(Map<String, Object> snapshot) {
        LuaCallbackScope.requireUiStateWrite();
        requireActive();
        runtime.requirePreparedViews();
        if (root().getParent() != null) {
            throw new IllegalStateException("Restore view state before attaching the candidate graph");
        }
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(snapshot);
        List<Runnable> restores = new ArrayList<>();
        for (Map.Entry<String, Object> saved : detached.snapshot().entrySet()) {
            GuiViewAdapter view = ids.get(saved.getKey());
            if (view == null) {
                continue;
            }
            Map<String, Object> entry = stateRecord(saved.getValue());
            if (!(entry.get("type") instanceof String)) {
                throw new IllegalArgumentException("View snapshot requires its control type");
            }
            if (definitions.get(saved.getKey()).type.equals(entry.get("type"))) {
                Runnable restore = view.prepareStateRestore(stateRecord(entry.get("state")));
                if (restore == null) {
                    throw new IllegalArgumentException("View adapter returned no prepared state restoration");
                }
                restores.add(restore);
            }
        }
        for (Runnable restore : restores) {
            restore.run();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stateRecord(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("View snapshot requires detached records");
        }
        return (Map<String, Object>) value;
    }

    public void update() {
        requireActive();
        for (GuiNodeDefinition node : definition.presentation.nodes) {
            GuiViewAdapter view = ids.get(node.id);
            for (String interaction : INTERACTIONS) {
                runtime.interaction(node.id, interaction, view.interaction(interaction));
            }
        }
        runtime.update();
        publish();
    }

    public void activate() {
        requireActive();
        runtime.activatePrepared();
    }

    /**
     * Java animation replay only: no source collection, binding evaluation or event
     * delivery.
     */
    public void sample() {
        requireActive();
        runtime.sample();
        publish();
    }

    public GuiPresentationTree reload(GuiDocumentDefinition replacement) {
        return reload(replacement, candidate -> {
        });
    }

    /**
     * Hosts can preflight resolved layout/resources/paint before the previous
     * revision retires.
     */
    public GuiPresentationTree reload(GuiDocumentDefinition replacement, Consumer<GuiPresentationTree> prepare) {
        return reload(replacement, factory, prepare);
    }

    public GuiPresentationTree reload(GuiDocumentDefinition replacement, Factory replacementFactory,
            Consumer<GuiPresentationTree> prepare) {
        try (PreparedReload prepared = prepareReload(replacement, replacementFactory, prepare)) {
            return prepared.commit();
        }
    }

    public PreparedReload prepareReload(GuiDocumentDefinition replacement, Factory replacementFactory,
            Consumer<GuiPresentationTree> prepare) {
        requireActive();
        GuiPresentationTree candidate = new GuiPresentationTree(replacement, replacementFactory, capabilities);
        try {
            GuiPresentationRuntime.PreparedReload staged = runtime.prepareReload(replacement,
                    prepared -> prepared.setViewValidator(candidate::validateValues), prepared -> {
                        candidate.runtime = prepared;
                        candidate.publish();
                        candidate.retainFrom(this);
                        prepare.accept(candidate);
                    });
            candidate.runtime = staged.candidate();
            return new PreparedReload(this, candidate, staged);
        } catch (RuntimeException | Error failure) {
            candidate.closeAfterFailure(failure);
            throw failure;
        }
    }

    public PreparedReload prepareReload(GuiDocumentDefinition replacement, Consumer<GuiPresentationTree> prepare) {
        return prepareReload(replacement, factory, prepare);
    }

    public static final class PreparedReload implements AutoCloseable {
        private final GuiPresentationTree previous;
        private final GuiPresentationTree candidate;
        private final GuiPresentationRuntime.PreparedReload runtime;
        private boolean finished;

        private PreparedReload(GuiPresentationTree previous, GuiPresentationTree candidate,
                GuiPresentationRuntime.PreparedReload runtime) {
            this.previous = previous;
            this.candidate = candidate;
            this.runtime = runtime;
        }

        public GuiPresentationTree candidate() {
            if (finished) {
                throw new IllegalStateException("Prepared tree reload is already committed or discarded");
            }
            return candidate;
        }

        public void validate() {
            candidate();
            previous.requireActive();
            candidate.requireActive();
            runtime.validate();
        }

        public GuiPresentationTree commit() {
            validate();
            runtime.commit();
            previous.close();
            finished = true;
            return candidate;
        }

        @Override
        public void close() {
            if (!finished) {
                finished = true;
                try {
                    runtime.close();
                } finally {
                    candidate.close();
                }
            }
        }
    }

    private void publish() {
        for (int i = 0; i < views.size(); i++) {
            GuiPresentationNode node = runtime.element(definition.presentation.nodes.get(i).id);
            if (published[i] != node.valueRevision()) {
                views.get(i).publish(node);
                GuiElement element = views.get(i).element();
                if (node.visible() && "hidden".equals(node.get("visibility"))) {
                    element.setHidden(true);
                } else {
                    element.setVisible(node.visible() && !"collapsed".equals(node.get("visibility")));
                }
                published[i] = node.valueRevision();
            }
        }
    }

    private void validateValues(GuiNodeDefinition node, Map<String, Object> values) {
        ids.get(node.id).validateValues(values);
    }

    public GuiElement compatibleElement(GuiPresentationTree previous, GuiElement oldElement) {
        String id = previous.viewIds.get(oldElement);
        GuiNodeDefinition old = previous.definitions.get(id);
        GuiNodeDefinition replacement = definitions.get(id);
        return old != null && replacement != null && old.type.equals(replacement.type) ? ids.get(id).element() : null;
    }

    private void retainFrom(GuiPresentationTree previous) {
        for (GuiNodeDefinition node : definition.presentation.nodes) {
            GuiViewAdapter old = previous.ids.get(node.id);
            if (old != null && compatibleElement(previous, old.element()) == ids.get(node.id).element()) {
                ids.get(node.id).retainFrom(old);
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        try {
            if (runtime != null) {
                runtime.close();
            }
        } catch (RuntimeException error) {
            failure = error;
        }
        for (int i = views.size() - 1; i >= 0; i--) {
            try {
                views.get(i).close();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            LuaScriptErrors.add(definition.owner,
                    "Retained UI " + definition.name + " cleanup failed: " + failure.getMessage(),
                    LuaScriptErrors.Outcome.UNKNOWN, failure);
        }
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private void requireActive() {
        if (closed) {
            throw new IllegalStateException("Retained presentation revision is closed");
        }
    }

    private static void validate(GuiDocumentDefinition definition, Set<GuiCapability> capabilities) {
        if (!definition.retained || definition.presentation == null || definition.presentation.nodes.isEmpty()
                || definition.presentation.nodes.size() > 4096) {
            throw new IllegalArgumentException("Retained documents require 1..4096 nodes");
        }
        Set<String> ids = new HashSet<>();
        int[] depths = new int[definition.presentation.nodes.size()];
        for (int i = 0; i < depths.length; i++) {
            GuiNodeDefinition node = definition.presentation.nodes.get(i);
            if (node.id == null || !ids.add(node.id) || node.parent >= i || node.parent < -1
                    || i == 0 && node.parent != -1 || i > 0 && node.parent == -1
                    || node.properties.descriptors().isEmpty()) {
                throw new IllegalArgumentException(
                        node.path + ": invalid retained identity, parent or property catalog");
            }
            depths[i] = node.parent < 0 ? 1 : depths[node.parent] + 1;
            if (depths[i] > 32) {
                throw new IllegalArgumentException(node.path + ": retained nesting exceeds 32");
            }
            for (GuiPropertyDescriptor descriptor : node.properties.descriptors().values()) {
                if (!capabilities.containsAll(descriptor.requirements)) {
                    throw new IllegalArgumentException(
                            node.path + "." + descriptor.name + ": unsupported host capability");
                }
            }
        }
    }
}
