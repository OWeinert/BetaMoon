package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import betamoon.interop.value.NativeExactValue;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;

/**
 * Explicit bounded operations. Custom collections/keys may execute arbitrary
 * code and need direct policy.
 */
final class NativeLuaContainers {
    private NativeLuaContainers() {
    }

    static void attach(NativeLuaHandle view, NativeAccessApi host, NativeContext context, boolean direct,
            Supplier<Object> receiver) {
        view.properties.put("length", function(view, args -> {
            Object object = receiver.get();
            read(context, direct, object);
            return LuaValue.valueOf(size(object));
        }));
        view.properties.put("getAt", function(view, args -> {
            Object object = receiver.get();
            read(context, direct, object);
            Object value;
            if (object instanceof Map) {
                value = ((Map<?, ?>) object).get(convertKey(host, args.arg1(), context));
            } else {
                int index = NativeAccessApi.integer(args.arg1(), 0, size(object) - 1);
                value = object.getClass().isArray() ? Array.get(object, index) : ((List<?>) object).get(index);
            }
            return host.result(value, context, direct);
        }));
        view.properties.put("setAt", function(view, args -> {
            Object object = receiver.get();
            write(context, direct, object);
            Object value = convertKey(host, args.arg(2), context);
            if (object instanceof Map) {
                put(object, convertKey(host, args.arg1(), context), value);
            } else {
                int index = NativeAccessApi.integer(args.arg1(), 0, size(object) - 1);
                if (object.getClass().isArray()) {
                    Class<?> component = object.getClass().getComponentType();
                    Object input = host.argument(args.arg(2), context);
                    context.bindings.validateNull(input, component, context);
                    Array.set(object, index, NativeConversions.convert(input, component));
                } else {
                    setList(object, index, value);
                }
            }
            return LuaValue.NONE;
        }));
        view.properties.put("setAll", function(view, args -> {
            Object object = receiver.get();
            if (!object.getClass().isArray()) {
                throw new NativeAccessFailure("array_required", "setAll is an atomic validated array batch");
            }
            write(context, direct, object);
            int start = NativeAccessApi.integer(args.arg1(), 0, size(object));
            LuaTable sequence = args.arg(2).checktable();
            int count = NativeLuaAccess.sequence(sequence, 16384);
            if ((long) start + count > size(object)) {
                throw new NativeAccessFailure("array_bounds", "batch exceeds array length");
            }
            Class<?> component = object.getClass().getComponentType();
            Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                Object input = host.argument(sequence.get(i + 1), context);
                context.bindings.validateNull(input, component, context);
                values[i] = NativeConversions.convert(input, component);
            }
            for (int i = 0; i < count; i++) {
                Array.set(object, start + i, values[i]);
            }
            return LuaValue.NONE;
        }));
        view.properties.put("add", function(view, args -> {
            Object object = receiver.get();
            write(context, direct, object);
            if (!(object instanceof List)) {
                throw new NativeAccessFailure("list_required", "add requires a native List");
            }
            addList(object, convertKey(host, args.arg1(), context));
            return LuaValue.NONE;
        }));
        view.properties.put("removeAt", function(view, args -> {
            Object object = receiver.get();
            write(context, direct, object);
            Object removed = object instanceof Map
                    ? ((Map<?, ?>) object).remove(convertKey(host, args.arg1(), context))
                    : object instanceof List
                            ? ((List<?>) object).remove(NativeAccessApi.integer(args.arg1(), 0, size(object) - 1))
                            : unsupported();
            return host.result(removed, context, direct);
        }));
        view.properties.put("snapshot", function(view, args -> {
            Object object = receiver.get();
            read(context, direct, object);
            int limit = args.arg1().isnil() ? 128 : NativeAccessApi.integer(args.arg1(), 1, 1000);
            LuaTable values = new LuaTable();
            boolean detached = true;
            int count = 0;
            int total = size(object);
            long[] remaining = {1048576};
            if (object instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    if (count == limit) {
                        break;
                    }
                    if (!consume(remaining, entry.getKey(), entry.getValue())) {
                        break;
                    }
                    LuaTable pair = new LuaTable();
                    LuaValue key = host.result(entry.getKey(), context, direct);
                    LuaValue value = host.result(entry.getValue(), context, direct);
                    detached &= !(key instanceof NativeLuaView) && !(value instanceof NativeLuaView);
                    pair.set("key", key);
                    pair.set("value", value);
                    values.set(++count, pair);
                }
            } else {
                for (int i = 0; i < Math.min(total, limit); i++) {
                    Object value = object.getClass().isArray() ? Array.get(object, i) : ((List<?>) object).get(i);
                    if (!consume(remaining, value)) {
                        break;
                    }
                    LuaValue converted = host.result(value, context, direct);
                    detached &= !(converted instanceof NativeLuaView);
                    values.set(++count, converted);
                }
            }
            LuaTable result = new LuaTable();
            result.set("items", values);
            result.set("detached", LuaValue.valueOf(detached));
            result.set("truncated", LuaValue.valueOf(count < total));
            result.set("total", LuaValue.valueOf(total));
            return result;
        }));
    }

    private static boolean consume(long[] remaining, Object... values) {
        for (Object value : values) {
            remaining[0] -= value instanceof String ? ((String) value).length() * 2L : 32;
        }
        return remaining[0] >= 0;
    }

    private static LuaValue function(NativeLuaHandle view, NativeLuaSupport.Action action) {
        return NativeLuaSupport.function(view, arguments -> {
            try {
                return action.run(arguments);
            } catch (NativeAccessFailure | LuaError exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new NativeAccessFailure("native_exception",
                        view.tojstring() + " container operation threw " + exception.getClass().getName(), exception);
            }
        });
    }

    private static void read(NativeContext context, boolean direct, Object object) {
        context.require(object.getClass().isArray() || object.getClass() == ArrayList.class
                ? NativeContext.Effect.READ
                : NativeContext.Effect.UNKNOWN, direct);
    }

    private static void write(NativeContext context, boolean direct, Object object) {
        context.require(object.getClass().isArray() || object.getClass() == ArrayList.class
                ? NativeContext.Effect.WRITE
                : NativeContext.Effect.UNKNOWN, direct);
    }

    private static int size(Object object) {
        return object.getClass().isArray()
                ? Array.getLength(object)
                : object instanceof List ? ((List<?>) object).size() : ((Map<?, ?>) object).size();
    }

    private static Object convertKey(NativeAccessApi host, LuaValue value, NativeContext context) {
        Object input = host.argument(value, context);
        return input instanceof NativeExactValue ? ((NativeExactValue) input).value : input;
    }

    @SuppressWarnings("unchecked")
    private static void put(Object map, Object key, Object value) {
        ((Map<Object, Object>) map).put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static void setList(Object list, int index, Object value) {
        ((List<Object>) list).set(index, value);
    }

    @SuppressWarnings("unchecked")
    private static void addList(Object list, Object value) {
        ((List<Object>) list).add(value);
    }

    private static Object unsupported() {
        throw new NativeAccessFailure("list_or_map_required", "this operation requires a native List or Map");
    }
}
