package betamoon.gui.presentation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One coherent source snapshot; hosts supply detached, validated field values.
 */
public final class GuiPresentationSources {
    public final Map<String, Object> state;
    public final Map<String, Object> data;
    public final Map<String, Object> nativeData;

    public GuiPresentationSources(Map<String, Object> state, Map<String, Object> data, Map<String, Object> nativeData) {
        this.state = copy(state);
        this.data = copy(data);
        this.nativeData = copy(nativeData);
    }

    private static Map<String, Object> copy(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
