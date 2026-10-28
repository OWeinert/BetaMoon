package betamoon.hooks;

import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Opaque immutable Lua facade; no Java object is exposed through userdata. */
public class HookLuaValue extends LuaValue {
    protected final Map<String, LuaValue> properties = new HashMap<>();

    @FunctionalInterface
    public interface Action {
        Varargs invoke(Varargs args);
    }

    public LuaValue method(Action action) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return action.invoke(args.arg1() == HookLuaValue.this ? args.subargs(2) : args);
                } catch (HookFailure | IllegalArgumentException error) {
                    throw new HookLuaError(error);
                }
            }
        };
    }

    @Override
    public int type() {
        return TUSERDATA;
    }

    @Override
    public String typename() {
        return "userdata";
    }

    @Override
    public LuaValue get(String key) {
        return get(valueOf(key));
    }

    protected LuaValue createProperty(String name) {
        return NIL;
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.type() != TSTRING) {
            return NIL;
        }
        String name = key.tojstring();
        LuaValue cached = properties.get(name);
        if (cached != null) {
            return cached;
        }
        LuaValue created = createProperty(name);
        if (!created.isnil()) {
            properties.put(name, created);
        }
        return created;
    }

    @Override
    public void set(String key, LuaValue value) {
        set(valueOf(key), value);
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        throw new LuaError("hooks: this value is immutable");
    }

    @Override
    public LuaValue setmetatable(LuaValue value) {
        throw new LuaError("hooks: this value is immutable");
    }

    public static LuaValue facade(HookFileCollectionContext context) {
        HookLuaValue facade = new HookLuaValue();
        facade.properties.put("createRegistry", facade.method(args -> {
            if (args.narg() != 0) {
                throw new HookFailure("hook_file_invalid", "createRegistry takes no arguments");
            }
            if (context == null) {
                throw new HookFailure("hook_registry_context_mismatch", "use a listed hook file");
            }
            HookLuaValue registry = new HookLuaValue();
            registry.properties.put("register", registry.method(registration -> {
                if (registration.narg() != 2) {
                    throw new HookFailure("hook_file_invalid", "register requires definition and callback");
                }
                HookDefinition definition = context.register(registration.arg1(), registration.arg(2));
                HookLuaValue token = new HookLuaValue();
                token.properties.put("id", valueOf(definition.id));
                return token;
            }));
            context.createRegistry(registry);
            return registry;
        }));
        return facade;
    }

    private static final class HookLuaError extends LuaError {
        HookLuaError(RuntimeException failure) {
            super(failure.getMessage());
            cause = failure;
        }
    }
}
