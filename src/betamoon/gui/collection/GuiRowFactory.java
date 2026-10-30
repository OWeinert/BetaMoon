package betamoon.gui.collection;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import java.util.Map;

/**
 * Java adapter boundary; Lua factories are invoked by the scoped component
 * adapter outside paint.
 */
@FunctionalInterface
public interface GuiRowFactory {
    /**
     * A failed preparation must release anything acquired before returning its
     * candidate.
     */
    Prepared prepare(Context context);

    interface Row extends AutoCloseable {
        GuiElement element();

        Map<String, Object> state();

        default void update(GuiContext context, int width, int height) {
        }

        default boolean needsRefresh() {
            return false;
        }

        default Object refreshStamp() {
            return null;
        }

        default GuiElement compatibleFocus(Row previous, GuiElement focused) {
            return this == previous ? focused : null;
        }

        /** Release row-owned resources; repeated calls must be harmless. */
        @Override
        void close();
    }

    interface Prepared extends AutoCloseable {
        Row candidate();

        GuiRowBudget.Reservation reservation();

        default void validate() {
        }

        /**
         * Returns the same candidate after validation; commit must not invoke fallible
         * user callbacks.
         */
        Row commit();

        @Override
        void close();
    }

    final class Context {
        public final GuiContext gui;
        public final GuiRowBudget budget;
        public final GuiCollectionModel.Record record;
        public final int depth;
        public final int width;
        public final int height;
        public final Map<String, Object> state;
        public final Row previous;

        public Context(GuiContext gui, GuiRowBudget budget, GuiCollectionModel.Record record, int depth, int width,
                int height, Map<String, Object> state, Row previous) {
            this.gui = gui;
            this.budget = budget;
            this.record = record;
            this.depth = depth;
            this.width = width;
            this.height = height;
            this.state = state;
            this.previous = previous;
        }
    }
}
