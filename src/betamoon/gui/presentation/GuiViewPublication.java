package betamoon.gui.presentation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A complete validated property/ownership candidate used by Java-only local
 * state projection.
 */
public final class GuiViewPublication {
    public final Map<String, Object> values;
    private final Set<String> controlled;

    public GuiViewPublication(Map<String, Object> values, Set<String> controlled) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.controlled = Collections.unmodifiableSet(new LinkedHashSet<>(controlled));
    }

    public boolean controlled(String property) {
        return controlled.contains(property);
    }
}
