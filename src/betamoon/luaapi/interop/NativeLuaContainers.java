package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeContainerPolicy;
import betamoon.interop.value.NativeConversions;
import betamoon.interop.value.NativeExactValue;
import java.lang.reflect.Array;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaError;

/**
 * Explicit bounded operations. Custom collections/keys may execute arbitrary
 * code and need direct policy.
 */
final class NativeLuaContainers {
    private NativeLuaContainers() {
    }

    static boolean supports(String name) {
        switch (name) {
            case "length":
            case "getAt":
            case "setAt":
            case "setAll":
            case "add":
            case "removeAt":
            case "snapshot":
                return true;
            default:
                return false;
        }
    }

    static LuaValue create(NativeLuaView view, NativeAccessApi host, boolean direct, Supplier<Object> receiver,
            String name) {
        switch (name) {
            case "length":
                return function(view, args -> {
                    NativeContext execution = view.executionContext();
                    Object object = receiver.get();
                    read(execution, direct, object);
                    return LuaValue.valueOf(size(object, execution));
                });
            case "getAt":
                return function(view, args -> {
                    NativeContext execution = view.executionContext();
                    Object object = receiver.get();
                    read(execution, direct, object);
                    Object value;
                    if (object instanceof Map) {
                        value = ((Map<?, ?>) object).get(convertKey(host, args.arg1(), execution));
                    } else {
                        int index = NativeAccessApi.integer(args.arg1(), 0, size(object, execution) - 1);
                        value = object.getClass().isArray() ? Array.get(object, index) : ((List<?>) object).get(index);
                    }
                    return host.result(value, execution, direct);
                });
            case "setAt":
                return function(view, args -> {
                    NativeContext execution = view.executionContext();
                    Object object = receiver.get();
                    write(execution, direct, object);
                    Object value = convertKey(host, args.arg(2), execution);
                    if (object instanceof Map) {
                        put(object, convertKey(host, args.arg1(), execution), value);
                    } else {
                        int index = NativeAccessApi.integer(args.arg1(), 0, size(object, execution) - 1);
                        if (object.getClass().isArray()) {
                            Class<?> component = object.getClass().getComponentType();
                            Object input = host.argument(args.arg(2), execution);
                            execution.bindings.validateNull(input, component, execution);
                            Array.set(object, index, NativeConversions.convert(input, component));
                        } else {
                            setList(object, index, value);
                        }
                    }
                    return LuaValue.NONE;
                });
            case "setAll":
                return function(view, args -> {
                    NativeContext execution = view.executionContext();
                    Object object = receiver.get();
                    if (!object.getClass().isArray()) {
                        throw new NativeAccessFailure("array_required", "setAll is an atomic validated array batch");
                    }
                    write(execution, direct, object);
                    int start = NativeAccessApi.integer(args.arg1(), 0, size(object, execution));
                    LuaTable sequence = args.arg(2).checktable();
                    int count = NativeLuaAccess.sequence(sequence, 16384);
                    if ((long) start + count > size(object, execution)) {
                        throw new NativeAccessFailure("array_bounds", "batch exceeds array length");
                    }
                    Class<?> component = object.getClass().getComponentType();
                    Object[] values = new Object[count];
                    for (int i = 0; i < count; i++) {
                        Object input = host.argument(sequence.get(i + 1), execution);
                        execution.bindings.validateNull(input, component, execution);
                        values[i] = NativeConversions.convert(input, component);
                    }
                    for (int i = 0; i < count; i++) {
                        Array.set(object, start + i, values[i]);
                    }
                    return LuaValue.NONE;
                });
            case "add":
                return function(view, args -> {
                    NativeContext execution = view.executionContext();
                    Object object = receiver.get();
                    write(execution, direct, object);
                    if (!(object instanceof List)) {
                        throw new NativeAccessFailure("list_required", "add requires a native List");
                    }
                    addList(object, convertKey(host, args.arg1(), execution));
                    return LuaValue.NONE;
                });
            case "removeAt":
                return function(view, args -> {
                    NativeContext execution = view.executionContext();
                    Object object = receiver.get();
                    write(execution, direct, object);
                    Object removed = object instanceof Map
                            ? ((Map<?, ?>) object).remove(convertKey(host, args.arg1(), execution))
                            : object instanceof List
                                    ? ((List<?>) object).remove(
                                            NativeAccessApi.integer(args.arg1(), 0, size(object, execution) - 1))
                                    : unsupported();
                    return host.result(removed, execution, direct);
                });
            case "snapshot":
                return function(view, args -> snapshot(host, direct, receiver.get(), view.executionContext(), args));
            default:
                return LuaValue.NIL;
        }
    }

    private static LuaValue snapshot(NativeAccessApi host, boolean direct, Object object, NativeContext execution,
            Varargs args) {
        read(execution, direct, object);
        int limit = args.arg1().isnil() ? 128 : NativeAccessApi.integer(args.arg1(), 1, 1000);
        int total = size(object, execution);
        LuaTable values = new LuaTable(Math.max(0, Math.min(total, limit)), 0);
        boolean detached = true;
        int count = 0;
        long[] remaining = {1048576};
        if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            Set<? extends Map.Entry<?, ?>> entries = external(execution, map::entrySet);
            Iterator<? extends Map.Entry<?, ?>> iterator = external(execution, entries::iterator);
            while (external(execution, iterator::hasNext)) {
                Map.Entry<?, ?> entry = external(execution, iterator::next);
                if (count == limit) {
                    break;
                }
                if (!consume(remaining, external(execution, entry::getKey), external(execution, entry::getValue))) {
                    break;
                }
                // Preserve custom entry access order, including repeated getters used for
                // conversion.
                LuaValue key = host.result(external(execution, entry::getKey), execution, direct);
                LuaValue value = host.result(external(execution, entry::getValue), execution, direct);
                detached &= !(key instanceof NativeLuaView) && !(value instanceof NativeLuaView);
                LuaTable pair = new LuaTable(0, 2);
                pair.set("key", key);
                pair.set("value", value);
                values.set(++count, pair);
            }
        } else {
            // Only exact JDK LinkedList uses the audited linear cursor. Custom lists keep
            // get(index) semantics.
            ListIterator<?> cursor = object.getClass() == LinkedList.class ? ((List<?>) object).listIterator() : null;
            for (int index = 0; index < Math.min(total, limit); index++) {
                Object value;
                if (object.getClass().isArray()) {
                    value = Array.get(object, index);
                } else if (cursor != null) {
                    value = cursor.next();
                } else if (object.getClass() == ArrayList.class) {
                    value = ((List<?>) object).get(index);
                } else {
                    int position = index;
                    value = external(execution, () -> ((List<?>) object).get(position));
                }
                if (!consume(remaining, value)) {
                    break;
                }
                LuaValue converted = host.result(value, execution, direct);
                detached &= !(converted instanceof NativeLuaView);
                values.set(++count, converted);
            }
        }
        LuaTable result = new LuaTable(0, 4);
        result.set("items", values);
        result.set("detached", LuaValue.valueOf(detached));
        result.set("truncated", LuaValue.valueOf(count < total));
        result.set("total", LuaValue.valueOf(total));
        return result;
    }

    private static <T> T external(NativeContext context, Supplier<T> operation) {
        context.check();
        T result = operation.get();
        context.check();
        return result;
    }

    private static boolean consume(long[] remaining, Object value) {
        remaining[0] -= value instanceof String ? ((String) value).length() * 2L : 32;
        return remaining[0] >= 0;
    }

    private static boolean consume(long[] remaining, Object first, Object second) {
        remaining[0] -= first instanceof String ? ((String) first).length() * 2L : 32;
        return consume(remaining, second);
    }

    private static LuaValue function(NativeLuaView view, NativeLuaSupport.Action action) {
        return NativeLuaSupport.function(view, arguments -> {
            NativeContext execution = view.executionContext();
            try {
                Varargs result = action.run(arguments);
                execution.check();
                return result;
            } catch (NativeAccessFailure | LuaError exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new NativeAccessFailure("native_exception",
                        view.tojstring() + " container operation threw " + exception.getClass().getName(), exception);
            }
        });
    }

    private static void read(NativeContext context, boolean direct, Object object) {
        context.require(NativeContainerPolicy.effect(object, false), direct);
    }

    private static void write(NativeContext context, boolean direct, Object object) {
        context.require(NativeContainerPolicy.effect(object, true), direct);
    }

    private static int size(Object object, NativeContext context) {
        if (object.getClass().isArray()) {
            return Array.getLength(object);
        }
        if (object.getClass() == ArrayList.class || object.getClass() == LinkedList.class) {
            return ((List<?>) object).size();
        }
        return external(context,
                () -> object instanceof List ? ((List<?>) object).size() : ((Map<?, ?>) object).size());
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
