package betamoon.entity;

import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.network.protocol.WireValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagByte;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagDouble;
import net.minecraft.src.NBTTagInt;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.NBTTagString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** One bounded persistent entity-data field, including recursive structured values. */
public final class EntityDataField {
    private static final int MAX_DEPTH = 4;
    private static final int MAX_RECORD_FIELDS = 64;
    private static final int MAX_LIST_LENGTH = 256;
    private static final int MAX_STRING_LENGTH = 1024;
    private static final int MAX_VALUE_NODES = 4096;
    private static final double MAX_VECTOR_COMPONENT = 30000000.0;

    public enum Type {
        BOOLEAN((byte) 1),
        INTEGER((byte) 3),
        NUMBER((byte) 6),
        STRING((byte) 8),
        LIST((byte) 9),
        RECORD((byte) 10),
        VECTOR((byte) 10),
        ITEM_STACK((byte) 10),
        ENTITY_REFERENCE((byte) 10);

        private final byte nbtType;

        Type(byte nbtType) {
            this.nbtType = nbtType;
        }

        public static Type parse(String name, String path) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw LuaDeclarationValues.error(path,
                        "expected boolean, integer, number, string, vector, item_stack, "
                                + "entity_reference, list, or record");
            }
        }
    }

    /** Runtime conversion for values that require a live callback context. */
    public interface LuaContext {
        ReferenceValue readReference(LuaValue value, String path);

        LuaValue writeReference(ReferenceValue value);
    }

    public static final class ReferenceValue {
        public final String kind;
        public final String identity;

        public ReferenceValue(String kind, String identity) {
            this.kind = kind;
            this.identity = identity;
        }

        public String token() {
            return kind + ":" + identity;
        }
    }

    private static final LuaContext DEFAULT_CONTEXT = new LuaContext() {
        @Override
        public ReferenceValue readReference(LuaValue value, String path) {
            if (value.isnil()) {
                return null;
            }
            throw LuaDeclarationValues.error(path, "entity-reference defaults must be nil");
        }

        @Override
        public LuaValue writeReference(ReferenceValue value) {
            throw new IllegalStateException("A live Lua context is required for entity references");
        }
    };

    public final String name;
    public final Type type;
    public final Object defaultValue;
    private final EntityDataField element;
    private final Map<String, EntityDataField> fields;
    private final int maxLength;

    private EntityDataField(String name, Type type, Object defaultValue, EntityDataField element,
            Map<String, EntityDataField> fields, int maxLength) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.element = element;
        this.fields = fields == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.maxLength = maxLength;
    }

    public static EntityDataField parse(String name, LuaValue declaration, String path) {
        return parse(name, declaration, path, 0);
    }

    private static EntityDataField parse(String name, LuaValue declaration, String path, int depth) {
        if (depth > MAX_DEPTH) {
            throw LuaDeclarationValues.error(path, "structured data may be nested at most " + MAX_DEPTH + " levels");
        }
        Type type = Type.parse(LuaDeclarationValues.string(
                LuaDeclarationValues.required(declaration, "type"), path + ".type"), path + ".type");
        String[] allowed = type == Type.LIST
                ? new String[]{"type", "element", "maxLength", "default", "save"}
                : type == Type.RECORD
                        ? new String[]{"type", "fields", "default", "save"}
                        : new String[]{"type", "default", "save"};
        LuaDeclarationValues.fields(declaration, path, allowed);
        LuaValue save = declaration.get("save");
        if (!save.isnil() && !save.eq_b(LuaValue.TRUE)) {
            throw LuaDeclarationValues.error(path + ".save", "persistent fields require save = true");
        }

        EntityDataField element = null;
        Map<String, EntityDataField> fields = null;
        int maxLength = 0;
        if (type == Type.LIST) {
            LuaValue elementDeclaration = LuaDeclarationValues.required(declaration, "element");
            element = parse("element", elementDeclaration, path + ".element", depth + 1);
            maxLength = declaration.get("maxLength").isnil() ? 64
                    : LuaDeclarationValues.integer(declaration.get("maxLength"), path + ".maxLength", 1,
                            MAX_LIST_LENGTH);
        } else if (type == Type.RECORD) {
            LuaValue fieldDeclarations = LuaDeclarationValues.required(declaration, "fields");
            List<String> keys = LuaDeclarationValues.keys(fieldDeclarations, path + ".fields");
            if (keys.size() > MAX_RECORD_FIELDS) {
                throw LuaDeclarationValues.error(path + ".fields",
                        "expected at most " + MAX_RECORD_FIELDS + " fields");
            }
            fields = new LinkedHashMap<>();
            for (String fieldName : keys) {
                validateName(fieldName, path + ".fields");
                fields.put(fieldName, parse(fieldName, fieldDeclarations.get(fieldName),
                        path + ".fields." + fieldName, depth + 1));
            }
        }

        EntityDataField provisional = new EntityDataField(name, type, null, element, fields, maxLength);
        if (provisional.maximumNodes() > MAX_VALUE_NODES) {
            throw LuaDeclarationValues.error(path,
                    "schema can contain at most " + MAX_VALUE_NODES + " stored values");
        }
        LuaValue declaredDefault = declaration.get("default");
        Object defaultValue = declaredDefault.isnil() ? provisional.intrinsicDefault()
                : provisional.fromLua(declaredDefault, DEFAULT_CONTEXT, path + ".default");
        return new EntityDataField(name, type, defaultValue, element, fields, maxLength);
    }

    public Object fromLua(LuaValue value, LuaContext context, String path) {
        switch (type) {
            case BOOLEAN:
                if (!value.isboolean()) {
                    throw LuaDeclarationValues.error(path, "expected a boolean");
                }
                return value.toboolean();
            case INTEGER:
                return LuaDeclarationValues.integer(value, path, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case NUMBER:
                return LuaDeclarationValues.number(value, path);
            case STRING:
                String text = LuaDeclarationValues.string(value, path);
                if (text.length() > MAX_STRING_LENGTH) {
                    throw LuaDeclarationValues.error(path,
                            "maximum string length is " + MAX_STRING_LENGTH);
                }
                return text;
            case VECTOR:
                requireTable(value, path);
                LuaDeclarationValues.fields(value, path, "x", "y", "z");
                return new VectorValue(vector(value.get("x"), path + ".x"),
                        vector(value.get("y"), path + ".y"), vector(value.get("z"), path + ".z"));
            case ITEM_STACK:
                return stack(value, path);
            case ENTITY_REFERENCE:
                return context.readReference(value, path);
            case LIST:
                int length = LuaDeclarationValues.length(value, path);
                if (length > maxLength) {
                    throw LuaDeclarationValues.error(path, "expected at most " + maxLength + " entries");
                }
                List<Object> list = new ArrayList<>();
                for (int index = 1; index <= length; index++) {
                    list.add(element.fromLua(value.get(index), context, path + "[" + index + "]"));
                }
                return Collections.unmodifiableList(list);
            case RECORD:
                requireTable(value, path);
                LuaDeclarationValues.fields(value, path, fields.keySet().toArray(new String[0]));
                Map<String, Object> record = new LinkedHashMap<>();
                for (EntityDataField field : fields.values()) {
                    LuaValue fieldValue = value.get(field.name);
                    record.put(field.name, fieldValue.isnil() ? field.defaultValue
                            : field.fromLua(fieldValue, context, path + "." + field.name));
                }
                return Collections.unmodifiableMap(record);
            default:
                throw new AssertionError(type);
        }
    }

    public LuaValue toLua(Object value, LuaContext context) {
        if (value == null) {
            return LuaValue.NIL;
        }
        switch (type) {
            case BOOLEAN:
                return LuaValue.valueOf((Boolean) value);
            case INTEGER:
                return LuaValue.valueOf((Integer) value);
            case NUMBER:
                return LuaValue.valueOf((Double) value);
            case STRING:
                return LuaValue.valueOf((String) value);
            case VECTOR:
                VectorValue vector = (VectorValue) value;
                LuaTable vectorTable = new LuaTable();
                vectorTable.set("x", vector.x);
                vectorTable.set("y", vector.y);
                vectorTable.set("z", vector.z);
                return vectorTable;
            case ITEM_STACK:
                StackValue stack = (StackValue) value;
                LuaTable stackTable = new LuaTable();
                stackTable.set("id", stack.id);
                stackTable.set("count", stack.count);
                stackTable.set("damage", stack.damage);
                stackTable.set("available", LuaValue.valueOf(stack.available()));
                return stackTable;
            case ENTITY_REFERENCE:
                return context.writeReference((ReferenceValue) value);
            case LIST:
                LuaTable list = new LuaTable();
                List<?> listValue = (List<?>) value;
                for (int index = 0; index < listValue.size(); index++) {
                    list.set(index + 1, element.toLua(listValue.get(index), context));
                }
                return list;
            case RECORD:
                LuaTable record = new LuaTable();
                Map<?, ?> recordValue = (Map<?, ?>) value;
                for (EntityDataField field : fields.values()) {
                    record.set(field.name, field.toLua(recordValue.get(field.name), context));
                }
                return record;
            default:
                throw new AssertionError(type);
        }
    }

    public WireValue toWire(Object value) {
        if (value == null) {
            return WireValue.nullValue();
        }
        switch (type) {
            case BOOLEAN:
                return WireValue.bool((Boolean) value);
            case INTEGER:
                return WireValue.integer((Integer) value);
            case NUMBER:
                return WireValue.decimal((Double) value);
            case STRING:
                return WireValue.text((String) value);
            case VECTOR:
                VectorValue vector = (VectorValue) value;
                Map<String, WireValue> vectorValues = new LinkedHashMap<>();
                vectorValues.put("x", WireValue.decimal(vector.x));
                vectorValues.put("y", WireValue.decimal(vector.y));
                vectorValues.put("z", WireValue.decimal(vector.z));
                return WireValue.record(vectorValues);
            case ITEM_STACK:
                StackValue stack = (StackValue) value;
                Map<String, WireValue> stackValues = new LinkedHashMap<>();
                stackValues.put("id", WireValue.integer(stack.id));
                stackValues.put("count", WireValue.integer(stack.count));
                stackValues.put("damage", WireValue.integer(stack.damage));
                return WireValue.record(stackValues);
            case ENTITY_REFERENCE:
                ReferenceValue reference = (ReferenceValue) value;
                Map<String, WireValue> referenceValues = new LinkedHashMap<>();
                referenceValues.put("kind", WireValue.text(reference.kind));
                referenceValues.put("identity", WireValue.text(reference.identity));
                return WireValue.record(referenceValues);
            case LIST:
                List<WireValue> list = new ArrayList<>();
                for (Object entry : (List<?>) value) {
                    list.add(element.toWire(entry));
                }
                return WireValue.list(list);
            case RECORD:
                Map<?, ?> record = (Map<?, ?>) value;
                Map<String, WireValue> recordValues = new LinkedHashMap<>();
                for (EntityDataField field : fields.values()) {
                    recordValues.put(field.name, field.toWire(record.get(field.name)));
                }
                return WireValue.record(recordValues);
            default:
                throw new AssertionError(type);
        }
    }

    public Object fromWire(WireValue value, String path) {
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        if (value.type() == WireValue.Type.NULL) {
            if (type == Type.ITEM_STACK || type == Type.ENTITY_REFERENCE) {
                return null;
            }
            throw new IllegalArgumentException(path + " cannot be null");
        }
        switch (type) {
            case BOOLEAN:
                requireWireType(value, WireValue.Type.BOOLEAN, path);
                return value.value();
            case INTEGER:
                requireWireType(value, WireValue.Type.INTEGER, path);
                return value.value();
            case NUMBER:
                requireWireType(value, WireValue.Type.DOUBLE, path);
                return value.value();
            case STRING:
                requireWireType(value, WireValue.Type.STRING, path);
                String text = (String) value.value();
                if (text.length() > MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException(path + " exceeds the maximum string length");
                }
                return text;
            case VECTOR:
                Map<String, WireValue> vector = exactRecord(value, path, "x", "y", "z");
                double x = wireNumber(vector.get("x"), path + ".x");
                double y = wireNumber(vector.get("y"), path + ".y");
                double z = wireNumber(vector.get("z"), path + ".z");
                if (Math.abs(x) > MAX_VECTOR_COMPONENT || Math.abs(y) > MAX_VECTOR_COMPONENT
                        || Math.abs(z) > MAX_VECTOR_COMPONENT) {
                    throw new IllegalArgumentException(path + " contains an out-of-range vector");
                }
                return new VectorValue(x, y, z);
            case ITEM_STACK:
                Map<String, WireValue> stack = exactRecord(value, path, "id", "count", "damage");
                int id = wireInteger(stack.get("id"), path + ".id");
                int count = wireInteger(stack.get("count"), path + ".count");
                int damage = wireInteger(stack.get("damage"), path + ".damage");
                if (id <= 0 || id > 32767 || count < 1 || count > 64 || damage < 0 || damage > 32767) {
                    throw new IllegalArgumentException(path + " contains an invalid item stack");
                }
                return new StackValue(id, count, damage);
            case ENTITY_REFERENCE:
                Map<String, WireValue> reference = exactRecord(value, path, "kind", "identity");
                String kind = wireString(reference.get("kind"), path + ".kind");
                String identity = wireString(reference.get("identity"), path + ".identity");
                if (!("player".equals(kind) || "entity".equals(kind)) || identity.isEmpty()
                        || identity.length() > 128) {
                    throw new IllegalArgumentException(path + " contains an invalid entity reference");
                }
                return new ReferenceValue(kind, identity);
            case LIST:
                requireWireType(value, WireValue.Type.LIST, path);
                if (value.listValue().size() > maxLength) {
                    throw new IllegalArgumentException(path + " exceeds its declared maximum length");
                }
                List<Object> list = new ArrayList<>();
                for (int index = 0; index < value.listValue().size(); index++) {
                    list.add(element.fromWire(value.listValue().get(index), path + "[" + (index + 1) + "]"));
                }
                return Collections.unmodifiableList(list);
            case RECORD:
                requireWireType(value, WireValue.Type.RECORD, path);
                Map<String, WireValue> wireRecord = value.recordValue();
                if (!wireRecord.keySet().equals(fields.keySet())) {
                    throw new IllegalArgumentException(path + " does not match its declared record fields");
                }
                Map<String, Object> parsed = new LinkedHashMap<>();
                for (EntityDataField field : fields.values()) {
                    parsed.put(field.name, field.fromWire(wireRecord.get(field.name), path + "." + field.name));
                }
                return Collections.unmodifiableMap(parsed);
            default:
                throw new AssertionError(type);
        }
    }

    public boolean matches(NBTBase tag) {
        return tag != null && tag.getType() == type.nbtType;
    }

    public Object read(NBTTagCompound parent) {
        return readTag(find(parent, name), "saved data." + name);
    }

    public void write(NBTTagCompound parent, Object value) {
        parent.setTag(name, writeTag(value));
    }

    public boolean isCompatibleWith(EntityDataField previous) {
        if (previous == null || type != previous.type) {
            return previous == null;
        }
        if (type == Type.LIST) {
            return maxLength >= previous.maxLength && element.isCompatibleWith(previous.element);
        }
        if (type == Type.RECORD) {
            for (EntityDataField field : fields.values()) {
                EntityDataField oldField = previous.fields.get(field.name);
                if (oldField != null && !field.isCompatibleWith(oldField)) {
                    return false;
                }
            }
        }
        return true;
    }

    public String describeType() {
        if (type == Type.LIST) {
            return "list<" + element.describeType() + "> up to " + maxLength;
        }
        return type.name().toLowerCase(Locale.ROOT);
    }

    public int maximumNodes() {
        if (type == Type.LIST) {
            return boundedAdd(1, boundedMultiply(maxLength, element.maximumNodes()));
        }
        if (type == Type.RECORD) {
            int total = 1;
            for (EntityDataField field : fields.values()) {
                total = boundedAdd(total, field.maximumNodes());
            }
            return total;
        }
        return 1;
    }

    private Object intrinsicDefault() {
        switch (type) {
            case BOOLEAN:
                return false;
            case INTEGER:
                return 0;
            case NUMBER:
                return 0.0;
            case STRING:
                return "";
            case VECTOR:
                return new VectorValue(0, 0, 0);
            case ITEM_STACK:
            case ENTITY_REFERENCE:
                return null;
            case LIST:
                return Collections.emptyList();
            case RECORD:
                Map<String, Object> record = new LinkedHashMap<>();
                for (EntityDataField field : fields.values()) {
                    record.put(field.name, field.defaultValue);
                }
                return Collections.unmodifiableMap(record);
            default:
                throw new AssertionError(type);
        }
    }

    private Object readTag(NBTBase tag, String path) {
        if (tag == null || tag.getType() != type.nbtType) {
            throw new IllegalArgumentException(path + " has an incompatible NBT type");
        }
        switch (type) {
            case BOOLEAN:
                return ((NBTTagByte) tag).byteValue != 0;
            case INTEGER:
                return ((NBTTagInt) tag).intValue;
            case NUMBER:
                double number = ((NBTTagDouble) tag).doubleValue;
                if (!Double.isFinite(number)) {
                    throw new IllegalArgumentException(path + " must be finite");
                }
                return number;
            case STRING:
                String text = ((NBTTagString) tag).stringValue;
                if (text.length() > MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException(path + " exceeds the maximum string length");
                }
                return text;
            case VECTOR:
                NBTTagCompound vector = (NBTTagCompound) tag;
                return new VectorValue(savedVector(vector, "X", path), savedVector(vector, "Y", path),
                        savedVector(vector, "Z", path));
            case ITEM_STACK:
                NBTTagCompound stack = (NBTTagCompound) tag;
                if (!stack.getBoolean("Present")) {
                    return null;
                }
                int id = stack.getInteger("Id");
                int count = stack.getInteger("Count");
                int damage = stack.getInteger("Damage");
                if (id <= 0 || id > 32767 || count < 1 || count > 64 || damage < 0 || damage > 32767) {
                    throw new IllegalArgumentException(path + " contains an invalid item stack");
                }
                return new StackValue(id, count, damage);
            case ENTITY_REFERENCE:
                NBTTagCompound reference = (NBTTagCompound) tag;
                if (!reference.getBoolean("Present")) {
                    return null;
                }
                String kind = reference.getString("Kind");
                String identity = reference.getString("Identity");
                if (!("player".equals(kind) || "entity".equals(kind)) || identity.isEmpty()
                        || identity.length() > 128) {
                    throw new IllegalArgumentException(path + " contains an invalid entity reference");
                }
                return new ReferenceValue(kind, identity);
            case LIST:
                NBTTagList savedList = (NBTTagList) tag;
                if (savedList.tagCount() > maxLength) {
                    throw new IllegalArgumentException(path + " exceeds its declared maximum length");
                }
                List<Object> list = new ArrayList<>();
                for (int index = 0; index < savedList.tagCount(); index++) {
                    list.add(element.readTag(savedList.tagAt(index), path + "[" + (index + 1) + "]"));
                }
                return Collections.unmodifiableList(list);
            case RECORD:
                NBTTagCompound savedRecord = (NBTTagCompound) tag;
                Map<String, Object> record = new LinkedHashMap<>();
                for (EntityDataField field : fields.values()) {
                    NBTBase savedField = find(savedRecord, field.name);
                    record.put(field.name, savedField == null ? field.defaultValue
                            : field.readTag(savedField, path + "." + field.name));
                }
                return Collections.unmodifiableMap(record);
            default:
                throw new AssertionError(type);
        }
    }

    private NBTBase writeTag(Object value) {
        switch (type) {
            case BOOLEAN:
                return new NBTTagByte((byte) ((Boolean) value ? 1 : 0));
            case INTEGER:
                return new NBTTagInt((Integer) value);
            case NUMBER:
                return new NBTTagDouble((Double) value);
            case STRING:
                return new NBTTagString((String) value);
            case VECTOR:
                VectorValue vector = (VectorValue) value;
                NBTTagCompound vectorTag = new NBTTagCompound();
                vectorTag.setDouble("X", vector.x);
                vectorTag.setDouble("Y", vector.y);
                vectorTag.setDouble("Z", vector.z);
                return vectorTag;
            case ITEM_STACK:
                NBTTagCompound stackTag = new NBTTagCompound();
                stackTag.setBoolean("Present", value != null);
                if (value != null) {
                    StackValue stack = (StackValue) value;
                    stackTag.setInteger("Id", stack.id);
                    stackTag.setInteger("Count", stack.count);
                    stackTag.setInteger("Damage", stack.damage);
                }
                return stackTag;
            case ENTITY_REFERENCE:
                NBTTagCompound referenceTag = new NBTTagCompound();
                referenceTag.setBoolean("Present", value != null);
                if (value != null) {
                    ReferenceValue reference = (ReferenceValue) value;
                    referenceTag.setString("Kind", reference.kind);
                    referenceTag.setString("Identity", reference.identity);
                }
                return referenceTag;
            case LIST:
                NBTTagList listTag = new NBTTagList();
                for (Object entry : (List<?>) value) {
                    listTag.setTag(element.writeTag(entry));
                }
                return listTag;
            case RECORD:
                NBTTagCompound recordTag = new NBTTagCompound();
                Map<?, ?> record = (Map<?, ?>) value;
                for (EntityDataField field : fields.values()) {
                    recordTag.setTag(field.name, field.writeTag(record.get(field.name)));
                }
                return recordTag;
            default:
                throw new AssertionError(type);
        }
    }

    private static StackValue stack(LuaValue value, String path) {
        if (value.isnil()) {
            return null;
        }
        ItemStack stack;
        try {
            stack = LuaApiUtils.readItemStack(value, true, path);
        } catch (RuntimeException error) {
            throw LuaDeclarationValues.error(path, "expected a registered item stack");
        }
        if (stack.itemID <= 0 || stack.itemID >= Item.itemsList.length || Item.itemsList[stack.itemID] == null) {
            throw LuaDeclarationValues.error(path, "must reference a registered item");
        }
        int limit = Math.min(64, stack.getMaxStackSize());
        if (stack.stackSize < 1 || stack.stackSize > limit) {
            throw LuaDeclarationValues.error(path + ".count", "expected integer 1.." + limit);
        }
        if (stack.getItemDamage() < 0 || stack.getItemDamage() > 32767) {
            throw LuaDeclarationValues.error(path + ".damage", "expected integer 0..32767");
        }
        return new StackValue(stack.itemID, stack.stackSize, stack.getItemDamage());
    }

    private static double vector(LuaValue value, String path) {
        double component = LuaDeclarationValues.number(value, path);
        if (component < -MAX_VECTOR_COMPONENT || component > MAX_VECTOR_COMPONENT) {
            throw LuaDeclarationValues.error(path,
                    "expected a component within " + -MAX_VECTOR_COMPONENT + ".." + MAX_VECTOR_COMPONENT);
        }
        return component;
    }

    private static double savedVector(NBTTagCompound vector, String name, String path) {
        NBTBase component = find(vector, name);
        if (!(component instanceof NBTTagDouble)) {
            throw new IllegalArgumentException(path + " contains an invalid vector");
        }
        double value = ((NBTTagDouble) component).doubleValue;
        if (!Double.isFinite(value) || value < -MAX_VECTOR_COMPONENT || value > MAX_VECTOR_COMPONENT) {
            throw new IllegalArgumentException(path + " contains an out-of-range vector");
        }
        return value;
    }

    private static NBTBase find(NBTTagCompound compound, String name) {
        for (Object value : compound.func_28110_c()) {
            NBTBase tag = (NBTBase) value;
            if (name.equals(tag.getKey())) {
                return tag;
            }
        }
        return null;
    }

    private static void requireTable(LuaValue value, String path) {
        if (!value.istable()) {
            throw LuaDeclarationValues.error(path, "expected a table");
        }
    }

    private static void requireWireType(WireValue value, WireValue.Type expected, String path) {
        if (value.type() != expected) {
            throw new IllegalArgumentException(path + " has an incompatible synchronized type");
        }
    }

    private static Map<String, WireValue> exactRecord(WireValue value, String path, String... names) {
        requireWireType(value, WireValue.Type.RECORD, path);
        Map<String, WireValue> record = value.recordValue();
        if (record.size() != names.length) {
            throw new IllegalArgumentException(path + " has an incompatible synchronized record");
        }
        for (String name : names) {
            if (!record.containsKey(name)) {
                throw new IllegalArgumentException(path + " is missing synchronized field '" + name + "'");
            }
        }
        return record;
    }

    private static int wireInteger(WireValue value, String path) {
        requireWireType(value, WireValue.Type.INTEGER, path);
        return ((Integer) value.value()).intValue();
    }

    private static double wireNumber(WireValue value, String path) {
        requireWireType(value, WireValue.Type.DOUBLE, path);
        return ((Double) value.value()).doubleValue();
    }

    private static String wireString(WireValue value, String path) {
        requireWireType(value, WireValue.Type.STRING, path);
        return (String) value.value();
    }

    private static void validateName(String name, String path) {
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
            throw LuaDeclarationValues.error(path,
                    "field names must be lowercase identifiers of at most 64 characters");
        }
    }

    private static int boundedAdd(int left, int right) {
        return left > MAX_VALUE_NODES - right ? MAX_VALUE_NODES + 1 : left + right;
    }

    private static int boundedMultiply(int left, int right) {
        return right != 0 && left > MAX_VALUE_NODES / right ? MAX_VALUE_NODES + 1 : left * right;
    }

    private static final class VectorValue {
        private final double x;
        private final double y;
        private final double z;

        private VectorValue(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class StackValue {
        private final int id;
        private final int count;
        private final int damage;

        private StackValue(int id, int count, int damage) {
            this.id = id;
            this.count = count;
            this.damage = damage;
        }

        private boolean available() {
            return id > 0 && id < Item.itemsList.length && Item.itemsList[id] != null;
        }
    }
}
