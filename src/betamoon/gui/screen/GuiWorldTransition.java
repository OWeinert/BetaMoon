package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentLimits;
import betamoon.gui.presentation.GuiPublishedData;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded detached metadata; resolving a target and acquiring fresh authority
 * belong to the controller.
 */
public final class GuiWorldTransition {
    public static final GuiWorldTransition CLOSE = new GuiWorldTransition();
    public final String replacement;
    public final Map<String, Object> state;
    public final Map<String, Object> data;
    public final long metadataBytes;

    private GuiWorldTransition() {
        replacement = null;
        state = Collections.emptyMap();
        data = Collections.emptyMap();
        metadataBytes = 0;
    }

    @SuppressWarnings("unchecked")
    private GuiWorldTransition(String key, Map<String, Object> state, Map<String, Object> data) {
        replacement = GuiComponentDefinition.key(key);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("state", Objects.requireNonNull(state, "transition state"));
        values.put("data", Objects.requireNonNull(data, "transition data"));
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(values);
        GuiComponentLimits.Values budget = new GuiComponentLimits.Values(65536, 4096);
        budget.text(replacement);
        budget.add(detached.snapshot());
        this.state = (Map<String, Object>) detached.snapshot().get("state");
        this.data = (Map<String, Object>) detached.snapshot().get("data");
        metadataBytes = budget.estimatedBytes() + 128;
    }

    public static GuiWorldTransition replace(String key, Map<String, Object> state, Map<String, Object> data) {
        return new GuiWorldTransition(key, state, data);
    }

    public boolean replaces() {
        return replacement != null;
    }
}
