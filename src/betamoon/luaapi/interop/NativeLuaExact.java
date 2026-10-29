package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.value.NativeConversions;
import betamoon.interop.value.NativeExactValue;
import java.util.Objects;
import org.luaj.vm2.LuaValue;

final class NativeLuaExact extends NativeLuaHandle {
    static final NativeLuaExact NULL = new NativeLuaExact(null, NativeExactValue.NULL);
    final NativeExactValue exact;
    private final NativeAccessApi host;

    NativeLuaExact(NativeAccessApi host, NativeExactValue exact) {
        super(exact.value == null ? "native.NULL" : "Native value(" + exact.type.name() + ")");
        this.exact = exact;
        this.host = host;
    }

    @Override
    protected LuaValue createProperty(String name) {
        switch (name) {
            case "isNull":
                return valueOf(exact.value == null);
            case "type":
                return exact.type == null ? NIL : host.reference(exact.type);
            case "decimal":
                return NativeLuaSupport.function(this, args -> {
                    if (!(exact.value instanceof Long)) {
                        throw new NativeAccessFailure("not_an_exact_long", "decimal() requires a native long");
                    }
                    return valueOf(Long.toString((Long) exact.value));
                });
            case "toNumber":
                return NativeLuaSupport.function(this, args -> {
                    NativeAccessApi.options(args.arg1(), "lossy");
                    boolean lossy = args.arg1().istable() && args.arg1().get("lossy").optboolean(false);
                    if (exact.value instanceof Character) {
                        return valueOf((int) (Character) exact.value);
                    }
                    if (!(exact.value instanceof Number)) {
                        throw new NativeAccessFailure("not_a_numeric_value", "toNumber() requires a native number");
                    }
                    double number = ((Number) exact.value).doubleValue();
                    if (!lossy && ((exact.value instanceof Long && ((Long) exact.value < -NativeConversions.SAFE_INTEGER
                            || (Long) exact.value > NativeConversions.SAFE_INTEGER)) || !Double.isFinite(number)
                            || NativeConversions.negativeZero(number))) {
                        throw new NativeAccessFailure("lossy_conversion", "use toNumber({lossy=true}) explicitly");
                    }
                    return valueOf(number);
                });
            case "compare":
                return NativeLuaSupport.function(this, args -> {
                    if (!(args.arg1() instanceof NativeLuaExact) || !(exact.value instanceof Long)
                            || !(((NativeLuaExact) args.arg1()).exact.value instanceof Long)) {
                        throw new NativeAccessFailure("long_comparison_requires_long",
                                "compare another exact native long");
                    }
                    return valueOf(Long.compare((Long) exact.value, (Long) ((NativeLuaExact) args.arg1()).exact.value));
                });
            case "escaped":
                return NativeLuaSupport.function(this, args -> {
                    String text = text();
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < text.length(); i++) {
                        int unit = text.charAt(i);
                        result.append("\\u");
                        for (int shift = 12; shift >= 0; shift -= 4) {
                            result.append("0123456789abcdef".charAt((unit >>> shift) & 15));
                        }
                    }
                    return valueOf(result.toString());
                });
            case "length":
                return NativeLuaSupport.function(this, args -> valueOf(text().length()));
            case "unit":
                return NativeLuaSupport.function(this, args -> {
                    String text = text();
                    int index = NativeAccessApi.integer(args.arg1(), 0, text.length() - 1);
                    return valueOf((int) text.charAt(index));
                });
            default:
                return NIL;
        }
    }

    private String text() {
        if (exact.value instanceof String) {
            return (String) exact.value;
        }
        if (exact.value instanceof Character) {
            return String.valueOf((Character) exact.value);
        }
        throw new NativeAccessFailure("not_a_utf16_value", "use a native string or char");
    }

    @Override
    public LuaValue get(LuaValue key) {
        LuaValue result = super.get(key);
        if (exact.value == null && result.isnil()) {
            throw NativeLuaSupport.error(new NativeAccessFailure("null_receiver", "cannot traverse Java null"));
        }
        return result;
    }

    @Override
    public boolean eq_b(LuaValue other) {
        if (!(other instanceof NativeLuaExact)) {
            return false;
        }
        NativeExactValue value = ((NativeLuaExact) other).exact;
        return exact.type == null
                ? value.type == null && value.value == null
                : value.type != null && exact.type.key().equals(value.type.key())
                        && Objects.equals(exact.value, value.value);
    }
}
