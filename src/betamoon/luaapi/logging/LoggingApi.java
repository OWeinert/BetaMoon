package betamoon.luaapi.logging;

import betamoon.logging.LogLevel;
import betamoon.logging.RuntimeLogs;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owned, callable logging table and standard Lua print routing. */
public final class LoggingApi {
    private LoggingApi() {
    }

    public static void attach(LuaTable module, String owner) {
        LuaTable log = new LuaTable();
        for (LogLevel level : LogLevel.values()) {
            if (level != LogLevel.OFF) {
                log.set(level.toString(), new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        int offset = args.arg1() == log ? 1 : 0;
                        count(args, offset + 1, "betamoon.log:" + level);
                        RuntimeLogs.log(level, owner, string(args.arg(offset + 1), "message"));
                        return NONE;
                    }
                });
            }
        }
        LuaTable metatable = new LuaTable();
        metatable.set("__call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                count(args, 3, "betamoon.log(level, message)");
                String name = string(args.arg(2), "level");
                LogLevel level;
                try {
                    level = LogLevel.parse(name);
                    if (level == LogLevel.OFF) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException invalid) {
                    throw new LuaError("Log level must be trace, debug, info, warn, or error; received '" + name + "'.");
                }
                RuntimeLogs.log(level, owner, string(args.arg(3), "message"));
                return NONE;
            }
        });
        log.setmetatable(metatable);
        module.set("log", log);
    }

    public static void installPrint(Globals globals, String owner) {
        globals.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder text = new StringBuilder();
                LuaValue tostring = globals.get("tostring");
                for (int index = 1; index <= args.narg(); index++) {
                    if (index > 1) {
                        text.append('\t');
                    }
                    text.append(tostring.call(args.arg(index)).checkjstring());
                }
                RuntimeLogs.log(LogLevel.INFO, owner, text.toString());
                return NONE;
            }
        });
    }

    private static void count(Varargs args, int expected, String label) {
        if (args.narg() != expected) {
            throw new LuaError(label + ": expected " + (expected - 1) + " argument(s) after the receiver.");
        }
    }

    private static String string(LuaValue value, String label) {
        if (value.type() != LuaValue.TSTRING) {
            throw new LuaError("Log " + label + " must be a string.");
        }
        return value.tojstring();
    }
}
