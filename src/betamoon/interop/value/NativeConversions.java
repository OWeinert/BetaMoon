package betamoon.interop.value;

import betamoon.interop.binding.NativeAccessFailure;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Deterministic scalar conversion. Plain tables and LuaJ coercion do not
 * participate.
 */
public final class NativeConversions {
    public static final long SAFE_INTEGER = 9007199254740991L;
    private static final Class<?>[] NUMERIC = {Byte.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE};

    private NativeConversions() {
    }

    public static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new NativeAccessFailure("invalid_utf8", "native strings require valid UTF-8", exception);
        }
    }

    public static boolean validUtf16(String value) {
        try {
            StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    public static boolean negativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Long.MIN_VALUE;
    }

    public static int cost(Object input, Class<?> target) {
        try {
            convert(input, target);
        } catch (NativeAccessFailure exception) {
            return -1;
        }
        if (input instanceof NativeExactValue) {
            NativeExactValue exact = (NativeExactValue) input;
            if (exact.value == null) {
                return exact.type == null ? 100 : 0;
            }
            Class<?> source = primitive(exact.type.name());
            if (source != null) {
                return source == target
                        ? 0
                        : target.isPrimitive() ? widening(source, target) : 5 + distance(boxed(source), target);
            }
            input = exact.value;
        }
        if (input instanceof Double) {
            double value = (Double) input;
            Class<?> source = integral(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    ? Integer.TYPE
                    : integral(value) && Math.abs(value) <= SAFE_INTEGER ? Long.TYPE : Double.TYPE;
            if (target == source) {
                return 0;
            }
            if (target == Byte.TYPE || target == Short.TYPE || target == Float.TYPE) {
                return 10;
            }
            return target.isPrimitive() ? widening(source, target) : 20;
        }
        if (input instanceof Boolean) {
            return target == Boolean.TYPE ? 0 : target == Boolean.class ? 5 : 20;
        }
        return distance(input.getClass(), boxed(target));
    }

    public static Object convert(Object input, Class<?> target) {
        NativeExactValue exact = input instanceof NativeExactValue ? (NativeExactValue) input : null;
        Object value = exact == null ? input : exact.value;
        if (value == null) {
            if (exact == null || target.isPrimitive()) {
                throw failure("use native.NULL for Java null; primitives cannot be null");
            }
            // Typed-null assignability is resolved by NativeBindings before overload
            // selection.
            return null;
        }
        if (!target.isPrimitive() && target.isInstance(value)) {
            return value;
        }
        Class<?> primitive = unboxed(target);
        if (primitive == Boolean.TYPE && value instanceof Boolean) {
            return value;
        }
        if (primitive == Character.TYPE && exact != null && value instanceof Character) {
            return value;
        }
        if (primitive == Character.TYPE && value instanceof String && ((String) value).length() == 1) {
            return ((String) value).charAt(0);
        }
        if (value instanceof Character && primitive != null && widening(Character.TYPE, primitive) >= 0) {
            return numeric((int) (Character) value, primitive);
        }
        boolean numeric = value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double;
        if (!numeric || primitive == null || primitive == Boolean.TYPE || primitive == Character.TYPE) {
            throw failure("value is not assignable to " + target.getName());
        }
        if (exact != null && exact.type != null) {
            Class<?> source = primitive(exact.type.name());
            if (source == null || (source != primitive && widening(source, primitive) < 0)) {
                throw failure("explicit value cannot narrow to " + target.getName());
            }
            if (source == Long.TYPE && primitive == Long.TYPE) {
                return ((Number) value).longValue();
            }
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number)) {
            if (exact != null && (primitive == Float.TYPE || primitive == Double.TYPE)) {
                return primitive == Float.TYPE ? (Object) Float.valueOf((float) number) : Double.valueOf(number);
            }
            throw failure("number must be finite");
        }
        return numeric(number, primitive);
    }

    public static Object numeric(double number, Class<?> primitive) {
        if (primitive == Double.TYPE) {
            return number;
        }
        if (primitive == Float.TYPE) {
            if (Double.isFinite(number) && !Float.isFinite((float) number)) {
                throw failure("float overflow");
            }
            return (float) number;
        }
        double minimum = primitive == Byte.TYPE
                ? Byte.MIN_VALUE
                : primitive == Short.TYPE
                        ? Short.MIN_VALUE
                        : primitive == Integer.TYPE ? Integer.MIN_VALUE : -SAFE_INTEGER;
        double maximum = primitive == Byte.TYPE
                ? Byte.MAX_VALUE
                : primitive == Short.TYPE
                        ? Short.MAX_VALUE
                        : primitive == Integer.TYPE ? Integer.MAX_VALUE : SAFE_INTEGER;
        if (!integral(number) || number < minimum || number > maximum) {
            throw failure("integer is fractional, out of range or requires an exact long");
        }
        if (primitive == Byte.TYPE) {
            return (byte) number;
        }
        if (primitive == Short.TYPE) {
            return (short) number;
        }
        if (primitive == Integer.TYPE) {
            return (int) number;
        }
        return (long) number;
    }

    public static Class<?> primitive(String name) {
        switch (name) {
            case "void":
                return Void.TYPE;
            case "boolean":
                return Boolean.TYPE;
            case "byte":
                return Byte.TYPE;
            case "short":
                return Short.TYPE;
            case "int":
                return Integer.TYPE;
            case "long":
                return Long.TYPE;
            case "char":
                return Character.TYPE;
            case "float":
                return Float.TYPE;
            case "double":
                return Double.TYPE;
            default:
                return null;
        }
    }

    public static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        return Void.class;
    }

    private static Class<?> unboxed(Class<?> type) {
        for (String name : new String[]{"boolean", "byte", "short", "int", "long", "char", "float", "double"}) {
            Class<?> primitive = primitive(name);
            if (type == primitive || type == boxed(primitive)) {
                return primitive;
            }
        }
        return null;
    }

    private static int widening(Class<?> from, Class<?> to) {
        if (from == to) {
            return 0;
        }
        if (from == Character.TYPE) {
            return to == Integer.TYPE ? 1 : to == Long.TYPE ? 2 : to == Float.TYPE ? 3 : to == Double.TYPE ? 4 : -1;
        }
        int source = -1;
        int target = -1;
        for (int i = 0; i < NUMERIC.length; i++) {
            if (NUMERIC[i] == from) {
                source = i;
            }
            if (NUMERIC[i] == to) {
                target = i;
            }
        }
        return source >= 0 && target > source ? target - source : -1;
    }

    public static int distance(Class<?> source, Class<?> target) {
        if (source == target) {
            return 0;
        }
        Deque<Class<?>> pending = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        pending.add(source);
        int distance = 1;
        while (!pending.isEmpty()) {
            int count = pending.size();
            for (int i = 0; i < count; i++) {
                Class<?> type = pending.removeFirst();
                if (!visited.add(type)) {
                    continue;
                }
                if (type == target) {
                    return distance;
                }
                if (type.getSuperclass() != null) {
                    pending.add(type.getSuperclass());
                }
                for (Class<?> contract : type.getInterfaces()) {
                    pending.add(contract);
                }
            }
            distance++;
        }
        return 50;
    }

    private static boolean integral(double value) {
        return Double.isFinite(value) && value == Math.rint(value);
    }

    private static NativeAccessFailure failure(String detail) {
        return new NativeAccessFailure("conversion_failed", detail);
    }
}
