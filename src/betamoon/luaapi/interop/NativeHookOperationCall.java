package betamoon.luaapi.interop;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookInvocation;
import betamoon.hooks.HookLuaValue;
import betamoon.hooks.HookOperationInvocation;
import betamoon.hooks.HookOperationSite;
import betamoon.interop.runtime.NativeContext;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.objectweb.asm.Type;

/**
 * Typed operation facade; operands are explicitly separate from the containing
 * method's saved arguments.
 */
public final class NativeHookOperationCall extends HookLuaValue {
    private final HookInvocation invocation;
    private final Supplier<NativeContext> context;
    private final HookOperationSite operation;
    private final Type resultType;
    private final boolean editsAllowed;
    private final LuaValue enclosing;

    public NativeHookOperationCall(HookInvocation invocation, Supplier<NativeContext> context, boolean editsAllowed) {
        this.invocation = invocation;
        this.context = context;
        this.operation = invocation.operation().operation;
        this.resultType = Type.getReturnType(operation.hookDescriptor());
        this.editsAllowed = editsAllowed;
        properties.put("id", valueOf(invocation.definition.id));
        properties.put("owner", valueOf(invocation.definition.owner));
        properties.put("site", valueOf(invocation.site));
        properties.put("mode", valueOf(operation.kind));
        properties.put("arguments", new Arguments(invocation, context, editsAllowed, false));
        NativeAccessApi metadata = new NativeAccessApi(context.get().bindings::snapshot);
        Map<String, Object> description = new LinkedHashMap<>(operation.transport());
        description.put("ordinal", invocation.definition.point.ordinal);
        description.put("matches", invocation.definition.point.exact);
        properties.put("point", metadata.metadata(description, true));
        enclosing = new Enclosing(invocation, context);
        properties.put("setResult", method(args -> {
            edit();
            if (operation.before() || resultType.getSort() == Type.VOID || args.narg() != 1) {
                throw new HookFailure("hook_edit_denied",
                        "setResult(value) requires a non-void invoke_after or field_read point");
            }
            invocation.result(NativeHookCall.convert(args.arg1(), resultType, invocation, context.get()));
            return NONE;
        }));
        properties.put("setValue", method(args -> {
            edit();
            if (!operation.kind.equals("field_write") || args.narg() != 1) {
                throw new HookFailure("hook_edit_denied", "setValue(value) requires a field_write point");
            }
            invocation.argument(0,
                    NativeHookCall.convert(args.arg1(), Type.getType(operation.descriptor), invocation, context.get()));
            return NONE;
        }));
    }

    private void edit() {
        invocation.check();
        if (!editsAllowed || !invocation.definition.edit) {
            throw new HookFailure("hook_edit_denied", "declare effects=edit in an eligible enclosing entry");
        }
    }

    @Override
    public LuaValue get(LuaValue key) {
        invocation.check();
        if (key.type() == TSTRING) {
            switch (key.tojstring()) {
                case "receiver":
                    return operation.instance() ? NativeAccessApi.hookValue(invocation.receiver(), context.get()) : NIL;
                case "native":
                    return NativeAccessApi.contextAccess(context.get());
                case "enclosing":
                    return enclosing;
                case "result":
                    return !operation.before() && resultType.getSort() != Type.VOID
                            ? NativeAccessApi.hookValue(invocation.result(), context.get())
                            : NIL;
                case "value":
                    return operation.kind.equals("field_write")
                            ? NativeAccessApi.hookValue(invocation.argument(0), context.get())
                            : NIL;
                default:
                    break;
            }
        }
        return super.get(key);
    }

    private static final class Enclosing extends HookLuaValue {
        private final HookInvocation invocation;
        private final Supplier<NativeContext> context;

        Enclosing(HookInvocation invocation, Supplier<NativeContext> context) {
            this.invocation = invocation;
            this.context = context;
            properties.put("site", valueOf(invocation.site));
            properties.put("arguments", new Arguments(invocation, context, false, true));
        }

        @Override
        public LuaValue get(LuaValue key) {
            invocation.check();
            if (key.type() == TSTRING && key.tojstring().equals("receiver")) {
                Object receiver = invocation.operation().enclosingReceiver();
                return receiver == null ? NIL : NativeAccessApi.hookValue(receiver, context.get());
            }
            return super.get(key);
        }
    }

    private static final class Arguments extends HookLuaValue {
        private final HookInvocation invocation;

        Arguments(HookInvocation invocation, Supplier<NativeContext> context, boolean editsAllowed, boolean enclosing) {
            this.invocation = invocation;
            HookOperationInvocation data = invocation.operation();
            Type[] types = Type.getArgumentTypes(enclosing
                    ? invocation.site.substring(invocation.site.indexOf('('))
                    : data.operation.kind.startsWith("invoke_") ? data.operation.descriptor : "()V");
            properties.put("count", valueOf(types.length));
            properties.put("get", method(args -> {
                invocation.check();
                if (args.narg() != 1 || types.length == 0) {
                    throw new HookFailure("hook_argument_index", "get(index) selects a zero-based Java argument");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, types.length - 1);
                Object value = enclosing ? data.enclosingArgument(index) : invocation.argument(index);
                return NativeAccessApi.hookValue(value, context.get());
            }));
            properties.put("set", method(args -> {
                invocation.check();
                if (enclosing || !editsAllowed || !invocation.definition.edit
                        || !data.operation.kind.equals("invoke_before")) {
                    throw new HookFailure("hook_edit_denied",
                            "only an editing invoke_before point may change call arguments");
                }
                if (args.narg() != 2 || types.length == 0) {
                    throw new HookFailure("hook_argument_index", "set(index,value) selects a zero-based Java argument");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, types.length - 1);
                invocation.argument(index,
                        NativeHookCall.convert(args.arg(2), types[index], invocation, context.get()));
                return NONE;
            }));
        }

        @Override
        public LuaValue get(LuaValue key) {
            invocation.check();
            return super.get(key);
        }
    }
}
