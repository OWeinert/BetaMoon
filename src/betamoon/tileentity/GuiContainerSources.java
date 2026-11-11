package betamoon.tileentity;

import betamoon.gui.presentation.GuiPublishedData;
import betamoon.gui.presentation.GuiControlType;
import betamoon.luaapi.tileentity.GuiContainerLuaAccess;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.Slot;

/**
 * Detached observations of this container's existing domain and native
 * geometry.
 */
final class GuiContainerSources {
    private final GuiPublishedData publication = new GuiPublishedData();
    private GuiContainerLuaAccess.Snapshot snapshot = new GuiContainerLuaAccess.Snapshot(Collections.emptyMap());
    private final LuaTileEntity tile;
    private final LuaContainer backend;
    private final GuiLuaContainer screen;

    GuiContainerSources(LuaTileEntity tile, LuaContainer backend, GuiLuaContainer screen) {
        this.tile = tile;
        this.backend = backend;
        this.screen = screen;
    }

    GuiContainerLuaAccess.Snapshot snapshot(ContainerGuiDefinition gui) {
        Map<String, Object> result = new LinkedHashMap<>();
        ContainerDefinition definition = gui.container;
        for (Map.Entry<String, Object> field : backend.controls().session().snapshot().entrySet()) {
            result.put("container.session." + field.getKey(), field.getValue());
        }
        for (TileEntityDefinition.Field field : definition.tileEntity.fields.values()) {
            if (ContainerGuiData.readable(field)) {
                result.put("container.data." + field.name, tile.getDataValue(field.name));
            }
        }
        for (ContainerControlDefinition control : definition.controls.values()) {
            result.put("container.control." + control.name, control(control, definition));
        }
        result.put("container.schema", schema(definition));
        result.put("container.layout", layout(gui));
        if (publication.publish(result)) {
            snapshot = new GuiContainerLuaAccess.Snapshot(publication.snapshot());
        }
        return snapshot;
    }

    Map<String, Object> control(ContainerControlDefinition control, ContainerDefinition definition) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", control.type.name().toLowerCase(Locale.ROOT));
        value.put("enabled", backend.controls().enabled(control));
        if (control.hasValue() && (control.source == ContainerControlDefinition.Source.SESSION
                || ContainerGuiData.readable(definition.tileEntity.fields.get(control.field)))) {
            value.put("value", backend.controls().value(control));
        }
        return value;
    }

    private Map<String, Object> schema(ContainerDefinition definition) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", definition.name);
        Map<String, Object> session = new LinkedHashMap<>();
        for (ContainerDefinition.SessionField field : definition.session.values()) {
            Map<String, Object> description = new LinkedHashMap<>();
            description.put("type", field.type.getLuaName());
            description.put("maximumLength", field.maximumLength);
            session.put(field.name, description);
        }
        schema.put("session", session);
        Map<String, Object> data = new LinkedHashMap<>();
        for (TileEntityDefinition.Field field : definition.tileEntity.fields.values()) {
            if (ContainerGuiData.readable(field)) {
                data.put(field.name, field.schema.type.name().toLowerCase(Locale.ROOT));
            }
        }
        schema.put("data", data);
        Map<String, Object> controls = new LinkedHashMap<>();
        for (ContainerControlDefinition control : definition.controls.values()) {
            Map<String, Object> description = new LinkedHashMap<>();
            description.put("type", control.type.name().toLowerCase(Locale.ROOT));
            description.put("source", control.source.name().toLowerCase(Locale.ROOT));
            if (control.field != null) {
                description.put("field", control.field);
            }
            if (control.type == GuiControlType.NUMBER) {
                description.put("minimum", control.minimum);
                description.put("maximum", control.maximum);
                description.put("step", control.step);
            }
            if (control.type == GuiControlType.TEXT) {
                description.put("maximumLength", control.maximumLength);
            }
            if (!control.choices.isEmpty()) {
                description.put("choices", control.choices);
            }
            controls.put(control.name, description);
        }
        schema.put("controls", controls);
        return schema;
    }

    private Map<String, Object> layout(ContainerGuiDefinition definition) {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("windowId", backend.windowId);
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("x", (screen.width - definition.width) / 2);
        panel.put("y", (screen.height - definition.height) / 2);
        panel.put("width", definition.width);
        panel.put("height", definition.height);
        layout.put("panel", panel);
        List<Object> slots = new ArrayList<>();
        for (int index = 0; index < backend.slots.size(); index++) {
            Slot slot = (Slot) backend.slots.get(index);
            int playerIndex = index - definition.container.slots.size();
            String id = playerIndex < 0
                    ? "tile/" + definition.container.slots.get(index).name
                    : playerIndex < 27
                            ? "player/inventory/" + (playerIndex + 1)
                            : "player/hotbar/" + (playerIndex - 26);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("nativeIndex", index);
            value.put("x", slot.xDisplayPosition);
            value.put("y", slot.yDisplayPosition);
            value.put("width", 16);
            value.put("height", 16);
            slots.add(value);
        }
        layout.put("slots", slots);
        return layout;
    }
}
