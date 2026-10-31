package betamoon.gui.collection;

import betamoon.gui.presentation.GuiProperties;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.luaapi.client.ui.GuiLuaCollectionValues;
import betamoon.luaapi.client.ui.GuiLuaData;
import betamoon.luaapi.client.ui.GuiPropertyValues;
import java.util.Collections;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Large collection fields keep their own limits without relaxing ordinary
 * detached state/data.
 */
public final class GuiCollectionValuesTest {
    public static void main(String[] arguments) {
        Globals lua = JsePlatform.standardGlobals();
        LuaValue records = lua.load(
                "rows={};for i=1,4096 do rows[i]={key='row'..i,label='Row '..i," + "data={index=i}} end;return rows")
                .call();
        GuiPropertyDescriptor field = GuiPropertyDescriptor.value("records", Kind.COLLECTION, Collections.emptyList());
        GuiCollectionValues values = (GuiCollectionValues) GuiPropertyValues.read(field, records);
        require(values.size() == 4096 && values.model().records.size() == 4096 && field.normalize(values) == values,
                "A complete collection failed admission or repeated normalization discarded its validated snapshot");
        lua.load("rows[1].label='Changed';rows[1].data.index=99").call();
        require(values.model().records.get(0).label.equals("Row 1")
                && values.model().records.get(0).data.get("index").equals(1.0),
                "A collection retained mutable Lua record or payload values");
        expect(() -> values.get(0).put("label", "Changed"));
        expect(() -> values.clear());
        expect(() -> GuiLuaData.read(records, "ordinaryData"));
        for (String source : new String[]{"return {{key='same'},{key='same'}}",
                "return {{key='child',parent='missing'}}", "return {{key='a',parent='b'},{key='b',parent='a'}}",
                "return {{key=1}}", "return {{key='a',enabled=1}}", "return {{key='a',unknown=true}}",
                "return {[1]={key='a'},[3]={key='b'}}", "return {{key='a',data=function()end}}",
                "local t={};t.self=t;return {{key='a',data=t}}"}) {
            LuaValue invalid = lua.load(source).call();
            expect(() -> GuiPropertyValues.read(field, invalid));
        }
        GuiCollectionValues empty = (GuiCollectionValues) GuiPropertyValues.read(field, lua.load("return {}").call());
        require(empty.isEmpty() && values.model().keys.containsKey("row1"),
                "An empty/rejected model changed the previously validated collection");
        LuaValue oversized = lua
                .load("reads=0; local payload=string.rep('x',350000);local rows={};"
                        + "for i=1,64 do rows[i]=setmetatable({key='row'..i},{__index=function(_,key) "
                        + "if key=='data' then reads=reads+1; return {payload=payload} end end}) end;return rows")
                .call();
        expect(() -> GuiLuaCollectionValues.read(oversized, "largeRecords"));
        require(lua.get("reads").checkint() < 30,
                "Oversized collection payloads were all copied before aggregate metadata admission");
        try {
            new GuiProperties("test", Collections.singletonList(field))
                    .withDefaults(Collections.singletonMap("records", Collections.singletonList("invalid")));
            throw new AssertionError("A malformed Java collection field was accepted");
        } catch (IllegalArgumentException expected) {
        }
        System.out
                .println("Collection fields passed: 4096 bounded records, cached immutable models, detached payloads, "
                        + "strict shapes/keys/trees and unchanged ordinary-data limits.");
    }

    private static void expect(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException | UnsupportedOperationException | LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid or mutable collection values were accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
