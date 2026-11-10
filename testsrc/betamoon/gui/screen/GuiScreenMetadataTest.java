package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentRegistry;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luaapi.client.ui.GuiUiApi;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Metadata has no native/GL dependency and never resolves a transition target.
 */
final class GuiScreenMetadataTest {
    private static final String OWNER = "screen-metadata-fixture";

    static void run() {
        try {
            names();
            transitionMetadata();
        } finally {
            ScriptResourceTracker.unload(OWNER);
        }
    }

    private static void names() {
        Globals lua = JsePlatform.standardGlobals();
        GuiComponentRegistry components = new GuiComponentRegistry();
        LuaTable value = lua.load("return {key='test:metadata',root={type='text',text='Example'}}").call().checktable();
        GuiScreenDefinition fallback = GuiScreenParser.parse(OWNER, value, components);
        require(fallback.displayName.equals(fallback.key), "Screen name did not default to the canonical key");
        value.set("key", "test:" + String.join("", Collections.nCopies(160, "a")));
        GuiScreenDefinition longKey = GuiScreenParser.parse(OWNER, value, components);
        require(longKey.displayName.equals(longKey.key), "Optional labels invalidated an existing long canonical key");
        value.set("key", "test:metadata");
        value.set("displayName", "Moon appearance settings");
        GuiScreenDefinition named = GuiScreenParser.parse(OWNER, value, components);
        require(named.key.equals(fallback.key) && named.displayName.equals("Moon appearance settings")
                && named.metadataBytes > fallback.metadataBytes, "Screen label changed identity or escaped accounting");
        for (LuaValue invalid : new LuaValue[]{LuaValue.valueOf(""), LuaValue.TRUE, LuaValue.valueOf(4),
                LuaValue.valueOf(String.join("", Collections.nCopies(129, "x"))), LuaValue.valueOf("Bad\nName"),
                LuaValue.valueOf("Bad\u00a7fName"), LuaValue.valueOf("Bad\u2028Name")}) {
            value.set("displayName", invalid);
            fail(() -> GuiScreenParser.parse(OWNER, value, components));
        }
        value.set("displayName", "Moon appearance settings");
        LuaTable api = new LuaTable();
        GuiUiApi.attach(api, OWNER);
        LuaValue screens = api.get("client").get("ui").get("screens");
        LuaValue added = screens.get("add").call(value);
        require(added.get("displayName").tojstring().equals("Moon appearance settings"),
                "Registration omitted screen metadata");
        added.set("displayName", "Edited detached record");
        require(screens.get("getRequired").call(LuaValue.valueOf("test:metadata")).get("displayName").tojstring()
                .equals("Moon appearance settings"),
                "Editing a detached screen info record changed registered metadata");
        value.set("worldTransition", "close");
        fail(() -> GuiScreenParser.parse(OWNER, value, components));
    }

    private static void transitionMetadata() {
        require(!GuiWorldTransition.CLOSE.replaces() && GuiWorldTransition.CLOSE.metadataBytes == 0,
                "Default close policy acquired replacement metadata");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("message", "Welcome");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entry", nested);
        GuiWorldTransition replace = GuiWorldTransition.replace("test:forward", Collections.emptyMap(), data);
        nested.put("message", "Changed");
        require(replace.replaces() && ((Map<?, ?>) replace.data.get("entry")).get("message").equals("Welcome")
                && replace.metadataBytes > 128, "Transition initialization was not detached/accounted");
        fail(() -> replace.data.put("other", true));
        fail(() -> GuiWorldTransition.replace("invalid", Collections.emptyMap(), Collections.emptyMap()));
        fail(() -> GuiWorldTransition.replace("test:self", Collections.emptyMap(),
                Collections.singletonMap("bad", new Object())));
        fail(() -> GuiWorldTransition.replace("test:self", Collections.emptyMap(),
                Collections.singletonMap("bad", Double.NaN)));
        Map<String, Object> cycle = new LinkedHashMap<>();
        cycle.put("self", cycle);
        fail(() -> GuiWorldTransition.replace("test:self", cycle, Collections.emptyMap()));
        fail(() -> GuiWorldTransition.replace("test:self", Collections.emptyMap(),
                Collections.singletonMap("text", String.join("", Collections.nCopies(65537, "x")))));
        Map<String, Object> large = new LinkedHashMap<>();
        for (int index = 0; index < 4097; index++) {
            large.put("key" + index, index);
        }
        fail(() -> GuiWorldTransition.replace("test:self", large, Collections.emptyMap()));
    }

    private static void fail(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException | LuaError | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Invalid metadata was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
