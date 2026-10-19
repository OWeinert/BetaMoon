package betamoon.tileentity;

import betamoon.gui.presentation.GuiValueObserver;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Typed values that live only while one container instance remains open. */
public final class ContainerSession {
    private final Map<String, ContainerDefinition.SessionField> fields;
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();
    private final GuiValueObserver observer = new GuiValueObserver();
    private boolean active = true;

    public ContainerSession(Map<String, ContainerDefinition.SessionField> fields) {
        this.fields = fields;
        for (ContainerDefinition.SessionField field : fields.values()) {
            Object value = field.defaultValue;
            if (field.type == TileDataType.NUMBER && ((Number) value).doubleValue() == 0) {
                value = Double.valueOf(0);
            }
            values.put(field.name, value);
        }
    }

    public Object get(String name) {
        requireActive();
        requireField(name);
        return values.get(name);
    }

    public void set(String name, Object value) {
        requireActive();
        LuaCallbackScope.requireUiStateWrite();
        ContainerDefinition.SessionField field = requireField(name);
        if (!field.type.accepts(value)) {
            throw new LuaError("Invalid state value for '" + name + "'; expected "
                    + field.type.getLuaName() + ".");
        }
        if (value instanceof Number && !Double.isFinite(((Number) value).doubleValue())) {
            throw new LuaError("State numbers must be finite.");
        }
        if (value instanceof String && ((String) value).length() > field.maximumLength) {
            throw new LuaError("State field '" + name + "' is limited to " + field.maximumLength
                    + " characters.");
        }
        double number = value instanceof Number ? ((Number) value).doubleValue() : 0;
        Object normalized = field.type == TileDataType.NUMBER ? Double.valueOf(number == 0 ? 0 : number) : value;
        Object previous = values.put(name, normalized);
        observer.changed(name, previous, normalized);
    }

    public void setLua(String name, LuaValue value) {
        ContainerDefinition.SessionField field = requireField(name);
        set(name, field.type.fromLua(value));
    }

    public AutoCloseable observe(GuiValueObserver.Listener listener) {
        requireActive();
        return observer.subscribe(listener);
    }

    public Map<String, Object> snapshot() {
        requireActive();
        return new LinkedHashMap<>(values);
    }

    public void close() {
        active = false;
        values.clear();
    }

    public boolean isActive() {
        return active;
    }

    private ContainerDefinition.SessionField requireField(String name) {
        ContainerDefinition.SessionField field = fields.get(name);
        if (field == null) {
            throw new LuaError("Unknown container state field: " + name);
        }
        return field;
    }

    private void requireActive() {
        if (!active) {
            throw new LuaError("Container state is no longer active.");
        }
    }
}
