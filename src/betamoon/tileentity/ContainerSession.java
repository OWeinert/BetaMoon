package betamoon.tileentity;

import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Typed values that live only while one container instance remains open. */
public final class ContainerSession {
    private final Map<String, ContainerDefinition.SessionField> fields;
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();
    private boolean active = true;

    public ContainerSession(Map<String, ContainerDefinition.SessionField> fields) {
        this.fields = fields;
        for (ContainerDefinition.SessionField field : fields.values()) {
            values.put(field.name, field.defaultValue);
        }
    }

    public Object get(String name) {
        requireActive();
        requireField(name);
        return values.get(name);
    }

    public void set(String name, Object value) {
        requireActive();
        ContainerDefinition.SessionField field = requireField(name);
        if (!field.type.accepts(value)) {
            throw new LuaError("Invalid session value for '" + name + "'; expected "
                    + field.type.getLuaName() + ".");
        }
        if (value instanceof Number && !Double.isFinite(((Number) value).doubleValue())) {
            throw new LuaError("Session numbers must be finite.");
        }
        if (value instanceof String && ((String) value).length() > field.maximumLength) {
            throw new LuaError("Session field '" + name + "' is limited to " + field.maximumLength
                    + " characters.");
        }
        values.put(name, field.type == TileDataType.NUMBER
                ? Double.valueOf(((Number) value).doubleValue()) : value);
    }

    public void setLua(String name, LuaValue value) {
        ContainerDefinition.SessionField field = requireField(name);
        set(name, field.type.fromLua(value));
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
            throw new LuaError("Unknown container session field: " + name);
        }
        return field;
    }

    private void requireActive() {
        if (!active) {
            throw new LuaError("Container session is no longer active.");
        }
    }
}
