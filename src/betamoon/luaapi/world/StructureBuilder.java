package betamoon.luaapi.world;

import betamoon.worldgen.structure.StructureTemplate;
import java.io.IOException;
import java.util.IdentityHashMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Mutable table-backed structure document with non-enumerated authoring helpers. */
final class StructureBuilder extends LuaTable {
    private static final String[][] ELEMENT_METHODS = new String[][]{
        {"block", "block"},
        {"fill", "fill"},
        {"shell", "shell"},
        {"frame", "frame"},
        {"line", "line"},
        {"staircase", "staircase"},
        {"cylinder", "cylinder"},
        {"ellipsoid", "ellipsoid"},
        {"voxelMap", "voxel_map"},
        {"clear", "clear"},
        {"replaceState", "replace_state"},
        {"template", "template"},
        {"use", "template"},
        {"array", "array"},
        {"repeatElement", "repeat"},
        {"chance", "chance"},
        {"choice", "choice"},
        {"loot", "loot"},
        {"marker", "marker"}
    };

    StructureBuilder(LuaValue initial) {
        if (!initial.isnil()) {
            if (!initial.istable()) {
                throw new LuaError("worldgen.structures:builder: expected a table or nil");
            }
            copyObject((LuaTable) initial, this, new IdentityHashMap<LuaTable, LuaTable>());
        }
        if (get("format").isnil()) {
            set("format", "betamoon_structure");
        }
        tableField("palette");
        tableField("templates");
        tableField("elements");
        tableField("processors");
        installMethods();
    }

    private void tableField(String name) {
        LuaValue value = get(name);
        if (value.isnil()) {
            set(name, new LuaTable());
        } else if (!value.istable()) {
            throw new LuaError("StructureBuilder." + name + ": expected a table");
        }
    }

    private void installMethods() {
        LuaTable methods = new LuaTable();
        methods.set("state", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                StructureBuilder receiver = receiver(arguments, "state");
                String name = arguments.arg(2).checkjstring();
                LuaValue definition = copiedTable(arguments.arg(3), "StructureBuilder:state definition");
                receiver.get("palette").set(name, definition);
                return receiver;
            }
        });
        for (String[] method : ELEMENT_METHODS) {
            methods.set(method[0], element(method[0], method[1]));
        }
        methods.set("processor", append("processor", "processors"));
        methods.set("compile", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                StructureBuilder receiver = receiver(arguments, "compile");
                try {
                    return new CompiledStructureValue(StructureTemplate.read(
                            LuaStructureDocument.snapshot(receiver, "StructureBuilder")));
                } catch (IOException error) {
                    throw new LuaError("StructureBuilder:compile: " + error.getMessage());
                }
            }
        });
        methods.set("clone", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                return new StructureBuilder(receiver(arguments, "clone"));
            }
        });
        LuaTable metatable = new LuaTable();
        metatable.set("__index", methods);
        setmetatable(metatable);
    }

    private static VarArgFunction element(final String method, final String type) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                StructureBuilder receiver = receiver(arguments, method);
                LuaTable definition = (LuaTable) copiedTable(arguments.arg(2),
                        "StructureBuilder:" + method + " definition");
                LuaValue existingType = definition.get("type");
                if (!existingType.isnil() && !existingType.tojstring().equals(type)) {
                    throw new LuaError("StructureBuilder:" + method + ": definition.type must be '" + type + "'");
                }
                definition.set("type", type);
                append(receiver.get("elements"), definition, "StructureBuilder.elements");
                return receiver;
            }
        };
    }

    private static VarArgFunction append(final String method, final String field) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                StructureBuilder receiver = receiver(arguments, method);
                LuaValue definition = copiedTable(arguments.arg(2),
                        "StructureBuilder:" + method + " definition");
                append(receiver.get(field), definition, "StructureBuilder." + field);
                return receiver;
            }
        };
    }

    private static void append(LuaValue target, LuaValue value, String path) {
        if (!target.istable()) {
            throw new LuaError(path + ": expected a table");
        }
        target.set(target.length() + 1, value);
    }

    private static StructureBuilder receiver(Varargs arguments, String method) {
        if (!(arguments.arg1() instanceof StructureBuilder)) {
            throw new LuaError("StructureBuilder:" + method + ": call the method with ':'");
        }
        return (StructureBuilder) arguments.arg1();
    }

    private static LuaValue copiedTable(LuaValue value, String path) {
        if (!value.istable()) {
            throw new LuaError(path + ": expected a table");
        }
        return copy((LuaTable) value, new IdentityHashMap<LuaTable, LuaTable>());
    }

    private static LuaValue copy(LuaValue value, IdentityHashMap<LuaTable, LuaTable> copies) {
        if (value instanceof LootTableReference) {
            return value;
        }
        if (!value.istable()) {
            return value;
        }
        LuaTable source = (LuaTable) value;
        LuaTable existing = copies.get(source);
        if (existing != null) {
            return existing;
        }
        LuaTable result = new LuaTable();
        copies.put(source, result);
        copyObject(source, result, copies);
        return result;
    }

    private static void copyObject(LuaTable source, LuaTable target, IdentityHashMap<LuaTable, LuaTable> copies) {
        copies.put(source, target);
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = source.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return;
            }
            target.set(key, copy(next.arg(2), copies));
        }
    }

    static final class CompiledStructureValue extends LuaTable {
        private final StructureTemplate template;

        private CompiledStructureValue(StructureTemplate template) {
            this.template = template;
        }

        StructureTemplate template() {
            return template;
        }
    }
}
