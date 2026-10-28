package betamoon.interop.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Private result construction. Implementations must not publish partial graphs
 * or execute foreign callbacks.
 */
public interface NativeDataOutput {
    Object record(int fields);

    Object sequence(int length);

    Object scalar(Object value);

    void field(Object record, String key, Object value);

    void element(Object sequence, int index, Object value);

    NativeDataOutput JAVA = new NativeDataOutput() {
        public Object record(int fields) {
            return new LinkedHashMap<String, Object>(Math.max(1, fields * 4 / 3 + 1));
        }

        public Object sequence(int length) {
            return new ArrayList<>(length);
        }

        public Object scalar(Object value) {
            return value;
        }

        @SuppressWarnings("unchecked")
        public void field(Object record, String key, Object value) {
            ((Map<String, Object>) record).put(key, value);
        }

        @SuppressWarnings("unchecked")
        public void element(Object sequence, int index, Object value) {
            ((List<Object>) sequence).add(value);
        }
    };
}
