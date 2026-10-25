package betamoon.luaapi.interop;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeQueryService;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.reference.NativeReflection;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Metadata-only Lua facade. Native object roots and executable binding arrive
 * in N3/N4.
 */
public final class NativeDiscoveryApi {
    public interface Backend {
        NativeIndexSnapshot snapshot();

        Map<String, Object> status();

        NativeDiscoveryService.Ticket refresh();

        void requireClient();
    }

    private NativeDiscoveryApi() {
    }

    public static void attach(LuaTable module, String source) {
        attach(module, source, new Backend() {
            public NativeIndexSnapshot snapshot() {
                return NativeDiscoveryRuntime.snapshot();
            }

            public Map<String, Object> status() {
                return NativeDiscoveryRuntime.status();
            }

            public NativeDiscoveryService.Ticket refresh() {
                return NativeDiscoveryRuntime.refresh();
            }

            public void requireClient() {
                NativeDiscoveryRuntime.requireClientAccess();
            }
        });
    }

    static void attach(LuaTable module, String source, Backend backend) {
        LuaTable api = new LuaTable();
        NativeAccessApi access = new NativeAccessApi(backend::snapshot);
        long generation = ScriptResourceTracker.ownerGeneration(source);
        RefreshLease lease = new RefreshLease();
        api.set("query", function(api,
                args -> access.queryPage(NativeQueryService.query(backend.snapshot(), options(args.arg1())))));
        api.set("status", function(api, args -> value(backend.status())));
        api.set("describe", NativeLuaSupport.function(api, args -> access.describe(args.arg1(), args.arg(2))));
        api.set("source", NativeLuaSupport.function(api, args -> {
            Map<String, Object> options = options(args.arg(2));
            keys(options, "originId");
            NativeIndexSnapshot snapshot = backend.snapshot();
            String typeName;
            Map<String, Object> type;
            if (args.arg1() instanceof NativeLuaReference) {
                NativeLuaReference reference = (NativeLuaReference) args.arg1();
                NativeTypeRef owner = reference.typeRef != null
                        ? reference.typeRef
                        : reference.memberRef.declaringType != null
                                ? reference.memberRef.declaringType
                                : reference.memberRef.lookupType;
                if (reference.memberRef != null) {
                    Map<String, Object> member = NativeReflection.describe(reference.memberRef, snapshot);
                    if (member != null) {
                        owner = (NativeTypeRef) member.get("declaringTypeRef");
                    }
                }
                typeName = owner.name();
                if (!options.containsKey("originId") && owner.originId != null) {
                    options.put("originId", owner.originId);
                }
                type = NativeReflection.describe(owner.selected(null, null, optionText(options, "originId")), snapshot);
            } else {
                typeName = args.checkjstring(1);
                type = NativeQueryService.describe(snapshot, typeName, optionText(options, "originId"));
            }
            if (type == null) {
                return absent(snapshot.generation == 0 ? "index_pending" : "class_not_found", snapshot.coverage);
            }
            List<?> matches = type.get("sourceMatches") instanceof List
                    ? (List<?>) type.get("sourceMatches")
                    : Collections.emptyList();
            boolean available = matches.stream()
                    .anyMatch(row -> Boolean.TRUE.equals(CatalogValues.object(row).get("available"))
                            && !"source_mismatch".equals(CatalogValues.object(row).get("confidence")));
            Map<String, Object> detail = CatalogValues.record("available", available, "matches", matches, "generation",
                    snapshot.generation);
            return available ? value(detail) : absent("source_unavailable", detail);
        }));
        api.set("readSource", function(api, args -> {
            requireIo(source, generation, backend);
            Map<String, Object> options = options(args.arg(2));
            keys(options, "startLine", "limit");
            NativeIndexSnapshot snapshot = backend.snapshot();
            if (snapshot.sources == null) {
                return absent("index_pending", snapshot.coverage);
            }
            try {
                return value(snapshot.sources.read(args.checkjstring(1), number(options, "startLine", 1),
                        number(options, "limit", 200)));
            } catch (IOException exception) {
                String message = exception.getMessage();
                String reason = Arrays.asList("source_mismatch", "source_page_limit", "source_outside_attachment")
                        .contains(message) ? message : "source_unavailable";
                return absent(reason, CatalogValues.record("message", message));
            }
        }));
        api.set("refresh", function(api, args -> {
            requireIo(source, generation, backend);
            if (args.narg() > 0) {
                keys(options(args.arg1()));
            }
            if (!lease.tracked) {
                ScriptResourceTracker.track(lease::close);
                lease.tracked = true;
            }
            if (lease.current != null && "pending".equals(lease.current.status().get("status"))) {
                return ticket(lease.current, source, generation, backend);
            }
            lease.current = backend.refresh();
            return ticket(lease.current, source, generation, backend);
        }));
        module.set("native", api);
        access.attach(api);
    }

    private static LuaTable ticket(NativeDiscoveryService.Ticket ticket, String source, long generation,
            Backend backend) {
        LuaTable handle = new LuaTable();
        handle.set("status", function(handle, args -> {
            requireOwner(source, generation);
            return value(ticket.status());
        }));
        handle.set("cancel", function(handle, args -> {
            requireIo(source, generation, backend);
            ticket.close();
            return LuaValue.NONE;
        }));
        return handle;
    }

    private static void requireOwner(String source, long generation) {
        if (source != null && ScriptResourceTracker.ownerGeneration(source) != generation) {
            throw new LuaError("native: stale_owner");
        }
    }

    private static void requireIo(String source, long generation, Backend backend) {
        requireOwner(source, generation);
        LuaCallbackScope.requireGameplayWrite();
        LuaCallbackScope.requireUiStateWrite();
        backend.requireClient();
    }

    private static VarArgFunction function(LuaTable self, Action action) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return action.run(args.arg1() == self ? args.subargs(2) : args);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw new LuaError("native: " + exception.getMessage());
                }
            }
        };
    }

    private static Map<String, Object> options(LuaValue value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value.isnil()) {
            return result;
        }
        LuaTable table = value.checktable();
        if (table.keyCount() > 24) {
            throw new LuaError("native: too many option fields");
        }
        for (LuaValue key : table.keys()) {
            if (key.type() != LuaValue.TSTRING) {
                throw new LuaError("native: option keys must be strings");
            }
            LuaValue entry = table.rawget(key);
            Object converted;
            if (entry.type() == LuaValue.TSTRING) {
                converted = entry.tojstring();
            } else if (entry.isboolean()) {
                converted = entry.toboolean();
            } else if (entry.isnumber() && Double.isFinite(entry.todouble()) && entry.todouble() == entry.toint()) {
                converted = entry.toint();
            } else {
                throw new LuaError("native: expected string, boolean or integer option: " + key.tojstring());
            }
            result.put(key.tojstring(), converted);
        }
        return result;
    }

    private static void keys(Map<String, Object> options, String... allowed) {
        for (String key : options.keySet()) {
            if (!Arrays.asList(allowed).contains(key)) {
                throw new LuaError("native: unknown option: " + key);
            }
        }
    }

    private static int number(Map<String, Object> values, String key, int fallback) {
        return values.containsKey(key) ? CatalogValues.integer(values, key) : fallback;
    }

    private static String optionText(Map<String, Object> options, String key) {
        if (!options.containsKey(key)) {
            return null;
        }
        return CatalogValues.text(options, key);
    }

    private static Varargs absent(String reason, Map<String, Object> detail) {
        return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(reason), value(detail)});
    }

    private static LuaValue value(Object value) {
        if (value == null) {
            return LuaValue.NIL;
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
        LuaTable result = new LuaTable();
        if (value instanceof List) {
            int index = 1;
            for (Object entry : (List<?>) value) {
                result.rawset(index++, value(entry));
            }
        } else {
            for (Map.Entry<String, Object> entry : CatalogValues.object(value).entrySet()) {
                result.rawset(entry.getKey(), value(entry.getValue()));
            }
        }
        return result;
    }

    private interface Action {
        Varargs run(Varargs args);
    }

    private static final class RefreshLease implements AutoCloseable {
        private NativeDiscoveryService.Ticket current;
        private boolean tracked;

        @Override
        public void close() {
            if (current != null) {
                current.close();
                current = null;
            }
        }
    }
}
