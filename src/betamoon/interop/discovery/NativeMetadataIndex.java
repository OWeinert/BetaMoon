package betamoon.interop.discovery;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable bounded posting indexes. A dimension that exceeds its budget falls
 * back to the original table.
 */
final class NativeMetadataIndex {
    private static final int MAXIMUM_KEYS = 400000;
    private static final int MAXIMUM_POSTINGS = 2000000;
    private static final String[] DIMENSIONS = {"name", "originId", "declaringType", "descriptor"};
    private final Map<String, Table> tables;

    NativeMetadataIndex(Map<String, List<Map<String, Object>>> rows) {
        Map<String, Table> result = new LinkedHashMap<>();
        Budget budget = new Budget();
        for (Map.Entry<String, List<Map<String, Object>>> entry : rows.entrySet()) {
            result.put(entry.getKey(), new Table(entry.getValue(), budget));
        }
        tables = Collections.unmodifiableMap(result);
    }

    List<Map<String, Object>> candidates(String kind, Map<String, Object> filter) {
        Table table = tables.get(kind);
        int[] smallest = null;
        for (String dimension : DIMENSIONS) {
            if (!(filter.get(dimension) instanceof String)) {
                continue;
            }
            Map<String, int[]> index = table.indexes.get(dimension);
            if (index == null) {
                continue;
            }
            int[] positions = index.get(filter.get(dimension));
            if (positions == null) {
                return Collections.emptyList();
            }
            if (smallest == null || positions.length < smallest.length) {
                smallest = positions;
            }
        }
        return smallest == null ? table.rows : new PostingRows(table.rows, smallest);
    }

    private static final class Table {
        private final List<Map<String, Object>> rows;
        private final Map<String, Map<String, int[]>> indexes = new LinkedHashMap<>();

        private Table(List<Map<String, Object>> rows, Budget budget) {
            this.rows = rows;
            for (String dimension : DIMENSIONS) {
                Map<String, Posting> pending = new HashMap<>();
                int keys = 0;
                int postings = 0;
                boolean limited = false;
                for (int position = 0; position < rows.size(); position++) {
                    if ((position & 255) == 0 && Thread.currentThread().isInterrupted()) {
                        throw new CancellationException("native_index_cancelled");
                    }
                    Map<String, Object> row = rows.get(position);
                    Object primary = row.get(dimension);
                    Object alternate = null;
                    if (dimension.equals("name")) {
                        primary = row.getOrDefault("name", row.getOrDefault("path", row.getOrDefault("id", "")));
                        alternate = row.get("runtimeName");
                    } else if (dimension.equals("originId") && primary == null) {
                        primary = row.get("id");
                    } else if (dimension.equals("descriptor")) {
                        alternate = row.get("runtimeDescriptor");
                    }
                    if (primary instanceof String) {
                        keys += add(pending, (String) primary, position);
                        postings++;
                    }
                    if (alternate instanceof String && !alternate.equals(primary)) {
                        keys += add(pending, (String) alternate, position);
                        postings++;
                    }
                    if (budget.keys + keys > MAXIMUM_KEYS || budget.postings + postings > MAXIMUM_POSTINGS) {
                        limited = true;
                        break;
                    }
                }
                if (limited) {
                    continue;
                }
                Map<String, int[]> frozen = new HashMap<>(pending.size());
                for (Map.Entry<String, Posting> entry : pending.entrySet()) {
                    frozen.put(entry.getKey(), entry.getValue().freeze());
                }
                budget.keys += keys;
                budget.postings += postings;
                indexes.put(dimension, Collections.unmodifiableMap(frozen));
            }
        }
    }

    private static int add(Map<String, Posting> map, String key, int position) {
        Posting posting = map.get(key);
        boolean added = posting == null;
        if (added) {
            posting = new Posting();
            map.put(key, posting);
        }
        posting.add(position);
        return added ? 1 : 0;
    }

    private static final class Budget {
        private int keys;
        private int postings;
    }

    private static final class Posting {
        private int first;
        private int size;
        private int[] values;

        private void add(int position) {
            if (size == 0) {
                first = position;
            } else {
                if (values == null) {
                    values = new int[4];
                    values[0] = first;
                } else if (size == values.length) {
                    values = Arrays.copyOf(values, size * 2);
                }
                values[size] = position;
            }
            size++;
        }

        private int[] freeze() {
            return size == 1 ? new int[]{first} : Arrays.copyOf(values, size);
        }
    }

    private static final class PostingRows extends AbstractList<Map<String, Object>> {
        private final List<Map<String, Object>> rows;
        private final int[] positions;

        private PostingRows(List<Map<String, Object>> rows, int[] positions) {
            this.rows = rows;
            this.positions = positions;
        }

        @Override
        public Map<String, Object> get(int index) {
            return rows.get(positions[index]);
        }

        @Override
        public int size() {
            return positions.length;
        }
    }
}
