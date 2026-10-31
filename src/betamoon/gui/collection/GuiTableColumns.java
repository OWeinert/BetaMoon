package betamoon.gui.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validated primitive table fields and shared header/cell widths; no Lua
 * comparators.
 */
public final class GuiTableColumns {
    public static final int MAX_COLUMNS = 64;
    public static final class Column {
        public final String key;
        public final String label;
        public final String field;
        public final int width;

        private Column(Map<?, ?> values) {
            for (Object name : values.keySet()) {
                if (!Arrays.asList("key", "label", "field", "width").contains(name)) {
                    throw new IllegalArgumentException("Unknown table column field: " + name);
                }
            }
            key = text(values.get("key"), null, 255);
            label = text(values.get("label"), key, 8192);
            field = text(values.get("field"), key, 255);
            Object size = values.get("width");
            if (size != null && (!(size instanceof Number) || !Double.isFinite(((Number) size).doubleValue())
                    || ((Number) size).doubleValue() != Math.rint(((Number) size).doubleValue())
                    || ((Number) size).doubleValue() < 0 || ((Number) size).doubleValue() > 16384)) {
                throw new IllegalArgumentException("Table column width requires an integer from 0 to 16384");
            }
            width = size == null ? 0 : ((Number) size).intValue();
        }

        public Object value(GuiCollectionModel.Record record) {
            return record.data.get(field);
        }
    }

    public final List<Column> columns;
    private final int fixedWidth;
    private final int flexible;

    public GuiTableColumns(Object values) {
        if (!(values instanceof List) || ((List<?>) values).isEmpty() || ((List<?>) values).size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("Tables require 1..64 columns");
        }
        List<Column> columns = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int fixed = 0;
        int flexible = 0;
        for (Object value : (List<?>) values) {
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("Table columns require records");
            }
            Column column = new Column((Map<?, ?>) value);
            if (!keys.add(column.key)) {
                throw new IllegalArgumentException("Table column keys must be unique");
            }
            columns.add(column);
            fixed += column.width;
            flexible += column.width == 0 ? 1 : 0;
        }
        if (fixed + flexible > 16384) {
            throw new IllegalArgumentException("Table columns exceed 16384 content pixels");
        }
        this.columns = Collections.unmodifiableList(columns);
        fixedWidth = fixed;
        this.flexible = flexible;
    }

    public Column column(String key) {
        for (Column column : columns) {
            if (column.key.equals(key)) {
                return column;
            }
        }
        return null;
    }

    public int[] widths(int viewport) {
        int[] result = new int[columns.size()];
        int available = Math.max(flexible, Math.min(16384, viewport) - fixedWidth);
        int remaining = flexible;
        for (int i = 0; i < result.length; i++) {
            int fixed = columns.get(i).width;
            result[i] = fixed == 0 ? available / remaining-- : fixed;
            if (fixed == 0) {
                available -= result[i];
            }
        }
        return result;
    }

    public void validate(GuiCollectionModel.Snapshot records, boolean defaultRows, String sortColumn,
            String direction) {
        if (!Arrays.asList("none", "ascending", "descending").contains(direction)
                || !sortColumn.isEmpty() && column(sortColumn) == null
                || sortColumn.isEmpty() && !direction.equals("none")) {
            throw new IllegalArgumentException("Table sort requires a declared column and a valid direction");
        }
        for (GuiCollectionModel.Record record : records.records) {
            for (Column column : columns) {
                Object value = column.value(record);
                if (value instanceof Map || value instanceof List
                        || defaultRows && value instanceof String && ((String) value).length() > 65534) {
                    throw new IllegalArgumentException("Table cells require bounded primitive data fields");
                }
            }
        }
    }

    public List<String> project(GuiCollectionModel.Snapshot records, String sortColumn, String direction,
            String filter) {
        if (filter.isEmpty() && direction.equals("none")) {
            return null;
        }
        String match = filter.toLowerCase(Locale.ROOT);
        List<GuiCollectionModel.Record> rows = new ArrayList<>();
        for (GuiCollectionModel.Record record : records.records) {
            boolean included = record.label.toLowerCase(Locale.ROOT).contains(match);
            for (Column column : columns) {
                included |= cell(column.value(record)).toLowerCase(Locale.ROOT).contains(match);
            }
            if (included) {
                rows.add(record);
            }
        }
        if (!direction.equals("none")) {
            Column column = column(sortColumn);
            Comparator<GuiCollectionModel.Record> comparator = (a, b) -> compare(column.value(a), column.value(b));
            rows.sort(direction.equals("descending") ? comparator.reversed() : comparator);
        }
        List<String> keys = new ArrayList<>();
        for (GuiCollectionModel.Record record : rows) {
            keys.add(record.key);
        }
        return Collections.unmodifiableList(keys);
    }

    public static String cell(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int compare(Object a, Object b) {
        int left = type(a);
        int right = type(b);
        if (left != right) {
            return Integer.compare(left, right);
        }
        if (a instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof Boolean) {
            return Boolean.compare((Boolean) a, (Boolean) b);
        }
        return cell(a).compareToIgnoreCase(cell(b));
    }

    private static int type(Object value) {
        return value == null ? 0 : value instanceof Boolean ? 1 : value instanceof Number ? 2 : 3;
    }

    private static String text(Object value, String fallback, int maximum) {
        String text = value == null ? fallback : value instanceof String ? (String) value : null;
        if (text == null || text.isEmpty() || text.length() > maximum) {
            throw new IllegalArgumentException("Table column keys/labels/fields require bounded nonempty strings");
        }
        return text;
    }
}
