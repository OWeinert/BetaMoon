package betamoon.gui.collection;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

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
        private final Consumer<Map<String, ?>> statePublication;

        public Context(GuiContext gui, GuiRowBudget budget, GuiCollectionModel.Record record, int depth, int width,
                int height, Map<String, Object> state, Row previous) {
            this(gui, budget, record, depth, width, height, state, previous, values -> {
                throw new IllegalStateException("This row context has no live state publication owner");
            });
        }

        public Context(GuiContext gui, GuiRowBudget budget, GuiCollectionModel.Record record, int depth, int width,
                int height, Map<String, Object> state, Row previous, Consumer<Map<String, ?>> statePublication) {
            this.gui = gui;
            this.budget = budget;
            this.record = record;
            this.depth = depth;
            this.width = width;
            this.height = height;
            this.state = state;
            this.previous = previous;
            this.statePublication = Objects.requireNonNull(statePublication, "row state publication");
        }

        /**
         * Publish combined typed/view state only while this exact realized row is the
         * accepted owner.
         */
        public void publishState(Map<String, ?> values) {
            LuaCallbackScope.requireUiStateWrite();
            statePublication.accept(values);
        }
    }
}
