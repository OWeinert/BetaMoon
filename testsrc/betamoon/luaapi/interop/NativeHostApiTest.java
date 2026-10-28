package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeBindings;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.util.Collections;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real Lua class declarations, typed callbacks, direct super policy and owned
 * handle reuse.
 */
public final class NativeHostApiTest {
    public static void run(NativeBindings bindings) {
        String owner = "native-host-lua";
        Globals globals = JsePlatform.standardGlobals();
        NativeAccessApi api = new NativeAccessApi(NativeIndexSnapshot::empty);
        LuaTable nativeApi = new LuaTable();
        api.attach(nativeApi);
        LuaTable module = new LuaTable();
        module.set("native", nativeApi);
        globals.set("betamoon", module);
        globals.load("Base=betamoon.native:type('betamoon.interop.hosts.NativeHostTest$Base',{names='runtime'}); "
                + "Answer=Base:method('answer','()I'); "
                + "shape=betamoon.native:hostType{base=Base,constructor=Base:constructor('(I)V'),overrides={Answer}}; "
                + "assert(not pcall(function() betamoon.native:hostType{base=Base,constructor=Answer,overrides={Answer}} end))")
                .call();
        ScriptExecution.withOwner(owner, () -> {
            try (LuaCallbackScope scope = new LuaCallbackScope(true);
                    NativeContext context = NativeContext.open(bindings, owner, scope, true, Collections.emptyMap(),
                            () -> true)) {
                globals.set("access", new NativeLuaAccess(api, context, false));
                globals.load("assert(not pcall(function() access:newHost(shape,{arguments={3},methods={}}) end)); "
                        + "a=access:direct(); hits=0; "
                        + "host=a:newHost(shape,{arguments={3},methods={{method=Answer,callback=function(ctx) "
                        + "hits=hits+1; assert(not ctx.native:describe().gameplayWrites); "
                        + "assert(ctx.method.name=='answer' and ctx.receiver:describe().bound); "
                        + "assert(not pcall(function() return ctx:callSuper() end)); "
                        + "saved=ctx.native; savedReceiver=ctx.receiver; savedSuper=ctx.callSuper; return 42 end}}}); "
                        + "assert(a:use(host):answer()==42 and hits==1); "
                        + "assert(not pcall(function() saved:describe() end)); "
                        + "assert(not pcall(function() savedReceiver:describe() end)); "
                        + "assert(not pcall(function() savedSuper() end)); "
                        + "assert(host:describe().ownership.state=='active'); "
                        + "a:release(host); assert(host:describe().ownership.state~='active'); "
                        + "gameplay=a:newHost(shape,{arguments={3},methods={{method=Answer,policy='gameplay',callback=function(ctx) "
                        + "assert(ctx.native:describe().gameplayWrites); return ctx:callSuper()+10 end}}}); "
                        + "assert(a:use(gameplay):answer()==14); a:release(gameplay); " + "local configured=0; "
                        + "local ready=a:newHost(shape,{arguments={3},setup=function(ctx) "
                        + "assert(ctx.native:direct():call(Base,'answer',ctx.receiver)==4); savedSetup=ctx.native; configured=configured+1 end, "
                        + "methods={{method=Answer,callback=function() return 21 end}}}); "
                        + "assert(configured==1 and a:use(ready):answer()==21); "
                        + "assert(not pcall(function() savedSetup:describe() end)); "
                        + "local Consumer=betamoon.native:type('betamoon.interop.hosts.NativeHostEdgeTest$Consumer',{names='runtime'}); "
                        + "local descriptor='(Lbetamoon/interop/hosts/NativeHostEdgeTest$ConsumerBase;)V'; "
                        + "local CBase=betamoon.native:type('betamoon.interop.hosts.NativeHostEdgeTest$ConsumerBase',{names='runtime'}); "
                        + "local Value=CBase:method('value','()I'); "
                        + "local ct=betamoon.native:hostType{base=CBase,constructor=CBase:constructor('()V'),overrides={Value}}; "
                        + "local attached=a:newHost(ct,{arguments={},methods={{method=Value,callback=function() return 12 end}}}); "
                        + "local consumer=a:bind(Consumer):new(); "
                        + "local token=a:attach{implementation=attached,receiver=consumer,add=Consumer:method('add',descriptor), "
                        + "remove=Consumer:method('remove',descriptor),lifetime='owner'}; "
                        + "assert(token:describe().implementation.shape==attached:describe().shape); "
                        + "assert(token:describe().implementation.base.name==attached:describe().base.name); "
                        + "token:close(); a:release(ready); "
                        + "assert(not pcall(function() a:newHost(shape,{arguments={3},methods={{method=Answer, "
                        + "onClosed='noop',callback=function() return 1 end}}}) end)); "
                        + "assert(not pcall(function() a:newHost(shape,{arguments={3},methods={},setup=function() error('must not run') end}) end))")
                        .call();
            }
            return null;
        });
        bindings.interfaces.observe(null, null);
        System.out.println(
                "Native host Lua contracts passed: metadata-only shapes, scoped callbacks, explicit super authority and release.");
    }
}
