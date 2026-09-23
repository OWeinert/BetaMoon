package betamoon.luaapi.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagByte;
import net.minecraft.src.NBTTagByteArray;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagDouble;
import net.minecraft.src.NBTTagFloat;
import net.minecraft.src.NBTTagInt;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.NBTTagLong;
import net.minecraft.src.NBTTagShort;
import net.minecraft.src.NBTTagString;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Detached, bounded NBT snapshot with type-preserving read operations. */
public final class LuaNbtView extends LuaTable {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 8192;
    private static final int MAX_LIST = 1024;
    private static final int MAX_STRING = 4096;
    private static final int MAX_BYTES = 4096;

    private final Map<String, Node> values;

    private LuaNbtView(Map<String, Node> values) {
        this.values = values;
        set("has", new Has(this));
        set("keys", new Keys(this));
        set("getType", new GetType(this));
        set("get", new Get(this));
        set("getString", new TypedGet(this, "string"));
        set("getNumber", new TypedGet(this, "number"));
        set("getBoolean", new TypedGet(this, "boolean"));
        set("getCompound", new TypedGet(this, "compound"));
        set("getList", new TypedGet(this, "list"));
    }

    public static LuaNbtView capture(NBTTagCompound compound) {
        Budget budget = new Budget();
        return new LuaNbtView(readCompound(compound, 0, budget));
    }

    private static Map<String, Node> readCompound(NBTTagCompound compound, int depth, Budget budget) {
        requireDepth(depth);
        List<String> names = new ArrayList<>();
        Map<String, NBTBase> tags = new LinkedHashMap<>();
        for (Object value : compound.func_28110_c()) {
            NBTBase tag = (NBTBase) value;
            names.add(tag.getKey());
            tags.put(tag.getKey(), tag);
        }
        Collections.sort(names);
        Map<String, Node> result = new LinkedHashMap<>();
        for (String name : names) {
            result.put(name, read(tags.get(name), depth + 1, budget));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Node read(NBTBase tag, int depth, Budget budget) {
        budget.consume();
        requireDepth(depth);
        switch (tag.getType()) {
            case 1:
                return new Node("byte", Integer.valueOf(((NBTTagByte) tag).byteValue));
            case 2:
                return new Node("short", Integer.valueOf(((NBTTagShort) tag).shortValue));
            case 3:
                return new Node("integer", Integer.valueOf(((NBTTagInt) tag).intValue));
            case 4:
                return new Node("long", Long.valueOf(((NBTTagLong) tag).longValue));
            case 5:
                return new Node("float", Double.valueOf(((NBTTagFloat) tag).floatValue));
            case 6:
                return new Node("double", Double.valueOf(((NBTTagDouble) tag).doubleValue));
            case 7:
                byte[] bytes = ((NBTTagByteArray) tag).byteArray;
                if (bytes.length > MAX_BYTES) {
                    throw new LuaError("NBT byte array exceeds " + MAX_BYTES + " entries.");
                }
                List<Node> byteValues = new ArrayList<>();
                for (byte value : bytes) {
                    byteValues.add(new Node("byte", Integer.valueOf(value & 255)));
                }
                return new Node("byte_array", Collections.unmodifiableList(byteValues));
            case 8:
                String text = ((NBTTagString) tag).stringValue;
                if (text.length() > MAX_STRING) {
                    throw new LuaError("NBT string exceeds " + MAX_STRING + " characters.");
                }
                return new Node("string", text);
            case 9:
                NBTTagList list = (NBTTagList) tag;
                if (list.tagCount() > MAX_LIST) {
                    throw new LuaError("NBT list exceeds " + MAX_LIST + " entries.");
                }
                List<Node> listValues = new ArrayList<>();
                for (int index = 0; index < list.tagCount(); index++) {
                    listValues.add(read(list.tagAt(index), depth + 1, budget));
                }
                return new Node("list", Collections.unmodifiableList(listValues));
            case 10:
                return new Node("compound", readCompound((NBTTagCompound) tag, depth + 1, budget));
            default:
                throw new LuaError("Unsupported NBT tag type " + tag.getType() + ".");
        }
    }

    private static LuaValue lua(Node node) {
        if (node == null) {
            return NIL;
        }
        if (node.value instanceof String) {
            return valueOf((String) node.value);
        }
        if (node.value instanceof Integer) {
            return valueOf(((Integer) node.value).intValue());
        }
        if (node.value instanceof Long) {
            return valueOf(((Long) node.value).longValue());
        }
        if (node.value instanceof Double) {
            return valueOf(((Double) node.value).doubleValue());
        }
        if (node.value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Node> compound = (Map<String, Node>) node.value;
            return new LuaNbtView(compound);
        }
        @SuppressWarnings("unchecked")
        List<Node> values = (List<Node>) node.value;
        LuaTable result = new LuaTable();
        for (int index = 0; index < values.size(); index++) {
            result.set(index + 1, lua(values.get(index)));
        }
        return result;
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new LuaError("NBT snapshot exceeds the maximum depth of " + MAX_DEPTH + ".");
        }
    }

    private static String key(Varargs arguments, LuaNbtView owner) {
        return arguments.arg(arguments.arg1() == owner ? 2 : 1).checkjstring();
    }

    private static final class Has extends VarArgFunction {
        private final LuaNbtView owner;
        private Has(LuaNbtView owner) {
            this.owner = owner;
        }
        public Varargs invoke(Varargs arguments) {
            return valueOf(owner.values.containsKey(key(arguments, owner)));
        }
    }

    private static final class Keys extends VarArgFunction {
        private final LuaNbtView owner;
        private Keys(LuaNbtView owner) {
            this.owner = owner;
        }
        public Varargs invoke(Varargs arguments) {
            LuaTable result = new LuaTable();
            int index = 0;
            for (String name : owner.values.keySet()) {
                result.set(++index, name);
            }
            return result;
        }
    }

    private static final class GetType extends VarArgFunction {
        private final LuaNbtView owner;
        private GetType(LuaNbtView owner) {
            this.owner = owner;
        }
        public Varargs invoke(Varargs arguments) {
            Node node = owner.values.get(key(arguments, owner));
            return node == null ? NIL : valueOf(node.type);
        }
    }

    private static final class Get extends VarArgFunction {
        private final LuaNbtView owner;
        private Get(LuaNbtView owner) {
            this.owner = owner;
        }
        public Varargs invoke(Varargs arguments) {
            return lua(owner.values.get(key(arguments, owner)));
        }
    }

    private static final class TypedGet extends VarArgFunction {
        private final LuaNbtView owner;
        private final String expected;
        private TypedGet(LuaNbtView owner, String expected) {
            this.owner = owner;
            this.expected = expected;
        }
        public Varargs invoke(Varargs arguments) {
            String key = key(arguments, owner);
            Node node = owner.values.get(key);
            if (node == null) {
                return NIL;
            }
            boolean number = "number".equals(expected) && (node.value instanceof Number);
            boolean bool = "boolean".equals(expected) && "byte".equals(node.type);
            if (!number && !bool && !expected.equals(node.type)) {
                throw new LuaError("NBT field '" + key + "' is " + node.type + ", expected " + expected + ".");
            }
            if (bool) {
                return valueOf(((Integer) node.value).intValue() != 0);
            }
            return lua(node);
        }
    }

    private static final class Node {
        private final String type;
        private final Object value;
        private Node(String type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final class Budget {
        private int nodes;
        private void consume() {
            if (++nodes > MAX_NODES) {
                throw new LuaError("NBT snapshot exceeds " + MAX_NODES + " values.");
            }
        }
    }
}
