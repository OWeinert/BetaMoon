package betamoon.gui.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable keyed choices shared by selection controls; labels never identify
 * an option.
 */
public final class GuiSelectionOptions {
    public static final int MAX_OPTIONS = 256;

    public static final class Option {
        public final String key;
        public final String label;
        public final boolean enabled;

        private Option(Map<?, ?> values) {
            for (Object name : values.keySet()) {
                if (!Arrays.asList("key", "label", "enabled").contains(name)) {
                    throw new IllegalArgumentException("Unknown selection option field: " + name);
                }
            }
            key = key(values.get("key"), false);
            Object label = values.getOrDefault("label", null);
            if (label != null && (!(label instanceof String) || ((String) label).length() > 8192)) {
                throw new IllegalArgumentException("Option labels require at most 8192 UTF-16 units");
            }
            this.label = label == null ? key : (String) label;
            if (values.containsKey("enabled") && !(values.get("enabled") instanceof Boolean)) {
                throw new IllegalArgumentException("Option enabled requires a boolean");
            }
            enabled = !Boolean.FALSE.equals(values.get("enabled"));
        }
    }

    public final List<Option> options;

    public GuiSelectionOptions(Object values) {
        if (values instanceof Map && ((Map<?, ?>) values).isEmpty()) {
            values = Collections.emptyList();
        }
        if (!(values instanceof List) || ((List<?>) values).size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("Selection options require a dense list with at most 256 entries");
        }
        List<Option> options = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Object value : (List<?>) values) {
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("Selection options require keyed records");
            }
            Option option = new Option((Map<?, ?>) value);
            if (!keys.add(option.key)) {
                throw new IllegalArgumentException("Selection option keys must be unique");
            }
            options.add(option);
        }
        this.options = Collections.unmodifiableList(options);
    }

    public String selection(String key) {
        for (Option option : options) {
            if (option.key.equals(key) && option.enabled) {
                return key;
            }
        }
        return "";
    }

    public String navigate(String current, int direction, boolean boundary) {
        int index = direction > 0 ? -1 : options.size();
        if (!boundary) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).key.equals(current)) {
                    index = i;
                    break;
                }
            }
        }
        for (int attempt = 0; attempt < options.size(); attempt++) {
            index = (index + direction + options.size()) % options.size();
            if (options.get(index).enabled) {
                return options.get(index).key;
            }
        }
        return "";
    }

    public static String key(Object value, boolean allowEmpty) {
        if (!(value instanceof String) || ((String) value).length() > 255
                || !allowEmpty && ((String) value).isEmpty()) {
            throw new IllegalArgumentException("Selection keys require " + (allowEmpty ? "" : "nonempty ")
                    + "strings of at most 255 UTF-16 units");
        }
        return (String) value;
    }
}
