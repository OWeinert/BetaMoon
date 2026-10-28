package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticGuidance;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeReferenceSelectors;
import betamoon.interop.reference.NativeReflection;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import betamoon.interop.value.NativeExactValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Lua factories/metadata and detached conversion; executable operations require
 * an audited current scope.
 */
public final class NativeAccessApi {
    private final Supplier<NativeIndexSnapshot> snapshots;

    NativeAccessApi(Supplier<NativeIndexSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    void attach(LuaTable api) {
        api.set("NULL", NativeLuaExact.NULL);
        api.set("dataPlan", NativeLuaSupport.function(api, args -> NativeLuaDataPlan.create(args.arg1())));
        api.set("hostType", NativeLuaSupport.function(api, args -> NativeLuaHostType.create(this, args.arg1())));
        api.set("type",
                NativeLuaSupport.function(api, args -> new NativeLuaReference(this, type(args.arg1(), args.arg(2)))));
        api.set("typeOf", NativeLuaSupport.function(api, args -> {
            LuaValue value = args.arg1();
            if (value instanceof NativeLuaExact) {
                NativeExactValue exact = ((NativeLuaExact) value).exact;
                if (exact.value == null) {
                    throw new NativeAccessFailure("null_has_no_runtime_type",
                            "typed-null declarations remain in .type");
                }
                return new NativeLuaReference(this, exact.type);
            }
            if (value instanceof NativeLuaView && !((NativeLuaView) value).classView) {
                NativeLuaView view = (NativeLuaView) value;
                Object actual = view.payload(NativeContext.current());
                return new NativeLuaReference(this, NativeContext.current().bindings.reference(actual.getClass()));
            }
            throw new NativeAccessFailure("native_type_required", "use a native view or exact value");
        }));
        api.set("value", NativeLuaSupport.function(api, args -> exact(type(args.arg1(), LuaValue.NIL), args.arg(2))));
        api.set("long", NativeLuaSupport.function(api, args -> longValue(args.arg1())));
        api.set("null", NativeLuaSupport.function(api, args -> {
            NativeTypeRef type = type(args.arg1(), LuaValue.NIL);
            if (!type.reference()) {
                throw new NativeAccessFailure("invalid_null_type", "typed null requires a reference type");
            }
            return new NativeLuaExact(this, new NativeExactValue(type, null));
        }));
        api.set("withContext", NativeLuaSupport.function(api, args -> {
            NativeContext parent = NativeContext.current();
            LuaValue callback = args.arg1().checkfunction();
            try (NativeContext child = parent.child()) {
                return callback.invoke(new NativeLuaAccess(this, child, false));
            }
        }));
    }

    public static LuaValue contextAccess(NativeContext context) {
        NativeIndexSnapshot detached = context.bindings.snapshot();
        return new NativeLuaAccess(new NativeAccessApi(() -> detached), context, false);
    }

    public static LuaValue hookValue(Object value, NativeContext context) {
        return new NativeAccessApi(context.bindings::snapshot).result(value, context, false);
    }

    public static Object hookConvert(LuaValue value, Class<?> type, NativeContext context) {
        context.check();
        NativeAccessApi host = new NativeAccessApi(context.bindings::snapshot);
        Object input = host.argument(value, context);
        context.bindings.validateNull(input, type, context);
        return NativeConversions.convert(input, type);
    }

    NativeTypeRef type(LuaValue value, LuaValue suppliedOptions) {
        return NativeReferenceSelectors.type(value, suppliedOptions);
    }

    NativeMemberRef selector(NativeTypeRef lookup, NativeMemberRef.Kind kind, Varargs args) {
        return NativeReferenceSelectors.member(lookup, kind, args);
    }

    Varargs describe(LuaValue value, LuaValue suppliedOptions) {
        NativeIndexSnapshot snapshot = snapshots.get();
        NativeLuaReference reference = value instanceof NativeMemberReference
                && ((NativeMemberReference) value).nativeMemberReference() != null
                        ? new NativeLuaReference(this, ((NativeMemberReference) value).nativeMemberReference())
                        : new NativeLuaReference(this, type(value, suppliedOptions));
        Map<String, Object> description;
        NativeTypeRef requestedType = reference.typeRef == null ? null : type(reference, suppliedOptions);
        try {
            if (requestedType != null) {
                description = NativeReflection.describe(requestedType, snapshot);
            } else {
                options(suppliedOptions);
                description = NativeReflection.describe(reference.memberRef, snapshot);
            }
        } catch (NativeAccessFailure failure) {
            if (failure.reason.equals("index_incomplete")) {
                return missing(failure.reason,
                        CatalogValues.record("coverage", snapshot.coverage, "message", failure.getMessage()));
            }
            throw failure;
        }
        if (description == null) {
            String reason = snapshot.generation == 0 ? "index_pending" : "index_incomplete";
            NativeTypeRef owner = requestedType != null
                    ? requestedType
                    : reference.memberRef.declaringType != null
                            ? reference.memberRef.declaringType
                            : reference.memberRef.lookupType;
            boolean declaredOnly = requestedType != null || reference.memberRef.declaringType != null
                    || reference.memberRef.kind == NativeMemberRef.Kind.CONSTRUCTOR;
            if (snapshot.generation > 0 && declaredOnly && owner.originId != null && snapshot.rows("origins").stream()
                    .anyMatch(row -> owner.originId.equals(row.get("id")) && "indexed".equals(row.get("status")))) {
                reason = reference.typeRef == null ? "member_not_found" : "class_not_found";
            }
            return missing(reason, snapshot.coverage);
        }
        return metadata(description, true);
    }

    LuaValue queryPage(Map<String, Object> page) {
        Map<String, Object> copy = new LinkedHashMap<>(page);
        List<Object> items = new ArrayList<>();
        for (Object value : (List<?>) page.get("items")) {
            Map<String, Object> row = CatalogValues.object(value);
            String kind = (String) row.get("kind");
            if (Arrays.asList("METHOD", "FIELD", "CONSTRUCTOR").contains(kind)) {
                items.add(NativeReflection.memberRecord(row, ((Number) page.get("generation")).longValue()));
            } else {
                items.add(row);
            }
        }
        copy.put("items", items);
        return metadata(copy, false);
    }

    LuaValue metadata(Object value, boolean readOnly) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof NativeTypeRef) {
            return new NativeLuaReference(this, (NativeTypeRef) value);
        }
        if (value instanceof NativeMemberRef) {
            return new NativeLuaReference(this, (NativeMemberRef) value);
        }
        if (value instanceof String) {
            return LuaValue.valueOf((String) value);
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf((Boolean) value);
        }
        if (value instanceof Number) {
            return LuaValue.valueOf(((Number) value).doubleValue());
        }
        Map<LuaValue, LuaValue> fields = new LinkedHashMap<>();
        if (value instanceof List) {
            int index = 1;
            for (Object element : (List<?>) value) {
                fields.put(LuaValue.valueOf(index++), metadata(element, readOnly));
            }
        } else {
            for (Map.Entry<String, Object> entry : CatalogValues.object(value).entrySet()) {
                fields.put(LuaValue.valueOf(entry.getKey()), metadata(entry.getValue(), readOnly));
            }
        }
        LuaTable table = readOnly ? new NativeLuaSupport.ReadOnlyTable(fields) : new LuaTable();
        if (!readOnly) {
            for (Map.Entry<LuaValue, LuaValue> entry : fields.entrySet()) {
                table.rawset(entry.getKey(), entry.getValue());
            }
        }
        Object ref = value instanceof Map ? ((Map<?, ?>) value).get("ref") : null;
        if (ref instanceof NativeMemberRef && ((NativeMemberRef) ref).kind == NativeMemberRef.Kind.METHOD) {
            LuaValue call = NativeLuaSupport.function(table,
                    args -> new NativeLuaMethod(this, NativeContext.current(), false, (NativeMemberRef) ref)
                            .callSelected(args));
            if (readOnly) {
                ((NativeLuaSupport.ReadOnlyTable) table).install("call", call);
            } else {
                table.set("call", call);
            }
        }
        return table;
    }

    NativeMemberRef methodRecord(LuaValue record) {
        LuaTable table = record.checktable();
        LuaValue ref = table.rawget("ref");
        if (ref instanceof NativeMemberReference && ((NativeMemberReference) ref).nativeMemberReference() != null) {
            return ((NativeMemberReference) ref).nativeMemberReference();
        }
        Map<String, Object> values = CatalogValues.record("kind", text(table.get("kind")), "declaringType",
                text(table.get("declaringType")), "name", text(table.get("name")), "descriptor",
                text(table.get("descriptor")), "originId", optionalText(table.get("originId")), "nameDomain",
                optionalText(table.get("nameDomain")));
        LuaValue ids = table.get("definingLoaderIds");
        if (ids.istable() && ids.length() == 1) {
            values.put("definingLoaderIds", Arrays.asList(text(ids.get(1))));
        }
        return NativeReflection.memberReference(values);
    }

    Object receiver(LuaValue value, NativeContext context) {
        if (value.isnil()) {
            return null;
        }
        if (value instanceof NativeLuaExact && ((NativeLuaExact) value).exact.value instanceof String) {
            context.check();
            return ((NativeLuaExact) value).exact.value;
        }
        if (!(value instanceof NativeLuaView) || ((NativeLuaView) value).classView) {
            throw new NativeAccessFailure("wrong_receiver", "expected a native object view or nil for a static call");
        }
        Object result = ((NativeLuaView) value).payload(context);
        context.retainScoped(result);
        return result;
    }

    Object argument(LuaValue value, NativeContext context) {
        if (value instanceof NativeLuaExact) {
            return ((NativeLuaExact) value).exact;
        }
        if (value instanceof NativeLuaView && !((NativeLuaView) value).classView) {
            Object result = ((NativeLuaView) value).payload(context);
            context.retainScoped(result);
            return result;
        }
        switch (value.type()) {
            case LuaValue.TBOOLEAN:
                return value.toboolean();
            case LuaValue.TNUMBER:
                return value.todouble();
            case LuaValue.TSTRING:
                return text(value);
            default:
                throw new NativeAccessFailure("conversion_failed", "use native values, views or native.NULL");
        }
    }

    Object[] arguments(Varargs values, NativeContext context) {
        if (values.narg() > 255) {
            throw new NativeAccessFailure("argument_count", "too many native arguments");
        }
        Object[] result = new Object[values.narg()];
        for (int i = 0; i < result.length; i++) {
            result[i] = argument(values.arg(i + 1), context);
        }
        return result;
    }

    NativeContext operationContext(NativeContext context, Varargs arguments) {
        NativeContext result = context;
        for (int i = 1; i <= arguments.narg(); i++) {
            if (arguments.arg(i) instanceof NativeLuaView) {
                NativeLuaView view = (NativeLuaView) arguments.arg(i);
                result = result.inheritOwnership(view.executionContext());
            }
        }
        return result;
    }

    LuaValue result(Object value, NativeContext context, boolean direct) {
        context.check();
        if (value == null) {
            return NativeLuaExact.NULL;
        }
        if (value instanceof Long) {
            return new NativeLuaExact(this, new NativeExactValue(NativeTypeRef.named("long", null, null, null), value));
        }
        if (value instanceof Character) {
            return new NativeLuaExact(this, new NativeExactValue(NativeTypeRef.named("char", null, null, null), value));
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf((Boolean) value);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return LuaValue.valueOf(((Number) value).intValue());
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) && !NativeConversions.negativeZero(number)
                    ? LuaValue.valueOf(number)
                    : new NativeLuaExact(this, new NativeExactValue(
                            NativeTypeRef.named(value instanceof Float ? "float" : "double", null, null, null), value));
        }
        if (value instanceof String) {
            String text = (String) value;
            if (text.length() > 1048576) {
                throw new NativeAccessFailure("native_value_limit", "native text exceeds 1 Mi UTF-16 units");
            }
            return NativeConversions.validUtf16(text)
                    ? LuaValue.valueOf(text)
                    : new NativeLuaExact(this,
                            new NativeExactValue(NativeTypeRef.named("java.lang.String", "runtime", null, null), text));
        }
        return new NativeLuaView(this, context, direct, value);
    }

    private LuaValue exact(NativeTypeRef type, LuaValue value) {
        Object scalar;
        String name = type.name();
        if (name.equals("char")) {
            scalar = value.type() == LuaValue.TSTRING ? text(value) : null;
            if (scalar instanceof String && ((String) scalar).length() == 1) {
                scalar = ((String) scalar).charAt(0);
            } else {
                scalar = (char) integer(value, 0, 65535);
            }
        } else if (name.equals("java.lang.String")) {
            scalar = text(value);
        } else if (name.equals("boolean")) {
            if (!value.isboolean()) {
                throw new NativeAccessFailure("conversion_failed", "boolean required");
            }
            scalar = value.toboolean();
        } else if (name.equals("long")) {
            return longValue(value);
        } else {
            Class<?> primitive = NativeConversions.primitive(name);
            if (primitive == null || primitive == Void.TYPE) {
                throw new NativeAccessFailure("unsupported_exact_type", name);
            }
            if (value.type() == LuaValue.TSTRING && (primitive == Float.TYPE || primitive == Double.TYPE)) {
                String text = text(value);
                double number = text.equals("NaN")
                        ? Double.NaN
                        : text.equals("Infinity")
                                ? Double.POSITIVE_INFINITY
                                : text.equals("-Infinity") ? Double.NEGATIVE_INFINITY : Double.NaN;
                if (text.equals("-0.0")) {
                    number = -0.0;
                }
                if (!Arrays.asList("NaN", "Infinity", "-Infinity", "-0.0").contains(text)) {
                    throw new NativeAccessFailure("conversion_failed", "invalid floating special value");
                }
                scalar = primitive == Float.TYPE ? (Object) Float.valueOf((float) number) : Double.valueOf(number);
            } else {
                scalar = NativeConversions.numeric(number(value), primitive);
            }
        }
        return new NativeLuaExact(this, new NativeExactValue(type, scalar));
    }

    private NativeLuaExact longValue(LuaValue input) {
        long value;
        if (input.type() == LuaValue.TSTRING) {
            String decimal = text(input);
            if (!decimal.matches("[+-]?[0-9]+") || decimal.length() > 32) {
                throw new NativeAccessFailure("invalid_long", "expected signed decimal text");
            }
            try {
                value = Long.parseLong(decimal);
            } catch (NumberFormatException exception) {
                throw new NativeAccessFailure("long_overflow", decimal);
            }
        } else {
            double number = number(input);
            if (number != Math.rint(number) || Math.abs(number) > NativeConversions.SAFE_INTEGER) {
                throw new NativeAccessFailure("invalid_long", "use decimal text outside Lua's safe integer range");
            }
            value = (long) number;
        }
        return new NativeLuaExact(this, new NativeExactValue(NativeTypeRef.named("long", null, null, null), value));
    }

    static LuaTable options(LuaValue value, String... allowed) {
        if (value.isnil()) {
            return new LuaTable();
        }
        LuaTable table = value.checktable();
        if (table.keyCount() > 16) {
            throw new LuaError("native: too many options");
        }
        for (LuaValue key : table.keys()) {
            if (key.type() != LuaValue.TSTRING || !Arrays.asList(allowed).contains(key.tojstring())) {
                throw new LuaError("native: unknown option " + key.tojstring());
            }
        }
        return table;
    }

    static String text(LuaValue value) {
        if (value.type() != LuaValue.TSTRING) {
            throw new LuaError("native: expected string");
        }
        LuaString string = value.checkstring();
        return NativeConversions
                .utf8(Arrays.copyOfRange(string.m_bytes, string.m_offset, string.m_offset + string.m_length));
    }

    static String optionalText(LuaValue value) {
        return value.isnil() ? null : text(value);
    }

    static int integer(LuaValue value, int minimum, int maximum) {
        double number = number(value);
        if (number != Math.rint(number) || number < minimum || number > maximum) {
            throw new NativeAccessFailure("index_or_integer_range",
                    "expected integer in [" + minimum + "," + maximum + "]");
        }
        return (int) number;
    }

    private static double number(LuaValue value) {
        if (value.type() != LuaValue.TNUMBER || !Double.isFinite(value.todouble())) {
            throw new NativeAccessFailure("conversion_failed", "expected finite number");
        }
        return value.todouble();
    }

    private Varargs missing(String reason, Map<String, Object> detail) {
        Map<String, Object> copy = new LinkedHashMap<>(detail);
        copy.put("guidance", NativeDiagnosticGuidance.forReason(reason));
        return absent(reason, metadata(copy, false));
    }

    static Varargs absent(String reason, LuaValue detail) {
        return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(reason), detail});
    }
}
