package betamoon.luaapi.interop;

import betamoon.interop.NativeDiscoveryTest;
import betamoon.interop.discovery.NativeDiscoveryService;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.source.NativeSourceAttachment;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Exercises the actual Lua facade with an injected metadata backend, without
 * loading Minecraft.
 */
public final class NativeDiscoveryApiTest {
    private NativeDiscoveryApiTest() {
    }

    public static void run(NativeDiscoveryService service, NativeLoaderRegistry.Capture capture,
            List<NativeSourceAttachment> sources, String sourceId) throws Exception {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        String owner = "native-discovery-api-fixture";
        NativeDiscoveryApi.attach(module, owner, new NativeDiscoveryApi.Backend() {
            public NativeIndexSnapshot snapshot() {
                return service.snapshot();
            }

            public Map<String, Object> status() {
                return service.status();
            }

            public NativeDiscoveryService.Ticket refresh() {
                return service.request(capture, "named", sources);
            }

            public void requireClient() {
                service.requireClientAccess();
            }
        });
        lua.set("betamoon", module);
        lua.set("fixtureSourceId", sourceId);
        lua.load("local n=betamoon.native; "
                + "local p=n:query({name='fixture.Known',originId='game'}); assert(#p.items==1); "
                + "p.items[1].name='tampered'; "
                + "assert(n:query({name='fixture.Known',originId='game'}).items[1].name=='fixture.Known'); "
                + "local c,reason,detail=n:describe('missing.Type'); assert(c==nil and reason=='index_incomplete'); "
                + "assert(detail.guidance.code=='inspect_selector'); "
                + "assert(n.describe('mod_Test').name=='mod_Test'); "
                + "local src=n:source('fixture.Known',{originId='game'}); assert(src.available); "
                + "local text=n:readSource(fixtureSourceId,{startLine=1,limit=1}); assert(text.lines[1]=='package fixture;'); "
                + "assert(not pcall(function() n:query({limit=1.5}) end)); "
                + "assert(not pcall(function() n:query({unknown=true}) end)); " + "assert(n:status().status=='ready')")
                .call();
        try (LuaCallbackScope ignored = LuaCallbackScope.binding()) {
            lua.load("assert(#betamoon.native:query({name='mod_Test'}).items==1); "
                    + "assert(not pcall(function() betamoon.native:refresh() end)); "
                    + "assert(not pcall(function() betamoon.native:readSource(fixtureSourceId) end))").call();
        }
        try (LuaCallbackScope ignored = new LuaCallbackScope(false, false)) {
            lua.load("assert(not pcall(function() betamoon.native:refresh() end))").call();
        }
        NativeDiscoveryService.Ticket other = service.request(capture, "named", sources);
        LuaValue api = module.get("native");
        LuaValue handle = ScriptExecution.invoke(owner, api.get("refresh"), api).arg1();
        ScriptResourceTracker.unload(owner);
        NativeDiscoveryTest.await(service, other);
        try {
            handle.get("status").call(handle);
            throw new AssertionError("Old-owner scan handle remained valid");
        } catch (LuaError expected) {
            if (!expected.getMessage().contains("stale_owner")) {
                throw expected;
            }
        }
        lua.load("assert(not pcall(function() betamoon.native:refresh() end)); "
                + "assert(#betamoon.native:query({name='mod_Test'}).items==1)").call();
    }
}
