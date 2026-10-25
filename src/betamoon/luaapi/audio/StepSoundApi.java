package betamoon.luaapi.audio;

import betamoon.luaapi.utils.LuaCallbackScope;
import net.minecraft.src.StepSound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Registration and lookup for sound-event-backed block step sounds. */
public final class StepSoundApi {
    private StepSoundApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable sounds = new LuaTable();
        sounds.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaCallbackScope.requireNoBindingMutation();
                LuaValue definition = argument(args, sounds);
                if (!definition.istable()) {
                    throw new LuaError("Step sound add expects a definition table.");
                }
                return BlockStepSounds.register(definition).reference();
            }
        });
        sounds.set("get", lookup(sounds, false));
        sounds.set("getRequired", lookup(sounds, true));
        module.set("stepSounds", sounds);
    }

    private static VarArgFunction lookup(LuaTable sounds, boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = argument(args, sounds).checkjstring();
                BlockStepSounds.Entry entry = BlockStepSounds.entry(name);
                if (entry != null) {
                    return entry.reference();
                }
                StepSound vanilla = BlockStepSounds.find(name);
                if (vanilla != null) {
                    return LuaValue.valueOf(BlockStepSounds.name(vanilla));
                }
                if (required) {
                    throw new LuaError("Step sound is not registered: " + name);
                }
                return NIL;
            }
        };
    }

    private static LuaValue argument(Varargs args, LuaTable receiver) {
        return args.arg(args.arg1() == receiver ? 2 : 1);
    }
}
