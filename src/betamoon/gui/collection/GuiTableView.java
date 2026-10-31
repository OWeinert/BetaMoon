package betamoon.gui.collection;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.widget.GuiStyledView;
import betamoon.gui.widget.GuiBuiltInViews;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Table headers/cells share styled controls and an ordered projection of the
 * full keyed model.
 */
public final class GuiTableView extends GuiCollectionView {
    private final GuiStyleSheet theme;
    private final Events events;
    private final boolean defaultRows;
    private final GuiStyledView header;
    private final Map<String, GuiStyledView> headings = new LinkedHashMap<>();
    private GuiTableColumns columns;
    private Object columnMetadata;
    private Map<String, Object> values = Collections.emptyMap();
    private Predicate<String> controlled = name -> false;
    private String sortColumn = "";
    private String sortDirection = "none";
    private boolean published;
    private GuiViewStateAdmission admission;
    private List<Object> projectionStamp;
    private List<Object> headerStamp;
    private long headerIntent;

    public GuiTableView(GuiRowBudget budget, GuiRowFactory factory, GuiStyleSheet theme, GuiVisualTemplate template,
            Events events, Consumer<Throwable> failures) {
        super("table", budget, factory, theme, template, events, failures);
        this.theme = theme;
        this.events = events;
        defaultRows = factory == null;
        header = new GuiStyledView("row", theme, GuiDefaultTemplate.INSTANCE, (event, value) -> false) {
            @Override
            protected void prepareStructureElement(GuiContext context) {
                prepareHeader(context);
            }
        };
        header.configure(Collections.singletonMap("clip", true));
        add(header);
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        new GuiTableColumns(values.get("columns")).validate(((GuiCollectionValues) values.get("records")).model(),
                defaultRows, (String) values.get("sortColumn"), (String) values.get("sortDirection"));
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        publishTable(node.values(), node::controlled);
    }

    @Override
    public GuiTableView configure(Map<String, Object> values) {
        super.configure(values);
        published = false;
        publishTable(GuiBuiltInViews.properties("table").withDefaults(values).baseline(), name -> false);
        return this;
    }

    private void publishTable(Map<String, Object> values, Predicate<String> controlled) {
        boolean changed = !Objects.equals(columnMetadata, values.get("columns"));
        columnMetadata = values.get("columns");
        this.values = values;
        this.controlled = controlled;
        columns = new GuiTableColumns(values.get("columns"));
        if (!published || controlled.test("sortColumn")) {
            sortColumn = (String) values.get("sortColumn");
        }
        if (!published || controlled.test("sortDirection")) {
            sortDirection = (String) values.get("sortDirection");
        }
        if (sortColumn.isEmpty() || columns.column(sortColumn) == null) {
            sortColumn = "";
            sortDirection = "none";
        }
        published = true;
        if (changed) {
            collection().refreshRows();
        }
        project();
        requestLayout();
    }

    private void project() {
        List<Object> stamp = Arrays.asList(values.get("records"), values.get("columns"), values.get("filter"),
                sortColumn, sortDirection);
        if (stamp.equals(projectionStamp)) {
            return;
        }
        boolean settingsChanged = projectionStamp != null && !projectionStamp.subList(1, 5).equals(stamp.subList(1, 5));
        GuiCollectionModel.Snapshot model = ((GuiCollectionValues) values.get("records")).model();
        collection().setProjection(columns.project(model, sortColumn, sortDirection, (String) values.get("filter")));
        if (settingsChanged) {
            collection().scrollToStart();
        }
        projectionStamp = Collections.unmodifiableList(stamp);
        headerStamp = null;
        headerIntent = collection().intentRevision();
    }

    @Override
    public Map<String, Object> captureState() {
        return withSort(super.captureState(), sortColumn, sortDirection);
    }

    @Override
    public Map<String, Object> projectState(Map<String, Object> values, Predicate<String> controlled,
            Map<String, Object> current) {
        String key = !published || controlled.test("sortColumn")
                ? (String) values.get("sortColumn")
                : (String) current.getOrDefault("sortColumn", sortColumn);
        String direction = !published || controlled.test("sortDirection")
                ? (String) values.get("sortDirection")
                : (String) current.getOrDefault("sortDirection", sortDirection);
        if (key.isEmpty() || new GuiTableColumns(values.get("columns")).column(key) == null) {
            key = "";
            direction = "none";
        }
        return withSort(super.projectState(values, controlled, withoutSort(current)), key, direction);
    }

    @Override
    public Runnable prepareStateRestore(Map<String, Object> state) {
        String key = (String) state.getOrDefault("sortColumn", "");
        String direction = (String) state.getOrDefault("sortDirection", "none");
        if (!key.isEmpty() && columns.column(key) == null) {
            key = "";
            direction = "none";
        }
        columns.validate(((GuiCollectionValues) values.get("records")).model(), defaultRows, key, direction);
        Runnable restore = super.prepareStateRestore(withoutSort(state));
        String restoredKey = key;
        String restoredDirection = direction;
        return () -> {
            restore.run();
            if (!controlled.test("sortColumn")) {
                sortColumn = restoredKey;
            }
            if (!controlled.test("sortDirection")) {
                sortDirection = restoredDirection;
            }
            if (sortColumn.isEmpty()) {
                sortDirection = "none";
            }
            projectionStamp = null;
            project();
        };
    }

    @Override
    public void admitStateWith(GuiViewStateAdmission admission) {
        super.admitStateWith(new GuiViewStateAdmission() {
            public Proposal begin(Map<String, Object> prospective) {
                Proposal proposal = admission.begin(complete(prospective));
                return new Proposal() {
                    public void commit(Map<String, Object> accepted) {
                        proposal.commit(complete(accepted));
                    }

                    public void close() {
                        proposal.close();
                    }
                };
            }

            private Map<String, Object> complete(Map<String, Object> state) {
                return state.containsKey("sortColumn") ? state : withSort(state, sortColumn, sortDirection);
            }

            public Object revision() {
                return admission.revision();
            }

            public boolean pending() {
                return admission.pending();
            }
        });
        this.admission = admission;
    }

    private boolean sort(String key) {
        if (columns.column(key) == null) {
            return false;
        }
        String direction = !sortColumn.equals(key) || sortDirection.equals("none")
                ? "ascending"
                : sortDirection.equals("ascending") ? "descending" : "none";
        String localKey = controlled.test("sortColumn") ? sortColumn : key;
        String localDirection = localKey.isEmpty()
                ? "none"
                : controlled.test("sortDirection") ? sortDirection : direction;
        Map<String, Object> prospective = withSort(super.captureState(), localKey, localDirection);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("column", key);
        event.put("direction", direction);
        try (GuiViewStateAdmission.Proposal proposal = admission == null
                ? GuiViewStateAdmission.UNRESTRICTED
                : admission.begin(prospective)) {
            if (!events.propose("sort", Collections.unmodifiableMap(event))) {
                return false;
            }
            if (!controlled.test("sortColumn")) {
                sortColumn = localKey;
            }
            if (!controlled.test("sortDirection")) {
                sortDirection = localDirection;
            }
            project();
            proposal.commit(captureState());
            return true;
        } catch (GuiViewStateAdmission.Refusal refused) {
            return false;
        }
    }

    private void prepareHeader(GuiContext context) {
        if (columns == null || collection().acceptedIntentRevision() < headerIntent) {
            return;
        }
        List<Object> stamp = Arrays.asList(values.get("columns"), collection().getWidth(), sortColumn, sortDirection,
                values.get("headerHeight"));
        if (stamp.equals(headerStamp)) {
            return;
        }
        int[] widths = columns.widths(collection().getWidth());
        context.cancelGesturesIn(header);
        List<GuiStyledView> children = new ArrayList<>();
        Map<String, GuiStyledView> next = new LinkedHashMap<>();
        for (int i = 0; i < columns.columns.size(); i++) {
            GuiTableColumns.Column column = columns.columns.get(i);
            GuiStyledView button = headings.get(column.key);
            if (button == null) {
                button = new GuiStyledView("button", theme, GuiDefaultTemplate.INSTANCE,
                        (event, value) -> sort(column.key));
            }
            String marker = !sortColumn.equals(column.key) || sortDirection.equals("none")
                    ? ""
                    : sortDirection.equals("ascending") ? " ^" : " v";
            button.configure(cellValues(column.label + marker, widths[i], headerHeight()));
            children.add(button);
            next.put(column.key, button);
        }
        for (Map.Entry<String, GuiStyledView> old : headings.entrySet()) {
            if (!next.containsKey(old.getKey())) {
                old.getValue().close();
            }
        }
        headings.clear();
        headings.putAll(next);
        header.reconcileChildren(children);
        headerStamp = stamp;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        super.arrangeChildren(context);
        Rect area = collection().getBounds();
        int height = Math.min(area.getHeight(), headerHeight());
        int gap = ((Number) values.getOrDefault("gap", 0)).intValue();
        header.arrange(context, Rect.fromPositionAndSize(area.getLeft(), area.getTop(), area.getWidth(), height));
        collection().arrange(context, Rect.fromPositionAndSize(area.getLeft(), area.getTop() + height + gap,
                area.getWidth(), Math.max(0, area.getHeight() - height - gap)));
    }

    private int headerHeight() {
        return ((Number) values.getOrDefault("headerHeight", 20)).intValue();
    }

    @Override
    protected GuiRowFactory.Prepared prepareDefault(GuiRowFactory.Context context, GuiStyleSheet theme,
            GuiVisualTemplate template) {
        GuiStyledView button = new GuiStyledView("button", theme, GuiDefaultTemplate.INSTANCE,
                (event, value) -> activateDefault(context));
        button.configure(Collections.singletonMap("height", context.height));
        GuiStyledView cells = new GuiStyledView("row", theme, GuiDefaultTemplate.INSTANCE, (event, value) -> false);
        GuiTableColumns rowColumns = columns;
        cells.configure(Collections.singletonMap("align", "center"));
        List<GuiStyledView> children = new ArrayList<>();
        for (GuiTableColumns.Column column : rowColumns.columns) {
            GuiStyledView cell = new GuiStyledView("text", theme, GuiDefaultTemplate.INSTANCE, (event, value) -> false);
            children.add(cell);
            cells.add(cell);
        }
        button.add(cells);
        int[] previous = {-1};
        BiConsumer<Integer, Integer> update = (width, height) -> {
            if (previous[0] == width) {
                return;
            }
            int[] widths = rowColumns.widths(width);
            for (int i = 0; i < children.size(); i++) {
                String text = GuiTableColumns.cell(rowColumns.columns.get(i).value(context.record));
                if (i == 0) {
                    text = (context.selected ? "* " : "  ") + text;
                }
                children.get(i).configure(
                        cellValues(text, widths[i], theme.resolve("default", 0).font().lineHeight(context.gui)));
            }
            previous[0] = width;
        };
        update.accept(context.width, context.height);
        return preparedRow(context, button, children.size() + 3, update);
    }

    private static Map<String, Object> cellValues(String text, int width, int height) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("text", text);
        values.put("width", width);
        values.put("height", height);
        return values;
    }

    private static Map<String, Object> withoutSort(Map<String, Object> state) {
        Map<String, Object> result = new LinkedHashMap<>(state);
        result.remove("sortColumn");
        result.remove("sortDirection");
        if (result.size() == 1 && result.containsKey("schema")) {
            result.clear();
        }
        return result;
    }

    private static Map<String, Object> withSort(Map<String, Object> state, String column, String direction) {
        Map<String, Object> result = new LinkedHashMap<>(state);
        if (!column.isEmpty() || !direction.equals("none")) {
            result.put("sortColumn", column);
            result.put("sortDirection", direction);
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public void close() {
        try {
            for (GuiStyledView button : headings.values()) {
                button.close();
            }
            headings.clear();
            header.close();
        } finally {
            super.close();
        }
    }
}
