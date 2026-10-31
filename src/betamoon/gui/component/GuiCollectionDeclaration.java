package betamoon.gui.component;

import java.util.Objects;
import org.luaj.vm2.LuaValue;

/**
 * Parsed row authorship, separate from the collection's detached record model.
 */
public final class GuiCollectionDeclaration {
    public final LuaValue row;
    public final GuiComponentSchema state;

    public GuiCollectionDeclaration(LuaValue row, GuiComponentSchema state) {
        if (row == null || !row.isnil() && !row.isfunction()) {
            throw new IllegalArgumentException("A collection row requires a function");
        }
        this.row = row;
        this.state = Objects.requireNonNull(state, "row state schema");
        if (row.isnil() && !state.fields().isEmpty()) {
            throw new IllegalArgumentException("rowState requires a row function");
        }
    }
}
