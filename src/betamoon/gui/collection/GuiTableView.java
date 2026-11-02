package betamoon.gui.collection;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiGeometry.Constraints;
import org.lwjgl.input.Keyboard;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.widget.GuiStyledView;
import betamoon.gui.widget.GuiScrollView;
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
    private GuiTableColumns displayedColumns;
    private boolean xControlled;
    private boolean horizontalRetained;
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
    private int headingWidth = -1;
    private final List<String> headingLabels = new ArrayList<>();

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

            @Override
            protected void arrangeChildren(GuiContext context) {
                reflowHeader(context, getWidth());
                super.arrangeChildren(context);
            }
        };
        header.configure(Collections.singletonMap("clip", true));
        collectionContent().add(header);
        viewport().setPolicy(GuiScrollView.Policy.BOTH);
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
        boolean nextXControlled = controlled.test("scrollX");
        horizontalRetained |= xControlled && !nextXControlled;
        int x = !published || nextXControlled ? ((Number) values.get("scrollX")).intValue() : viewport().getScrollX();
        xControlled = nextXControlled;
        horizontalRetained |= !xControlled && x != 0;
        viewport().publishScrollPosition(x, viewport().getScrollY());
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
        return completeHorizontal(withSort(super.captureState(), sortColumn, sortDirection));
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
        Map<String, Object> next = withSort(super.projectState(values, controlled, withoutSort(current)), key,
                direction);
        boolean retain = horizontalRetained || current.containsKey("scrollX")
                || xControlled && !controlled.test("scrollX")
                || !published && ((Number) values.get("scrollX")).intValue() != 0;
        int x = ((Number) current.getOrDefault("scrollX", viewport().getScrollX())).intValue();
        return horizontal(next, x, retain, controlled.test("scrollX"));
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
        int restoredX = scrollX(state);
        Runnable restore = super.prepareStateRestore(withoutSort(state));
        String restoredKey = key;
        String restoredDirection = direction;
        return () -> {
            restore.run();
            if (!xControlled) {
                viewport().publishScrollPosition(restoredX, viewport().getScrollY());
                horizontalRetained = state.containsKey("scrollX");
            }
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
                Map<String, Object> sorted = state.containsKey("sortColumn")
                        ? state
                        : withSort(state, sortColumn, sortDirection);
                return sorted.containsKey("scrollX") ? sorted : completeHorizontal(sorted);
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
        Map<String, Object> prospective = completeHorizontal(withSort(super.captureState(), localKey, localDirection));
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
        if (displayedColumns != columns) {
            displayedColumns = columns;
            requestLayout();
        }
        int[] widths = columns.widths(collection().getWidth());
        context.cancelGesturesIn(header);
        headingLabels.clear();
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
            String label = column.label + marker;
            headingLabels.add(label);
            button.configure(cellValues(label, widths[i], headerHeight()));
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
        headingWidth = collection().getWidth();
        headerStamp = stamp;
    }

    private void reflowHeader(GuiContext context, int width) {
        if (displayedColumns == null || headingWidth == width) {
            return;
        }
        context.cancelGesturesIn(header);
        int[] widths = displayedColumns.widths(width);
        int i = 0;
        for (GuiStyledView button : headings.values()) {
            button.configure(cellValues(headingLabels.get(i), widths[i], headerHeight()));
            button.measure(context, Constraints.upTo(width, headerHeight()));
            i++;
        }
        headingWidth = width;
    }

    public GuiStyledView header() {
        return header;
    }

    @Override
    protected int contentWidth(int viewportWidth) {
        GuiTableColumns visible = displayedColumns == null ? columns : displayedColumns;
        return visible == null ? viewportWidth : visible.contentWidth(viewportWidth);
    }

    @Override
    protected int fixedHeaderHeight(int viewportHeight) {
        return Math.min(viewportHeight, headerHeight() + ((Number) values.getOrDefault("gap", 0)).intValue());
    }

    @Override
    protected void arrangeCollection(GuiContext context, Rect area) {
        int height = Math.min(area.getHeight(), headerHeight());
        int gap = Math.min(Math.max(0, area.getHeight() - height), ((Number) values.getOrDefault("gap", 0)).intValue());
        int width = contentWidth(area.getWidth());
        int left = area.getLeft() - viewport().getScrollX();
        header.arrange(context, Rect.fromPositionAndSize(left, area.getTop(), width, height));
        collection().arrange(context, Rect.fromPositionAndSize(left, area.getTop() + height + gap, width,
                Math.max(0, area.getHeight() - height - gap)));
    }

    @Override
    protected void proposeScroll(int x, int y) {
        if (x != viewport().getScrollX()) {
            Map<String, Object> next = horizontal(captureState(), x, true, xControlled);
            Map<String, Object> event = Collections.singletonMap("scrollX", x);
            try (GuiViewStateAdmission.Proposal proposal = admission == null
                    ? GuiViewStateAdmission.UNRESTRICTED
                    : admission.begin(next)) {
                if (events.propose("scroll", event)) {
                    if (!xControlled) {
                        viewport().publishScrollPosition(x, viewport().getScrollY());
                        horizontalRetained = true;
                    }
                    proposal.commit(captureState());
                }
            } catch (GuiViewStateAdmission.Refusal refused) {
                // The accepted horizontal position remains usable.
            }
        }
        super.proposeScroll(viewport().getScrollX(), y);
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED) {
            int delta = 0;
            switch (event.getKeyCode()) {
                case Keyboard.KEY_LEFT:
                    delta = -16;
                    break;
                case Keyboard.KEY_RIGHT:
                    delta = 16;
                    break;
                case Keyboard.KEY_PRIOR:
                    if (event.isShiftDown()) {
                        delta = -viewport().getWidth();
                    }
                    break;
                case Keyboard.KEY_NEXT:
                    if (event.isShiftDown()) {
                        delta = viewport().getWidth();
                    }
                    break;
                case Keyboard.KEY_HOME:
                    if (event.isShiftDown()) {
                        delta = -16384;
                    }
                    break;
                case Keyboard.KEY_END:
                    if (event.isShiftDown()) {
                        delta = 16384;
                    }
                    break;
                default:
                    break;
            }
            if (delta != 0) {
                int before = viewport().getScrollX();
                viewport().setScrollPosition(Math.max(0, Math.min(16384, before + delta)), viewport().getScrollY());
                return before != viewport().getScrollX();
            }
        }
        return super.onInput(context, event);
    }

    private Map<String, Object> completeHorizontal(Map<String, Object> state) {
        return horizontal(state, viewport().getScrollX(), horizontalRetained, xControlled);
    }

    private static Map<String, Object> horizontal(Map<String, Object> state, int x, boolean retain,
            boolean controlled) {
        Map<String, Object> result = new LinkedHashMap<>(state);
        result.remove("scrollX");
        if (retain && !controlled) {
            result.put("scrollX", x);
        }
        return Collections.unmodifiableMap(result);
    }

    private static int scrollX(Map<String, Object> state) {
        Object value = state.getOrDefault("scrollX", 0);
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())
                || ((Number) value).doubleValue() != Math.rint(((Number) value).doubleValue())
                || ((Number) value).doubleValue() < 0 || ((Number) value).doubleValue() > 16384) {
            throw new IllegalArgumentException("Saved table horizontal offset requires an integer 0..16384");
        }
        return ((Number) value).intValue();
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
        GuiTableColumns rowColumns = columns;
        List<GuiStyledView> children = new ArrayList<>();
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
                GuiStyledView cell = children.get(i);
                cell.configure(cellValues(text, widths[i], theme.resolve("default", 0).font().lineHeight(context.gui)));
                cell.measure(context.gui, Constraints.upTo(width, height));
            }
            previous[0] = width;
        };
        GuiStyledView cells = new GuiStyledView("row", theme, GuiDefaultTemplate.INSTANCE, (event, value) -> false) {
            @Override
            protected void arrangeChildren(GuiContext gui) {
                update.accept(getWidth(), context.height);
                super.arrangeChildren(gui);
            }
        };
        cells.configure(Collections.singletonMap("align", "center"));
        for (GuiTableColumns.Column column : rowColumns.columns) {
            GuiStyledView cell = new GuiStyledView("text", theme, GuiDefaultTemplate.INSTANCE, (event, value) -> false);
            children.add(cell);
            cells.add(cell);
        }
        button.add(cells);
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

    @Override
    protected Object templateValue(String name, boolean targets) {
        return name.equals("scrollX") ? viewport().getScrollX() : super.templateValue(name, targets);
    }

    private static Map<String, Object> withoutSort(Map<String, Object> state) {
        Map<String, Object> result = new LinkedHashMap<>(state);
        result.remove("sortColumn");
        result.remove("sortDirection");
        result.remove("scrollX");
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
