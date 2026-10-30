package betamoon.luaapi.client.ui;

import betamoon.gui.drawing.GuiPaintBudget;
import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.style.GuiNamedFontFace;
import betamoon.gui.style.GuiPreparedTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiThemeDefinition;
import betamoon.gui.widget.GuiStyledView;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.ScriptExecution;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Invokes one readonly builder; finite command alternatives are compiled once
 * and selected by Java.
 */
public final class GuiTemplateSource implements GuiPreparedTemplate.Source {
    private final String path;
    private final GuiThemeDefinition theme;
    private final GuiThemeDefinition.Template template;
    private final GuiPaintCompiler compiler;
    private final GuiPaintBudget budget;
    private final Consumer<LuaCallbackScope> nativeContext;

    public GuiTemplateSource(String path, GuiThemeDefinition theme, GuiThemeDefinition.Template template,
            GuiProperties properties, GuiPaintCompiler.Resources resources, GuiPaintBudget budget,
            Consumer<LuaCallbackScope> nativeContext) {
        this.path = path;
        this.theme = theme;
        this.template = template;
        List<GuiPropertyDescriptor> descriptors = new ArrayList<>(properties.descriptors().values());
        descriptors.add(GuiPropertyDescriptor.number("fraction", 0, 0, 1, GuiPropertyDescriptor.Stage.PAINT));
        this.compiler = new GuiPaintCompiler(new GuiProperties("template", descriptors), resources);
        this.budget = budget;
        this.nativeContext = nativeContext;
    }

    public GuiPaintProgram[] prepare(GuiContext context, GuiStyledView view) {
        theme.requireActive();
        LuaValue result;
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            nativeContext.accept(scope);
            LuaTable input = new LuaTable();
            input.set("kind", view.kind());
            input.set("width", view.getWidth());
            input.set("height", view.getHeight());
            input.set("props", GuiReadOnlyValues.fromPublished(view.templateValues(true)));
            GuiControlStyle normal = view.style(0);
            input.set("font",
                    normal.font() instanceof GuiNamedFontFace ? ((GuiNamedFontFace) normal.font()).key : "native");
            Map<String, Object> styles = new LinkedHashMap<>();
            String[] names = {"default", "hovered", "pressed", "selected", "focused", "invalid", "disabled"};
            int[] masks = {0, GuiStyleSheet.HOVERED, GuiStyleSheet.PRESSED, GuiStyleSheet.SELECTED,
                    GuiStyleSheet.FOCUSED, GuiStyleSheet.INVALID, GuiStyleSheet.DISABLED};
            for (int index = 0; index < names.length; index++) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : view.style(masks[index]).values().entrySet()) {
                    if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
                styles.put(names[index], values);
            }
            input.set("styles", GuiReadOnlyValues.fromPublished(styles));
            int lineHeight = normal.font().lineHeight(context);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("lineHeight", lineHeight);
            metrics.put("textWidth", normal.font().width(context, view.text()));
            metrics.put("padding", normal.number("padding", 0));
            if (view.editor() != null) {
                int caret = view.editor().caret();
                int start = view.editor().selectionStart();
                int end = view.editor().selectionEnd();
                metrics.put("caretX", normal.number("padding", 0)
                        + normal.font().width(context, view.text().substring(0, caret)) - view.textOffset());
                metrics.put("selectionLeft", normal.number("padding", 0)
                        + normal.font().width(context, view.text().substring(0, start)) - view.textOffset());
                metrics.put("selectionRight", normal.number("padding", 0)
                        + normal.font().width(context, view.text().substring(0, end)) - view.textOffset());
            }
            input.set("metrics", GuiReadOnlyValues.fromPublished(metrics));
            result = ScriptExecution.invokeScoped(template.owner, "ui.template." + path, template.paint, input, scope)
                    .arg1();
        }
        theme.requireActive();
        Map<Integer, GuiPaintProgram> variants = new LinkedHashMap<>();
        if (result.istable() && (result.checktable().keys().length == 0 || result.get(1).istable())) {
            variants.put(0, compiler.compile(result, path));
        } else {
            for (String name : LuaDeclarationValues.keys(result, path)) {
                int selector = selector(name);
                if (variants.put(selector, compiler.compile(result.get(name), path + "." + name)) != null) {
                    throw LuaDeclarationValues.error(path, "duplicate template state");
                }
            }
        }
        if (!variants.containsKey(0) || variants.size() > 64) {
            throw LuaDeclarationValues.error(path, "template requires default paint and at most 64 alternatives");
        }
        for (GuiPaintProgram program : variants.values()) {
            if (program.hitVertices() != 0) {
                throw LuaDeclarationValues.error(path,
                        "templates retain control hit behavior; custom hit regions belong to components");
            }
        }
        GuiPaintProgram[] programs = new GuiPaintProgram[128];
        for (int state = 0; state < 128; state++) {
            int effective = (state & GuiStyleSheet.DISABLED) != 0
                    ? state & ~(GuiStyleSheet.HOVERED | GuiStyleSheet.PRESSED)
                    : state;
            GuiPaintProgram selected = variants.get(0);
            for (int bit : new int[]{GuiStyleSheet.HOVERED, GuiStyleSheet.PRESSED, GuiStyleSheet.SELECTED,
                    GuiStyleSheet.FOCUSED, GuiStyleSheet.INVALID, GuiStyleSheet.DISABLED, 64}) {
                if ((effective & bit) != 0 && variants.containsKey(bit)) {
                    selected = variants.get(bit);
                }
            }
            for (Map.Entry<Integer, GuiPaintProgram> entry : variants.entrySet()) {
                if (Integer.bitCount(entry.getKey()) > 1 && (effective & entry.getKey()) == entry.getKey()) {
                    selected = entry.getValue();
                }
            }
            programs[state] = selected;
        }
        return programs;
    }

    public void accept(GuiPaintProgram[] programs) {
        budget.accept(path, programs);
    }

    private static int selector(String name) {
        boolean caret = false;
        StringBuilder style = new StringBuilder();
        for (String part : name.split("\\+", -1)) {
            if (part.equals("caret")) {
                if (caret) {
                    throw LuaDeclarationValues.error("template state", "duplicate caret state");
                }
                caret = true;
            } else {
                if (style.length() > 0) {
                    style.append('+');
                }
                style.append(part);
            }
        }
        return (style.length() == 0 ? 0 : GuiStyleParser.selector(style.toString())) | (caret ? 64 : 0);
    }
}
