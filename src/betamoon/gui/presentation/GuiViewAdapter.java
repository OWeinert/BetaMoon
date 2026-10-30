package betamoon.gui.presentation;

import betamoon.gui.framework.GuiElement;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Java views consume published values; animation replay cannot call Lua through
 * this adapter.
 */
public interface GuiViewAdapter extends AutoCloseable {
    GuiElement element();

    /**
     * Check all target values before any candidate views are published or the
     * active revision is retired.
     */
    default void validateValues(Map<String, Object> values) {
    }

    void publish(GuiPresentationNode node);

    /**
     * Copies compatible Java editor/interaction state into an already validated
     * candidate view.
     */
    default void retainFrom(GuiViewAdapter previous) {
    }

    /**
     * Detached local values only; pointer gestures, native resources and callbacks
     * are never persisted.
     */
    default Map<String, Object> captureState() {
        return Collections.emptyMap();
    }

    /** Pure local state after the proposed properties/ownership are published. */
    default Map<String, Object> projectState(Map<String, Object> values, Predicate<String> controlled,
            Map<String, Object> current) {
        return current;
    }

    default void admitStateWith(GuiViewStateAdmission admission) {
    }

    /**
     * Validate now, then return an infallible Java-only application for an
     * unactivated candidate.
     */
    default Runnable prepareStateRestore(Map<String, Object> state) {
        return () -> {
        };
    }

    default boolean interaction(String name) {
        return false;
    }

    @Override
    default void close() {
    }
}
