package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Unused recursive metadata stays lazy; scoped helpers and shared root captures
 * never revive after close.
 */
final class NativeLazyFacadeTest {
    static void run(NativeBindings bindings) {
        NativeAccessApi api = new NativeAccessApi(NativeIndexSnapshot::empty);
        StringBuilder name = new StringBuilder("int");
        for (int index = 0; index < 255; index++) {
            name.append("[]");
        }
        NativeLuaReference reference = api.reference(NativeTypeRef.named(name.toString(), "runtime", null, null));
        if (!reference.properties.isEmpty() || reference.get("componentType").get("dimensions").toint() != 254) {
            throw new AssertionError("array metadata was eager or lost component information");
        }
        ScriptExecution.withOwner("native-lazy-test", () -> {
            Object subject = new Object();
            Map<String, Object> supplied = new HashMap<>();
            supplied.put("subject", subject);
            LuaValue[] retained = new LuaValue[2];
            try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
                NativeEntry entry = new NativeEntry(bindings, scope, NativeEntry.Phase.GAMEPLAY, true, supplied,
                        () -> true);
                supplied.clear();
                try (NativeContext context = entry.open("native-lazy-test")) {
                    NativeLuaAccess access = new NativeLuaAccess(api, context, false);
                    if (!access.properties.isEmpty()) {
                        throw new AssertionError("unused scoped access built operation helpers");
                    }
                    LuaValue direct = access.get("direct").call();
                    if (direct != access.get("direct").call()) {
                        throw new AssertionError("scoped direct view was rebuilt");
                    }
                    retained[0] = direct.get("describe");
                    retained[1] = access.get("subject").call();
                    try (NativeContext child = context.child()) {
                        if (child.root("subject") != subject) {
                            throw new AssertionError("caller mutation changed captured roots");
                        }
                    }
                    if (context.root("subject") != subject) {
                        throw new AssertionError("child close cleared shared parent roots");
                    }
                    try {
                        retained[1].set("describe", LuaValue.NIL);
                        throw new AssertionError("uncreated reserved helper became a writable native field");
                    } catch (LuaError expected) {
                        if (!expected.getMessage().contains("reserved_member_name")) {
                            throw expected;
                        }
                    }
                }
                for (LuaValue value : retained) {
                    try {
                        if (value instanceof NativeLuaView) {
                            value.get("describe").call();
                        } else {
                            value.call();
                        }
                        throw new AssertionError("lazy helper revived after scope close");
                    } catch (LuaError expected) {
                        if (!expected.getMessage().contains("expired_scope")) {
                            throw expected;
                        }
                    }
                }
            }
            return null;
        });
        System.out.println(
                "Lazy facades passed: bounded metadata, immutable root capture, reserved helpers and expired views.");
    }
}
