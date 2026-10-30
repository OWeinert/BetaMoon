package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One screen's typed detached model and dirty-field notifications. */
public final class GuiScreenData implements AutoCloseable {
    private final GuiComponentSchema schema;
    private final GuiPublishedData data = new GuiPublishedData();
    private final List<GuiPresentationHost.Observer> observers = new ArrayList<>();
    private boolean closed;

    public GuiScreenData(GuiComponentSchema schema, Map<String, Object> initial) {
        this.schema = schema;
        data.publish(schema.patch(schema.normalize(Collections.emptyMap()), initial));
    }

    public Map<String, Object> snapshot() {
        requireActive();
        return data.snapshot();
    }

    public long revision() {
        requireActive();
        return data.revision();
    }

    public void publish(Map<String, Object> changes) {
        LuaCallbackScope.requireUiStateWrite();
        requireActive();
        Map<String, Object> before = data.snapshot();
        if (!data.publish(schema.patch(before, changes))) {
            return;
        }
        for (String name : data.snapshot().keySet()) {
            Object previous = before.get(name);
            Object value = data.snapshot().get(name);
            if (!Objects.equals(previous, value)) {
                for (GuiPresentationHost.Observer observer : new ArrayList<>(observers)) {
                    observer.changed("data:" + name, previous, value);
                }
            }
        }
    }

    public AutoCloseable observe(GuiPresentationHost.Observer observer) {
        requireActive();
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    public static GuiScreenData refresh(GuiComponentSchema schema, Map<String, Object> previous) {
        Map<String, Object> compatible = new LinkedHashMap<>();
        for (Map.Entry<String, GuiComponentSchema.Field> field : schema.fields().entrySet()) {
            if (previous.containsKey(field.getKey())) {
                try {
                    compatible.put(field.getKey(), field.getValue().descriptor.normalize(previous.get(field.getKey())));
                } catch (IllegalArgumentException incompatible) {
                    /* Use the current validated default. */ }
            }
        }
        return new GuiScreenData(schema, compatible);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            observers.clear();
            data.publish(Collections.emptyMap());
        }
    }

    private void requireActive() {
        if (closed) {
            throw new IllegalStateException("Screen data model is closed");
        }
    }
}
