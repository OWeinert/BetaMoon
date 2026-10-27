package betamoon.interop.reference;

import betamoon.interop.value.NativeConversions;
import java.util.Arrays;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * The shared preparation/runtime symbolic factory; no class lookup, native
 * context or normal API dependency.
 */
public final class NativeReferenceSelectors {
    private NativeReferenceSelectors() {
    }

    public static boolean equal(LuaValue first, LuaValue second) {
        NativeTypeRef type = first instanceof NativeTypeReference
                ? ((NativeTypeReference) first).nativeTypeReference()
                : null;
        if (type != null) {
            NativeTypeRef other = second instanceof NativeTypeReference
                    ? ((NativeTypeReference) second).nativeTypeReference()
                    : null;
            return other != null && type.key().equals(other.key());
        }
        NativeMemberRef member = first instanceof NativeMemberReference
                ? ((NativeMemberReference) first).nativeMemberReference()
                : null;
        NativeMemberRef other = second instanceof NativeMemberReference
                ? ((NativeMemberReference) second).nativeMemberReference()
                : null;
        return member != null && other != null && member.key().equals(other.key());
    }

    public static NativeTypeRef type(LuaValue value, LuaValue supplied) {
        LuaTable options = options(supplied, "names", "loaderId", "originId");
        String names = optional(options.rawget("names"));
        String loader = optional(options.rawget("loaderId"));
        String origin = optional(options.rawget("originId"));
        if (value instanceof NativeTypeReference && ((NativeTypeReference) value).nativeTypeReference() != null) {
            return ((NativeTypeReference) value).nativeTypeReference().selected(names, loader, origin);
        }
        return NativeTypeRef.named(text(value), names, loader, origin);
    }

    public static NativeMemberRef member(NativeTypeRef type, NativeMemberRef.Kind kind, Varargs args) {
        LuaValue descriptor = args.arg(2);
        LuaValue supplied = args.arg(3);
        if (kind == NativeMemberRef.Kind.FIELD && descriptor.istable()) {
            supplied = descriptor;
            descriptor = LuaValue.NIL;
        }
        LuaTable options = options(supplied, "names", "descriptorNames", "declaringType");
        NativeTypeRef declaring = options.rawget("declaringType").isnil()
                ? null
                : type(options.rawget("declaringType"), LuaValue.NIL);
        return new NativeMemberRef(kind, type, declaring, text(args.arg1()), optional(descriptor),
                optional(options.rawget("names")), optional(options.rawget("descriptorNames")));
    }

    private static LuaTable options(LuaValue value, String... allowed) {
        if (value.isnil()) {
            return new LuaTable();
        }
        LuaValue metatable = value.getmetatable();
        if (!value.istable() || metatable != null && !metatable.isnil()) {
            throw new IllegalArgumentException("symbolic reference options require a plain table");
        }
        LuaTable table = value.checktable();
        if (table.keyCount() > allowed.length) {
            throw new IllegalArgumentException("too many symbolic reference options");
        }
        for (LuaValue key : table.keys()) {
            if (key.type() != LuaValue.TSTRING || !Arrays.asList(allowed).contains(key.tojstring())) {
                throw new IllegalArgumentException("unknown symbolic reference option " + key.tojstring());
            }
        }
        return table;
    }

    private static String optional(LuaValue value) {
        return value.isnil() ? null : text(value);
    }

    private static String text(LuaValue value) {
        if (value.type() != LuaValue.TSTRING || value.rawlen() == 0 || value.rawlen() > 8192) {
            throw new IllegalArgumentException("expected a nonempty bounded string");
        }
        LuaString bytes = value.checkstring();
        return NativeConversions
                .utf8(Arrays.copyOfRange(bytes.m_bytes, bytes.m_offset, bytes.m_offset + bytes.m_length));
    }
}
