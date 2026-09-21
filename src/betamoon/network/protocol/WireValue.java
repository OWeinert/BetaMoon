package betamoon.network.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable value tree used for declared synchronized state. */
public final class WireValue {
    public enum Type {
        NULL(0), BOOLEAN(1), INTEGER(2), LONG(3), DOUBLE(4), STRING(5), LIST(6), RECORD(7);

        private final int id;

        Type(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }

        static Type byId(int id) throws ProtocolException {
            for (Type type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            throw new ProtocolException("Unknown synchronized value type: " + id);
        }
    }

    private final Type type;
    private final Object value;

    private WireValue(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static WireValue nullValue() {
        return new WireValue(Type.NULL, null);
    }

    public static WireValue bool(boolean value) {
        return new WireValue(Type.BOOLEAN, Boolean.valueOf(value));
    }

    public static WireValue integer(int value) {
        return new WireValue(Type.INTEGER, Integer.valueOf(value));
    }

    public static WireValue longInteger(long value) {
        return new WireValue(Type.LONG, Long.valueOf(value));
    }

    public static WireValue decimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Synchronized numbers must be finite");
        }
        return new WireValue(Type.DOUBLE, Double.valueOf(value));
    }

    public static WireValue text(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Synchronized text is required");
        }
        return new WireValue(Type.STRING, value);
    }

    public static WireValue list(List<WireValue> values) {
        if (values == null) {
            throw new IllegalArgumentException("Synchronized list is required");
        }
        return new WireValue(Type.LIST, Collections.unmodifiableList(new ArrayList<WireValue>(values)));
    }

    public static WireValue record(Map<String, WireValue> values) {
        if (values == null) {
            throw new IllegalArgumentException("Synchronized record is required");
        }
        return new WireValue(Type.RECORD,
                Collections.unmodifiableMap(new LinkedHashMap<String, WireValue>(values)));
    }

    public Type type() {
        return type;
    }

    public Object value() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public List<WireValue> listValue() {
        return (List<WireValue>) value;
    }

    @SuppressWarnings("unchecked")
    public Map<String, WireValue> recordValue() {
        return (Map<String, WireValue>) value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WireValue)) {
            return false;
        }
        WireValue that = (WireValue) other;
        return type == that.type && (value == null ? that.value == null : value.equals(that.value));
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + (value == null ? 0 : value.hashCode());
    }
}
