package betamoon.luaapi.resource;

import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.block.BlockTickRegistry;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.resources.EnumTexAtlas;
import betamoon.query.QueryEntries;
import betamoon.query.QueryEntry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemFood;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.ItemPickaxe;
import net.minecraft.src.ItemAxe;
import net.minecraft.src.ItemSpade;
import net.minecraft.src.ItemHoe;
import net.minecraft.src.ItemSword;
import net.minecraft.src.ModLoader;
import net.minecraft.src.StatCollector;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static org.luaj.vm2.LuaValue.*;

/** Installs the concise registry, reference, query, and override APIs. */
public final class ResourceApi {
    private ResourceApi() {
    }

    /** Adds the v2 resource registries while retaining all legacy entry points. */
    public static void attach(LuaTable module, LuaTable backend) {
        module.set("blocks", new Registry(backend, true, null));
        module.set("items", new Registry(backend, false, null));
        module.set("tools", new Registry(backend, false, "tool"));
        module.set("armor", new Registry(backend, false, "armor"));
        module.set("stack", new StackFunction());
        module.set("overrides", new OverrideService());
    }

    /** Shared block/item registry with identical get/find/first/one semantics. */
    private static final class Registry extends LuaTable {
        private final LuaTable root;
        private final boolean blocks;
        private final String filterType;

        private Registry(LuaTable root, boolean blocks, String filterType) {
            this.root = root;
            this.blocks = blocks;
            this.filterType = filterType;
            set("get", new Get(this, false));
            set("require", new Get(this, true));
            set("find", new Find(this, 0));
            set("first", new Find(this, 1));
            set("one", new Find(this, 2));
            set("add", new Add(this));
        }

        private LuaValue reference(int id, int damage) {
            if (blocks) {
                if (id < 0 || id >= Block.blocksList.length || Block.blocksList[id] == null) return NIL;
                return new BlockReference(Block.blocksList[id], damage);
            }
            if (id < 0 || id >= Item.itemsList.length || Item.itemsList[id] == null) return NIL;
            return new ItemReference(Item.itemsList[id], damage);
        }

        private LuaValue reference(String key) {
            String expected = key.indexOf(':') >= 0 ? key.substring(key.indexOf(':') + 1) : key;
            int length = blocks ? Block.blocksList.length : Item.itemsList.length;
            for (int id = 0; id < length; id++) {
                LuaValue reference = reference(id, 0);
                if (reference.isnil()) continue;
                String name = reference.get("name").optjstring("");
                String bare = name.startsWith("tile.") || name.startsWith("item.") ? name.substring(5) : name;
                if (key.equals(name) || expected.equals(name) || expected.equals(bare)) return reference;
            }
            return NIL;
        }

        private List find(LuaValue criteria) {
            List values = new ArrayList();
            List entries = blocks ? QueryEntries.buildBlockEntries() : QueryEntries.buildItemEntries();
            for (int i = 0; i < entries.size(); i++) {
                QueryEntry entry = (QueryEntry) entries.get(i);
                LuaValue ref = reference(entry.id, entry.damage);
                if (!ref.isnil() && (filterType == null || filterType.equals(ref.get("category").tojstring()))
                    && matches((ResourceReference) ref, criteria)) values.add(ref);
            }
            return values;
        }
    }

    private static final class Get extends VarArgFunction {
        private final Registry registry;
        private final boolean required;
        private Get(Registry registry, boolean required) { this.registry = registry; this.required = required; }
        public Varargs invoke(Varargs args) {
            LuaValue value = argument(args, registry, 1);
            int id = -1;
            LuaValue result;
            // LuaJ numbers are string-coercible, so numeric dispatch must come first.
            if (value.isnumber()) {
                id = value.istable() ? resolveReference(value).id : value.checkint();
                result = registry.reference(id, 0);
            } else if (value.isstring()) {
                result = registry.reference(value.checkjstring());
            } else {
                id = resolveReference(value).id;
                result = registry.reference(id, 0);
            }
            if (required && result.isnil()) {
                throw new LuaError((registry.blocks ? "Block" : "Item") + " '" + value.tojstring() + "' is not registered.");
            }
            return result;
        }
    }

    /** mode: 0=list, 1=first, 2=exactly one. */
    private static final class Find extends VarArgFunction {
        private final Registry registry;
        private final int mode;
        private Find(Registry registry, int mode) { this.registry = registry; this.mode = mode; }
        public Varargs invoke(Varargs args) {
            LuaValue criteria = argument(args, registry, 1);
            if (criteria.isnil()) criteria = new LuaTable();
            if (!criteria.istable()) throw new LuaError("find criteria must be a table.");
            List values = registry.find(criteria);
            if (mode == 1) return values.isEmpty() ? NIL : (LuaValue) values.get(0);
            if (mode == 2) {
                if (values.isEmpty()) return NIL;
                if (values.size() != 1) throw new LuaError("Expected exactly one result, found " + values.size() + ".");
                return (LuaValue) values.get(0);
            }
            return new LuaResultList(values, new LuaResultList.BulkOverride() {
                public LuaValue apply(LuaValue reference, LuaValue definition, int index) {
                    return ((ResourceReference) reference).applyOverride(definition);
                }
            });
        }
    }

    /** Declarative creation adapter backed by the existing, battle-tested builders. */
    private static final class Add extends VarArgFunction {
        private final Registry registry;
        private Add(Registry registry) { this.registry = registry; }
        public Varargs invoke(Varargs args) {
            LuaValue definition = argument(args, registry, 1);
            if (!definition.istable()) throw new LuaError("add expects a resource definition table.");
            if ("armor".equals(registry.filterType) && definition.get("type").isnil()) {
                definition.set("type", "armor");
            }
            if ("tool".equals(registry.filterType) && definition.get("type").isnil()) {
                throw new LuaError("Tool definition requires type = 'pickaxe', 'axe', 'shovel', 'hoe', or 'sword'.");
            }
            return registry.blocks ? addBlock(registry.root, definition) : addItem(registry.root, definition);
        }
    }

    /** Base reference exposing Lua properties and owned override behavior. */
    private abstract static class ResourceReference extends LuaTable {
        protected final int id;
        protected final int damage;
        protected final Object target;
        protected final String namespace;

        private ResourceReference(String namespace, int id, int damage, Object target) {
            this.namespace = namespace;
            this.id = id;
            this.damage = damage;
            this.target = target;
            set("id", valueOf(id));
            set("damage", valueOf(damage));
            String owner = LuaContentRegistry.findOwner(target);
            // Vanilla names are obfuscated in production, so untracked registry entries
            // must be treated as Minecraft-owned instead of inspecting the class name.
            String effectiveOwner = owner == null ? "minecraft" : owner;
            set("owner", valueOf(effectiveOwner));
            set("isVanilla", valueOf("minecraft".equals(effectiveOwner)));
            set("isBetaMoon", valueOf(owner != null));
            set("exists", TRUE);
            set("override", new ApplyOverride(this));
        }

        protected abstract OverrideManager.PropertyAdapter adapter(String property);
        protected abstract Object convert(String property, LuaValue value);

        private LuaValue applyOverride(LuaValue definition) {
            if (!definition.istable()) throw new LuaError("override expects a definition table.");
            LuaValue changes = definition.get("changes");
            if (changes.isnil()) changes = definition;
            if (!changes.istable()) throw new LuaError("override changes must be a table.");
            LuaValue when = definition.get("when");
            String inactiveReason = checkConditions(this, when);
            LuaTable handle = new LuaTable();
            handle.set("target", this);
            handle.set("active", valueOf(inactiveReason == null));
            if (inactiveReason != null) {
                handle.set("reason", valueOf(inactiveReason));
                return handle;
            }
            List layers = new ArrayList();
            int priority = definition.get("priority").optint(0);
            LuaValue key = NIL;
            while (true) {
                Varargs next = changes.next(key);
                key = next.arg1();
                if (key.isnil()) break;
                String property = key.checkjstring();
                if (property.equals("when") || property.equals("key") || property.equals("priority")) continue;
                OverrideManager.PropertyAdapter adapter = adapter(property);
                if (adapter == null) {
                    throw new LuaError("Property '" + property + "' cannot be overridden on " + namespace + " " + id + ".");
                }
                Object converted = convert(property, next.arg(2));
                layers.add(OverrideManager.apply(namespace + ":" + id, target, property, converted, priority, adapter));
                // Keep the reference used for declaration useful immediately after the patch.
                set(property, next.arg(2));
            }
            handle.set("remove", new RemoveOverride(layers, handle));
            return handle;
        }
    }

    private static final class ApplyOverride extends VarArgFunction {
        private final ResourceReference reference;
        private ApplyOverride(ResourceReference reference) { this.reference = reference; }
        public Varargs invoke(Varargs args) { return reference.applyOverride(argument(args, reference, 1)); }
    }

    private static final class RemoveOverride extends VarArgFunction {
        private final List layers;
        private final LuaTable handle;
        private RemoveOverride(List layers, LuaTable handle) { this.layers = layers; this.handle = handle; }
        public Varargs invoke(Varargs args) {
            if (!handle.get("active").toboolean()) return NIL;
            for (int i = layers.size() - 1; i >= 0; i--) ((OverrideManager.Layer) layers.get(i)).remove();
            handle.set("active", FALSE);
            return NIL;
        }
    }

    private static final class BlockReference extends ResourceReference {
        private final Block block;
        private BlockReference(Block block, int damage) {
            super("block", block.blockID, damage, block);
            this.block = block;
            set("name", stringOrNil(block.getBlockName()));
            set("key", stringOrNil(block.getBlockName()));
            set("displayName", stringOrNil(block.translateBlockName()));
            set("texture", valueOf(block.blockIndexInTexture));
            set("light", valueOf(Block.lightValue[id]));
            set("lightOpacity", valueOf(Block.lightOpacity[id]));
            set("hardness", numberField(block, Block.class, "blockHardness", "bo"));
            set("resistance", numberField(block, Block.class, "blockResistance", "bp"));
        }

        protected OverrideManager.PropertyAdapter adapter(String property) {
            if (property.equals("displayName")) return displayNameAdapter(block);
            if (property.equals("hardness")) return floatFieldAdapter(Block.class, "blockHardness", "bo");
            if (property.equals("resistance")) return floatFieldAdapter(Block.class, "blockResistance", "bp");
            if (property.equals("texture")) return intFieldAdapter(Block.class, "blockIndexInTexture", "bm");
            if (property.equals("light")) return arrayAdapter(Block.lightValue, id);
            if (property.equals("lightOpacity")) return arrayAdapter(Block.lightOpacity, id);
            return null;
        }

        protected Object convert(String property, LuaValue value) {
            if (property.equals("displayName")) return value.checkjstring();
            if (property.equals("texture")) return Integer.valueOf(textureIndex(EnumTexAtlas.BLOCKS, value));
            if (property.equals("light") || property.equals("lightOpacity")) return Integer.valueOf(value.checkint());
            return Float.valueOf((float) value.checkdouble());
        }
    }

    private static final class ItemReference extends ResourceReference {
        private final Item item;
        private ItemReference(Item item, int damage) {
            super("item", item.shiftedIndex, damage, item);
            this.item = item;
            set("name", stringOrNil(item.getItemName()));
            set("key", stringOrNil(item.getItemName()));
            set("displayName", stringOrNil(itemDisplayName(item, damage)));
            set("maxStackSize", valueOf(item.getItemStackLimit()));
            set("maxDamage", valueOf(item.getMaxDamage()));
            set("hasSubtypes", valueOf(item.getHasSubtypes()));
            set("icon", valueOf(item.getIconFromDamage(damage)));
            String type = itemType(item);
            set("type", valueOf(type));
            set("category", valueOf(item instanceof ItemArmor ? "armor" : isTool(item) ? "tool" : "item"));
        }

        protected OverrideManager.PropertyAdapter adapter(String property) {
            if (property.equals("displayName")) return displayNameAdapter(item);
            if (property.equals("maxStackSize")) return intFieldAdapter(Item.class, "maxStackSize", "bg");
            if (property.equals("maxDamage")) return intFieldAdapter(Item.class, "maxDamage", "a");
            if (property.equals("hasSubtypes")) return booleanFieldAdapter(Item.class, "hasSubtypes", "bj");
            if (property.equals("icon") || property.equals("texture")) return intFieldAdapter(Item.class, "iconIndex", "bh");
            if (property.equals("healing") && item instanceof ItemFood) return intFieldAdapter(ItemFood.class, "healAmount", "a");
            if (property.equals("wolfFood") && item instanceof ItemFood) return booleanFieldAdapter(ItemFood.class, "isWolfsFavoriteMeat", "bk");
            return null;
        }

        protected Object convert(String property, LuaValue value) {
            if (property.equals("displayName")) return value.checkjstring();
            if (property.equals("hasSubtypes") || property.equals("wolfFood")) return Boolean.valueOf(value.toboolean());
            if (property.equals("texture")) return Integer.valueOf(textureIndex(EnumTexAtlas.ITEMS, value));
            if (property.equals("icon") && value.istable()) {
                return Integer.valueOf(value.get("x").checkint() + value.get("y").checkint() * 16);
            }
            return Integer.valueOf(value.checkint());
        }
    }

    /** Central declarative form: overrides:add({ target=..., when=..., changes=... }). */
    private static final class OverrideService extends LuaTable {
        private OverrideService() { set("add", new AddOverride(this)); }
    }

    private static final class AddOverride extends VarArgFunction {
        private final OverrideService service;
        private AddOverride(OverrideService service) { this.service = service; }
        public Varargs invoke(Varargs args) {
            LuaValue definition = argument(args, service, 1);
            if (!definition.istable()) throw new LuaError("overrides:add expects a definition table.");
            LuaValue target = definition.get("target");
            ResourceReference reference = resolveReference(target);
            return reference.applyOverride(definition);
        }
    }

    private static final class StackFunction extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            LuaValue item = args.arg(1);
            ResourceReference ref = item.istable() ? resolveReference(item) : null;
            int id = ref == null ? item.checkint() : ref.id;
            int count = args.narg() >= 2 ? args.arg(2).optint(1) : 1;
            int damage = args.narg() >= 3 ? args.arg(3).optint(ref == null ? 0 : ref.damage) : (ref == null ? 0 : ref.damage);
            LuaTable stack = new LuaTable();
            stack.set("id", id);
            stack.set("item", ref == null ? item : ref);
            stack.set("count", count);
            stack.set("damage", damage);
            return stack;
        }
    }

    private static LuaValue addBlock(LuaTable root, LuaValue def) {
        int id = requiredInt(def, "id");
        String material = requiredString(def, "material");
        String name = internalName(def);
        LuaValue handle = root.get("createBlock").invoke(varargsOf(new LuaValue[] {
            valueOf(id), valueOf(material), valueOf(name)
        })).arg1();
        callIfPresent(handle, "setHardness", def.get("hardness"));
        callIfPresent(handle, "setResistance", def.get("resistance"));
        callIfPresent(handle, "setLightValue", def.get("light"));
        callIfPresent(handle, "setLightOpacity", def.get("lightOpacity"));
        callIfPresent(handle, "setStepSound", def.get("stepSound"));
        if (def.get("unbreakable").toboolean()) call(handle, "setUnbreakable");
        LuaValue texture = def.get("texture");
        if (!texture.isnil()) call(handle, texture.isnumber() ? "setTextureId" : "addTexture", texture);
        LuaValue textures = def.get("textures");
        if (!textures.isnil()) call(handle, "setTextureMap", textures);
        LuaValue harvest = def.get("harvest");
        if (harvest.istable()) {
            LuaValue key = NIL;
            while (true) {
                Varargs next = harvest.next(key); key = next.arg1(); if (key.isnil()) break;
                call(handle, "setBlockHarvestLevel", key, next.arg(2));
            }
        }
        LuaValue drops = def.get("drops");
        if (drops.istable()) {
            for (int i = 1; i <= drops.length(); i++) {
                LuaValue drop = drops.get(i);
                LuaValue item = required(drop, "item");
                LuaValue min = drop.get("min");
                LuaValue max = drop.get("max");
                if (min.isnil()) call(handle, "addCustomDrop", item);
                else if (max.isnil()) call(handle, "addCustomDrop", item, min);
                else call(handle, "addCustomDrop", item, min, max);
            }
        }
        call(handle, "register", optionalString(def, "displayName", name));
        Block block = Block.blocksList[id];
        if (block instanceof betamoon.wrappers.BlockWrapper) {
            BlockTickRegistry.register((betamoon.wrappers.BlockWrapper) block,
                def.get("onTick"), def.get("onDisplayTick"));
        }
        return new BlockReference(Block.blocksList[id], 0);
    }

    private static LuaValue addItem(LuaTable root, LuaValue def) {
        int id = requiredInt(def, "id");
        String name = internalName(def);
        String type = def.get("type").optjstring("item").toLowerCase();
        LuaValue handle;
        if (type.equals("item") || type.equals("food")) {
            handle = root.get("createItem").invoke(varargsOf(new LuaValue[] { valueOf(id), valueOf(name) })).arg1();
            if (type.equals("food")) {
                LuaValue food = def.get("food");
                int healing = food.istable() ? food.get("healing").checkint() : requiredInt(def, "healing");
                boolean wolf = food.istable() && food.get("wolfFood").toboolean();
                call(handle, "setFood", valueOf(healing), valueOf(wolf));
            }
        } else if (type.equals("armor")) {
            LuaValue material = required(def, "material");
            LuaValue slot = required(def, "slot");
            handle = root.get("createArmor").invoke(varargsOf(new LuaValue[] {
                valueOf(id), material, slot, valueOf(name)
            })).arg1();
        } else {
            LuaValue material = def.get("material");
            if (material.isnil()) throw new LuaError("Tool definition requires material.");
            handle = root.get("createTool").invoke(varargsOf(new LuaValue[] { valueOf(id), material, valueOf(name) })).arg1();
            LuaValue kind = handle.get(type);
            if (kind.isnil()) throw new LuaError("Unknown item/tool type: " + type);
            handle = kind.call(handle);
        }
        callIfPresent(handle, "setMaxStackSize", def.get("maxStackSize"));
        callIfPresent(handle, "setMaxDamage", def.get("maxDamage"));
        callIfPresent(handle, "setHasSubtypes", def.get("hasSubtypes"));
        callIfPresent(handle, "setEfficiency", def.get("efficiency"));
        callIfPresent(handle, "setDamageVsEntity", def.get("damageVsEntity"));
        if (def.get("full3D").toboolean()) call(handle, "setFull3D");
        LuaValue modelTexture = def.get("modelTexture");
        LuaValue renderIndex = def.get("renderIndex");
        if (type.equals("armor") && !modelTexture.isnil() && !renderIndex.isnil()) {
            throw new LuaError("Armor definition cannot use modelTexture and renderIndex together.");
        }
        if (type.equals("armor")) {
            if (!modelTexture.isnil()) {
                call(handle, "setArmorTexture", modelTexture.checkstring());
            } else {
                call(handle, "useVanillaArmorTexture");
            }
            callIfPresent(handle, "setVanillaRenderIndex", renderIndex);
        }
        LuaValue icon = def.get("icon");
        if (icon.istable()) call(handle, "setIconCoord", icon.get("x"), icon.get("y"));
        LuaValue texture = def.get("texture");
        if (texture.isstring()) call(handle, "addTexture", texture);
        call(handle, "register", optionalString(def, "displayName", name));
        return new ItemReference(Item.itemsList[id], 0);
    }

    private static boolean matches(ResourceReference ref, LuaValue criteria) {
        if (criteria.isnil()) return true;
        LuaValue value = criteria.get("id");
        if (!value.isnil() && ref.id != value.checkint()) return false;
        value = criteria.get("damage");
        if (!value.isnil() && !matchesDamage(ref.damage, value)) return false;
        if (!matchesText(ref.get("name"), criteria, "name")) return false;
        if (!matchesText(ref.get("displayName"), criteria, "displayName")) return false;
        value = criteria.get("type");
        if (!value.isnil() && !ref.get("type").tojstring().equalsIgnoreCase(value.checkjstring())) return false;
        value = criteria.get("owner");
        if (!value.isnil() && !ref.get("owner").tojstring().equalsIgnoreCase(value.checkjstring())) return false;
        value = criteria.get("nameContains");
        if (!value.isnil() && !contains(ref.get("name"), value, criteria.get("ignoreCase").toboolean())) return false;
        LuaValue predicate = criteria.get("where");
        return predicate.isnil() || predicate.call(ref).toboolean();
    }

    private static String checkConditions(ResourceReference ref, LuaValue when) {
        if (when.isnil()) return null;
        if (!when.istable()) throw new LuaError("override when must be a table.");
        LuaValue owner = when.get("owner");
        if (!owner.isnil() && !owner.checkjstring().equals(ref.get("owner").tojstring())) {
            return "target owner is '" + ref.get("owner").tojstring() + "', expected '" + owner.tojstring() + "'";
        }
        LuaValue properties = when.get("properties");
        if (properties.istable()) {
            LuaValue key = NIL;
            while (true) {
                Varargs next = properties.next(key); key = next.arg1(); if (key.isnil()) break;
                String property = key.checkjstring();
                OverrideManager.PropertyAdapter adapter = ref.adapter(property);
                if (adapter == null) return "property '" + property + "' cannot be inspected";
                Object actual = adapter.read(ref.target);
                Object expected = ref.convert(property, next.arg(2));
                if (!valuesEqual(actual, expected)) {
                    return "property '" + key.tojstring() + "' did not match the expected value";
                }
            }
        }
        return null;
    }

    private static boolean valuesEqual(Object actual, Object expected) {
        if (actual == expected) return true;
        if (actual == null || expected == null) return false;
        if (actual instanceof Number && expected instanceof Number) {
            return Double.compare(((Number) actual).doubleValue(), ((Number) expected).doubleValue()) == 0;
        }
        return actual.equals(expected);
    }

    private static OverrideManager.PropertyAdapter displayNameAdapter(final Object target) {
        return new OverrideManager.PropertyAdapter() {
            public Object read(Object ignored) {
                if (target instanceof Block) return ((Block) target).translateBlockName();
                Item item = (Item) target;
                return itemDisplayName(item, 0);
            }
            public void write(Object ignored, Object value) { ModLoader.AddName(target, String.valueOf(value)); }
        };
    }

    private static OverrideManager.PropertyAdapter arrayAdapter(final int[] array, final int index) {
        return new OverrideManager.PropertyAdapter() {
            public Object read(Object target) { return Integer.valueOf(array[index]); }
            public void write(Object target, Object value) { array[index] = ((Number) value).intValue(); }
        };
    }

    private static OverrideManager.PropertyAdapter intFieldAdapter(final Class owner, final String... names) {
        return fieldAdapter(owner, Integer.TYPE, names);
    }

    private static OverrideManager.PropertyAdapter floatFieldAdapter(final Class owner, final String... names) {
        return fieldAdapter(owner, Float.TYPE, names);
    }

    private static OverrideManager.PropertyAdapter booleanFieldAdapter(final Class owner, final String... names) {
        return fieldAdapter(owner, Boolean.TYPE, names);
    }

    private static OverrideManager.PropertyAdapter fieldAdapter(final Class owner, final Class type, final String... names) {
        final Field field = resolveField(owner, type, names);
        return new OverrideManager.PropertyAdapter() {
            public Object read(Object target) {
                try { return field.get(target); } catch (Exception e) { throw new IllegalStateException(e); }
            }
            public void write(Object target, Object value) {
                try { field.set(target, value); } catch (Exception e) { throw new IllegalStateException(e); }
            }
        };
    }

    private static Field resolveField(Class owner, Class type, String... names) {
        for (int i = 0; i < names.length; i++) {
            try {
                Field field = owner.getDeclaredField(names[i]);
                if (field.getType() != type) continue;
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) { }
        }
        throw new IllegalStateException("Unable to resolve " + owner.getName() + " field.");
    }

    private static LuaValue numberField(Object target, Class owner, String... names) {
        Object value = floatFieldAdapter(owner, names).read(target);
        return valueOf(((Number) value).doubleValue());
    }

    private static int textureIndex(EnumTexAtlas atlas, LuaValue value) {
        if (value.isnumber()) return value.checkint();
        if (value.isstring()) return LuaApiUtils.registerTexture(atlas, value.checkjstring());
        if (value.istable()) {
            LuaValue all = value.get("all");
            if (!all.isnil()) return textureIndex(atlas, all);
            LuaValue x = value.get("x");
            LuaValue y = value.get("y");
            if (!x.isnil() && !y.isnil()) return x.checkint() + y.checkint() * 16;
        }
        throw new LuaError("Texture must be a path, atlas index, or {x, y} table.");
    }

    private static boolean matchesDamage(int actual, LuaValue expected) {
        if (expected.isnumber()) return actual == expected.checkint();
        if (!expected.istable()) throw new LuaError("damage must be a number or range table.");
        int min = expected.get("min").isnil() ? expected.get(1).optint(Integer.MIN_VALUE) : expected.get("min").checkint();
        int max = expected.get("max").isnil() ? expected.get(2).optint(Integer.MAX_VALUE) : expected.get("max").checkint();
        return actual >= min && actual <= max;
    }

    private static boolean matchesText(LuaValue actual, LuaValue criteria, String key) {
        LuaValue expected = criteria.get(key);
        if (expected.isnil()) return true;
        String left = actual.optjstring("");
        String right = expected.checkjstring();
        return criteria.get("ignoreCase").toboolean() ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private static boolean contains(LuaValue actual, LuaValue expected, boolean ignoreCase) {
        String left = actual.optjstring("");
        String right = expected.checkjstring();
        if (ignoreCase) { left = left.toLowerCase(); right = right.toLowerCase(); }
        return left.indexOf(right) >= 0;
    }

    private static ResourceReference resolveReference(LuaValue value) {
        if (!(value instanceof ResourceReference)) throw new LuaError("Expected a block or item reference.");
        return (ResourceReference) value;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        int offset = args.arg1() == receiver ? 1 : 0;
        return args.arg(index + offset);
    }

    private static void callIfPresent(LuaValue handle, String method, LuaValue value) {
        if (!value.isnil()) call(handle, method, value);
    }

    private static LuaValue call(LuaValue handle, String method, LuaValue... args) {
        LuaValue function = handle.get(method);
        if (function.isnil()) throw new LuaError("Resource does not support " + method + ".");
        LuaValue[] values = new LuaValue[args.length + 1];
        values[0] = handle;
        System.arraycopy(args, 0, values, 1, args.length);
        return function.invoke(varargsOf(values)).arg1();
    }

    private static int requiredInt(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) throw new LuaError("Definition requires '" + key + "'.");
        return value.checkint();
    }

    private static LuaValue required(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) throw new LuaError("Definition requires '" + key + "'.");
        return value;
    }

    private static String requiredString(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) throw new LuaError("Definition requires '" + key + "'.");
        return value.checkjstring();
    }

    private static String internalName(LuaValue definition) {
        LuaValue value = definition.get("internalName");
        if (value.isnil()) value = definition.get("name");
        if (value.isnil()) value = definition.get("key");
        if (value.isnil()) throw new LuaError("Definition requires 'name', 'internalName', or 'key'.");
        String name = value.checkjstring();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static LuaValue optionalString(LuaValue table, String key, String fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? valueOf(fallback) : valueOf(value.checkjstring());
    }

    private static LuaValue stringOrNil(String value) { return value == null ? NIL : valueOf(value); }

    private static String itemDisplayName(Item item, int damage) {
        net.minecraft.src.ItemStack stack = new net.minecraft.src.ItemStack(item, 1, damage);
        String key = item.getItemNameIS(stack);
        return key == null ? null : StatCollector.translateToLocal(key + ".name");
    }

    private static boolean isTool(Item item) {
        return item instanceof ItemPickaxe || item instanceof ItemAxe || item instanceof ItemSpade
            || item instanceof ItemHoe || item instanceof ItemSword;
    }

    private static String itemType(Item item) {
        if (item instanceof ItemFood) return "food";
        if (item instanceof ItemPickaxe) return "pickaxe";
        if (item instanceof ItemAxe) return "axe";
        if (item instanceof ItemSpade) return "shovel";
        if (item instanceof ItemHoe) return "hoe";
        if (item instanceof ItemSword) return "sword";
        if (item instanceof ItemArmor) return "armor";
        return "item";
    }
}
