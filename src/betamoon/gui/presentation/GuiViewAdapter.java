package betamoon.gui.presentation;

import betamoon.gui.framework.GuiElement;
import java.util.Map;

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

    @Override
    default void close() {
    }
}
