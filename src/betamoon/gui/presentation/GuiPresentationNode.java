package betamoon.gui.presentation;

import betamoon.gui.animation.GuiAnimationRequest;
import betamoon.gui.animation.GuiTransitionPolicy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable presentation of one node in one open screen, never shared through the
 * registry.
 */
public final class GuiPresentationNode {
    public final GuiNodeDefinition definition;
    final Map<String, Object> baseline = new LinkedHashMap<>();
    final Map<String, Object> bindingValues = new LinkedHashMap<>();
    final Map<String, Object> derived = new LinkedHashMap<>();
    final Map<String, Object> targets = new LinkedHashMap<>();
    final Map<String, Object> displayed = new LinkedHashMap<>();
    final Map<String, Override> overrides = new LinkedHashMap<>();
    final Map<String, Track> tracks = new LinkedHashMap<>();
    final Map<String, String> interactionKeys = new LinkedHashMap<>();
    final Set<String> dependencies = new LinkedHashSet<>();
    final Set<String> diagnostics = new LinkedHashSet<>();
    GuiTransitionPolicy policy;
    Candidate pending;
    boolean suspended;
    boolean bindingFailed;
    boolean dirty = true;
    int evaluations;
    int suppressedDiagnostics;

    GuiPresentationNode(GuiNodeDefinition definition) {
        this.definition = definition;
        for (String name : new String[]{"hovered", "pressed", "focused", "disabled", "selected", "invalid"}) {
            interactionKeys.put(name, "interaction:" + definition.path + ":" + name);
        }
        baseline.putAll(definition.properties.baseline());
        derived.putAll(baseline);
        targets.putAll(baseline);
        displayed.putAll(baseline);
        policy = definition.transition;
    }

    public Object get(String property) {
        return displayed.get(definition.properties.canonical(property));
    }

    public Object target(String property) {
        return targets.get(definition.properties.canonical(property));
    }

    public double number(String property, double fallback) {
        Object value = get(property);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    void sample(double now) {
        for (String property : new LinkedHashSet<>(tracks.keySet())) {
            Track track = tracks.get(property);
            if (now >= track.start + track.options.delay + track.options.duration) {
                displayed.put(property, track.to);
                tracks.remove(property);
                track.request.property(property, true, null);
            } else if (now > track.start + track.options.delay) {
                double p = (now - track.start - track.options.delay) / track.options.duration;
                displayed.put(property, definition.properties.interpolate(property, track.from, track.to,
                        track.options.easing.sample(p)));
            }
        }
    }

    void retarget(Map<String, Object> values, GuiTransitionPolicy transition, GuiAnimationRequest request, double now,
            boolean immediate, boolean explicit, String reason) {
        sample(now);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String property = entry.getKey();
            Object to = entry.getValue();
            boolean unchanged = Objects.equals(targets.get(property), to);
            Track old = tracks.get(property);
            if (unchanged && !explicit) {
                if (request != null) {
                    request.property(property, true, null);
                }
                continue;
            }
            if (old != null) {
                old.request.property(property, false, reason);
                tracks.remove(property);
            }
            targets.put(property, to);
            if (unchanged && old != null && explicit && !immediate) {
                tracks.put(property, new Track(old.from, old.to, old.start, old.options, request));
                continue;
            }
            Object from = displayed.get(property);
            GuiTransitionPolicy.Options options = transition.forProperty(property);
            boolean tween = !immediate && !Objects.equals(from, to) && from instanceof Number && to instanceof Number
                    && definition.properties.animatable().contains(property) && options != null
                    && (options.duration > 0 || options.delay > 0);
            if (tween) {
                tracks.put(property, new Track(from, to, now, options, request));
            } else {
                displayed.put(property, to);
                if (request != null) {
                    request.property(property, true, null);
                }
            }
        }
        if (request != null) {
            request.commit();
        }
    }

    static final class Override {
        final Object value;
        final boolean held;

        Override(Object value, boolean held) {
            this.value = value;
            this.held = held;
        }
    }

    static final class Track {
        final Object from;
        final Object to;
        final double start;
        final GuiTransitionPolicy.Options options;
        final GuiAnimationRequest request;

        Track(Object from, Object to, double start, GuiTransitionPolicy.Options options, GuiAnimationRequest request) {
            this.from = from;
            this.to = to;
            this.start = start;
            this.options = options;
            this.request = request;
        }
    }

    static final class Candidate {
        final Map<String, Object> bindingValues;
        final Map<String, Object> derived;
        final Map<String, Object> effective;
        final Map<String, Override> overrides;
        final Set<String> dependencies;
        final GuiTransitionPolicy policy;

        Candidate(Map<String, Object> bindingValues, Map<String, Object> derived, Map<String, Object> effective,
                Map<String, Override> overrides, Set<String> dependencies, GuiTransitionPolicy policy) {
            this.bindingValues = bindingValues;
            this.derived = derived;
            this.effective = effective;
            this.overrides = overrides;
            this.dependencies = dependencies;
            this.policy = policy;
        }
    }
}
