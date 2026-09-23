package betamoon.luaapi.tileentity;

import betamoon.capability.CapabilityAttachmentDefinition;
import betamoon.capability.CapabilityDefinition;
import betamoon.data.DataRecords;
import betamoon.data.DataField;
import betamoon.luaapi.capability.CapabilitiesApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.TileEntityDefinition;
import betamoon.tileentity.TileEntityRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Installs startup-only tile entity, container, and container GUI registries.
 */
public final class TileEntityApi {
    private TileEntityApi() {
    }

    public static void attach(LuaTable root) {
        root.set("tileEntities", new Registry("tileEntities"));
        root.set("containers", new Registry("containers"));
        root.set("containerGuis", new Registry("containerGuis"));
    }

    private static final class Registry extends LuaTable {
        private final String type;
        private Registry(String type) {
            this.type = type;
            set("add", new Add(this));
        }
    }

    private static final class Add extends VarArgFunction {
        private final Registry registry;
        private Add(Registry registry) {
            this.registry = registry;
        }

        public Varargs invoke(Varargs args) {
            LuaValue def = args.arg1() == registry ? args.arg(2) : args.arg1();
            if (!def.istable()) {
                throw new LuaError(registry.type + ":add expects a definition table.");
            }
            if ("tileEntities".equals(registry.type)) {
                return addTileEntity(def);
            }
            if ("containers".equals(registry.type)) {
                return addContainer(def);
            }
            return addGui(def);
        }
    }

    private static TileEntityHandle addTileEntity(LuaValue def) {
        String owner = requireOwner();
        String name = qualifiedName(owner, requiredString(def, "name"));
        TileEntityDefinition existing = TileEntityRegistry.getTileEntity(name);
        if (existing != null) {
            return new TileEntityHandle(existing);
        }

        LuaValue inventory = def.get("inventory");
        if (inventory.isnil()) {
            inventory = new LuaTable();
        } else if (!inventory.istable()) {
            throw new LuaError("inventory must be a table.");
        }
        LuaValue slotDefs = inventory.get("slots");
        if (slotDefs.isnil()) {
            slotDefs = new LuaTable();
        } else if (!slotDefs.istable()) {
            throw new LuaError("inventory.slots must be a table.");
        }
        Map<String, Integer> slots = new LinkedHashMap<>();
        Map<String, LuaValue> slotTables = new HashMap<>();
        List<String> slotNames = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = slotDefs.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            if (!key.isstring() || !next.arg(2).istable()) {
                throw new LuaError("inventory.slots must map names to definition tables.");
            }
            slotNames.add(key.checkjstring());
            slotTables.put(key.checkjstring(), next.arg(2));
        }
        Collections.sort(slotNames);
        Set<Integer> usedIndexes = new HashSet<>();
        for (int i = 0; i < slotNames.size(); i++) {
            String slotName = slotNames.get(i);
            LuaValue indexValue = slotTables.get(slotName).get("index");
            if (indexValue.isnil()) {
                continue;
            }
            int slotIndex = indexValue.checkint();
            if (slotIndex < 0 || slotIndex >= slotNames.size() || !usedIndexes.add(Integer.valueOf(slotIndex))) {
                throw new LuaError("Inventory slot '" + slotName + "' has an invalid or duplicate index.");
            }
            slots.put(slotName, Integer.valueOf(slotIndex));
        }
        for (int i = 0; i < slotNames.size(); i++) {
            String slotName = slotNames.get(i);
            LuaValue indexValue = slotTables.get(slotName).get("index");
            if (!indexValue.isnil()) {
                continue;
            }
            int slotIndex = nextFreeIndex(usedIndexes);
            usedIndexes.add(Integer.valueOf(slotIndex));
            slots.put(slotName, Integer.valueOf(slotIndex));
        }

        Map<String, TileEntityDefinition.Field> fields = new LinkedHashMap<>();
        LuaValue data = def.get("data");
        if (!data.isnil()) {
            if (!data.istable()) {
                throw new LuaError("tile entity data must be a table.");
            }
            key = LuaValue.NIL;
            while (true) {
                Varargs next = data.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                String fieldName = key.checkjstring();
                LuaValue fieldDef = next.arg(2);
                if (!fieldDef.istable()) {
                    throw new LuaError("Data field '" + fieldName + "' must be a table.");
                }
                LuaTable schemaDefinition = new LuaTable();
                LuaValue fieldKey = LuaValue.NIL;
                while (true) {
                    Varargs fieldNext = fieldDef.next(fieldKey);
                    fieldKey = fieldNext.arg1();
                    if (fieldKey.isnil()) {
                        break;
                    }
                    if (!"sync".equals(fieldKey.tojstring())) {
                        schemaDefinition.set(fieldKey, fieldNext.arg(2));
                    }
                }
                DataField schema = DataField.parse(fieldName, schemaDefinition, "tileEntity.data." + fieldName);
                if (schema.containsEntityReference()) {
                    throw new LuaError("tileEntity.data." + fieldName
                            + ": entity_reference is currently supported only by entity data.");
                }
                boolean sync = fieldDef.get("sync").toboolean();
                if (sync && !(schema.type == DataField.Type.INTEGER || schema.type == DataField.Type.BOOLEAN)) {
                    throw new LuaError("Only integer and boolean data fields can use sync = true.");
                }
                fields.put(fieldName, new TileEntityDefinition.Field(fieldName, schema, sync));
            }
        }

        LuaValue tick = def.get("onTick");
        LuaValue action = LuaValue.NIL;
        int initialDelay = 0;
        int repeatDelay = 0;
        boolean randomTicks = false;
        double randomChance = 0.05D;
        if (!tick.isnil()) {
            if (!tick.istable()) {
                throw new LuaError("onTick must be a table.");
            }
            action = required(tick, "action");
            if (!action.isfunction()) {
                throw new LuaError("onTick.action must be a function.");
            }
            String mode = requiredString(tick, "mode").toLowerCase();
            if ("continuous".equals(mode) || "default".equals(mode)) {
                initialDelay = repeatDelay = 1;
            } else if ("scheduled".equals(mode)) {
                LuaValue schedule = requiredTable(tick, "schedule");
                initialDelay = requiredInt(schedule, "delay");
                repeatDelay = schedule.get("repeatEvery").optint(0);
                if (initialDelay <= 0 || repeatDelay < 0) {
                    throw new LuaError("onTick schedule values must be positive.");
                }
            } else if ("random".equals(mode)) {
                randomTicks = true;
                randomChance = tick.get("chance").optdouble(0.05D);
                if (randomChance < 0.0D || randomChance > 1.0D || Double.isNaN(randomChance)
                        || Double.isInfinite(randomChance)) {
                    throw new LuaError("onTick.chance must be between 0 and 1.");
                }
            } else {
                throw new LuaError("onTick.mode must be 'continuous', 'random', or 'scheduled'.");
            }
        }
        LuaValue inventoryChanged = callbackAction(def.get("onInventoryChanged"), "onInventoryChanged");
        List<CapabilityAttachmentDefinition> capabilities = parseCapabilities(def.get("capabilities"));
        TileEntityDefinition definition = new TileEntityDefinition(name, owner, inventory.get("name").optjstring(name),
                slots, fields, capabilities, action, inventoryChanged, initialDelay, repeatDelay, randomTicks,
                randomChance);
        TileEntityRegistry.register(definition);
        return new TileEntityHandle(definition);
    }

    private static List<CapabilityAttachmentDefinition> parseCapabilities(LuaValue declarations) {
        if (declarations.isnil()) {
            return Collections.emptyList();
        }
        if (!declarations.istable()) {
            throw new LuaError("tile entity capabilities must be an array.");
        }
        int count = declarations.length();
        if (count > 32) {
            throw new LuaError("A tile entity may implement at most 32 capabilities.");
        }
        List<CapabilityAttachmentDefinition> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int index = 1; index <= count; index++) {
            String path = "tileEntity.capabilities[" + index + "]";
            LuaValue declaration = declarations.get(index);
            betamoon.luaapi.utils.LuaDeclarationValues.fields(
                    declaration, path, "capability", "config", "operations", "ports");
            CapabilityDefinition capability = CapabilitiesApi.definition(required(declaration, "capability"),
                    path + ".capability");
            if (!used.add(capability.key.toString())) {
                throw new LuaError(path + ": capability is attached more than once: " + capability.key);
            }
            Map<String, Object> config = DataRecords.read(
                    capability.config, declaration.get("config"), path + ".config", true);
            LuaValue operationValues = declaration.get("operations");
            if (operationValues.isnil()) {
                operationValues = new LuaTable();
            } else if (!operationValues.istable()) {
                throw new LuaError(path + ".operations must be a table.");
            }
            betamoon.luaapi.utils.LuaDeclarationValues.fields(operationValues, path + ".operations",
                    capability.operations.keySet().toArray(new String[0]));
            Map<String, LuaValue> operations = new LinkedHashMap<>();
            for (String operation : capability.operations.keySet()) {
                LuaValue callback = operationValues.get(operation);
                if (!callback.isfunction()) {
                    throw new LuaError(path + ".operations." + operation + " must be a function.");
                }
                operations.put(operation, callback);
            }
            Map<String, Object> ports = parsePorts(declaration.get("ports"), path + ".ports");
            result.add(new CapabilityAttachmentDefinition(capability, config, operations, ports));
        }
        return result;
    }

    private static Map<String, Object> parsePorts(LuaValue declaration, String path) {
        if (declaration.isnil()) {
            return Collections.emptyMap();
        }
        betamoon.luaapi.utils.LuaDeclarationValues.fields(declaration, path,
                "north", "south", "east", "west", "up", "down", "front", "back", "left", "right");
        Map<String, Object> result = new LinkedHashMap<>();
        String[] faces = {"north", "south", "east", "west", "up", "down", "front", "back", "left", "right"};
        for (String face : faces) {
            LuaValue value = declaration.get(face);
            if (value.isnil()) {
                continue;
            }
            if (value.isboolean() && !value.toboolean()) {
                result.put(face, Boolean.FALSE);
            } else if (value.isstring()) {
                String port = value.checkjstring();
                if (port.isEmpty() || port.length() > 64) {
                    throw new LuaError(path + "." + face + " must contain 1 to 64 characters.");
                }
                result.put(face, port);
            } else {
                throw new LuaError(path + "." + face + " must be a string or false.");
            }
        }
        return result;
    }

    private static ContainerHandle addContainer(LuaValue def) {
        String owner = requireOwner();
        String name = qualifiedName(owner, requiredString(def, "name"));
        ContainerDefinition existing = TileEntityRegistry.getContainer(name);
        if (existing != null) {
            return new ContainerHandle(existing);
        }
        TileEntityDefinition tile = tileHandle(required(def, "tileEntity")).definition;
        requireSameOwner(owner, tile.owner, "tile entity");
        List<ContainerDefinition.SlotDefinition> slots = new ArrayList<>();
        Set<Integer> visibleSlots = new HashSet<>();
        LuaValue slotDefs = requiredTable(def, "slots");
        for (int i = 1; i <= slotDefs.length(); i++) {
            LuaValue slot = slotDefs.get(i);
            if (!slot.istable()) {
                throw new LuaError("Container slots must be definition tables.");
            }
            String slotName = requiredString(slot, "slot");
            Integer slotIndex = tile.slots.get(slotName);
            if (slotIndex == null) {
                throw new LuaError("Unknown tile entity slot: " + slotName);
            }
            if (!visibleSlots.add(slotIndex)) {
                throw new LuaError("Container lists tile slot '" + slotName + "' more than once.");
            }
            slots.add(
                    new ContainerDefinition.SlotDefinition(slot.get("name").optjstring(slotName), slotIndex.intValue(),
                            requiredInt(slot, "x"), requiredInt(slot, "y"), slot.get("outputOnly").toboolean()));
        }
        LuaValue player = requiredTable(def, "playerInventory");
        ContainerDefinition definition = new ContainerDefinition(name, owner, tile, slots, requiredInt(player, "x"),
                requiredInt(player, "y"), player.get("includeHotbar").optboolean(true));
        TileEntityRegistry.register(definition);
        return new ContainerHandle(definition);
    }

    private static GuiHandle addGui(LuaValue def) {
        String owner = requireOwner();
        String name = qualifiedName(owner, requiredString(def, "name"));
        ContainerGuiDefinition existing = TileEntityRegistry.getGui(name);
        if (existing != null) {
            return new GuiHandle(existing);
        }
        ContainerDefinition container = containerHandle(required(def, "container")).definition;
        requireSameOwner(owner, container.owner, "container");

        LuaValue layout = def.get("layout");
        if (layout.isnil()) {
            layout = new LuaTable();
        }
        if (!layout.istable()) {
            throw new LuaError("layout must be a table.");
        }
        String preset = layout.get("preset").optjstring("minecraft:container").toLowerCase();
        int rows = layout.get("rows").optint(3);
        if (rows < 1 || rows > 6) {
            throw new LuaError("layout.rows must be between 1 and 6.");
        }
        LuaValue backgroundValue = def.get("background");
        if (backgroundValue.isnil()) {
            backgroundValue = new LuaTable();
        }
        if (!backgroundValue.istable()) {
            throw new LuaError("background must be a table.");
        }
        ContainerGuiDefinition.Background background = ContainerGuiParser.parseBackground(backgroundValue, preset,
                rows);
        int defaultHeight = "minecraft:chest".equals(preset) ? 114 + rows * 18 : 166;
        int width = layout.get("width").optint(background.texture == null ? 176 : background.texture.width);
        int height = layout.get("height")
                .optint(background.texture == null
                        ? defaultHeight
                        : "chest".equals(background.style) ? defaultHeight : background.texture.height);
        if (width <= 0 || width > 256 || height <= 0 || height > 256) {
            throw new LuaError("Container GUI dimensions must be between 1 and 256 pixels.");
        }
        ContainerGuiDefinition.Label title = ContainerGuiParser.parseLabel(layout.get("title"),
                container.tileEntity.inventoryName, 8, 6, width - 16);
        ContainerGuiDefinition.Label inventoryLabel = ContainerGuiParser.parseLabel(layout.get("playerInventoryLabel"),
                "Inventory", 8, height - 94, width - 16);
        List<ContainerGuiDefinition.Element> elements = new ArrayList<>();
        LuaValue elementDefs = def.get("elements");
        if (!elementDefs.isnil()) {
            ContainerGuiParser.parseElements(elementDefs, container.tileEntity, elements, 0, 0, null);
        }
        ContainerGuiParser.validateBounds(elements, width, height);
        ContainerGuiDefinition definition = new ContainerGuiDefinition(name, owner, container, width, height, title,
                inventoryLabel, background, layout.get("pauseGame").optboolean(false), elements);
        TileEntityRegistry.register(definition);
        return new GuiHandle(definition);
    }

    public static final class TileEntityHandle extends LuaTable {
        public final TileEntityDefinition definition;
        private TileEntityHandle(TileEntityDefinition definition) {
            this.definition = definition;
            set("name", definition.name);
        }
    }
    public static final class ContainerHandle extends LuaTable {
        public final ContainerDefinition definition;
        private ContainerHandle(ContainerDefinition definition) {
            this.definition = definition;
            set("name", definition.name);
        }
    }
    public static final class GuiHandle extends LuaTable {
        public final ContainerGuiDefinition definition;
        private GuiHandle(ContainerGuiDefinition definition) {
            this.definition = definition;
            set("name", definition.name);
        }
    }

    public static TileEntityHandle tileHandle(LuaValue value) {
        if (!(value instanceof TileEntityHandle)) {
            throw new LuaError("Expected a tile entity handle.");
        }
        return (TileEntityHandle) value;
    }

    public static ContainerHandle containerHandle(LuaValue value) {
        if (!(value instanceof ContainerHandle)) {
            throw new LuaError("Expected a container handle.");
        }
        return (ContainerHandle) value;
    }

    public static GuiHandle guiHandle(LuaValue value) {
        if (!(value instanceof GuiHandle)) {
            throw new LuaError("Expected a container GUI handle.");
        }
        return (GuiHandle) value;
    }

    private static String requireOwner() {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Structural content must be registered from modInit.");
        }
        return owner;
    }

    private static String qualifiedName(String owner, String name) {
        if (name.indexOf(':') >= 0) {
            return name.toLowerCase();
        }
        int dot = owner.lastIndexOf('.');
        String namespace = (dot > 0 ? owner.substring(0, dot) : owner).replaceAll("[^A-Za-z0-9_]", "_").toLowerCase();
        return namespace + ":" + name.toLowerCase();
    }

    private static void requireSameOwner(String owner, String referencedOwner, String kind) {
        if (!owner.equals(referencedOwner)) {
            throw new LuaError("A structural " + kind + " must be declared by the same script.");
        }
    }

    private static int nextFreeIndex(Set<Integer> used) {
        int index = 0;
        while (used.contains(Integer.valueOf(index))) {
            index++;
        }
        return index;
    }

    private static LuaValue callbackAction(LuaValue definition, String name) {
        if (definition.isnil()) {
            return LuaValue.NIL;
        }
        if (!definition.istable()) {
            throw new LuaError(name + " must be a table.");
        }
        LuaValue action = required(definition, "action");
        if (!action.isfunction()) {
            throw new LuaError(name + ".action must be a function.");
        }
        return action;
    }

    private static LuaValue required(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) {
            throw new LuaError("Definition requires '" + key + "'.");
        }
        return value;
    }

    private static LuaValue requiredTable(LuaValue table, String key) {
        LuaValue value = required(table, key);
        if (!value.istable()) {
            throw new LuaError(key + " must be a table.");
        }
        return value;
    }

    private static String requiredString(LuaValue table, String key) {
        return required(table, key).checkjstring();
    }

    private static int requiredInt(LuaValue table, String key) {
        return required(table, key).checkint();
    }
}
