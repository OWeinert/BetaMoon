package betamoon.gui.widget;

import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.collection.GuiTableColumns;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.gui.presentation.GuiPropertyDescriptor.Stage;
import betamoon.gui.presentation.GuiViewAdapter;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Shared typed catalog and Java view factory; the legacy container catalog
 * remains separate.
 */
public final class GuiBuiltInViews implements GuiPresentationTree.Factory {
    private static final List<String> TYPES = Collections.unmodifiableList(Arrays.asList("text", "image", "panel",
            "row", "column", "grid", "scroll", "list", "tree", "table", "overlay", "spacer", "divider", "button",
            "checkbox", "choice", "radio", "slider", "stepper", "textbox", "textarea", "numberbox", "dropdown",
            "contextmenu", "tabs", "progress", "dialog", "tooltip"));
    private final GuiStyleSheet theme;
    private final Function<GuiNodeDefinition, GuiVisualTemplate> templates;
    private final Function<GuiNodeDefinition, GuiStyledView.Events> events;

    public GuiBuiltInViews(GuiStyleSheet theme, Function<GuiNodeDefinition, GuiStyledView.Events> events) {
        this(theme, node -> GuiDefaultTemplate.INSTANCE, events);
    }

    public GuiBuiltInViews(GuiStyleSheet theme, Function<GuiNodeDefinition, GuiVisualTemplate> templates,
            Function<GuiNodeDefinition, GuiStyledView.Events> events) {
        this.theme = theme;
        this.templates = templates;
        this.events = events;
    }

    @Override
    public GuiViewAdapter create(GuiNodeDefinition definition) {
        requireType(definition.type);
        if (collection(definition.type)) {
            throw new IllegalArgumentException("Collection views require a host row budget and GuiCollectionView");
        }
        return createControl(definition.type, theme, templates.apply(definition), events.apply(definition));
    }

    /** Java and authored component plans instantiate the same retained controls. */
    public static GuiStyledView createControl(String type, GuiStyleSheet theme, GuiVisualTemplate template,
            GuiStyledView.Events events) {
        requireType(type);
        if (collection(type)) {
            throw new IllegalArgumentException("Collection views require a host row budget and GuiCollectionView");
        }
        switch (type) {
            case "scroll":
                return new GuiScrollControl(theme, template, events);
            case "radio":
                return new GuiRadioView(theme, template, events);
            case "tabs":
                return new GuiRadioView(type, theme, template, events);
            case "dropdown":
            case "contextmenu":
                return new GuiSelectView(type, theme, template, events);
            case "textarea":
                return new GuiTextAreaView(theme, template, events);
            case "numberbox":
                return new GuiNumberBoxView(theme, template, events);
            default:
                return new GuiStyledView(type, theme, template, events);
        }
    }

    public static GuiProperties properties(String type) {
        requireType(type);
        List<GuiPropertyDescriptor> properties = new ArrayList<>();
        for (String name : Arrays.asList("width", "height", "minWidth", "minHeight")) {
            properties.add(GuiPropertyDescriptor.number(name, 0, 0, 16384, Stage.MEASURE, Stage.ARRANGE));
        }
        for (String name : Arrays.asList("x", "y", "translateX", "translateY")) {
            properties.add(GuiPropertyDescriptor.number(name, 0, -65536, 65536, Stage.ARRANGE, Stage.HIT));
        }
        for (String name : Arrays.asList("scaleX", "scaleY")) {
            properties.add(GuiPropertyDescriptor.number(name, 1, 0.001, 1000, Stage.HIT));
        }
        for (String name : Arrays.asList("anchorX", "anchorY")) {
            properties.add(GuiPropertyDescriptor.number(name, 0.5, 0, 1, Stage.ARRANGE));
        }
        properties.add(GuiPropertyDescriptor.number("opacity", 1, 0, 1, Stage.PAINT, Stage.HIT));
        properties.add(GuiPropertyDescriptor.number("grow", 0, 0, 10000, Stage.ARRANGE));
        properties.add(GuiPropertyDescriptor.number("padding", 0, 0, 1024, Stage.MEASURE));
        properties.add(GuiPropertyDescriptor.number("gap", 0, 0, 1024, Stage.MEASURE));
        properties.add(GuiPropertyDescriptor.value("fill", Kind.BOOLEAN, false, Stage.ARRANGE));
        properties.add(GuiPropertyDescriptor.value("enabled", Kind.BOOLEAN, true, Stage.HIT));
        properties.add(GuiPropertyDescriptor.value("visible", Kind.BOOLEAN, true, Stage.HIT, Stage.MEASURE));
        properties.add(GuiPropertyDescriptor.value("visibility", Kind.STRING, "visible", Stage.HIT, Stage.MEASURE));
        properties.add(GuiPropertyDescriptor.value("variant", Kind.STRING, "default", Stage.MEASURE, Stage.PAINT));
        properties.add(GuiPropertyDescriptor.value("styleRef", Kind.STRING, "", Stage.MEASURE, Stage.PAINT));
        properties.add(GuiPropertyDescriptor.value("align", Kind.STRING, "stretch", Stage.ARRANGE));
        properties.add(GuiPropertyDescriptor.value("justify", Kind.STRING, "start", Stage.ARRANGE));
        properties.add(GuiPropertyDescriptor.value("clip", Kind.BOOLEAN, false, Stage.HIT));
        if ("scroll".equals(type)) {
            properties.add(GuiPropertyDescriptor.value("direction", Kind.STRING, "vertical", Stage.MEASURE));
            properties.add(GuiPropertyDescriptor.value("scrollbars", Kind.BOOLEAN, true, Stage.PAINT, Stage.HIT));
            for (String name : Arrays.asList("contentWidth", "contentHeight", "scrollX", "scrollY")) {
                properties.add(new GuiPropertyDescriptor(name, Kind.INTEGER, 0, 0, 16384, false, false,
                        Collections.singleton(Stage.MEASURE), Collections.emptySet()));
            }
        }
        if (collection(type)) {
            properties.add(GuiPropertyDescriptor.value("scrollbars", Kind.BOOLEAN, true, Stage.PAINT, Stage.HIT));
            properties.add(
                    GuiPropertyDescriptor.value("records", Kind.COLLECTION, Collections.emptyList(), Stage.SEMANTICS));
            properties.add(GuiPropertyDescriptor.value("selectedKey", Kind.STRING, "", Stage.SEMANTICS));
            properties.add(new GuiPropertyDescriptor("rowHeight", Kind.INTEGER, 20, 1, 1024, false, false,
                    Collections.singleton(Stage.MEASURE), Collections.emptySet()));
            properties.add(new GuiPropertyDescriptor("overscan", Kind.INTEGER, 1, 0, 8, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
            if ("tree".equals(type)) {
                properties.add(GuiPropertyDescriptor.value("expandedKeys", Kind.STRING_LIST, Collections.emptyList(),
                        Stage.SEMANTICS));
            }
        }
        if ("table".equals(type)) {
            properties.add(new GuiPropertyDescriptor("scrollX", Kind.INTEGER, 0, 0, 16384, false, false,
                    Collections.singleton(Stage.ARRANGE), Collections.emptySet()));
            properties.add(GuiPropertyDescriptor.value("columns", Kind.DATA, Collections.emptyList(), Stage.MEASURE));
            properties.add(GuiPropertyDescriptor.value("sortColumn", Kind.STRING, "", Stage.SEMANTICS));
            properties.add(GuiPropertyDescriptor.value("sortDirection", Kind.STRING, "none", Stage.SEMANTICS));
            properties.add(GuiPropertyDescriptor.value("filter", Kind.STRING, "", Stage.SEMANTICS));
            properties.add(new GuiPropertyDescriptor("headerHeight", Kind.INTEGER, 20, 1, 1024, false, false,
                    Collections.singleton(Stage.MEASURE), Collections.emptySet()));
        }
        if ("grid".equals(type)) {
            properties.add(new GuiPropertyDescriptor("columns", Kind.INTEGER, 1, 0, 64, false, false,
                    Collections.singleton(Stage.MEASURE), Collections.emptySet()));
            properties.add(new GuiPropertyDescriptor("minimumCellWidth", Kind.INTEGER, 0, 0, 16384, false, false,
                    Collections.singleton(Stage.MEASURE), Collections.emptySet()));
        }
        if (Arrays.asList("text", "button", "checkbox", "choice", "stepper", "textbox", "textarea", "dropdown",
                "contextmenu", "dialog", "tooltip").contains(type)) {
            properties.add(GuiPropertyDescriptor.value("text", Kind.STRING, "", Stage.MEASURE, Stage.PAINT));
        }
        if ("image".equals(type)) {
            properties.add(GuiPropertyDescriptor.value("image", Kind.STRING, "", Stage.PAINT));
        }
        if ("checkbox".equals(type)) {
            properties.add(GuiPropertyDescriptor.value("selected", Kind.BOOLEAN, false, Stage.PAINT, Stage.SEMANTICS));
        }
        if (Arrays.asList("radio", "tabs", "dropdown", "contextmenu").contains(type)) {
            properties.add(GuiPropertyDescriptor.value("options", Kind.DATA, Collections.emptyList(), Stage.MEASURE));
            properties.add(GuiPropertyDescriptor.value("selectedKey", Kind.STRING, "", Stage.SEMANTICS));
        }
        if ("radio".equals(type) || "tabs".equals(type)) {
            properties.add(GuiPropertyDescriptor.value("orientation", Kind.STRING,
                    "tabs".equals(type) ? "horizontal" : "vertical", Stage.MEASURE));
            properties.add(new GuiPropertyDescriptor("optionHeight", Kind.INTEGER, 20, 1, 1024, false, false,
                    Collections.singleton(Stage.MEASURE), Collections.emptySet()));
        }
        if ("choice".equals(type)) {
            properties.add(new GuiPropertyDescriptor("value", Kind.INTEGER, 1, 1, 4096, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
            properties.add(GuiPropertyDescriptor.value("options", Kind.STRING_LIST, Collections.singletonList(""),
                    Stage.MEASURE));
        }
        if (Arrays.asList("slider", "stepper", "progress").contains(type)) {
            properties.add("stepper".equals(type)
                    ? new GuiPropertyDescriptor("value", Kind.NUMBER, 0, -1.0E12, 1.0E12, false, false,
                            EnumSet.of(Stage.MEASURE, Stage.PAINT, Stage.SEMANTICS), Collections.emptySet())
                    : GuiPropertyDescriptor.number("value", 0, -1.0E12, 1.0E12, Stage.PAINT, Stage.SEMANTICS));
            properties.add(new GuiPropertyDescriptor("minimum", Kind.NUMBER, 0, -1.0E12, 1.0E12, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
            properties.add(new GuiPropertyDescriptor("maximum", Kind.NUMBER, 100, -1.0E12, 1.0E12, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
            properties.add(new GuiPropertyDescriptor("step", Kind.NUMBER, 1, 1.0E-6, 1.0E12, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
            if (!"stepper".equals(type)) {
                properties.add(
                        GuiPropertyDescriptor.value("orientation", Kind.STRING, "horizontal", Stage.PAINT, Stage.HIT));
            }
        }
        if ("textarea".equals(type)) {
            properties.add(GuiPropertyDescriptor.value("wrap", Kind.BOOLEAN, true, Stage.MEASURE));
            properties.add(GuiPropertyDescriptor.value("scrollbars", Kind.BOOLEAN, true, Stage.PAINT, Stage.HIT));
        }
        if ("numberbox".equals(type)) {
            properties.add(new GuiPropertyDescriptor("value", Kind.NUMBER, 0, -1.0E12, 1.0E12, false, false,
                    EnumSet.of(Stage.MEASURE, Stage.PAINT, Stage.SEMANTICS), Collections.emptySet()));
            properties.add(new GuiPropertyDescriptor("minimum", Kind.NUMBER, 0, -1.0E12, 1.0E12, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
            properties.add(new GuiPropertyDescriptor("maximum", Kind.NUMBER, 100, -1.0E12, 1.0E12, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
            properties.add(new GuiPropertyDescriptor("step", Kind.NUMBER, 0, 0, 1.0E12, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
        }
        if ("progress".equals(type)) {
            properties.add(GuiPropertyDescriptor.value("text", Kind.STRING, "", Stage.PAINT));
            properties.add(
                    GuiPropertyDescriptor.value("presentation", Kind.STRING, "linear", Stage.MEASURE, Stage.PAINT));
            properties.add(GuiPropertyDescriptor.value("indeterminate", Kind.BOOLEAN, false, Stage.PAINT));
            properties.add(new GuiPropertyDescriptor("segments", Kind.INTEGER, 10, 1, 256, false, false,
                    Collections.singleton(Stage.PAINT), Collections.emptySet()));
        }
        if ("textbox".equals(type) || "textarea".equals(type)) {
            properties.add(new GuiPropertyDescriptor("maximumLength", Kind.INTEGER, 4096, 0, 65536, false, false,
                    Collections.singleton(Stage.SEMANTICS), Collections.emptySet()));
        }
        return new GuiProperties(type, properties);
    }

    public static int elementAllowance(String type) {
        if ("dropdown".equals(type) || "contextmenu".equals(type)) {
            return 7;
        }
        return "scroll".equals(type) || "textarea".equals(type)
                ? 5
                : "table".equals(type) ? 7 + GuiTableColumns.MAX_COLUMNS : collection(type) ? 6 : 1;
    }

    public static boolean collection(String type) {
        return "list".equals(type) || "tree".equals(type) || "table".equals(type);
    }

    private static void requireType(String type) {
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown built-in view: " + type);
        }
    }
}
