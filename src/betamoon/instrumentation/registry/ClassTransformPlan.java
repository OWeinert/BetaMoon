package betamoon.instrumentation.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Immutable ordered collection of hooks targeting one runtime class name. */
public final class ClassTransformPlan {
    private final String runtimeClassName;
    private final List<PlannedHook> hooks;

    ClassTransformPlan(String runtimeClassName, List<PlannedHook> hooks) {
        this.runtimeClassName = runtimeClassName;
        List<PlannedHook> ordered = new ArrayList<PlannedHook>(hooks);
        Collections.sort(ordered, new Comparator<PlannedHook>() {
            public int compare(PlannedHook left, PlannedHook right) {
                int priority = Integer.compare(right.getDefinition().getPriority(),
                    left.getDefinition().getPriority());
                return priority != 0 ? priority
                    : left.getDefinition().getId().compareTo(right.getDefinition().getId());
            }
        });
        this.hooks = Collections.unmodifiableList(ordered);
    }

    public String getRuntimeClassName() {
        return runtimeClassName;
    }

    public List<PlannedHook> getHooks() {
        return hooks;
    }
}
