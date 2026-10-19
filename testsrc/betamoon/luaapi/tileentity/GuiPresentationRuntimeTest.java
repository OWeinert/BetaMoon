package betamoon.luaapi.tileentity;

import betamoon.gui.animation.GuiEasing;
import betamoon.luaapi.audio.AudioApi;
import betamoon.luaapi.chat.ChatApi;
import betamoon.luaapi.event.EventsApi;
import java.util.Map;
import betamoon.gui.presentation.GuiGeometry;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.tileentity.ContainerControlRuntime;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.ContainerPresentationRuntime;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Fake-clock checks for the actual Lua declarations, observation, transaction and ownership paths. */
public final class GuiPresentationRuntimeTest {
    private static int sequence;

    public static void main(String[] arguments) throws Exception {
        Field registered = TileEntityRegistry.class.getDeclaredField("minecraftTypeRegistered");
        registered.setAccessible(true);
        registered.setBoolean(null, true);
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "gui_presentation_test.lua");
        try {
            easing();
            observationAndTiming();
            valueRecovery();
            transactions();
            eventsAndRequests();
            ownership();
            requestEdgeCases();
            nativeSourcesAndPolicy();
            geometryAndReload();
            invalidContracts();
            capturedEffectsAreReadOnly();
            overrideOwnerLifecycle();
            proposedAndAcceptedValues();
        } finally {
            owner.invoke(null, new Object[]{null});
        }
        System.out.println("GUI presentation passed: Lua bindings, transactions, state permissions, recovery, "
                + "request barriers, interruption, transforms and reload.");
    }

    private static void easing() {
        near(GuiEasing.bezier(0.42, 0, 1, 1).sample(0.5), 0.3153568, 1e-6);
        near(GuiEasing.bezier(0, 0, 0.58, 1).sample(0.5), 0.6846432, 1e-6);
        near(GuiEasing.bounce(0), 0, 0);
        near(GuiEasing.bounce(1), 1, 0);
        for (int i = 0; i <= 100; i++) {
            double value = GuiEasing.bounce(i / 100.0);
            require(value >= 0 && value <= 1, "Bounce left its endpoints");
        }
    }

    private static void observationAndTiming() {
        try (Fixture f = fixture("{type='panel',id='p',x=8,y=8,width=24,height=24,"
                + "bind=function(v) savedView=v; local expanded=v.state:get('expanded'); "
                + "return {width=expanded and 72 or 24,height=expanded and 100 or 24,"
                + "tint=expanded and (v.data:get('heat')>5 and 0xFF0000 or 0xFFFFFF) or 0xFFFFFF} end,"
                + "transition={default={duration=.25},width={easing='ease_in'},"
                + "height={duration=.45,easing='bounce_out'}}}")) {
            f.runtime.update();
            require(f.runtime.evaluationCount("p") == 1, "Initial binding did not run exactly once");
            for (int i = 0; i < 5; i++) {
                f.runtime.update();
            }
            require(f.runtime.evaluationCount("p") == 1, "Idle screen reevaluated Lua");
            f.tile.setDataInt("heat", 10);
            f.runtime.update();
            require(f.runtime.evaluationCount("p") == 1, "Unobserved conditional heat read invalidated Lua");
            f.controls.session().set("expanded", true);
            f.runtime.update();
            near(f.number("width"), 24, 0);
            near(((Number) f.runtime.element("p").target("width")).doubleValue(), 72, 0);
            f.time = 0.125;
            f.runtime.sample();
            require(f.number("width") > 24 && f.number("width") < 72, "Width did not tween");
            require(f.number("height") > 24 && f.number("height") < 100, "Height did not tween independently");
            double beforeReverse = f.number("width");
            f.controls.session().set("expanded", false);
            f.runtime.update();
            near(f.number("width"), beforeReverse, 1e-9);
            int evaluations = f.runtime.evaluationCount("p");
            f.controls.session().set("expanded", false);
            f.runtime.update();
            require(f.runtime.evaluationCount("p") == evaluations, "Unchanged write caused invalidation");
            expectLua(() -> f.lua.load("savedView.state:get('expanded')").call());
        }
    }

    private static void valueRecovery() {
        try (Fixture f = fixture("{type='panel',id='p',x=8,y=8,width=24,height=24,"
                + "bind=function(v) local n=v.state:get('amount'); return {width=n} end}")) {
            f.controls.session().set("amount", -4.0);
            f.runtime.update();
            near(f.number("width"), 24, 0);
            int count = f.runtime.evaluationCount("p");
            f.runtime.update();
            require(f.runtime.evaluationCount("p") == count, "Invalid screen polled a failed binding");
            f.controls.session().set("amount", 40.0);
            f.runtime.update();
            near(f.number("width"), 40, 0);
            require(f.runtime.evaluationCount("p") == count + 1, "Initial invalid result did not recover");
        }
        try (Fixture f = fixture("{type='panel',id='p',x=8,y=8,width=24,height=24,"
                + "bind=function(v) v.state:get('expanded'); if broken then return {widht=40} end return {width=40} end}")) {
            f.lua.set("broken", LuaValue.TRUE);
            f.runtime.update();
            int count = f.runtime.evaluationCount("p");
            f.lua.set("broken", LuaValue.FALSE);
            f.controls.session().set("expanded", true);
            f.runtime.update();
            require(f.runtime.evaluationCount("p") == count, "Programming error retried automatically");
            f.command("ctx.gui:element('p'):refresh()");
            f.runtime.update();
            near(f.number("width"), 40, 0);
        }
    }

    private static void transactions() {
        String elements = "{type='button',id='a',control='command',x=8,y=8,width=20,height=20,"
                + "bind=function(v) local n=v.state:get('amount');return {x=n==24 and 8 or 60} end},"
                + "{type='button',id='b',control='command',x=60,y=8,width=20,height=20,"
                + "bind=function(v) local n=v.state:get('amount');return {x=n==24 and 60 or n==40 and 8 or 60} end},"
                + "{type='rectangle',id='color',x=8,y=40,width=10,height=10,color=0xFFFFFF,"
                + "bind=function(v) return {color=v.state:get('amount')==50 and 0xFF0000 or 0xFFFFFF} end}";
        try (Fixture f = fixture(elements)) {
            f.runtime.update();
            f.controls.session().set("amount", 40.0);
            f.runtime.update();
            near(((Number) f.runtime.element("a").target("x")).doubleValue(), 60, 0);
            near(((Number) f.runtime.element("b").target("x")).doubleValue(), 8, 0);
            f.controls.session().set("amount", 50.0);
            f.runtime.update();
            near(((Number) f.runtime.element("b").target("x")).doubleValue(), 8, 0);
            require(((Number) f.runtime.element("color").get("color")).intValue() == 0xFF0000,
                    "Independent appearance update was rolled back with geometry");
            f.controls.session().set("amount", 24.0);
            f.runtime.update();
            near(((Number) f.runtime.element("a").target("x")).doubleValue(), 8, 0);
            near(((Number) f.runtime.element("b").target("x")).doubleValue(), 60, 0);
        }
    }

    private static void eventsAndRequests() {
        try (Fixture f = fixture("{type='rectangle',id='p',x=8,y=8,width=20,height=20,color=0xFFFFFF}")) {
            f.runtime.update();
            f.command("request=ctx.gui:element('p'):animate({scaleX=1.2,scaleY=1.2},"
                    + "{scaleX={duration=.3},scaleY={duration=.45,easing='bounce_out'}});"
                    + "ctx.state:set('amount',request.id); savedCtx=ctx");
            require("running".equals(f.lua.load("return request:status()").call().tojstring()), "Request not running");
            expectLua(() -> f.lua.load("savedCtx.state:set('expanded',true)").call());
            f.time = .3;
            f.runtime.update();
            require(f.lua.get("complete").toint() == 0, "Request completed when only width finished");
            f.time = .45;
            f.runtime.update();
            require(f.lua.get("complete").toint() == 1, "Aggregate completion missing or duplicated");
            f.command("request=ctx.gui:element('p'):animate({scaleX=1.2},{scaleX={duration=0}})");
            require(f.lua.get("complete").toint() == 1, "No-op completion was delivered inline");
            f.runtime.update();
            require(f.lua.get("complete").toint() == 2, "No-op request never completed");
            f.command("ctx.state:set('expanded',true)");
            require(Boolean.TRUE.equals(f.controls.session().get("expanded")), "GUI event cannot write typed state");
        }
    }

    private static void ownership() {
        try (Fixture f = fixture("{type='panel',id='p',x=8,y=8,width=24,height=24,"
                + "bind=function(v) return {width=v.state:get('amount')} end}")) {
            f.runtime.update();
            f.command("ctx.gui:element('p'):set({width=50})");
            near(f.number("width"), 50, 0);
            f.controls.session().set("amount", 30.0);
            f.runtime.update();
            near(f.number("width"), 30, 0);
            f.command("ctx.gui:element('p'):override({width=55})");
            f.controls.session().set("amount", 35.0);
            f.runtime.update();
            near(f.number("width"), 55, 0);
            f.command("ctx.gui:element('p'):clearOverride({'width'})");
            near(f.number("width"), 35, 0);
            f.command("ctx.gui:element('p'):set({width=20}); ctx.gui:element('p'):clearOverride({'width'})");
            near(f.number("width"), 35, 0);
        }
    }

    private static void requestEdgeCases() {
        String element = "{type='rectangle',id='p',x=40,y=40,width=20,height=20,color=0xFFFFFF}";
        try (Fixture f = fixture(element)) {
            f.runtime.update();
            f.command("local n=ctx.gui:element('p'); old=n:animate({scaleX=2,scaleY=2},{default={duration=1}})");
            f.time = .2;
            f.command("new=ctx.gui:element('p'):animate({scaleX=3},{default={duration=.5}})");
            require("interrupted".equals(f.lua.load("return old:status()").call().tojstring()),
                    "Partial replacement did not interrupt the aggregate");
            f.command("old:finish()");
            near(f.number("scaleY"), 2, 0);
            require(f.number("scaleX") < 3, "Old request finished a newer replacement");
            f.command("new:cancel()");
            require("canceled".equals(f.lua.load("return new:status()").call().tojstring()), "Whole cancel status lost");
            f.time = 2;
            f.runtime.update();
            require(f.lua.get("complete").toint() == 0, "Interrupted request emitted a late success");
        }
        try (Fixture f = fixture(element)) {
            f.runtime.update();
            f.command("old=ctx.gui:element('p'):animate({scaleX=2},{default={duration=1}})");
            f.time = .4;
            f.command("new=ctx.gui:element('p'):animate({scaleX=2},{default={duration=10}})");
            f.time = 1;
            f.runtime.update();
            near(f.number("scaleX"), 2, 0);
            require("completed".equals(f.lua.load("return new:status()").call().tojstring()),
                    "Equal-target adoption restarted or retimed the trajectory");
        }
        try (Fixture f = fixture(element)) {
            f.runtime.update();
            f.command("local n=ctx.gui:element('p'); request=n:animate({scaleX=2}); request:cancel()");
            near(f.number("scaleX"), 1, 0);
            require("canceled".equals(f.lua.load("return request:status()").call().tojstring()),
                    "A staged pending request could not be canceled");
            f.command("local n=ctx.gui:element('p'); request=n:animate({scaleY=2}); request:finish()");
            near(f.number("scaleY"), 2, 0);
            require("completed".equals(f.lua.load("return request:status()").call().tojstring()),
                    "A staged pending request could not be finished");
        }
    }

    private static void nativeSourcesAndPolicy() {
        try (Fixture f = fixture("{type='text',id='p',x=8,y=8,value='heat',format='Heat %d',"
                + "bind=function(v) if v.state:get('expanded') then return {text='Override'} end return {} end}")) {
            f.runtime.update();
            require("Heat 0".equals(f.runtime.element("p").get("text")), "Native text baseline missing");
            int evaluations = f.runtime.evaluationCount("p");
            f.tile.setDataInt("heat", 5);
            f.runtime.update();
            require("Heat 5".equals(f.runtime.element("p").get("text")), "Native source did not invalidate baseline");
            require(f.runtime.evaluationCount("p") == evaluations, "Native source update reran unrelated Lua");
            f.controls.session().set("expanded", true);
            f.runtime.update();
            f.tile.setDataLua("heat", LuaValue.valueOf(7));
            f.runtime.update();
            require("Override".equals(f.runtime.element("p").get("text")), "Native baseline overrode binding output");
            f.controls.session().set("expanded", false);
            f.runtime.update();
            require("Heat 7".equals(f.runtime.element("p").get("text")), "Omission restored a stale native baseline");
        }
        try (Fixture f = fixture("{type='panel',id='p',x=8,y=8,width=24,height=24,"
                + "bind=function(v) return {width=v.state:get('amount')},"
                + "{width={duration=v.state:get('expanded') and 5 or 1}} end}")) {
            f.runtime.update();
            f.controls.session().set("amount", 50.0);
            f.runtime.update();
            f.time = .4;
            f.runtime.sample();
            double sampled = f.number("width");
            f.controls.session().set("expanded", true);
            f.runtime.update();
            near(f.number("width"), sampled, 1e-9);
            f.time = 1;
            f.runtime.update();
            near(f.number("width"), 50, 0);
        }
        expectLua(() -> fixture("{type='group',opacity=.5,elements={{type='item',item=1}}}"));
        expectLua(() -> fixture("{type='item',item=1,opacity=.5}"));
        expectLua(() -> fixture("{type='rectangle',width=10,height=10,color=0xFFFFFF,scaleX=1e300}"));
    }

    private static void geometryAndReload() {
        try (Fixture f = fixture("{type='group',id='g',x=5,y=6,width=100,height=80,scaleX=2,scaleY=2,"
                + "elements={{type='rectangle',id='p',x=8,y=8,width=20,height=20,color=0xFFFFFF,"
                + "transition={default={duration=1}}}}}")) {
            f.runtime.update();
            GuiGeometry geometry = f.runtime.geometry(f.runtime.element("p"));
            near(geometry.x, 21, 0);
            near(geometry.y, 22, 0);
            near(geometry.localX(31), 5, 0);
            require(geometry.contains(21, 22) && !geometry.contains(61, 22), "Transformed hit bounds disagree");
            f.command("request=ctx.gui:element('p'):animate({translateX=10})");
            f.time = .4;
            f.runtime.sample();
            double before = f.runtime.element("p").number("translateX", 0);
            ContainerPresentationRuntime previous = f.runtime;
            f.runtime = f.runtime.reload(f.gui);
            f.runtime.sample();
            near(f.runtime.element("p").number("translateX", 0), before, 1e-9);
            expectLua(() -> previous.element("p"));
            expectLua(() -> f.lua.load("request:status()").call());
        }
    }

    private static void invalidContracts() {
        try (Fixture f = fixture("{type='rectangle',id='p',width=10,height=10,color=0xFFFFFF}")) {
            Object color = f.runtime.element("p").definition.properties.interpolate("color", 0, 0xFFFFFF, 1e300);
            require(Integer.valueOf(0xFFFFFF).equals(color), "Custom overshoot overflowed before RGB clamping");
        }
        try (Fixture f = fixture("{type='panel',id='p',x=8,y=8,width=24,height=24}")) {
            f.runtime.update();
            f.command("ctx.data:set('heat',9)");
            require(Integer.valueOf(0).equals(f.tile.getDataValue("heat")), "GUI event gained gameplay authority");
        }
        expectLua(() -> fixture("{type='panel',id='p',width=24,height=24,transition={duration=.2}}"));
        expectLua(() -> fixture("{type='panel',id='p',width=24,height=24,transition={text={duration=.2}}}"));
        expectLua(() -> fixture("{type='panel',id='p',width=24,height=24},{type='panel',id='p',x=40,width=24,height=24}"));
    }

    private static void capturedEffectsAreReadOnly() {
        try (Fixture f = fixture("{type='panel',id='p',width=24,height=24,"
                + "bind=function(v) local ok,err=pcall(function() betamoon.chat:send('blocked') end);"
                + "assert(not ok and string.find(tostring(err),'presentation derivation'));"
                + "local a,b=pcall(function() betamoon.audio:play('missing.ogg') end);"
                + "assert(not a and string.find(tostring(b),'presentation derivation'));"
                + "local c,d=pcall(function() betamoon.events:on('world_join',function()end) end);"
                + "assert(not c and string.find(tostring(d),'presentation derivation'));return {} end}")) {
            LuaTable api = f.lua.get("betamoon").checktable();
            ChatApi.attach(api);
            AudioApi.attach(api);
            EventsApi.attach(api);
            f.runtime.update();
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = (Map<String, Object>) f.runtime.debugSnapshot().get("p");
            require(Boolean.FALSE.equals(snapshot.get("suspended")), "A captured effect bypassed binding isolation");
            f.lua.load("assert(betamoon.gui:lerp(10,20,1.5)==25);"
                    + "assert(betamoon.gui:lerp(1e308,1e308,2)==1e308);"
                    + "assert(betamoon.gui:ease('linear',.5)==.5);"
                    + "assert(not pcall(function()betamoon.gui:ease('linear',1.1)end))").call();
            expectLua(() -> f.lua.load("local entries={};for i=1,1000 do "
                    + "entries={{type='group',elements=entries}} end;"
                    + "betamoon.containerGuis:add{name='too_deep',container=container,elements=entries}").call());
        }
    }

    private static void overrideOwnerLifecycle() throws Exception {
        try (Fixture f = fixture("{type='panel',id='p',width=24,height=24}")) {
            f.runtime.update();
            Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
            owner.setAccessible(true);
            owner.invoke(null, "gui_patch_owner.lua");
            try {
                f.lua.load("callbackRuns=0; guiHandle:override{changes={elements={{type='panel',id='p',"
                        + "width=24,height=24,bind=function(v) callbackRuns=callbackRuns+1;"
                        + "v.state:get('expanded');return{}end}},events={'control'},"
                        + "onEvent=function(ctx,event) savedNode=ctx.gui:element('p') end}}").call();
            } finally {
                owner.invoke(null, "gui_presentation_test.lua");
            }
            ContainerGuiDefinition replacement = TileEntityRegistry.getGui(f.gui.name);
            require("gui_patch_owner.lua".equals(replacement.presentation.callbackOwner),
                    "GUI override callback was attributed to the base GUI owner");
            f.runtime = f.runtime.reload(replacement);
            f.controls.activate(f.gui.container.controls.get("command"), ContainerControlRuntime.input("test"));
            f.runtime.update();
            int calls = f.lua.get("callbackRuns").toint();
            ScriptResourceTracker.unload("gui_patch_owner.lua");
            f.controls.session().set("expanded", true);
            f.runtime.update();
            require(f.lua.get("callbackRuns").toint() == calls, "Unloaded override callback still ran");
            expectLua(() -> f.lua.load("savedNode:get('width')").call());
            f.runtime = f.runtime.reload(TileEntityRegistry.getGui(f.gui.name));
            f.runtime.update();
        }
    }

    private static void proposedAndAcceptedValues() {
        try (Fixture f = fixture("{type='rectangle',id='p',width=10,height=10,color=0xFFFFFF}")) {
            f.runtime.update();
            f.lua.load("scriptEvent=function(ctx,event) proposed=event.proposed; acceptedValue=event.value end").call();
            f.controls.changeNumber(f.gui.container.controls.get("replace"), 20,
                    ContainerControlRuntime.input("test"));
            f.runtime.update();
            require(f.lua.get("proposed").toint() == 20 && f.lua.get("acceptedValue").toint() == 7,
                    "Control notification lost the attempted value when validation replaced it");
        }
    }

    private static Fixture fixture(String elements) {
        return new Fixture(elements);
    }

    private static void expectLua(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected LuaError");
    }

    private static void near(double actual, double expected, double tolerance) {
        require(Math.abs(actual - expected) <= tolerance, actual + " != " + expected);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Globals lua = JsePlatform.standardGlobals();
        private final ContainerGuiDefinition gui;
        private final LuaTileEntity tile;
        private final ContainerControlRuntime controls;
        private ContainerPresentationRuntime runtime;
        private double time;

        private Fixture(String elements) {
            LuaTable api = new LuaTable();
            TileEntityApi.attach(api);
            lua.set("betamoon", api);
            lua.set("fixtureName", "presentation_" + (++sequence));
            LuaValue result = lua.load("local t=betamoon.tileEntities:add{name=fixtureName,"
                    + "data={heat={type='integer',default=0,sync=true}}};"
                    + "container=betamoon.containers:add{name=fixtureName,tileEntity=t,slots={},"
                    + "playerInventory={x=8,y=84},state={expanded={type='boolean',default=false},"
                    + "amount={type='number',default=24}},controls={command={type='action'},"
                    + "replace={type='number',bind={state='amount'},minimum=0,maximum=100,"
                    + "beforeChange=function()return {result='handled',value=7}end}}};"
                    + "complete=0; cancels=0; return betamoon.containerGuis:add{name=fixtureName,container=container,"
                    + "events={'control','animation'},onEvent=function(ctx,event) "
                    + "if event.type=='control' and scriptEvent then scriptEvent(ctx,event) "
                    + "elseif event.type=='animation' then if event.phase=='complete' then complete=complete+1 "
                    + "else cancels=cancels+1 end end end,elements={" + elements + "}}").call();
            lua.set("guiHandle", result);
            gui = ((TileEntityApi.GuiHandle) result).definition;
            ContainerDefinition container = gui.container;
            tile = new LuaTileEntity(container.tileEntity.name);
            NBTTagCompound saved = new NBTTagCompound();
            saved.setString("BetaMoonType", container.tileEntity.name);
            tile.readFromNBT(saved);
            controls = new ContainerControlRuntime(container, tile, null);
            runtime = new ContainerPresentationRuntime(gui, tile, controls, text -> text.length() * 6, () -> time);
        }

        private void command(String body) {
            lua.load("scriptEvent=function(ctx,event) " + body + " end").call();
            controls.activate(gui.container.controls.get("command"), ContainerControlRuntime.input("test"));
            runtime.update();
        }

        private double number(String property) {
            return runtime.element("p").number(property, 0);
        }

        @Override
        public void close() {
            runtime.close();
            controls.close();
        }
    }
}
