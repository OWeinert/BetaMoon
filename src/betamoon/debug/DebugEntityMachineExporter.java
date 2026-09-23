package betamoon.debug;

import betamoon.entity.EntityTypeRegistry;
import betamoon.tileentity.TileEntityRegistry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Exports declarative entity, tile-entity, container, and GUI structures. */
final class DebugEntityMachineExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        exportEntities(session);
        exportTileEntities(session);
        exportContainers(session);
        exportGuis(session);
    }

    private static void exportEntities(DebugExportSession session) throws Exception {
        final List<EntityTypeRegistry.Description> entities = new ArrayList<EntityTypeRegistry.Description>(
                EntityTypeRegistry.snapshot());
        Collections.sort(entities, new Comparator<EntityTypeRegistry.Description>() {
            @Override
            public int compare(EntityTypeRegistry.Description left, EntityTypeRegistry.Description right) {
                return left.key.toString().compareTo(right.key.toString());
            }
        });
        session.writeTextFile("entities.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < entities.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    EntityTypeRegistry.Description entity = entities.get(index);
                    writer.write("key: " + entity.key + " | owner: " + safe(entity.owner));
                    writer.newLine();
                    writer.write("kind: " + entity.kind + " | lifecycle: " + entity.lifecycle
                            + " | display name: " + safe(entity.displayName));
                    writer.newLine();
                    writer.write("size: " + entity.width + " x " + entity.height + " | tick interval: "
                            + entity.tickInterval);
                    writer.newLine();
                    writer.write("capabilities: " + list(entity.capabilities));
                    writer.newLine();
                    if (entity.modelPath != null) {
                        writer.write("appearance model: " + entity.modelPath + " | override: "
                                + entity.modelOverridePath + " | dynamic: " + entity.dynamicAppearance);
                        writer.newLine();
                    }
                    if (entity.aiMode != null) {
                        writer.write("AI mode: " + entity.aiMode + " | aggression: " + entity.aggression);
                        writer.newLine();
                    }
                    if (entity.spawning != null) {
                        EntityTypeRegistry.SpawnDescription spawn = entity.spawning;
                        writer.write("spawning: category=" + spawn.category + ", weight=" + spawn.weight
                                + ", group=" + spawn.groupMin + ".." + spawn.groupMax + ", cap=" + spawn.cap
                                + ", light=" + spawn.minLight + ".." + spawn.maxLight + ", height=" + spawn.minY
                                + ".." + spawn.maxY + ", dimensions=" + list(spawn.dimensions) + ", biomes="
                                + list(spawn.biomes) + ", substrates=" + list(spawn.substrates)
                                + ", native despawn=" + spawn.nativeDespawn);
                        writer.newLine();
                    }
                    for (EntityTypeRegistry.DataDescription field : entity.data) {
                        writer.write("data field: " + safe(field.name) + " | type: " + field.type
                                + " | default: " + safe(field.defaultValue));
                        writer.newLine();
                    }
                    for (EntityTypeRegistry.PartDescription part : entity.parts) {
                        writer.write("part: " + safe(part.name) + " | hitbox: " + part.hitbox
                                + " | interaction box: " + part.interactionBox);
                        writer.newLine();
                    }
                    writer.write("callbacks: " + list(entity.callbacks));
                    writer.newLine();
                }
                return entities.size();
            }
        });
    }

    private static void exportTileEntities(DebugExportSession session) throws Exception {
        final List<TileEntityRegistry.TileDescription> definitions =
                new ArrayList<TileEntityRegistry.TileDescription>(TileEntityRegistry.tileEntityDescriptions());
        Collections.sort(definitions, namedTile());
        final List<TileEntityRegistry.BlockBindingDescription> bindings =
                new ArrayList<TileEntityRegistry.BlockBindingDescription>(TileEntityRegistry.blockBindings());
        Collections.sort(bindings, new Comparator<TileEntityRegistry.BlockBindingDescription>() {
            @Override
            public int compare(TileEntityRegistry.BlockBindingDescription left,
                    TileEntityRegistry.BlockBindingDescription right) {
                return Integer.compare(left.blockId, right.blockId);
            }
        });
        session.writeTextFile("tile_entities.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < definitions.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    TileEntityRegistry.TileDescription definition = definitions.get(index);
                    writer.write("name: " + safe(definition.name) + " | owner: " + safe(definition.owner)
                            + " | inventory: " + safe(definition.inventoryName));
                    writer.newLine();
                    writer.write("tick: initial=" + definition.initialTickDelay + ", repeat="
                            + definition.repeatTickDelay + ", random=" + definition.randomTicks + ", chance="
                            + definition.randomTickChance);
                    writer.newLine();
                    for (Map.Entry<String, Integer> slot : definition.slots.entrySet()) {
                        writer.write("slot: " + safe(slot.getKey()) + " | index: " + slot.getValue());
                        writer.newLine();
                    }
                    for (TileEntityRegistry.FieldDescription field : definition.fields) {
                        writer.write("field: " + safe(field.name) + " | type: " + field.type
                                + " | default: " + safe(field.defaultValue) + " | synchronized: "
                                + field.synchronizedToClient);
                        writer.newLine();
                    }
                    for (TileEntityRegistry.BlockBindingDescription binding : bindings) {
                        if (definition.name.equals(binding.tileEntity)) {
                            writer.write("attached block ID: " + binding.blockId + " | container: "
                                    + safe(binding.container) + " | GUI: " + safe(binding.gui) + " | redstone: "
                                    + binding.redstone);
                            writer.newLine();
                        }
                    }
                }
                return definitions.size();
            }
        });
    }

    private static void exportContainers(DebugExportSession session) throws Exception {
        final List<TileEntityRegistry.ContainerDescription> definitions =
                new ArrayList<TileEntityRegistry.ContainerDescription>(TileEntityRegistry.containerDescriptions());
        Collections.sort(definitions, new Comparator<TileEntityRegistry.ContainerDescription>() {
            @Override
            public int compare(TileEntityRegistry.ContainerDescription left,
                    TileEntityRegistry.ContainerDescription right) {
                return left.name.compareTo(right.name);
            }
        });
        session.writeTextFile("containers.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < definitions.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    TileEntityRegistry.ContainerDescription definition = definitions.get(index);
                    writer.write("name: " + safe(definition.name) + " | owner: " + safe(definition.owner)
                            + " | tile entity: " + safe(definition.tileEntity) + " | hotbar: "
                            + definition.includeHotbar);
                    writer.newLine();
                    for (TileEntityRegistry.SlotDescription slot : definition.slots) {
                        writer.write("slot: " + safe(slot.name) + " | inventory index: " + slot.index + " | position: "
                                + slot.x + "," + slot.y + " | output only: " + slot.outputOnly + " | fuel set: "
                                + (slot.acceptedFuelSet == null ? "none" : slot.acceptedFuelSet));
                        writer.newLine();
                    }
                }
                return definitions.size();
            }
        });
    }

    private static void exportGuis(DebugExportSession session) throws Exception {
        final List<TileEntityRegistry.GuiDescription> definitions =
                new ArrayList<TileEntityRegistry.GuiDescription>(TileEntityRegistry.guiDescriptions());
        Collections.sort(definitions, new Comparator<TileEntityRegistry.GuiDescription>() {
            @Override
            public int compare(TileEntityRegistry.GuiDescription left, TileEntityRegistry.GuiDescription right) {
                return left.name.compareTo(right.name);
            }
        });
        session.writeTextFile("guis.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < definitions.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    TileEntityRegistry.GuiDescription definition = definitions.get(index);
                    writer.write("name: " + safe(definition.name) + " | owner: " + safe(definition.owner)
                            + " | container: " + safe(definition.container) + " | size: " + definition.width
                            + "x" + definition.height + " | pauses game: " + definition.pauseGame);
                    writer.newLine();
                    writer.write("background style: " + safe(definition.backgroundStyle) + " | slot frames: "
                            + definition.drawSlotFrames + " | elements: " + definition.elements.size());
                    writer.newLine();
                    for (TileEntityRegistry.ElementDescription element : definition.elements) {
                        writer.write("element: " + element.type + " | position: " + element.x + "," + element.y
                                + " | layer: " + element.layer + " | anchor: " + element.anchor);
                        writer.newLine();
                    }
                }
                return definitions.size();
            }
        });
    }

    private static Comparator<TileEntityRegistry.TileDescription> namedTile() {
        return new Comparator<TileEntityRegistry.TileDescription>() {
            @Override
            public int compare(TileEntityRegistry.TileDescription left, TileEntityRegistry.TileDescription right) {
                return left.name.compareTo(right.name);
            }
        };
    }

    private static String list(List<?> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        List<String> text = new ArrayList<String>();
        for (Object value : values) {
            text.add(String.valueOf(value));
        }
        return safe(String.join(", ", text));
    }

    private static String safe(String value) {
        return DebugExportNames.safeString(value == null || value.isEmpty() ? "none" : value);
    }
}
