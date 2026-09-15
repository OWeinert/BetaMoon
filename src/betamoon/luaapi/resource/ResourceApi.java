package betamoon.luaapi.resource;

import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luaapi.asset.AssetReference;
import betamoon.luaapi.block.BlockCallbackOverrides;
import betamoon.luaapi.block.BlockApi;
import betamoon.luaapi.block.BlockDisplayOverrideDefinition;
import betamoon.luaapi.block.BlockDisplayTickOverrides;
import betamoon.luaapi.item.ItemCallbackOverrides;
import betamoon.luaapi.item.ItemApi;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.query.QueryEntries;
import betamoon.query.QueryEntry;
import betamoon.resources.EnumTexAtlas;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.ItemAxe;
import net.minecraft.src.ItemFood;
import net.minecraft.src.ItemHoe;
import net.minecraft.src.ItemPickaxe;
import net.minecraft.src.ItemSpade;
import net.minecraft.src.ItemStack;
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

    /** Adds resource registration, lookup and override entry points. */
    public static void attach(LuaTable module) {
        module.set("blocks", new Registry(true, null));
        module.set("items", new Registry(false, null));
        module.set("tools", new Registry(false, "tool"));
        module.set("armor", new Registry(false, "armor"));
        module.set("stack", new StackFunction());
        module.set("overrides", new OverrideService());
    }

    /** Shared block/item registry with identical get/find/first/one semantics. */
    private static final class Registry extends LuaTable {
        private final boolean blocks;
        private final String filterType;

        private Registry(boolean blocks, String filterType) {
            this.blocks = blocks;
            this.filterType = filterType;
            set("get", new Get(this, false));
            set("getRequired", new Get(this, true));
            set("find", new Find(this, 0));
            set("first", new Find(this, 1));
            set("one", new Find(this, 2));
            set("add", new Add(this));
        }

        private LuaValue reference(int id, int damage) {
            if (blocks) {
                if (id < 0 || id >= Block.blocksList.length || Block.blocksList[id] == null) {
                    return NIL;
                }
                return new BlockReference(Block.blocksList[id], damage);
            }
            if (id < 0 || id >= Item.itemsList.length || Item.itemsList[id] == null) {
                return NIL;
            }
            return new ItemReference(Item.itemsList[id], damage);
        }

        private LuaValue reference(String key) {
            String expected = key.indexOf(':') >= 0 ? key.substring(key.indexOf(':') + 1) : key;
            int length = blocks ? Block.blocksList.length : Item.itemsList.length;
            for (int id = 0; id < length; id++) {
                LuaValue reference = reference(id, 0);
                if (reference.isnil()) {
                    continue;
                }
                String name = reference.get("name").optjstring("");
                String bare = name.startsWith("tile.") || name.startsWith("item.") ? name.substring(5) : name;
                if (key.equals(name) || expected.equals(name) || expected.equals(bare)) {
                    return reference;
                }
            }
            return NIL;
        }

        private List<LuaValue> find(LuaValue criteria) {
            List<LuaValue> values = new ArrayList<>();
            List<QueryEntry> entries = blocks ? QueryEntries.buildBlockEntries() : QueryEntries.buildItemEntries();
            for (int i = 0; i < entries.size(); i++) {
                QueryEntry entry = entries.get(i);
                LuaValue ref = reference(entry.id, entry.damage);
                if (!ref.isnil() && (filterType == null || filterType.equals(ref.get("category").tojstring()))
                        && matches((ResourceReference<?>) ref, criteria)) {
                    values.add(ref);
                }
            }
            return values;
        }
    }

    private static final class Get extends VarArgFunction {
        private final Registry registry;
        private final boolean required;
        private Get(Registry registry, boolean required) {
            this.registry = registry;
            this.required = required;
        }

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
                throw new LuaError(
                        (registry.blocks ? "Block" : "Item") + " '" + value.tojstring() + "' is not registered.");
            }
            return result;
        }
    }

    /** mode: 0=list, 1=first, 2=exactly one. */
    private static final class Find extends VarArgFunction {
        private final Registry registry;
        private final int mode;
        private Find(Registry registry, int mode) {
            this.registry = registry;
            this.mode = mode;
        }

        public Varargs invoke(Varargs args) {
            LuaValue criteria = argument(args, registry, 1);
            if (criteria.isnil()) {
                criteria = new LuaTable();
            }
            if (!criteria.istable()) {
                throw new LuaError("find criteria must be a table.");
            }
            List<LuaValue> values = registry.find(criteria);
            if (mode == 1) {
                return values.isEmpty() ? NIL : (LuaValue) values.get(0);
            }
            if (mode == 2) {
                if (values.isEmpty()) {
                    return NIL;
                }
                if (values.size() != 1) {
                    throw new LuaError("Expected exactly one result, found " + values.size() + ".");
                }
                return (LuaValue) values.get(0);
            }
            return new LuaResultList(values, new LuaResultList.BulkOverride() {
                public LuaValue apply(LuaValue reference, LuaValue definition, int index) {
                    return ((ResourceReference<?>) reference).applyOverride(definition);
                }
            });
        }
    }

    /**
     * Delegates declarations to their block or item domain API.
     */
    private static final class Add extends VarArgFunction {
        private final Registry registry;
        private Add(Registry registry) {
            this.registry = registry;
        }

        public Varargs invoke(Varargs args) {
            LuaValue definition = argument(args, registry, 1);
            if (!definition.istable()) {
                throw new LuaError("add expects a resource definition table.");
            }
            if ("armor".equals(registry.filterType) && definition.get("type").isnil()) {
                definition.set("type", "armor");
            }
            if ("tool".equals(registry.filterType) && definition.get("type").isnil()) {
                throw new LuaError("Tool definition requires type = 'pickaxe', 'axe', 'shovel', 'hoe', or 'sword'.");
            }
            return registry.blocks
                    ? new BlockReference(BlockApi.add(definition), 0)
                    : new ItemReference(ItemApi.add(definition), 0);
        }
    }

    /** Base reference exposing Lua properties and owned override definition. */
    private abstract static class ResourceReference<T> extends LuaTable {
        protected final int id;
        protected final int damage;
        protected final T target;
        protected final String namespace;

        private ResourceReference(String namespace, int id, int damage, T target) {
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

        protected abstract ResourceProperty<T, ?> property(String name);

        private LuaValue applyOverride(LuaValue definition) {
            if (!definition.istable()) {
                throw new LuaError("override expects a definition table.");
            }
            LuaValue changes = definition.get("changes");
            if (changes.isnil()) {
                changes = definition;
            }
            if (!changes.istable()) {
                throw new LuaError("override changes must be a table.");
            }
            LuaValue when = definition.get("when");
            String inactiveReason = checkConditions(this, when);
            LuaTable handle = new LuaTable();
            handle.set("target", this);
            handle.set("active", valueOf(inactiveReason == null));
            if (inactiveReason != null) {
                handle.set("reason", valueOf(inactiveReason));
                return handle;
            }
            List<OverrideManager.Layer<?, ?>> layers = new ArrayList<>();
            int priority = definition.get("priority").optint(0);
            LuaValue key = NIL;
            while (true) {
                Varargs next = changes.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                String property = key.checkjstring();
                if (property.equals("when") || property.equals("key") || property.equals("priority")) {
                    continue;
                }
                ResourceProperty<T, ?> propertyDefinition = property(property);
                if (propertyDefinition == null) {
                    throw new LuaError(
                            "Property '" + property + "' cannot be overridden on " + namespace + " " + id + ".");
                }
                layers.add(apply(propertyDefinition, next.arg(2), priority));
                // Keep the reference used for declaration useful immediately after the patch.
                set(property, next.arg(2));
            }
            handle.set("remove", new RemoveOverride(layers, handle));
            return handle;
        }

        private <V> OverrideManager.Layer<T, V> apply(ResourceProperty<T, V> definition, LuaValue value, int priority) {
            return OverrideManager.apply(namespace + ":" + id, target, definition.property, definition.convert(value),
                    priority);
        }
    }

    /** Binds Lua conversion to the matching typed native property. */
    private static final class ResourceProperty<T, V> {
        private final OverrideManager.Property<T, V> property;
        private final Function<LuaValue, V> converter;

        private ResourceProperty(String name, OverrideManager.PropertyAdapter<T, V> adapter,
                Function<LuaValue, V> converter) {
            this.property = new OverrideManager.Property<>(name, adapter);
            this.converter = converter;
        }

        private V convert(LuaValue value) {
            return converter.apply(value);
        }

        private V read(T target) {
            return property.read(target);
        }
    }

    private static final class ApplyOverride extends VarArgFunction {
        private final ResourceReference<?> reference;
        private ApplyOverride(ResourceReference<?> reference) {
            this.reference = reference;
        }

        public Varargs invoke(Varargs args) {
            return reference.applyOverride(argument(args, reference, 1));
        }
    }

    private static final class RemoveOverride extends VarArgFunction {
        private final List<OverrideManager.Layer<?, ?>> layers;
        private final LuaTable handle;
        private RemoveOverride(List<OverrideManager.Layer<?, ?>> layers, LuaTable handle) {
            this.layers = layers;
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.get("active").toboolean()) {
                return NIL;
            }
            for (int i = layers.size() - 1; i >= 0; i--) {
                (layers.get(i)).remove();
            }
            handle.set("active", FALSE);
            return NIL;
        }
    }

    private static final class BlockReference extends ResourceReference<Block> {
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

        protected ResourceProperty<Block, ?> property(String property) {
            if (BlockCallbackOverrides.supports(property)) {
                return resourceProperty(property, BlockCallbackOverrides.adapter(property),
                        value -> new LuaOverrideDefinition(property, value));
            }
            if (property.equals("onDisplayTick")) {
                return resourceProperty(property, BlockDisplayTickOverrides.ADAPTER,
                        BlockDisplayOverrideDefinition::new);
            }
            if (property.equals("displayName")) {
                return resourceProperty(property, displayNameAdapter(), LuaValue::checkjstring);
            }
            if (property.equals("hardness")) {
                return resourceProperty(property, floatFieldAdapter(Block.class, "blockHardness", "bo"),
                        value -> Float.valueOf((float) value.checkdouble()));
            }
            if (property.equals("resistance")) {
                return resourceProperty(property, floatFieldAdapter(Block.class, "blockResistance", "bp"),
                        value -> Float.valueOf((float) value.checkdouble()));
            }
            if (property.equals("texture")) {
                return resourceProperty(property, intFieldAdapter(Block.class, "blockIndexInTexture", "bm"),
                        value -> Integer.valueOf(textureIndex(EnumTexAtlas.BLOCKS, value)));
            }
            if (property.equals("light")) {
                return resourceProperty(property, arrayAdapter(Block.lightValue, id),
                        value -> Integer.valueOf(value.checkint()));
            }
            if (property.equals("lightOpacity")) {
                return resourceProperty(property, arrayAdapter(Block.lightOpacity, id),
                        value -> Integer.valueOf(value.checkint()));
            }
            return null;
        }
    }

    private static final class ItemReference extends ResourceReference<Item> {
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

        protected ResourceProperty<Item, ?> property(String property) {
            if (property.equals("displayName")) {
                return resourceProperty(property, displayNameAdapter(), LuaValue::checkjstring);
            }
            if (ItemCallbackOverrides.supports(property)) {
                return resourceProperty(property, ItemCallbackOverrides.adapter(property),
                        value -> new LuaOverrideDefinition(property, value));
            }
            if (property.equals("maxStackSize")) {
                return integerProperty(property, intFieldAdapter(Item.class, "maxStackSize", "bg"));
            }
            if (property.equals("maxDamage")) {
                return integerProperty(property, intFieldAdapter(Item.class, "maxDamage", "a"));
            }
            if (property.equals("hasSubtypes")) {
                return booleanProperty(property, booleanFieldAdapter(Item.class, "hasSubtypes", "bj"));
            }
            if (property.equals("icon") || property.equals("texture")) {
                return resourceProperty(property, intFieldAdapter(Item.class, "iconIndex", "bh"),
                        value -> Integer.valueOf("texture".equals(property)
                                ? textureIndex(EnumTexAtlas.ITEMS, value)
                                : value.istable()
                                        ? value.get("x").checkint() + value.get("y").checkint() * 16
                                        : value.checkint()));
            }
            if (property.equals("healing") && item instanceof ItemFood) {
                return integerProperty(property, intFieldAdapter(ItemFood.class, "healAmount", "a"));
            }
            if (property.equals("wolfFood") && item instanceof ItemFood) {
                return booleanProperty(property, booleanFieldAdapter(ItemFood.class, "isWolfsFavoriteMeat", "bk"));
            }
            return null;
        }
    }

    /**
     * Central declarative form: overrides:add({ target=..., when=..., changes=...
     * }).
     */
    private static final class OverrideService extends LuaTable {
        private OverrideService() {
            set("add", new AddOverride(this));
        }
    }

    private static final class AddOverride extends VarArgFunction {
        private final OverrideService service;
        private AddOverride(OverrideService service) {
            this.service = service;
        }

        public Varargs invoke(Varargs args) {
            LuaValue definition = argument(args, service, 1);
            if (!definition.istable()) {
                throw new LuaError("overrides:add expects a definition table.");
            }
            LuaValue target = definition.get("target");
            if (target instanceof RecipeTarget) {
                return ((RecipeTarget) target).override(definition);
            }
            ResourceReference<?> reference = resolveReference(target);
            return reference.applyOverride(definition);
        }
    }

    private static final class StackFunction extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            LuaValue item = args.arg(1);
            ResourceReference<?> ref = item.istable() ? resolveReference(item) : null;
            int id = ref == null ? item.checkint() : ref.id;
            int count = args.narg() >= 2 ? args.arg(2).optint(1) : 1;
            int damage = args.narg() >= 3
                    ? args.arg(3).optint(ref == null ? 0 : ref.damage)
                    : (ref == null ? 0 : ref.damage);
            LuaTable stack = new LuaTable();
            stack.set("id", id);
            stack.set("item", ref == null ? item : ref);
            stack.set("count", count);
            stack.set("damage", damage);
            return stack;
        }
    }

    private static boolean matches(ResourceReference<?> ref, LuaValue criteria) {
        if (criteria.isnil()) {
            return true;
        }
        LuaValue value = criteria.get("id");
        if (!value.isnil() && ref.id != value.checkint()) {
            return false;
        }
        value = criteria.get("damage");
        if (!value.isnil() && !matchesDamage(ref.damage, value)) {
            return false;
        }
        if (!matchesText(ref.get("name"), criteria, "name")) {
            return false;
        }
        if (!matchesText(ref.get("displayName"), criteria, "displayName")) {
            return false;
        }
        value = criteria.get("type");
        if (!value.isnil() && !ref.get("type").tojstring().equalsIgnoreCase(value.checkjstring())) {
            return false;
        }
        value = criteria.get("owner");
        if (!value.isnil() && !ref.get("owner").tojstring().equalsIgnoreCase(value.checkjstring())) {
            return false;
        }
        value = criteria.get("nameContains");
        if (!value.isnil() && !contains(ref.get("name"), value, criteria.get("ignoreCase").toboolean())) {
            return false;
        }
        LuaValue predicate = criteria.get("where");
        return predicate.isnil() || predicate.call(ref).toboolean();
    }

    private static <T> String checkConditions(ResourceReference<T> ref, LuaValue when) {
        if (when.isnil()) {
            return null;
        }
        if (!when.istable()) {
            throw new LuaError("override when must be a table.");
        }
        LuaValue owner = when.get("owner");
        if (!owner.isnil() && !owner.checkjstring().equals(ref.get("owner").tojstring())) {
            return "target owner is '" + ref.get("owner").tojstring() + "', expected '" + owner.tojstring() + "'";
        }
        LuaValue properties = when.get("properties");
        if (properties.istable()) {
            LuaValue key = NIL;
            while (true) {
                Varargs next = properties.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                String property = key.checkjstring();
                ResourceProperty<T, ?> propertyDefinition = ref.property(property);
                if (propertyDefinition == null) {
                    return "property '" + property + "' cannot be inspected";
                }
                Object actual = propertyDefinition.read(ref.target);
                Object expected = propertyDefinition.convert(next.arg(2));
                if (!valuesEqual(actual, expected)) {
                    return "property '" + key.tojstring() + "' did not match the expected value";
                }
            }
        }
        return null;
    }

    private static boolean valuesEqual(Object actual, Object expected) {
        if (actual == expected) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        if (actual instanceof Number && expected instanceof Number) {
            return Double.compare(((Number) actual).doubleValue(), ((Number) expected).doubleValue()) == 0;
        }
        return actual.equals(expected);
    }

    private static <T> OverrideManager.PropertyAdapter<T, String> displayNameAdapter() {
        return new OverrideManager.PropertyAdapter<T, String>() {
            public String read(T target) {
                if (target instanceof Block) {
                    return ((Block) target).translateBlockName();
                }
                Item item = (Item) target;
                return itemDisplayName(item, 0);
            }

            public void write(T target, String value) {
                ModLoader.AddName(target, String.valueOf(value));
            }
        };
    }

    private static OverrideManager.PropertyAdapter<Block, Integer> arrayAdapter(final int[] array, final int index) {
        return new OverrideManager.PropertyAdapter<Block, Integer>() {
            public Integer read(Block target) {
                return Integer.valueOf(array[index]);
            }

            public void write(Block target, Integer value) {
                array[index] = value.intValue();
            }
        };
    }

    private static <T> OverrideManager.PropertyAdapter<T, Integer> intFieldAdapter(final Class<?> owner,
            final String... names) {
        return fieldAdapter(owner, Integer.TYPE, Integer.class, names);
    }

    private static <T> OverrideManager.PropertyAdapter<T, Float> floatFieldAdapter(final Class<?> owner,
            final String... names) {
        return fieldAdapter(owner, Float.TYPE, Float.class, names);
    }

    private static <T> OverrideManager.PropertyAdapter<T, Boolean> booleanFieldAdapter(final Class<?> owner,
            final String... names) {
        return fieldAdapter(owner, Boolean.TYPE, Boolean.class, names);
    }

    private static <T, V> OverrideManager.PropertyAdapter<T, V> fieldAdapter(final Class<?> owner,
            final Class<?> fieldType, final Class<V> valueType, final String... names) {
        final Field field = resolveField(owner, fieldType, names);
        return new OverrideManager.PropertyAdapter<T, V>() {
            public V read(T target) {
                try {
                    return valueType.cast(field.get(target));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            public void write(T target, V value) {
                try {
                    field.set(target, value);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private static <T, V> ResourceProperty<T, V> resourceProperty(String name,
            OverrideManager.PropertyAdapter<T, V> adapter, Function<LuaValue, V> converter) {
        return new ResourceProperty<>(name, adapter, converter);
    }

    private static <T> ResourceProperty<T, Integer> integerProperty(String name,
            OverrideManager.PropertyAdapter<T, Integer> adapter) {
        return resourceProperty(name, adapter, value -> Integer.valueOf(value.checkint()));
    }

    private static <T> ResourceProperty<T, Boolean> booleanProperty(String name,
            OverrideManager.PropertyAdapter<T, Boolean> adapter) {
        return resourceProperty(name, adapter, value -> Boolean.valueOf(value.toboolean()));
    }

    private static Field resolveField(Class<?> owner, Class<?> type, String... names) {
        for (int i = 0; i < names.length; i++) {
            try {
                Field field = owner.getDeclaredField(names[i]);
                if (field.getType() != type) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException("Unable to resolve " + owner.getName() + " field.");
    }

    private static LuaValue numberField(Object target, Class<?> owner, String... names) {
        Object value = floatFieldAdapter(owner, names).read(target);
        return valueOf(((Number) value).doubleValue());
    }

    private static int textureIndex(EnumTexAtlas atlas, LuaValue value) {
        if (value.isnumber()) {
            return value.checkint();
        }
        if (value.isstring() || value instanceof AssetReference) {
            return LuaApiUtils.registerTexture(atlas, AssetInputs.texture(value));
        }
        if (value.istable()) {
            LuaValue all = value.get("all");
            if (!all.isnil()) {
                return textureIndex(atlas, all);
            }
            LuaValue x = value.get("x");
            LuaValue y = value.get("y");
            if (!x.isnil() && !y.isnil()) {
                return x.checkint() + y.checkint() * 16;
            }
        }
        throw new LuaError("Texture must be a path, atlas index, or {x, y} table.");
    }

    private static boolean matchesDamage(int actual, LuaValue expected) {
        if (expected.isnumber()) {
            return actual == expected.checkint();
        }
        if (!expected.istable()) {
            throw new LuaError("damage must be a number or range table.");
        }
        int min = expected.get("min").isnil()
                ? expected.get(1).optint(Integer.MIN_VALUE)
                : expected.get("min").checkint();
        int max = expected.get("max").isnil()
                ? expected.get(2).optint(Integer.MAX_VALUE)
                : expected.get("max").checkint();
        return actual >= min && actual <= max;
    }

    private static boolean matchesText(LuaValue actual, LuaValue criteria, String key) {
        LuaValue expected = criteria.get(key);
        if (expected.isnil()) {
            return true;
        }
        String left = actual.optjstring("");
        String right = expected.checkjstring();
        return criteria.get("ignoreCase").toboolean() ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private static boolean contains(LuaValue actual, LuaValue expected, boolean ignoreCase) {
        String left = actual.optjstring("");
        String right = expected.checkjstring();
        if (ignoreCase) {
            left = left.toLowerCase();
            right = right.toLowerCase();
        }
        return left.indexOf(right) >= 0;
    }

    private static ResourceReference<?> resolveReference(LuaValue value) {
        if (!(value instanceof ResourceReference<?>)) {
            throw new LuaError("Expected a block or item reference.");
        }
        return (ResourceReference<?>) value;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        int offset = args.arg1() == receiver ? 1 : 0;
        return args.arg(index + offset);
    }

    private static LuaValue stringOrNil(String value) {
        return value == null ? NIL : valueOf(value);
    }

    private static String itemDisplayName(Item item, int damage) {
        ItemStack stack = new ItemStack(item, 1, damage);
        String key = item.getItemNameIS(stack);
        return key == null ? null : StatCollector.translateToLocal(key + ".name");
    }

    private static boolean isTool(Item item) {
        return item instanceof ItemPickaxe || item instanceof ItemAxe || item instanceof ItemSpade
                || item instanceof ItemHoe || item instanceof ItemSword;
    }

    private static String itemType(Item item) {
        if (item instanceof ItemFood) {
            return "food";
        }
        if (item instanceof ItemPickaxe) {
            return "pickaxe";
        }
        if (item instanceof ItemAxe) {
            return "axe";
        }
        if (item instanceof ItemSpade) {
            return "shovel";
        }
        if (item instanceof ItemHoe) {
            return "hoe";
        }
        if (item instanceof ItemSword) {
            return "sword";
        }
        if (item instanceof ItemArmor) {
            return "armor";
        }
        return "item";
    }
}
