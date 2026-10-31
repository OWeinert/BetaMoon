package betamoon.gui.collection;

import betamoon.gui.action.GuiActionOrigin;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewAdapter;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.widget.GuiStyledView;
import betamoon.gui.widget.GuiBuiltInViews;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.lwjgl.input.Keyboard;

/**
 * Keyed list/tree behavior composed from the shared styled and virtual row
 * elements.
 */
public final class GuiCollectionView extends GuiStyledView {
    private final GuiVirtualCollection collection;
    private final String kind;
    private final Events events;
    private final boolean defaultRows;
    private GuiViewStateAdmission admission;
    private Map<String, Object> properties = Collections.emptyMap();
    private Predicate<String> controlled = name -> false;
    private boolean published;

    public GuiCollectionView(String kind, GuiRowBudget budget, GuiRowFactory factory, GuiStyleSheet theme,
            GuiVisualTemplate template, Events events, Consumer<Throwable> failures) {
        super(kind, theme, template, events);
        this.kind = kind;
        this.events = events;
        defaultRows = factory == null;
        collection = new GuiVirtualCollection(budget,
                factory == null ? context -> prepareDefault(context, theme, GuiDefaultTemplate.INSTANCE) : factory,
                failures);
        add(collection);
        setFocusable(true);
    }

    public GuiVirtualCollection collection() {
        return collection;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        GuiCollectionModel.Snapshot records = ((GuiCollectionValues) values.get("records")).model();
        if ("list".equals(kind)) {
            for (GuiCollectionModel.Record record : records.records) {
                if (record.parent != null) {
                    throw new IllegalArgumentException("List records cannot have parents; use a tree");
                }
            }
        }
        key((String) values.get("selectedKey"));
        keys(values.getOrDefault("expandedKeys", Collections.emptyList()));
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        publishCollection(node.values(), node::controlled);
    }

    @Override
    public GuiCollectionView configure(Map<String, Object> values) {
        Map<String, Object> normalized = GuiBuiltInViews.properties(kind).withDefaults(values).baseline();
        validateValues(normalized);
        super.configure(normalized);
        published = false;
        publishCollection(normalized, name -> false);
        return this;
    }

    private void publishCollection(Map<String, Object> values, Predicate<String> controlled) {
        this.properties = values;
        this.controlled = controlled;
        collection.setModel(((GuiCollectionValues) values.get("records")).model());
        collection.setMetrics(((Number) values.get("rowHeight")).intValue(),
                ((Number) values.get("overscan")).intValue());
        if (!published || controlled.test("selectedKey")) {
            collection.setSelection(key((String) values.get("selectedKey")));
        }
        if ("tree".equals(kind) && (!published || controlled.test("expandedKeys"))) {
            collection.setExpansion(keys(values.get("expandedKeys")));
        }
        published = true;
    }

    @Override
    public void sample() {
        collection.sampleRows();
    }

    @Override
    public Map<String, Object> captureState() {
        return collection.captureState();
    }

    @Override
    public Map<String, Object> projectState(Map<String, Object> values, Predicate<String> controlled,
            Map<String, Object> current) {
        GuiCollectionModel.Snapshot records = ((GuiCollectionValues) values.get("records")).model();
        Map<String, Object> next = new LinkedHashMap<>(current);
        String selection = !published || controlled.test("selectedKey")
                ? key((String) values.get("selectedKey"))
                : (String) current.get("selection");
        GuiCollectionModel.Record selected = records.keys.get(selection);
        next.remove("selection");
        if (selected != null && selected.enabled) {
            next.put("selection", selection);
        }
        Set<String> expanded = !published || controlled.test("expandedKeys")
                ? keys(values.getOrDefault("expandedKeys", Collections.emptyList()))
                : keys(current.getOrDefault("expanded", Collections.emptyList()));
        expanded.removeIf(value -> !records.keys.containsKey(value) || !records.branch(value));
        next.remove("expanded");
        if (!expanded.isEmpty()) {
            next.put("expanded", Collections.unmodifiableList(new ArrayList<>(expanded)));
        }
        if (current.containsKey("rows")) {
            Map<String, Object> rows = new LinkedHashMap<>(record(current.get("rows")));
            rows.keySet().retainAll(records.keys.keySet());
            next.remove("rows");
            if (!rows.isEmpty()) {
                next.put("rows", Collections.unmodifiableMap(rows));
            }
        }
        if (next.size() == 1 && next.containsKey("schema")) {
            return Collections.emptyMap();
        }
        if (!next.isEmpty()) {
            next.put("schema", 1);
        }
        return Collections.unmodifiableMap(next);
    }

    @Override
    public void admitStateWith(GuiViewStateAdmission admission) {
        if (this.admission != null) {
            throw new IllegalStateException("Install collection admission once");
        }
        this.admission = Objects.requireNonNull(admission, "collection admission");
        collection.admitStateWith(admission);
    }

    @Override
    public Runnable prepareStateRestore(Map<String, Object> values) {
        Runnable restore = collection.prepareStateRestore(values);
        return () -> {
            restore.run();
            restoreControlled();
        };
    }

    private void restoreControlled() {
        if (published && controlled.test("selectedKey")) {
            collection.setSelection(key((String) properties.get("selectedKey")));
        }
        if (published && "tree".equals(kind) && controlled.test("expandedKeys")) {
            collection.setExpansion(keys(properties.get("expandedKeys")));
        }
    }

    @Override
    public void retainFrom(GuiViewAdapter previous) {
        if (previous instanceof GuiCollectionView && kind.equals(((GuiCollectionView) previous).kind)) {
            prepareStateRestore(previous.captureState()).run();
        }
    }

    private boolean select(String key, boolean reveal) {
        if (Objects.equals(key, collection.selection())) {
            return true;
        }
        Map<String, Object> next = new LinkedHashMap<>(captureState());
        boolean controlled = published && this.controlled.test("selectedKey");
        if (!controlled) {
            next.put("schema", 1);
            next.put("selection", key);
        }
        try (GuiViewStateAdmission.Proposal proposal = begin(next)) {
            if (!collection.recordEvent(key, () -> events.propose("change", key))) {
                return false;
            }
            if (!controlled) {
                collection.setSelection(key);
                if (reveal) {
                    collection.reveal(key);
                }
            }
            proposal.commit(captureState());
            return true;
        } catch (GuiViewStateAdmission.Refusal refusal) {
            return false;
        }
    }

    private boolean expand(String key, boolean expanded) {
        Set<String> keys = new LinkedHashSet<>(collection.expansion());
        if (expanded) {
            keys.add(key);
        } else {
            keys.remove(key);
        }
        boolean controlled = published && this.controlled.test("expandedKeys");
        Map<String, Object> next = new LinkedHashMap<>(captureState());
        if (!controlled) {
            next.put("schema", 1);
            next.put("expanded", Collections.unmodifiableList(new ArrayList<>(keys)));
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("key", key);
        event.put("expanded", expanded);
        try (GuiViewStateAdmission.Proposal proposal = begin(next)) {
            if (!collection.recordEvent(key, () -> events.propose("expand", Collections.unmodifiableMap(event)))) {
                return false;
            }
            if (!controlled) {
                collection.setExpansion(keys);
            }
            proposal.commit(captureState());
            return true;
        } catch (GuiViewStateAdmission.Refusal refusal) {
            return false;
        }
    }

    private GuiViewStateAdmission.Proposal begin(Map<String, Object> prospective) {
        return admission == null ? GuiViewStateAdmission.UNRESTRICTED : admission.begin(prospective);
    }

    private GuiRowFactory.Prepared prepareDefault(GuiRowFactory.Context context, GuiStyleSheet theme,
            GuiVisualTemplate template) {
        StringBuilder label = new StringBuilder(context.selected ? "* " : "  ");
        for (int depth = 0; depth < context.depth; depth++) {
            label.append("  ");
        }
        if ("tree".equals(kind)) {
            label.append(((GuiCollectionValues) properties.get("records")).model().branch(context.record.key)
                    ? context.expanded ? "v " : "> "
                    : "  ");
        }
        label.append(context.record.label);
        GuiStyledView view = new GuiStyledView("button", theme, template, (event, value) -> {
            try (GuiActionOrigin.Frame origins = GuiActionOrigin.enter(context.actionOrigins())) {
                if (!select(context.record.key, false)) {
                    return false;
                }
                if ("tree".equals(kind) && collection.model().branch(context.record.key)
                        && !expand(context.record.key, !collection.expansion().contains(context.record.key))) {
                    return false;
                }
                return events.propose("activate", context.record.key);
            }
        });
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("text", label.toString());
        values.put("height", context.height);
        values.put("fill", true);
        view.configure(values);
        GuiRowFactory.Row row = new GuiRowFactory.Row() {
            public GuiElement element() {
                return view;
            }

            public Map<String, Object> state() {
                return Collections.emptyMap();
            }

            public GuiElement compatibleFocus(GuiRowFactory.Row previous, GuiElement focused) {
                return view;
            }

            public void close() {
                view.close();
            }
        };
        GuiRowBudget.Reservation reservation = context.budget.reserve(2);
        return new GuiRowFactory.Prepared() {
            private boolean committed;
            public GuiRowFactory.Row candidate() {
                return row;
            }

            public GuiRowBudget.Reservation reservation() {
                return reservation;
            }

            public GuiRowFactory.Row commit() {
                committed = true;
                return row;
            }

            public void close() {
                if (!committed) {
                    row.close();
                    reservation.close();
                }
            }
        };
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            requestFocus(context);
            return true;
        }
        if (event.getType() != GuiInputEvent.Type.KEY_TYPED || !isFocused() && !defaultRows) {
            return false;
        }
        List<GuiCollectionModel.Row> rows = collection.visibleRows();
        int index = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).record.key.equals(collection.selection())) {
                index = i;
                break;
            }
        }
        int direction = 1;
        int target = index;
        switch (event.getKeyCode()) {
            case Keyboard.KEY_UP:
                target = index < 0 ? rows.size() - 1 : index - 1;
                direction = -1;
                break;
            case Keyboard.KEY_DOWN:
                target = index + 1;
                break;
            case Keyboard.KEY_HOME:
                target = 0;
                break;
            case Keyboard.KEY_END:
                target = rows.size() - 1;
                direction = -1;
                break;
            case Keyboard.KEY_PRIOR:
                target = Math.max(0, index - pageSize());
                direction = -1;
                break;
            case Keyboard.KEY_NEXT:
                target = Math.min(rows.size() - 1, index + pageSize());
                break;
            case Keyboard.KEY_LEFT:
            case Keyboard.KEY_RIGHT:
                if (!"tree".equals(kind) || index < 0) {
                    return false;
                }
                GuiCollectionModel.Record selected = rows.get(index).record;
                if (event.getKeyCode() == Keyboard.KEY_RIGHT && collection.model().branch(selected.key)) {
                    expand(selected.key, true);
                } else if (event.getKeyCode() == Keyboard.KEY_LEFT && collection.expansion().contains(selected.key)) {
                    expand(selected.key, false);
                } else if (event.getKeyCode() == Keyboard.KEY_LEFT && selected.parent != null) {
                    if (select(selected.parent, true)) {
                        collection.focusRow(selected.parent);
                    }
                }
                return true;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_SPACE:
                if (index < 0) {
                    return false;
                }
                String key = rows.get(index).record.key;
                return collection.recordEvent(key, () -> events.propose("activate", key));
            default:
                return false;
        }
        while (target >= 0 && target < rows.size() && !rows.get(target).record.enabled) {
            target += direction;
        }
        if (target >= 0 && target < rows.size() && select(rows.get(target).record.key, true)) {
            collection.focusRow(rows.get(target).record.key);
        }
        return true;
    }

    private int pageSize() {
        return Math.max(1, collection.getHeight() / ((Number) properties.get("rowHeight")).intValue());
    }

    @Override
    public void close() {
        try {
            collection.close();
        } finally {
            super.close();
        }
    }

    private static String key(String key) {
        if (key.length() > 255) {
            throw new IllegalArgumentException("Collection keys must contain at most 255 UTF-16 units");
        }
        return key.isEmpty() ? null : key;
    }

    private static Set<String> keys(Object values) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : (List<?>) values) {
            String key = key((String) value);
            if (key == null || !result.add(key)) {
                throw new IllegalArgumentException("Expanded keys must be nonempty and unique");
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> record(Object value) {
        return (Map<String, Object>) value;
    }
}
