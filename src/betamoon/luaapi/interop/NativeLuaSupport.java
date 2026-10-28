package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.runtime.NativeContext;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class NativeLuaSupport {
    interface Action {
        Varargs run(Varargs arguments);
    }

    private NativeLuaSupport() {
    }

    static LuaValue function(LuaValue self, Action action) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                try {
                    return action.run(arguments.arg1() == self ? arguments.subargs(2) : arguments);
                } catch (NativeAccessFailure | IllegalArgumentException exception) {
                    throw NativeLuaSupport.error(exception);
                }
            }
        };
    }

    static LuaValue scopedFunction(LuaValue self, NativeContext context, Action action) {
        return function(self, arguments -> {
            try {
                context.check();
                Varargs result = action.run(arguments);
                context.check();
                return result;
            } catch (NativeAccessFailure failure) {
                NativeLuaHandle handle = self instanceof NativeLuaHandle ? (NativeLuaHandle) self : null;
                if (context.bindings != null) {
                    throw context.bindings.explain(failure, handle == null ? null : handle.diagnosticType,
                            handle == null ? null : handle.diagnosticMember,
                            handle == null || handle.diagnosticPlan == null ? null : handle.diagnosticPlan.get(),
                            context, handle != null && handle.diagnosticDirect);
                }
                throw failure;
            }
        });
    }

    static LuaError error(RuntimeException exception) {
        if (exception instanceof NativeAccessFailure) {
            NativeContext.recordFailure((NativeAccessFailure) exception);
        }
        return new BridgeError(exception);
    }

    private static final class BridgeError extends LuaError {
        private BridgeError(RuntimeException failure) {
            super("native: " + failure.getMessage());
            cause = failure;
        }
    }

    static final class ReadOnlyTable extends LuaTable {
        ReadOnlyTable(Map<LuaValue, LuaValue> fields) {
            for (Map.Entry<LuaValue, LuaValue> entry : fields.entrySet()) {
                super.rawset(entry.getKey(), entry.getValue());
            }
        }

        void install(String name, LuaValue function) {
            super.rawset(LuaValue.valueOf(name), function);
        }

        @Override
        public void set(int key, LuaValue value) {
            readOnly();
        }

        @Override
        public void set(LuaValue key, LuaValue value) {
            readOnly();
        }

        @Override
        public void rawset(int key, LuaValue value) {
            readOnly();
        }

        @Override
        public void rawset(LuaValue key, LuaValue value) {
            readOnly();
        }

        @Override
        public LuaValue remove(int position) {
            return readOnly();
        }

        @Override
        public void insert(int position, LuaValue value) {
            readOnly();
        }

        @Override
        public void sort(LuaValue comparator) {
            readOnly();
        }

        @Override
        public LuaValue setmetatable(LuaValue metatable) {
            return readOnly();
        }

        private LuaValue readOnly() {
            throw new LuaError("native: metadata is read-only");
        }
    }
}
