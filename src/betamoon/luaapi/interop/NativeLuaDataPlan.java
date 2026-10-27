package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.query.NativeDataPlan;
import betamoon.interop.query.NativeDataPlan.Argument;
import betamoon.interop.query.NativeDataPlan.Projection;
import betamoon.interop.query.NativeDataPlan.Selection;
import betamoon.interop.query.NativeDataPlan.Slice;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeReferenceSelectors;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.value.NativeExactValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Declaration-only parsing. The resulting opaque value holds no Lua tables,
 * functions or contexts.
 */
final class NativeLuaDataPlan extends NativeLuaHandle {
    final NativeDataPlan plan;

    private NativeLuaDataPlan(NativeDataPlan plan) {
        super("Native data plan");
        this.plan = plan;
    }

    static NativeLuaDataPlan create(LuaValue definition) {
        LuaTable table = plain(definition, "type", "fields", "calls", "maxBytes");
        NativeTypeRef type = NativeReferenceSelectors.type(table.rawget("type"), NIL);
        if (!type.reference()) {
            throw invalid("a data plan requires a reference type");
        }
        List<Selection> selections = new ArrayList<>();
        Set<String> aliases = new HashSet<>();
        int[] metadataBytes = {32 + type.key().length() * 2};
        if (!table.rawget("fields").isnil()) {
            LuaTable fields = plain(table.rawget("fields"));
            TreeMap<String, LuaValue> ordered = new TreeMap<>();
            for (LuaValue key : fields.keys()) {
                ordered.put(name(key), fields.rawget(key));
            }
            for (Map.Entry<String, LuaValue> field : ordered.entrySet()) {
                aliases.add(field.getKey());
                LuaValue value = field.getValue();
                NativeMemberRef reference = reference(value);
                LuaTable options = null;
                if (reference == null) {
                    options = plain(value, "field", "slice", "select");
                    reference = reference(options.rawget("field"));
                }
                requireKind(reference, NativeMemberRef.Kind.FIELD);
                append(selections, metadataBytes,
                        new Selection(field.getKey(), reference, Collections.emptyList(),
                                projection(options == null ? NIL : options.rawget("select")),
                                slice(options == null ? NIL : options.rawget("slice"))));
            }
        }
        if (!table.rawget("calls").isnil()) {
            LuaTable calls = plain(table.rawget("calls"));
            int length = NativeLuaAccess.sequence(calls, NativeDataPlan.MAXIMUM_WORK);
            for (int index = 1; index <= length; index++) {
                LuaTable call = plain(calls.rawget(index), "key", "method", "arguments", "select", "slice");
                String key = name(call.rawget("key"));
                if (!aliases.add(key)) {
                    throw invalid("duplicate result alias: " + key);
                }
                NativeMemberRef reference = reference(call.rawget("method"));
                requireKind(reference, NativeMemberRef.Kind.METHOD);
                if (reference.descriptor.endsWith(")V")) {
                    throw invalid("query calls require a non-void result; use ordinary call() for void methods");
                }
                List<Argument> arguments = new ArrayList<>();
                if (!call.rawget("arguments").isnil()) {
                    LuaTable values = plain(call.rawget("arguments"));
                    int count = NativeLuaAccess.sequence(values, 255);
                    for (int argument = 1; argument <= count; argument++) {
                        LuaValue value = values.rawget(argument);
                        if (value.istable()) {
                            LuaTable input = plain(value, "input");
                            arguments.add(new Argument(name(input.rawget("input")), null));
                        } else {
                            arguments.add(new Argument(null, literal(value)));
                        }
                    }
                }
                if (arguments.size() != reference.parameters().size()) {
                    throw invalid("argument count differs from the exact method descriptor: " + key);
                }
                append(selections, metadataBytes, new Selection(key, reference, arguments,
                        projection(call.rawget("select")), slice(call.rawget("slice"))));
            }
        }
        int maximum = table.rawget("maxBytes").isnil()
                ? NativeDataPlan.MAXIMUM_BYTES
                : NativeAccessApi.integer(table.rawget("maxBytes"), 1, NativeDataPlan.MAXIMUM_BYTES);
        return new NativeLuaDataPlan(new NativeDataPlan(type, selections, maximum));
    }

    private static void append(List<Selection> selections, int[] footprint, Selection selection) {
        int bytes = selection.metadataBytes();
        if (bytes > NativeDataPlan.MAXIMUM_BYTES - footprint[0]) {
            throw invalid("plan metadata exceeds 256 KiB");
        }
        footprint[0] += bytes;
        selections.add(selection);
    }

    static NativeDataPlan require(LuaValue value) {
        if (!(value instanceof NativeLuaDataPlan)) {
            throw invalid("use a plan created by betamoon.native:dataPlan()");
        }
        return ((NativeLuaDataPlan) value).plan;
    }

    static LuaTable plain(LuaValue value, String... allowed) {
        LuaValue metatable = value.getmetatable();
        if (!value.istable() || metatable != null && !metatable.isnil()) {
            throw invalid("data query definitions and inputs require plain tables");
        }
        LuaTable table = value.checktable();
        if (table.keyCount() > NativeDataPlan.MAXIMUM_WORK) {
            throw invalid("table exceeds 4096 entries");
        }
        if (allowed.length > 0) {
            List<String> keys = Arrays.asList(allowed);
            for (LuaValue key : table.keys()) {
                if (key.type() != TSTRING || !keys.contains(name(key))) {
                    throw invalid("unknown data query option");
                }
            }
        }
        return table;
    }

    static String name(LuaValue value) {
        if (value.type() != TSTRING || value.rawlen() > 128) {
            throw invalid("names must be nonempty UTF-8 strings of at most 128 bytes");
        }
        return NativeAccessApi.text(value);
    }

    private static Object literal(LuaValue value) {
        if (value instanceof NativeLuaExact) {
            NativeExactValue exact = ((NativeLuaExact) value).exact;
            if (exact.value instanceof String && ((String) exact.value).length() > 8192) {
                throw invalid("literal text exceeds 8192 UTF-16 units");
            }
            return exact;
        }
        switch (value.type()) {
            case TBOOLEAN:
                return value.toboolean();
            case TNUMBER:
                return value.todouble();
            case TSTRING:
                if (value.rawlen() > 16384) {
                    throw invalid("literal UTF-8 text exceeds 16 KiB");
                }
                return NativeAccessApi.text(value);
            default:
                throw invalid("plan literals must be detached scalars; pass borrowed arguments through inputs");
        }
    }

    private static NativeMemberRef reference(LuaValue value) {
        NativeMemberRef result = value instanceof NativeMemberReference
                ? ((NativeMemberReference) value).nativeMemberReference()
                : null;
        if (result == null && value.istable()) {
            LuaValue ref = value.checktable().rawget("ref");
            if (ref instanceof NativeMemberReference) {
                result = ((NativeMemberReference) ref).nativeMemberReference();
            }
        }
        return result;
    }

    private static void requireKind(NativeMemberRef reference, NativeMemberRef.Kind kind) {
        if (reference == null || reference.kind != kind) {
            throw invalid("use an exact " + kind.name().toLowerCase(Locale.ROOT) + " reference or info record");
        }
    }

    private static Projection projection(LuaValue value) {
        if (value.isnil()) {
            return new Projection(Projection.Kind.SCALAR, null, null);
        }
        if (value instanceof NativeLuaDataPlan) {
            return new Projection(Projection.Kind.OBJECT, require(value), null);
        }
        LuaTable table = plain(value, "each", "keys", "values");
        if (!table.rawget("each").isnil()) {
            if (!table.rawget("keys").isnil() || !table.rawget("values").isnil()) {
                throw invalid("each and map projections cannot be combined");
            }
            return new Projection(Projection.Kind.EACH, optionalPlan(table.rawget("each")), null);
        }
        return new Projection(Projection.Kind.MAP, optionalPlan(table.rawget("keys")),
                optionalPlan(table.rawget("values")));
    }

    private static NativeDataPlan optionalPlan(LuaValue value) {
        return value.isnil() || value == TRUE ? null : require(value);
    }

    private static Slice slice(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        LuaTable table = plain(value, "start", "count");
        return new Slice(NativeAccessApi.integer(table.rawget("start"), 0, Integer.MAX_VALUE),
                NativeAccessApi.integer(table.rawget("count"), 0, NativeDataPlan.MAXIMUM_WORK));
    }

    private static NativeAccessFailure invalid(String message) {
        return new NativeAccessFailure("invalid_data_plan", message);
    }
}
