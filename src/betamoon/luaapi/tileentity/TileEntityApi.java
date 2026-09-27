package betamoon.luaapi.tileentity;

import betamoon.assets.AssetKey;
import betamoon.capability.CapabilityAttachmentDefinition;
import betamoon.capability.CapabilityDefinition;
import betamoon.data.DataField;
import betamoon.data.DataRecords;
import betamoon.luaapi.capability.CapabilitiesApi;
import betamoon.luaapi.fuel.FuelsApi;
import betamoon.luaapi.resource.LuaResultList;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaDefinitionCallbackLayers;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luaapi.utils.LuaOverrideLayers;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.tileentity.ContainerControlDefinition;
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
    private static final OverrideManager.Property<TileEntityRegistry.GuiEntry, ContainerGuiDefinition> GUI_DEFINITION =
            new OverrideManager.Property<TileEntityRegistry.GuiEntry, ContainerGuiDefinition>("definition",
                    new OverrideManager.PropertyAdapter<TileEntityRegistry.GuiEntry, ContainerGuiDefinition>() {
                        public ContainerGuiDefinition read(TileEntityRegistry.GuiEntry target) {
                            return target.base;
                        }

                        public void write(TileEntityRegistry.GuiEntry target, ContainerGuiDefinition value) {
                            TileEntityRegistry.publishGui(target, value);
                        }
                    });

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
            if ("tileEntities".equals(type)) {
                set("get", new GetDefinition(this, false));
                set("getRequired", new GetDefinition(this, true));
                set("find", new FindDefinitions(this, 0));
                set("first", new FindDefinitions(this, 1));
                set("one", new FindDefinitions(this, 2));
            } else if ("containers".equals(type)) {
                set("get", new GetDefinition(this, false));
                set("getRequired", new GetDefinition(this, true));
                set("find", new FindDefinitions(this, 0));
                set("first", new FindDefinitions(this, 1));
                set("one", new FindDefinitions(this, 2));
            } else if ("containerGuis".equals(type)) {
                set("get", new GetGui(this, false));
                set("getRequired", new GetGui(this, true));
                set("find", new FindGuis(this, 0));
                set("first", new FindGuis(this, 1));
                set("one", new FindGuis(this, 2));
            }
        }
    }

    private static final class GetDefinition extends VarArgFunction {
        private final Registry registry;
        private final boolean required;

        private GetDefinition(Registry registry, boolean required) {
            this.registry = registry;
            this.required = required;
        }

        public Varargs invoke(Varargs args) {
            String name = args.arg(args.arg1() == registry ? 2 : 1).checkjstring().toLowerCase();
            LuaValue result;
            if ("tileEntities".equals(registry.type)) {
                TileEntityDefinition definition = TileEntityRegistry.getTileEntity(name);
                result = definition == null ? NIL : new TileEntityHandle(definition);
            } else {
                ContainerDefinition definition = TileEntityRegistry.getContainer(name);
                result = definition == null ? NIL : new ContainerHandle(definition);
            }
            if (result.isnil() && required) {
                throw new LuaError(("tileEntities".equals(registry.type) ? "Tile entity" : "Container")
                        + " was not found: " + name);
            }
            return result;
        }
    }

    private static final class FindDefinitions extends VarArgFunction {
        private final Registry registry;
        private final int mode;

        private FindDefinitions(Registry registry, int mode) {
            this.registry = registry;
            this.mode = mode;
        }

        public Varargs invoke(Varargs args) {
            LuaValue criteria = args.arg(args.arg1() == registry ? 2 : 1);
            if (criteria.isnil()) {
                criteria = new LuaTable();
            }
            if (!criteria.istable()) {
                throw new LuaError(("tileEntities".equals(registry.type) ? "Tile-entity" : "Container")
                        + " query must be a table.");
            }
            List<LuaValue> matches = new ArrayList<LuaValue>();
            if ("tileEntities".equals(registry.type)) {
                for (TileEntityDefinition definition : TileEntityRegistry.tileEntities()) {
                    TileEntityHandle reference = new TileEntityHandle(definition);
                    if (reference.matches(criteria)) {
                        matches.add(reference);
                    }
                }
            } else {
                for (ContainerDefinition definition : TileEntityRegistry.containers()) {
                    ContainerHandle reference = new ContainerHandle(definition);
                    if (reference.matches(criteria)) {
                        matches.add(reference);
                    }
                }
            }
            if (mode == 1) {
                return matches.isEmpty() ? NIL : matches.get(0);
            }
            if (mode == 2) {
                if (matches.isEmpty()) {
                    return NIL;
                }
                if (matches.size() != 1) {
                    throw new LuaError("Expected exactly one "
                            + ("tileEntities".equals(registry.type) ? "tile entity" : "container")
                            + ", found " + matches.size() + ".");
                }
                return matches.get(0);
            }
            return new LuaResultList(matches, (reference, definition, index) -> {
                if (reference instanceof TileEntityHandle) {
                    return ((TileEntityHandle) reference).override(definition);
                }
                return ((ContainerHandle) reference).override(definition);
            });
        }
    }

    private static final class GetGui extends VarArgFunction {
        private final Registry registry;
        private final boolean required;

        private GetGui(Registry registry, boolean required) {
            this.registry = registry;
            this.required = required;
        }

        public Varargs invoke(Varargs args) {
            String name = args.arg(args.arg1() == registry ? 2 : 1).checkjstring().toLowerCase();
            TileEntityRegistry.GuiEntry entry = TileEntityRegistry.getGuiEntry(name);
            if (entry == null && required) {
                throw new LuaError("Container GUI was not found: " + name);
            }
            return entry == null ? NIL : new GuiHandle(entry.base);
        }
    }

    private static final class FindGuis extends VarArgFunction {
        private final Registry registry;
        private final int mode;

        private FindGuis(Registry registry, int mode) {
            this.registry = registry;
            this.mode = mode;
        }

        public Varargs invoke(Varargs args) {
            LuaValue criteria = args.arg(args.arg1() == registry ? 2 : 1);
            if (criteria.isnil()) {
                criteria = new LuaTable();
            }
            if (!criteria.istable()) {
                throw new LuaError("Container GUI query must be a table.");
            }
            List<LuaValue> matches = new ArrayList<LuaValue>();
            for (TileEntityRegistry.GuiEntry entry : TileEntityRegistry.guiEntries()) {
                GuiHandle reference = new GuiHandle(entry.base);
                if (reference.matches(criteria)) {
                    matches.add(reference);
                }
            }
            if (mode == 1) {
                return matches.isEmpty() ? NIL : matches.get(0);
            }
            if (mode == 2) {
                if (matches.isEmpty()) {
                    return NIL;
                }
                if (matches.size() != 1) {
                    throw new LuaError("Expected exactly one container GUI, found " + matches.size() + ".");
                }
                return matches.get(0);
            }
            return new LuaResultList(matches, (reference, definition, index) ->
                    ((GuiHandle) reference).override(definition));
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
            boolean outputOnly = slot.get("outputOnly").toboolean();
            AssetKey acceptedFuelSet = slot.get("acceptsFuel").isnil()
                    ? null
                    : FuelsApi.requireSetKey(slot.get("acceptsFuel"), "container slot acceptsFuel");
            if (outputOnly && acceptedFuelSet != null) {
                throw new LuaError("A container slot cannot be outputOnly and accept fuel.");
            }
            slots.add(new ContainerDefinition.SlotDefinition(slot.get("name").optjstring(slotName),
                    slotIndex.intValue(), requiredInt(slot, "x"), requiredInt(slot, "y"), outputOnly,
                    acceptedFuelSet));
        }
        LuaValue player = requiredTable(def, "playerInventory");
        Map<String, ContainerDefinition.SessionField> session = ContainerControlParser.parseSession(def.get("session"));
        Map<String, betamoon.tileentity.ContainerControlDefinition> controls = ContainerControlParser.parseControls(
                def.get("controls"), tile, session);
        ContainerDefinition definition = new ContainerDefinition(name, owner, tile, slots, requiredInt(player, "x"),
                requiredInt(player, "y"), player.get("includeHotbar").optboolean(true), session, controls,
                callback(def.get("onClose"), "container.onClose"));
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
        ContainerGuiDefinition definition = parseGuiDefinition(def, owner, name, container);
        TileEntityRegistry.register(definition);
        return new GuiHandle(definition);
    }

    private static ContainerGuiDefinition parseGuiDefinition(LuaValue def, String owner, String name,
            ContainerDefinition container) {
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
            ContainerGuiParser.parseElements(elementDefs, container, elements, 0, 0, null);
        }
        ContainerGuiParser.validateBounds(elements, width, height);
        return new ContainerGuiDefinition(name, owner, container, width, height, title,
                inventoryLabel, background, layout.get("pauseGame").optboolean(false), elements);
    }

    public static final class TileEntityHandle extends LuaTable {
        public final TileEntityDefinition definition;
        private TileEntityHandle(TileEntityDefinition definition) {
            this.definition = definition;
            set("name", definition.name);
            set("override", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return override(args.arg(args.arg1() == TileEntityHandle.this ? 2 : 1));
                }
            });
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (key.isstring()) {
                String name = key.tojstring();
                if (name.equals("exists")) {
                    return valueOf(TileEntityRegistry.getTileEntity(definition.name) == definition);
                }
                if (name.equals("owner")) {
                    return valueOf(definition.owner);
                }
                if (name.equals("tickMode")) {
                    return valueOf(definition.randomTicks ? "random"
                            : definition.repeatTickDelay == 1 && definition.initialTickDelay == 1
                                    ? "continuous" : definition.repeatTickDelay > 0 ? "scheduled" : "none");
                }
                if (name.equals("initialTickDelay")) {
                    return valueOf(definition.initialTickDelay);
                }
                if (name.equals("repeatTickDelay")) {
                    return valueOf(definition.repeatTickDelay);
                }
                if (name.equals("randomTickChance")) {
                    return valueOf(definition.randomTickChance);
                }
            }
            return super.get(key);
        }

        private boolean matches(LuaValue criteria) {
            return (criteria.get("name").isnil()
                    || definition.name.equals(criteria.get("name").checkjstring().toLowerCase()))
                    && (criteria.get("owner").isnil()
                            || definition.owner.equals(criteria.get("owner").checkjstring()));
        }

        private LuaValue override(LuaValue value) {
            if (TileEntityRegistry.getTileEntity(definition.name) != definition) {
                throw new LuaError("Tile entity is no longer registered: " + definition.name);
            }
            OverrideInput input = overrideInput(value, definition.owner, "Tile-entity");
            List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
            if (!input.active) {
                return inactiveHandle(this);
            }
            LuaValue field = NIL;
            while (!(field = input.changes.next(field).arg1()).isnil()) {
                String name = field.checkjstring();
                if (isOverrideEnvelopeField(name)) {
                    continue;
                }
                LuaValue changed = input.changes.get(field);
                if (name.equals("onTick")) {
                    addTickRequests(requests, changed, input.priority);
                } else if (name.equals("onInventoryChanged")) {
                    requests.add(OverrideManager.request("tileEntity:" + definition.name, definition,
                            tileCallbackProperty(definition, name), LuaOverrideLayers.single(
                                    new LuaOverrideDefinition(name, changed)), input.priority));
                } else {
                    throw new LuaError("Property '" + name + "' cannot be overridden on a tile entity.");
                }
            }
            return removableHandle(this, OverrideManager.applyAll(requests));
        }

        private void addTickRequests(List<OverrideManager.Request<?, ?>> requests, LuaValue changed, int priority) {
            LuaValue action = changed;
            if (changed.istable()) {
                betamoon.luaapi.utils.LuaDeclarationValues.fields(changed, "tileEntity.onTick",
                        "action", "mode", "schedule", "chance");
                action = changed.get("action");
            } else if (!changed.isfunction()) {
                throw new LuaError("tileEntity.onTick must be a function or table.");
            }
            if (!action.isnil()) {
                requests.add(OverrideManager.request("tileEntity:" + definition.name, definition,
                        tileCallbackProperty(definition, "onTick"), LuaOverrideLayers.single(
                                new LuaOverrideDefinition("onTick", action)), priority));
            }
            if (!changed.istable() || changed.get("mode").isnil()) {
                return;
            }
            String mode = changed.get("mode").checkjstring().toLowerCase();
            int initial;
            int repeat;
            boolean random;
            double chance = 0.05D;
            if (mode.equals("continuous") || mode.equals("default")) {
                initial = 1;
                repeat = 1;
                random = false;
            } else if (mode.equals("scheduled")) {
                LuaValue schedule = changed.get("schedule");
                if (!schedule.istable()) {
                    throw new LuaError("tileEntity.onTick.schedule must be a table.");
                }
                betamoon.luaapi.utils.LuaDeclarationValues.fields(schedule, "tileEntity.onTick.schedule",
                        "delay", "repeatEvery");
                initial = schedule.get("delay").checkint();
                repeat = schedule.get("repeatEvery").optint(0);
                if (initial <= 0 || repeat < 0) {
                    throw new LuaError("tileEntity.onTick schedule values must be positive.");
                }
                random = false;
            } else if (mode.equals("random")) {
                initial = 0;
                repeat = 0;
                random = true;
                chance = changed.get("chance").optdouble(0.05D);
                if (chance < 0.0D || chance > 1.0D || Double.isNaN(chance) || Double.isInfinite(chance)) {
                    throw new LuaError("tileEntity.onTick.chance must be between 0 and 1.");
                }
            } else {
                throw new LuaError("tileEntity.onTick.mode must be continuous, random, or scheduled.");
            }
            requests.add(OverrideManager.request("tileEntity:" + definition.name, definition,
                    tileIntegerProperty("initialTickDelay"), Integer.valueOf(initial), priority));
            requests.add(OverrideManager.request("tileEntity:" + definition.name, definition,
                    tileIntegerProperty("repeatTickDelay"), Integer.valueOf(repeat), priority));
            requests.add(OverrideManager.request("tileEntity:" + definition.name, definition,
                    tileBooleanProperty("randomTicks"), Boolean.valueOf(random), priority));
            requests.add(OverrideManager.request("tileEntity:" + definition.name, definition,
                    tileDoubleProperty("randomTickChance"), Double.valueOf(chance), priority));
        }
    }

    public static final class ContainerHandle extends LuaTable {
        public final ContainerDefinition definition;
        private ContainerHandle(ContainerDefinition definition) {
            this.definition = definition;
            set("name", definition.name);
            set("override", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return override(args.arg(args.arg1() == ContainerHandle.this ? 2 : 1));
                }
            });
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (key.isstring()) {
                String name = key.tojstring();
                if (name.equals("exists")) {
                    return valueOf(TileEntityRegistry.getContainer(definition.name) == definition);
                }
                if (name.equals("owner")) {
                    return valueOf(definition.owner);
                }
                if (name.equals("tileEntity")) {
                    return valueOf(definition.tileEntity.name);
                }
            }
            return super.get(key);
        }

        private boolean matches(LuaValue criteria) {
            return (criteria.get("name").isnil()
                    || definition.name.equals(criteria.get("name").checkjstring().toLowerCase()))
                    && (criteria.get("owner").isnil()
                            || definition.owner.equals(criteria.get("owner").checkjstring()))
                    && (criteria.get("tileEntity").isnil()
                            || definition.tileEntity.name.equals(criteria.get("tileEntity").checkjstring().toLowerCase()));
        }

        private LuaValue override(LuaValue value) {
            if (TileEntityRegistry.getContainer(definition.name) != definition) {
                throw new LuaError("Container is no longer registered: " + definition.name);
            }
            OverrideInput input = overrideInput(value, definition.owner, "Container");
            if (!input.active) {
                return inactiveHandle(this);
            }
            List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
            LuaValue field = NIL;
            while (!(field = input.changes.next(field).arg1()).isnil()) {
                String name = field.checkjstring();
                if (isOverrideEnvelopeField(name)) {
                    continue;
                }
                LuaValue changed = input.changes.get(field);
                if (name.equals("onClose")) {
                    requests.add(OverrideManager.request("container:" + definition.name, definition,
                            containerCloseProperty(definition), LuaOverrideLayers.single(
                                    new LuaOverrideDefinition(name, changed)), input.priority));
                } else if (name.equals("controls")) {
                    addControlRequests(requests, changed, input.priority);
                } else {
                    throw new LuaError("Property '" + name + "' cannot be overridden on a container.");
                }
            }
            return removableHandle(this, OverrideManager.applyAll(requests));
        }

        private void addControlRequests(List<OverrideManager.Request<?, ?>> requests, LuaValue changes, int priority) {
            if (!changes.istable()) {
                throw new LuaError("container.controls must be a table.");
            }
            LuaValue controlKey = NIL;
            while (!(controlKey = changes.next(controlKey).arg1()).isnil()) {
                String key = controlKey.checkjstring();
                ContainerControlDefinition control = definition.controls.get(key);
                if (control == null) {
                    throw new LuaError("Container has no control named '" + key + "'.");
                }
                LuaValue callbacks = changes.get(controlKey);
                if (!callbacks.istable()) {
                    throw new LuaError("container.controls." + key + " must be a table.");
                }
                betamoon.luaapi.utils.LuaDeclarationValues.fields(callbacks, "container.controls." + key,
                        "beforeChange", "onActivate", "onChange", "onEdit", "onCommit", "onInput");
                LuaValue callbackKey = NIL;
                while (!(callbackKey = callbacks.next(callbackKey).arg1()).isnil()) {
                    String callbackName = callbackKey.checkjstring();
                    requests.add(OverrideManager.request("container:" + definition.name + "/control/" + key,
                            control, controlCallbackProperty(control, callbackName), LuaOverrideLayers.single(
                                    new LuaOverrideDefinition(callbackName, callbacks.get(callbackKey))), priority));
                }
            }
        }
    }
    public static final class GuiHandle extends LuaTable {
        public final ContainerGuiDefinition definition;
        private GuiHandle(ContainerGuiDefinition definition) {
            this.definition = definition;
            set("name", definition.name);
            set("override", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return override(args.arg(args.arg1() == GuiHandle.this ? 2 : 1));
                }
            });
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (!key.isstring()) {
                return super.get(key);
            }
            TileEntityRegistry.GuiEntry entry = TileEntityRegistry.getGuiEntry(definition.name);
            String name = key.tojstring();
            if (name.equals("exists")) {
                return valueOf(entry != null);
            }
            if (entry == null) {
                return super.get(key);
            }
            ContainerGuiDefinition current = entry.effective;
            if (name.equals("owner")) {
                return valueOf(current.owner);
            }
            if (name.equals("container")) {
                return valueOf(current.container.name);
            }
            if (name.equals("width")) {
                return valueOf(current.width);
            }
            if (name.equals("height")) {
                return valueOf(current.height);
            }
            if (name.equals("pauseGame")) {
                return valueOf(current.pauseGame);
            }
            if (name.equals("elementCount")) {
                return valueOf(current.elements.size());
            }
            return super.get(key);
        }

        private boolean matches(LuaValue criteria) {
            TileEntityRegistry.GuiEntry entry = TileEntityRegistry.getGuiEntry(definition.name);
            if (entry == null) {
                return false;
            }
            if (!criteria.get("name").isnil()
                    && !definition.name.equals(criteria.get("name").checkjstring().toLowerCase())) {
                return false;
            }
            if (!criteria.get("owner").isnil()
                    && !entry.effective.owner.equals(criteria.get("owner").checkjstring())) {
                return false;
            }
            return criteria.get("container").isnil()
                    || entry.effective.container.name.equals(criteria.get("container").checkjstring());
        }

        private LuaValue override(LuaValue value) {
            final TileEntityRegistry.GuiEntry entry = TileEntityRegistry.getGuiEntry(definition.name);
            if (entry == null) {
                throw new LuaError("Container GUI is no longer registered: " + definition.name);
            }
            if (!value.istable()) {
                throw new LuaError("Container GUI override expects a table.");
            }
            LuaValue when = value.get("when");
            if (!when.isnil() && !when.istable()) {
                throw new LuaError("Container GUI override when must be a table.");
            }
            final LuaTable handle = new LuaTable();
            handle.set("target", this);
            if (!when.isnil() && !when.get("owner").isnil()
                    && !entry.base.owner.equals(when.get("owner").checkjstring())) {
                handle.set("active", FALSE);
                handle.set("reason", "target owner did not match");
                return handle;
            }
            LuaValue changes = value.get("changes");
            if (changes.isnil()) {
                changes = value;
            }
            if (!changes.istable()) {
                throw new LuaError("Container GUI override changes must be a table.");
            }
            ContainerGuiDefinition replacement = parseGuiDefinition(changes, entry.base.owner, entry.base.name,
                    entry.base.container);
            final OverrideManager.Layer<TileEntityRegistry.GuiEntry, ContainerGuiDefinition> layer =
                    OverrideManager.apply("containerGui:" + entry.base.name, entry, GUI_DEFINITION, replacement,
                            value.get("priority").optint(0));
            handle.set("active", TRUE);
            handle.set("remove", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    if (handle.get("active").toboolean()) {
                        layer.remove();
                        handle.set("active", FALSE);
                    }
                    return NIL;
                }
            });
            return handle;
        }
    }

    private static OverrideManager.Property<TileEntityDefinition, LuaOverrideLayers<LuaOverrideDefinition>>
            tileCallbackProperty(TileEntityDefinition definition, String name) {
        LuaValue original = name.equals("onTick") ? definition.tickAction : definition.inventoryChangedAction;
        return LuaDefinitionCallbackLayers.property(name, original, (target, callback) -> {
            if (name.equals("onTick")) {
                target.tickAction = callback;
            } else {
                target.inventoryChangedAction = callback;
            }
        });
    }

    private static OverrideManager.Property<TileEntityDefinition, Integer> tileIntegerProperty(final String name) {
        return new OverrideManager.Property<TileEntityDefinition, Integer>(name,
                new OverrideManager.PropertyAdapter<TileEntityDefinition, Integer>() {
                    public Integer read(TileEntityDefinition target) {
                        return Integer.valueOf(name.equals("initialTickDelay")
                                ? target.initialTickDelay : target.repeatTickDelay);
                    }

                    public void write(TileEntityDefinition target, Integer value) {
                        if (name.equals("initialTickDelay")) {
                            target.initialTickDelay = value.intValue();
                        } else {
                            target.repeatTickDelay = value.intValue();
                        }
                        target.tickRevision++;
                    }
                });
    }

    private static OverrideManager.Property<TileEntityDefinition, Boolean> tileBooleanProperty(final String name) {
        return new OverrideManager.Property<TileEntityDefinition, Boolean>(name,
                new OverrideManager.PropertyAdapter<TileEntityDefinition, Boolean>() {
                    public Boolean read(TileEntityDefinition target) {
                        return Boolean.valueOf(target.randomTicks);
                    }

                    public void write(TileEntityDefinition target, Boolean value) {
                        target.randomTicks = value.booleanValue();
                        target.tickRevision++;
                    }
                });
    }

    private static OverrideManager.Property<TileEntityDefinition, Double> tileDoubleProperty(final String name) {
        return new OverrideManager.Property<TileEntityDefinition, Double>(name,
                new OverrideManager.PropertyAdapter<TileEntityDefinition, Double>() {
                    public Double read(TileEntityDefinition target) {
                        return Double.valueOf(target.randomTickChance);
                    }

                    public void write(TileEntityDefinition target, Double value) {
                        target.randomTickChance = value.doubleValue();
                        target.tickRevision++;
                    }
                });
    }

    private static OverrideManager.Property<ContainerDefinition, LuaOverrideLayers<LuaOverrideDefinition>>
            containerCloseProperty(ContainerDefinition definition) {
        return LuaDefinitionCallbackLayers.property("onClose", definition.closeAction,
                (target, callback) -> target.closeAction = callback);
    }

    private static OverrideManager.Property<ContainerControlDefinition, LuaOverrideLayers<LuaOverrideDefinition>>
            controlCallbackProperty(ContainerControlDefinition control, String name) {
        LuaValue original = controlCallback(control, name);
        return LuaDefinitionCallbackLayers.property(name, original,
                (target, callback) -> setControlCallback(target, name, callback));
    }

    private static LuaValue controlCallback(ContainerControlDefinition control, String name) {
        if (name.equals("beforeChange")) {
            return control.beforeChange;
        }
        if (name.equals("onActivate")) {
            return control.onActivate;
        }
        if (name.equals("onChange")) {
            return control.onChange;
        }
        if (name.equals("onEdit")) {
            return control.onEdit;
        }
        if (name.equals("onCommit")) {
            return control.onCommit;
        }
        return control.onInput;
    }

    private static void setControlCallback(ContainerControlDefinition control, String name, LuaValue callback) {
        if (name.equals("beforeChange")) {
            control.beforeChange = callback;
        } else if (name.equals("onActivate")) {
            control.onActivate = callback;
        } else if (name.equals("onChange")) {
            control.onChange = callback;
        } else if (name.equals("onEdit")) {
            control.onEdit = callback;
        } else if (name.equals("onCommit")) {
            control.onCommit = callback;
        } else {
            control.onInput = callback;
        }
    }

    private static final class OverrideInput {
        private final LuaValue changes;
        private final int priority;
        private final boolean active;

        private OverrideInput(LuaValue changes, int priority, boolean active) {
            this.changes = changes;
            this.priority = priority;
            this.active = active;
        }
    }

    private static OverrideInput overrideInput(LuaValue value, String owner, String kind) {
        if (!value.istable()) {
            throw new LuaError(kind + " override expects a table.");
        }
        LuaValue when = value.get("when");
        if (!when.isnil() && !when.istable()) {
            throw new LuaError(kind + " override when must be a table.");
        }
        boolean active = when.isnil() || when.get("owner").isnil()
                || owner.equals(when.get("owner").checkjstring());
        LuaValue changes = value.get("changes");
        if (changes.isnil()) {
            changes = value;
        }
        if (!changes.istable()) {
            throw new LuaError(kind + " override changes must be a table.");
        }
        return new OverrideInput(changes, value.get("priority").optint(0), active);
    }

    private static boolean isOverrideEnvelopeField(String name) {
        return name.equals("when") || name.equals("changes") || name.equals("priority") || name.equals("target");
    }

    private static LuaValue inactiveHandle(LuaValue target) {
        LuaTable handle = new LuaTable();
        handle.set("target", target);
        handle.set("active", LuaValue.FALSE);
        handle.set("reason", "target owner did not match");
        return handle;
    }

    private static LuaValue removableHandle(LuaValue target, final List<OverrideManager.Layer<?, ?>> layers) {
        final LuaTable handle = new LuaTable();
        handle.set("target", target);
        handle.set("active", LuaValue.TRUE);
        handle.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                if (handle.get("active").toboolean()) {
                    for (int index = layers.size() - 1; index >= 0; index--) {
                        layers.get(index).remove();
                    }
                    handle.set("active", FALSE);
                }
                return LuaValue.NIL;
            }
        });
        return handle;
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

    private static LuaValue callback(LuaValue value, String path) {
        if (!value.isnil() && !value.isfunction()) {
            throw new LuaError(path + " must be a function.");
        }
        return value;
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
