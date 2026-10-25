package betamoon.tileentity;

import betamoon.gui.presentation.GuiConditionEvaluator;
import betamoon.gui.presentation.GuiControlType;
import betamoon.gui.presentation.GuiNodeDefinition;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Native source presentation remains the current baseline below binding and
 * manual overrides.
 */
final class ContainerGuiNativeBaseline {
    private ContainerGuiNativeBaseline() {
    }

    static Map<String, Object> resolve(GuiNodeDefinition node, ContainerDefinition container,
            Function<String, Object> data, Function<String, Object> state) {
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("__conditionVisible", GuiConditionEvaluator.evaluate(node.condition, data::apply, state::apply));
        ContainerGuiDefinition.Element element = node.element;
        if (element instanceof ContainerGuiDefinition.TextElement) {
            ContainerGuiDefinition.TextElement text = (ContainerGuiDefinition.TextElement) element;
            Object value = text.field == null ? text.text : data.apply(text.field);
            String rendered = value == null ? "" : String.valueOf(value);
            if (text.field != null && text.format != null) {
                try {
                    rendered = String.format(Locale.ENGLISH, text.format, value);
                } catch (RuntimeException ignored) {
                    // Match native formatted-label fallback without disabling the source.
                }
            }
            baseline.put("text", rendered);
        } else if (element instanceof ContainerGuiDefinition.StateImageElement) {
            ContainerGuiDefinition.StateImageElement image = (ContainerGuiDefinition.StateImageElement) element;
            ContainerGuiDefinition.Texture texture = image.states.get(String.valueOf(data.apply(image.field)));
            texture = texture == null ? image.defaultImage : texture;
            if (node.properties.baseline().get("width") == null) {
                baseline.put("width", Double.valueOf(texture == null ? 0 : texture.width));
            }
            if (node.properties.baseline().get("height") == null) {
                baseline.put("height", Double.valueOf(texture == null ? 0 : texture.height));
            }
        } else if (element instanceof ContainerGuiDefinition.ControlElement && baseline.containsKey("text")) {
            ContainerGuiDefinition.ControlElement control = (ContainerGuiDefinition.ControlElement) element;
            ContainerControlDefinition behavior = container.controls.get(control.control);
            Object value = behavior.source == ContainerControlDefinition.Source.DATA
                    ? data.apply(behavior.field)
                    : behavior.source == ContainerControlDefinition.Source.SESSION ? state.apply(behavior.field) : null;
            baseline.put("text",
                    control.text != null
                            ? control.text
                            : control.type == GuiControlType.CHOICE ? String.valueOf(value) : "");
        }
        return baseline;
    }
}
