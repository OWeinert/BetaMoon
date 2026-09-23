package betamoon.debug;

import betamoon.assets.BuiltinAssets;
import betamoon.entity.EntityTypeRegistry;
import betamoon.event.Events;
import betamoon.event.api.EventChannel;
import betamoon.fuel.FuelRegistration;
import betamoon.fuel.FuelRegistry;
import betamoon.fuel.FuelSetDefinition;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.luaapi.audio.SoundEvents;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockTickRegistry;
import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.module.ModuleRegistry;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Exports agent diagnostics and safe summaries of active runtime registrations. */
final class DebugRuntimeExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        exportInstrumentation(session);
        exportRegistrations(session);
    }

    private static void exportInstrumentation(DebugExportSession session) throws Exception {
        final List<HookDiagnostic> diagnostics = AgentRuntime.getReport().snapshot();
        session.writeTextFile("instrumentation.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                writer.write("agent status: " + AgentRuntime.getStatus().name().toLowerCase());
                writer.newLine();
                String failure = AgentRuntime.getFailureMessage();
                if (failure != null) {
                    writer.write("agent diagnostic: " + safe(failure));
                    writer.newLine();
                }
                for (HookDiagnostic diagnostic : diagnostics) {
                    writer.write("hook: " + safe(diagnostic.getHookId()) + " | requirement: "
                            + (diagnostic.isRequired() ? "required" : "optional") + " | matches: "
                            + safe(diagnostic.getMatchRequirement()) + " | status: "
                            + diagnostic.getStatus().name().toLowerCase());
                    writer.newLine();
                    writer.write("    target: " + safe(diagnostic.getTarget()) + " | diagnostic: "
                            + safe(diagnostic.getMessage()));
                    writer.newLine();
                }
                return diagnostics.size() + 1;
            }
        });
    }

    private static void exportRegistrations(DebugExportSession session) throws Exception {
        final List<String> rows = registrationRows();
        session.writeTextFile("runtime_registrations.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (String row : rows) {
                    writer.write(row);
                    writer.newLine();
                }
                return rows.size();
            }
        });
    }

    private static List<String> registrationRows() {
        List<String> rows = new ArrayList<String>();
        rows.add("registry: scripts | entries: " + LuaScriptRegistry.snapshot().size());
        rows.add("registry: retained block/item content | entries: " + LuaContentRegistry.snapshot().size());
        rows.add("registry: script assets | entries: " + ScriptAssetScope.snapshot().size());
        rows.add("registry: built-in models | lookup keys: " + BuiltinAssets.models().size());
        rows.add("registry: sound events | entries: " + SoundEvents.snapshot().size());
        rows.add("registry: entity types | entries: " + EntityTypeRegistry.snapshot().size());
        rows.add("registry: tile entities | entries: " + TileEntityRegistry.tileEntityDescriptions().size());
        rows.add("registry: containers | entries: " + TileEntityRegistry.containerDescriptions().size());
        rows.add("registry: container GUIs | entries: " + TileEntityRegistry.guiDescriptions().size());
        rows.add("registry: block callback definitions | entries: " + BlockCallbackRegistry.registeredCount());
        rows.add("registry: item callback definitions | entries: " + ItemCallbackRegistry.registeredCount());
        rows.add("registry: block tick definitions | entries: " + BlockTickRegistry.registeredDefinitionCount());
        rows.add("registry: scheduled block tick positions | entries: " + BlockTickRegistry.scheduledRunCount());
        rows.add("registry: world generators | entries: " + WorldGenRegistry.snapshot().size());
        rows.add("registry: biome overlays | entries: " + BiomeGenRegistry.snapshot().size());
        rows.add("registry: cross-mod exports | entries: " + ModuleRegistry.snapshot().size());
        for (FuelSetDefinition set : FuelRegistry.sets()) {
            rows.add("fuel set: " + set.key + " | owner: " + safe(set.owner) + " | includes: "
                    + (set.includes.isEmpty() ? "none" : safe(String.join(", ", strings(set.includes))))
                    + " | built-in: " + set.builtIn);
        }
        for (FuelRegistration registration : FuelRegistry.registrations()) {
            rows.add("fuel rule: set=" + registration.setKey + " | item registry ID=" + registration.itemId
                    + " | damage=" + (registration.damage == null ? "any" : registration.damage)
                    + " | burn time=" + registration.burnTime + " | owner=" + safe(registration.owner));
        }
        rows.addAll(eventRows());
        Collections.sort(rows);
        return rows;
    }

    private static List<String> eventRows() {
        List<String> rows = new ArrayList<String>();
        List<Field> fields = new ArrayList<Field>();
        Collections.addAll(fields, Events.class.getFields());
        Collections.sort(fields, new Comparator<Field>() {
            @Override
            public int compare(Field left, Field right) {
                return left.getName().compareTo(right.getName());
            }
        });
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers()) || !EventChannel.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                EventChannel<?> channel = (EventChannel<?>) field.get(null);
                rows.add("event channel: " + field.getName().toLowerCase() + " | listeners: "
                        + channel.listenerCount());
            } catch (IllegalAccessException ignored) {
                // Public event fields should be accessible; foreign fields may not be.
            }
        }
        return rows;
    }

    private static List<String> strings(List<?> values) {
        List<String> result = new ArrayList<String>();
        for (Object value : values) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private static String safe(String value) {
        return DebugExportNames.safeString(value == null || value.isEmpty() ? "unavailable" : value);
    }
}
