package betamoon.tileentity;

import betamoon.data.DataField;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationRuntime;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Container-specific sources and callback authority for the shared presentation
 * engine.
 */
final class ContainerPresentationHost implements GuiPresentationHost {
    private final ContainerGuiDefinition definition;
    private final LuaTileEntity tile;
    private final ContainerControlRuntime controls;

    ContainerPresentationHost(ContainerGuiDefinition definition, LuaTileEntity tile, ContainerControlRuntime controls) {
        this.definition = definition;
        this.tile = tile;
        this.controls = controls;
    }

    static ContainerPresentationHost defaults(ContainerGuiDefinition definition) {
        return new ContainerPresentationHost(definition, null, null);
    }

    @Override
    public GuiPresentationSources snapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        if (controls != null) {
            state.putAll(controls.session().snapshot());
        } else {
            for (ContainerDefinition.SessionField field : definition.container.session.values()) {
                state.put(field.name, field.defaultValue);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> nativeData = new LinkedHashMap<>();
        for (TileEntityDefinition.Field field : definition.container.tileEntity.fields.values()) {
            Object value = tile == null ? field.defaultValue : tile.getDataValue(field.name);
            nativeData.put(field.name, value);
            if (field.sync
                    && (field.schema.type == DataField.Type.INTEGER || field.schema.type == DataField.Type.BOOLEAN)) {
                data.put(field.name, value);
            }
        }
        return new GuiPresentationSources(state, data, nativeData);
    }

    @Override
    public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
        return ContainerGuiNativeBaseline.resolve(node, definition.container, sources.nativeData::get,
                sources.state::get);
    }

    @Override
    public List<AutoCloseable> observe(Observer observer) {
        if (controls == null || tile == null) {
            return Collections.emptyList();
        }
        List<AutoCloseable> subscriptions = new ArrayList<>();
        subscriptions.add(controls.session()
                .observe((key, previous, value) -> observer.changed("state:" + key, previous, value)));
        subscriptions.add(tile.observeGuiData((key, previous, value) -> {
            TileEntityDefinition.Field field = definition.container.tileEntity.fields.get(key);
            if (field != null) {
                observer.changed((field.sync ? "data:" : "native:") + key, previous, value);
            }
        }));
        if (definition.presentation.listens("control")) {
            subscriptions.add(controls.observeControls(notification -> observer.event("control", () -> {
                LuaTable event = new LuaTable();
                event.set("control", notification.control);
                event.set("phase", notification.phase);
                event.set("accepted", LuaValue.valueOf(notification.accepted));
                event.set("proposed", GuiPresentationRuntime.lua(notification.proposed));
                event.set("value", GuiPresentationRuntime.lua(notification.value));
                event.set("previous", GuiPresentationRuntime.lua(notification.previous));
                event.set("input", notification.input);
                return event;
            })));
        }
        return subscriptions;
    }

    @Override
    public LuaTable context(LuaCallbackScope scope) {
        if (controls == null) {
            throw new IllegalStateException("Declaration validation cannot dispatch GUI callbacks.");
        }
        return controls.guiContext(scope);
    }

    @Override
    public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
        if (!(replacement instanceof ContainerGuiDefinition)
                || ((ContainerGuiDefinition) replacement).container != definition.container) {
            throw new LuaError("Presentation refresh cannot replace the active container schema.");
        }
        return new ContainerPresentationHost((ContainerGuiDefinition) replacement, tile, controls);
    }
}
