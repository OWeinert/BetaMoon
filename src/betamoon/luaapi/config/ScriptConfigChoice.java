package betamoon.luaapi.config;

import java.util.Objects;

/** One selectable GUI value, with an optional label distinct from its saved value. */
public final class ScriptConfigChoice {
    private final Object value;
    private final String label;

    ScriptConfigChoice(Object value, String label) {
        this.value = value;
        this.label = label;
    }

    public Object value() {
        return value;
    }

    public String label() {
        return label;
    }

    public boolean matches(Object candidate) {
        if (value instanceof Number && candidate instanceof Number) {
            return ((Number) value).doubleValue() == ((Number) candidate).doubleValue();
        }
        return Objects.equals(value, candidate);
    }
}
