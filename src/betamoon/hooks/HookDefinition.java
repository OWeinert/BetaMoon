package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.value.NativeConversions;
import java.util.Arrays;
import java.util.Map;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Immutable declaration metadata. Lua closures are owned separately by a
 * generation.
 */
public final class HookDefinition {
    public final String owner;
    public final String file;
    public final String id;
    public final NativeMemberRef target;
    public final String at;
    public final boolean edit;
    public final int priority;
    public final boolean required;
    public final String sourceSha256;

    private HookDefinition(String owner, String file, String id, NativeMemberRef target, String at, boolean edit,
            int priority, boolean required, String sourceSha256) {
        this.owner = owner;
        this.file = file;
        this.id = id;
        this.target = target;
        this.at = at;
        this.edit = edit;
        this.priority = priority;
        this.required = required;
        this.sourceSha256 = sourceSha256;
    }

    static HookDefinition read(String owner, String file, LuaValue value) {
        LuaTable table = fields(value, "id", "target", "at", "effects", "priority", "required", "thread", "side");
        String id = text(table.rawget("id"));
        if (id.length() > 128 || !id.matches("[A-Za-z0-9_.:/-]+")) {
            throw new HookFailure("hook_file_invalid", "id must contain 1..128 identifier characters");
        }
        LuaValue requested = table.rawget("target");
        NativeMemberRef target;
        if (requested instanceof NativeMemberReference) {
            target = ((NativeMemberReference) requested).nativeMemberReference();
        } else {
            LuaTable selector = fields(requested, "class", "method", "descriptor", "names", "descriptorNames");
            target = new NativeMemberRef(NativeMemberRef.Kind.METHOD,
                    NativeTypeRef.named(text(selector.rawget("class")), optional(selector.rawget("names")), null, null),
                    null, text(selector.rawget("method")), text(selector.rawget("descriptor")),
                    optional(selector.rawget("names")), optional(selector.rawget("descriptorNames")));
        }
        if (target == null || target.kind != NativeMemberRef.Kind.METHOD) {
            throw new HookFailure("hook_file_invalid", "target must be an exact MethodRef");
        }
        if (target.lookupType.loaderId != null || target.lookupType.originId != null
                || target.declaringType != null && (target.declaringType.loaderId != null
                        || target.declaringType.originId != null || !target.declaringType.descriptor.startsWith("L"))) {
            throw new HookFailure("hook_file_invalid",
                    "target must name its declaring owner without session identities");
        }
        String at = text(table.rawget("at"));
        if (!Arrays.asList("before", "after", "error").contains(at)) {
            throw new HookFailure("hook_file_invalid", "at must be before, after or error");
        }
        String effects = optional(table.rawget("effects"));
        boolean edit = "edit".equals(effects);
        if (effects != null && !effects.equals("observe") && !edit || edit && at.equals("error")) {
            throw new HookFailure("hook_file_invalid", "effects must be observe, or edit for before/after");
        }
        for (String restriction : new String[]{"thread", "side"}) {
            LuaValue option = table.rawget(restriction);
            if (!option.isnil() && !text(option).equals("client")) {
                throw new HookFailure("hook_file_invalid", restriction + " currently supports client only");
            }
        }
        LuaValue priority = table.rawget("priority");
        if (!priority.isnil() && priority.type() != LuaValue.TNUMBER) {
            throw new HookFailure("hook_file_invalid", "priority must be a signed 32-bit integer");
        }
        double number = priority.isnil() ? 0 : priority.todouble();
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new HookFailure("hook_file_invalid", "priority must be a signed 32-bit integer");
        }
        LuaValue required = table.rawget("required");
        if (!required.isnil() && !required.isboolean()) {
            throw new HookFailure("hook_file_invalid", "required must be boolean");
        }
        return new HookDefinition(owner, file, id, target, at, edit, (int) number, required.toboolean(), "");
    }

    HookDefinition withSourceHash(String hash) {
        return new HookDefinition(owner, file, id, target, at, edit, priority, required, hash);
    }

    public String key() {
        return owner + ":" + id;
    }

    public String identity() {
        return owner.length() + ":" + owner + ":" + id.length() + ":" + id;
    }

    public String metadataKey() {
        return key() + ":" + target.key() + ":" + at + ":" + edit + ":" + priority + ":" + required;
    }

    Map<String, Object> transport() {
        return CatalogValues.record("owner", owner, "file", file, "id", id, "at", at, "edit", edit, "priority",
                priority, "required", required, "class", target.lookupType.name(), "classNames",
                target.lookupType.names, "method", target.name, "descriptor", target.descriptor, "names", target.names,
                "descriptorNames", target.descriptorNames, "sourceSha256", sourceSha256, "declaringClass",
                target.declaringType == null ? null : target.declaringType.name(), "declaringNames",
                target.declaringType == null ? null : target.declaringType.names);
    }

    static HookDefinition transport(Map<String, Object> value) {
        NativeTypeRef type = NativeTypeRef.named(CatalogValues.text(value, "class"),
                CatalogValues.text(value, "classNames"), null, null);
        NativeTypeRef declaring = value.get("declaringClass") == null
                ? null
                : NativeTypeRef.named(CatalogValues.text(value, "declaringClass"),
                        CatalogValues.text(value, "declaringNames"), null, null);
        NativeMemberRef method = new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, declaring,
                CatalogValues.text(value, "method"), CatalogValues.text(value, "descriptor"),
                CatalogValues.text(value, "names"), CatalogValues.text(value, "descriptorNames"));
        return new HookDefinition(CatalogValues.text(value, "owner"), CatalogValues.text(value, "file"),
                CatalogValues.text(value, "id"), method, CatalogValues.text(value, "at"),
                Boolean.TRUE.equals(value.get("edit")), CatalogValues.integer(value, "priority"),
                Boolean.TRUE.equals(value.get("required")), CatalogValues.text(value, "sourceSha256"));
    }

    public static LuaTable fields(LuaValue value, String... allowed) {
        LuaValue metatable = value.getmetatable();
        if (!value.istable() || metatable != null && !metatable.isnil()) {
            throw new HookFailure("hook_file_invalid", "definition data must be a plain table without a metatable");
        }
        LuaTable table = value.checktable();
        if (table.keyCount() > allowed.length) {
            throw new HookFailure("hook_file_invalid", "too many definition fields");
        }
        for (LuaValue key : table.keys()) {
            if (key.type() != LuaValue.TSTRING || !Arrays.asList(allowed).contains(key.tojstring())) {
                throw new HookFailure("hook_file_invalid", "unknown field " + key.tojstring());
            }
        }
        return table;
    }

    public static String text(LuaValue value) {
        if (value.type() != LuaValue.TSTRING || value.rawlen() == 0 || value.rawlen() > 8192) {
            throw new HookFailure("hook_file_invalid", "expected a nonempty bounded string");
        }
        LuaString bytes = value.checkstring();
        return NativeConversions
                .utf8(Arrays.copyOfRange(bytes.m_bytes, bytes.m_offset, bytes.m_offset + bytes.m_length));
    }

    public static String optional(LuaValue value) {
        return value.isnil() ? null : text(value);
    }
}
