package betamoon.luaapi.tileentity;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityDefinition;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.resources.LuaTextureResources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs startup-only tile entity, container, and container GUI registries. */
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
        private Registry(String type) { this.type = type; set("add", new Add(this)); }
    }

    private static final class Add extends VarArgFunction {
        private final Registry registry;
        private Add(Registry registry) { this.registry = registry; }
        public Varargs invoke(Varargs args) {
            LuaValue def = args.arg1() == registry ? args.arg(2) : args.arg1();
            if (!def.istable()) throw new LuaError(registry.type + ":add expects a definition table.");
            if ("tileEntities".equals(registry.type)) return addTileEntity(def);
            if ("containers".equals(registry.type)) return addContainer(def);
            return addGui(def);
        }
    }

    private static TileEntityHandle addTileEntity(LuaValue def) {
        String owner = requireOwner();
        String name = qualifiedName(owner, requiredString(def, "name"));
        TileEntityDefinition existing = TileEntityRegistry.getTileEntity(name);
        if (existing != null) return new TileEntityHandle(existing);

        LuaValue inventory = requiredTable(def, "inventory");
        LuaValue slotDefs = requiredTable(inventory, "slots");
        Map slots = new LinkedHashMap();
        Map slotTables = new HashMap();
        List slotNames = new ArrayList();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = slotDefs.next(key); key = next.arg1(); if (key.isnil()) break;
            if (!key.isstring() || !next.arg(2).istable()) {
                throw new LuaError("inventory.slots must map names to definition tables.");
            }
            slotNames.add(key.checkjstring());
            slotTables.put(key.checkjstring(), next.arg(2));
        }
        if (slotNames.isEmpty()) throw new LuaError("A tile entity inventory requires at least one slot.");
        Collections.sort(slotNames);
        java.util.Set usedIndexes = new HashSet();
        for (int i = 0; i < slotNames.size(); i++) {
            String slotName = (String) slotNames.get(i);
            LuaValue indexValue = ((LuaValue) slotTables.get(slotName)).get("index");
            if (indexValue.isnil()) continue;
            int slotIndex = indexValue.checkint();
            if (slotIndex < 0 || slotIndex >= slotNames.size()
                || !usedIndexes.add(Integer.valueOf(slotIndex))) {
                throw new LuaError("Inventory slot '" + slotName + "' has an invalid or duplicate index.");
            }
            slots.put(slotName, Integer.valueOf(slotIndex));
        }
        for (int i = 0; i < slotNames.size(); i++) {
            String slotName = (String) slotNames.get(i);
            LuaValue indexValue = ((LuaValue) slotTables.get(slotName)).get("index");
            if (!indexValue.isnil()) continue;
            int slotIndex = nextFreeIndex(usedIndexes);
            usedIndexes.add(Integer.valueOf(slotIndex));
            slots.put(slotName, Integer.valueOf(slotIndex));
        }

        Map fields = new LinkedHashMap();
        LuaValue data = def.get("data");
        if (!data.isnil()) {
            if (!data.istable()) throw new LuaError("tile entity data must be a table.");
            key = LuaValue.NIL;
            while (true) {
                Varargs next = data.next(key); key = next.arg1(); if (key.isnil()) break;
                String fieldName = key.checkjstring();
                LuaValue fieldDef = next.arg(2);
                if (!fieldDef.istable()) throw new LuaError("Data field '" + fieldName + "' must be a table.");
                String type = requiredString(fieldDef, "type").toLowerCase();
                LuaValue defaultValue = fieldDef.get("default");
                Object value = convertDefault(type, defaultValue);
                boolean sync = fieldDef.get("sync").toboolean();
                if (sync && !("integer".equals(type) || "boolean".equals(type))) {
                    throw new LuaError("Only integer and boolean data fields can use sync = true.");
                }
                fields.put(fieldName, new TileEntityDefinition.Field(fieldName, type, value, sync));
            }
        }

        LuaValue tick = def.get("onTick");
        LuaValue action = LuaValue.NIL;
        int initialDelay = 0;
        int repeatDelay = 0;
        boolean randomTicks = false;
        double randomChance = 0.05D;
        if (!tick.isnil()) {
            if (!tick.istable()) throw new LuaError("onTick must be a table.");
            action = required(tick, "action");
            if (!action.isfunction()) throw new LuaError("onTick.action must be a function.");
            String mode = requiredString(tick, "mode").toLowerCase();
            if ("continuous".equals(mode) || "default".equals(mode)) {
                initialDelay = repeatDelay = 1;
            }
            else if ("scheduled".equals(mode)) {
                LuaValue schedule = requiredTable(tick, "schedule");
                initialDelay = requiredInt(schedule, "delay");
                repeatDelay = schedule.get("repeatEvery").optint(0);
                if (initialDelay <= 0 || repeatDelay < 0) throw new LuaError("onTick schedule values must be positive.");
            } else if ("random".equals(mode)) {
                randomTicks = true;
                randomChance = tick.get("chance").optdouble(0.05D);
                if (randomChance < 0.0D || randomChance > 1.0D
                    || Double.isNaN(randomChance) || Double.isInfinite(randomChance)) {
                    throw new LuaError("onTick.chance must be between 0 and 1.");
                }
            } else throw new LuaError("onTick.mode must be 'continuous', 'random', or 'scheduled'.");
        }
        LuaValue inventoryChanged = callbackAction(def.get("onInventoryChanged"), "onInventoryChanged");
        TileEntityDefinition definition = new TileEntityDefinition(name, owner,
            inventory.get("name").optjstring(name), slots, fields, action, inventoryChanged,
            initialDelay, repeatDelay, randomTicks, randomChance);
        TileEntityRegistry.register(definition);
        return new TileEntityHandle(definition);
    }

    private static ContainerHandle addContainer(LuaValue def) {
        String owner = requireOwner();
        String name = qualifiedName(owner, requiredString(def, "name"));
        ContainerDefinition existing = TileEntityRegistry.getContainer(name);
        if (existing != null) return new ContainerHandle(existing);
        TileEntityDefinition tile = tileHandle(required(def, "tileEntity")).definition;
        requireSameOwner(owner, tile.owner, "tile entity");
        List slots = new ArrayList();
        java.util.Set visibleSlots = new HashSet();
        LuaValue slotDefs = requiredTable(def, "slots");
        for (int i = 1; i <= slotDefs.length(); i++) {
            LuaValue slot = slotDefs.get(i);
            if (!slot.istable()) throw new LuaError("Container slots must be definition tables.");
            String slotName = requiredString(slot, "slot");
            Integer slotIndex = (Integer) tile.slots.get(slotName);
            if (slotIndex == null) throw new LuaError("Unknown tile entity slot: " + slotName);
            if (!visibleSlots.add(slotIndex)) throw new LuaError("Container lists tile slot '" + slotName + "' more than once.");
            slots.add(new ContainerDefinition.SlotDefinition(slot.get("name").optjstring(slotName),
                slotIndex.intValue(), requiredInt(slot, "x"), requiredInt(slot, "y"),
                slot.get("outputOnly").toboolean()));
        }
        LuaValue player = requiredTable(def, "playerInventory");
        ContainerDefinition definition = new ContainerDefinition(name, owner, tile, slots,
            requiredInt(player, "x"), requiredInt(player, "y"),
            player.get("includeHotbar").optboolean(true));
        TileEntityRegistry.register(definition);
        return new ContainerHandle(definition);
    }

    private static GuiHandle addGui(LuaValue def) {
        String owner = requireOwner();
        String name = qualifiedName(owner, requiredString(def, "name"));
        ContainerGuiDefinition existing = TileEntityRegistry.getGui(name);
        if (existing != null) return new GuiHandle(existing);
        ContainerDefinition container = containerHandle(required(def, "container")).definition;
        requireSameOwner(owner, container.owner, "container");

        LuaValue layout = def.get("layout");
        if (layout.isnil()) layout = new LuaTable();
        if (!layout.istable()) throw new LuaError("layout must be a table.");
        String preset = layout.get("preset").optjstring("minecraft:container").toLowerCase();
        int rows = layout.get("rows").optint(3);
        if (rows < 1 || rows > 6) throw new LuaError("layout.rows must be between 1 and 6.");
        LuaValue backgroundValue = def.get("background");
        if (backgroundValue.isnil()) backgroundValue = new LuaTable();
        if (!backgroundValue.istable()) throw new LuaError("background must be a table.");
        ContainerGuiDefinition.Background background = ContainerGuiParser.parseBackground(backgroundValue, preset, rows);
        int defaultHeight = "minecraft:chest".equals(preset) ? 114 + rows * 18 : 166;
        int width = layout.get("width").optint(background.texture == null ? 176 : background.texture.width);
        int height = layout.get("height").optint(background.texture == null ? defaultHeight
            : "chest".equals(background.style) ? defaultHeight : background.texture.height);
        if (width <= 0 || width > 256 || height <= 0 || height > 256) {
            throw new LuaError("Container GUI dimensions must be between 1 and 256 pixels.");
        }
        ContainerGuiDefinition.Label title = ContainerGuiParser.parseLabel(layout.get("title"),
            container.tileEntity.inventoryName, 8, 6, width - 16);
        ContainerGuiDefinition.Label inventoryLabel = ContainerGuiParser.parseLabel(layout.get("playerInventoryLabel"),
            "Inventory", 8, height - 94, width - 16);
        List elements = new ArrayList();
        LuaValue elementDefs = def.get("elements");
        if (!elementDefs.isnil()) ContainerGuiParser.parseElements(elementDefs, container.tileEntity, elements, 0, 0, null);
        ContainerGuiParser.validateBounds(elements, width, height);
        ContainerGuiDefinition definition = new ContainerGuiDefinition(name, owner, container, width, height,
            title, inventoryLabel, background, layout.get("pauseGame").optboolean(false), elements);
        TileEntityRegistry.register(definition);
        return new GuiHandle(definition);
    }

    public static final class TileEntityHandle extends LuaTable {
        public final TileEntityDefinition definition;
        private TileEntityHandle(TileEntityDefinition definition) { this.definition = definition; set("name", definition.name); }
    }
    public static final class ContainerHandle extends LuaTable {
        public final ContainerDefinition definition;
        private ContainerHandle(ContainerDefinition definition) { this.definition = definition; set("name", definition.name); }
    }
    public static final class GuiHandle extends LuaTable {
        public final ContainerGuiDefinition definition;
        private GuiHandle(ContainerGuiDefinition definition) { this.definition = definition; set("name", definition.name); }
    }

    public static TileEntityHandle tileHandle(LuaValue value) {
        if (!(value instanceof TileEntityHandle)) throw new LuaError("Expected a tile entity handle.");
        return (TileEntityHandle) value;
    }
    public static ContainerHandle containerHandle(LuaValue value) {
        if (!(value instanceof ContainerHandle)) throw new LuaError("Expected a container handle.");
        return (ContainerHandle) value;
    }
    public static GuiHandle guiHandle(LuaValue value) {
        if (!(value instanceof GuiHandle)) throw new LuaError("Expected a container GUI handle.");
        return (GuiHandle) value;
    }

    private static String requireOwner() {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) throw new LuaError("Structural content must be registered from modInit.");
        return owner;
    }
    private static String qualifiedName(String owner, String name) {
        if (name.indexOf(':') >= 0) return name.toLowerCase();
        int dot = owner.lastIndexOf('.');
        String namespace = (dot > 0 ? owner.substring(0, dot) : owner).replaceAll("[^A-Za-z0-9_]", "_").toLowerCase();
        return namespace + ":" + name.toLowerCase();
    }
    private static void requireSameOwner(String owner, String referencedOwner, String kind) {
        if (!owner.equals(referencedOwner)) throw new LuaError("A structural " + kind + " must be declared by the same script.");
    }
    private static Object convertDefault(String type, LuaValue value) {
        if ("integer".equals(type)) return Integer.valueOf(value.optint(0));
        if ("number".equals(type)) return Double.valueOf(value.optdouble(0.0D));
        if ("boolean".equals(type)) return Boolean.valueOf(value.optboolean(false));
        if ("string".equals(type)) return value.optjstring("");
        throw new LuaError("Unsupported tile entity data type: " + type);
    }
    private static int nextFreeIndex(java.util.Set used) {
        int index = 0; while (used.contains(Integer.valueOf(index))) index++; return index;
    }
    private static LuaValue callbackAction(LuaValue definition, String name) {
        if (definition.isnil()) return LuaValue.NIL;
        if (!definition.istable()) throw new LuaError(name + " must be a table.");
        LuaValue action = required(definition, "action");
        if (!action.isfunction()) throw new LuaError(name + ".action must be a function.");
        return action;
    }
    private static LuaValue required(LuaValue table, String key) {
        LuaValue value = table.get(key); if (value.isnil()) throw new LuaError("Definition requires '" + key + "'."); return value;
    }
    private static LuaValue requiredTable(LuaValue table, String key) {
        LuaValue value = required(table, key); if (!value.istable()) throw new LuaError(key + " must be a table."); return value;
    }
    private static String requiredString(LuaValue table, String key) { return required(table, key).checkjstring(); }
    private static int requiredInt(LuaValue table, String key) { return required(table, key).checkint(); }
}
