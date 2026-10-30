package betamoon.gui.component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Engine-owned declaration/render-data budgets; Lua closures themselves remain
 * owned by the script runtime.
 */
public final class GuiComponentLimits {
    public static final long OWNER_METADATA_BYTES = 16 * 1024 * 1024;
    public static final long REGISTRY_METADATA_BYTES = 64 * 1024 * 1024;

    public static final class Values {
        private final int maximumText;
        private final int maximumValues;
        private int text;
        private int values;
        private long estimatedBytes;

        public Values(int maximumText, int maximumValues) {
            this.maximumText = maximumText;
            this.maximumValues = maximumValues;
        }

        public void add(Object value) {
            add(value, 0);
        }

        private void add(Object value, int depth) {
            if (++values > maximumValues || depth > 16) {
                throw new IllegalArgumentException("UI declaration value/depth budget exceeded");
            }
            estimatedBytes += 24;
            if (value instanceof String) {
                text((String) value);
            } else if (value instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException("UI declaration records require string keys");
                    }
                    text((String) entry.getKey());
                    add(entry.getValue(), depth + 1);
                }
            } else if (value instanceof List) {
                for (Object entry : (List<?>) value) {
                    add(entry, depth + 1);
                }
            } else if (value != null && !(value instanceof Boolean) && !(value instanceof Number)) {
                throw new IllegalArgumentException("UI declaration must contain detached values");
            }
        }

        public void text(String value) {
            if (value.length() > maximumText - text) {
                throw new IllegalArgumentException("UI declaration text budget exceeded");
            }
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maximumText - text) {
                throw new IllegalArgumentException("UI declaration UTF-8 budget exceeded");
            }
            text += bytes;
            estimatedBytes += bytes;
        }

        public long estimatedBytes() {
            return estimatedBytes;
        }
    }

    private GuiComponentLimits() {
    }
}
