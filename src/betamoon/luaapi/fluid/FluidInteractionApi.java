package betamoon.luaapi.fluid;

import betamoon.content.ContentKey;
import betamoon.content.ContentKeyException;
import betamoon.content.ContentType;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.interaction.FluidFace;
import betamoon.fluid.interaction.FluidInteractionDefinition;
import betamoon.fluid.interaction.FluidInteractionEffects;
import betamoon.fluid.interaction.FluidInteractionOutcome;
import betamoon.fluid.interaction.FluidInteractionRegistry;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luaapi.utils.LuaContentNamespaces;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Declarative Lua facade for deterministic fluid contact outcomes. */
final class FluidInteractionApi {
    private FluidInteractionApi() {
    }

    static LuaTable create() {
        LuaTable interactions = new LuaTable();
        interactions.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue value = arguments.arg(arguments.arg1() == interactions ? 2 : 1);
                return register(value);
            }
        });
        return interactions;
    }

    private static LuaValue register(LuaValue value) {
        fields(value, "fluid interaction", "key", "fluids", "replace", "faces", "outcomes", "effects");
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Fluid interactions may only be declared by a Lua script.");
        }
        ContentKey key = key(required(value, "key"));
        LuaValue fluidsValue = required(value, "fluids");
        if (length(fluidsValue, "fluid interaction.fluids") != 2) {
            throw new LuaError("fluid interaction.fluids must contain exactly two fluids.");
        }
        FluidRegistration first = fluid(fluidsValue.get(1), "fluid interaction.fluids[1]");
        FluidRegistration second = fluid(fluidsValue.get(2), "fluid interaction.fluids[2]");
        if (first == second) {
            throw new LuaError("fluid interaction.fluids must contain two distinct fluids.");
        }
        FluidRegistration replaced = fluid(required(value, "replace"), "fluid interaction.replace");
        if (replaced != first && replaced != second) {
            throw new LuaError("fluid interaction.replace must be one of fluid interaction.fluids.");
        }
        FluidRegistration counterpart = replaced == first ? second : first;
        FluidInteractionDefinition definition = new FluidInteractionDefinition(key, owner, replaced, counterpart,
                faces(value.get("faces")), outcomes(required(value, "outcomes")), effects(value.get("effects")));
        try {
            FluidInteractionRegistry.instance().register(definition);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new LuaError("Fluid interaction '" + key + "': " + error.getMessage());
        }
        LuaTable reference = new LuaTable();
        reference.set("key", key.toString());
        reference.set("replace", FluidReference.of(replaced));
        reference.set("counterpart", FluidReference.of(counterpart));
        reference.set("exists", LuaValue.TRUE);
        return reference;
    }

    private static EnumSet<FluidFace> faces(LuaValue value) {
        if (value.isnil()) {
            return EnumSet.allOf(FluidFace.class);
        }
        int count = length(value, "fluid interaction.faces");
        if (count == 0) {
            throw new LuaError("fluid interaction.faces must not be empty.");
        }
        EnumSet<FluidFace> result = EnumSet.noneOf(FluidFace.class);
        for (int index = 1; index <= count; index++) {
            try {
                FluidFace face = FluidFace.parse(value.get(index).checkjstring());
                if (!result.add(face)) {
                    throw new LuaError("fluid interaction.faces contains duplicate '" + face.value() + "'.");
                }
            } catch (IllegalArgumentException error) {
                throw new LuaError("fluid interaction.faces[" + index + "]: " + error.getMessage());
            }
        }
        return result;
    }

    private static List<FluidInteractionOutcome> outcomes(LuaValue value) {
        int count = length(value, "fluid interaction.outcomes");
        if (count == 0) {
            throw new LuaError("fluid interaction.outcomes must not be empty.");
        }
        List<FluidInteractionOutcome> outcomes = new ArrayList<FluidInteractionOutcome>();
        for (int index = 1; index <= count; index++) {
            LuaValue outcome = value.get(index);
            fields(outcome, "fluid interaction.outcomes[" + index + "]", "when", "replaceWith");
            LuaValue when = outcome.get("when");
            Boolean source = null;
            Boolean falling = null;
            int minimum = 0;
            int maximum = 7;
            if (!when.isnil()) {
                fields(when, "fluid interaction.outcomes[" + index + "].when", "source", "falling", "minLevel",
                        "maxLevel");
                source = optionalBoolean(when.get("source"), "when.source");
                falling = optionalBoolean(when.get("falling"), "when.falling");
                minimum = when.get("minLevel").isnil() ? 0
                        : integer(when.get("minLevel"), "when.minLevel", 0, 7);
                maximum = when.get("maxLevel").isnil() ? 7
                        : integer(when.get("maxLevel"), "when.maxLevel", 0, 7);
            }
            int resultBlock = resultBlock(required(outcome, "replaceWith"));
            outcomes.add(new FluidInteractionOutcome(source, falling, minimum, maximum, resultBlock));
        }
        return outcomes;
    }

    private static int resultBlock(LuaValue value) {
        if (value.isboolean()) {
            if (value.toboolean()) {
                throw new LuaError("fluid interaction replaceWith boolean may only be false (air).");
            }
            return 0;
        }
        Block block = ResourceApi.resolveBlock(value);
        if (FluidRegistry.instance().getByBlockId(block.blockID) != null) {
            throw new LuaError("fluid interaction replaceWith must be a non-fluid block or false.");
        }
        return block.blockID;
    }

    private static FluidInteractionEffects effects(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        fields(value, "fluid interaction.effects", "sound", "volume", "pitch", "particle", "particleCount", "when");
        String sound = value.get("sound").isnil() ? null : value.get("sound").checkjstring();
        float volume = optionalPositive(value.get("volume"), 0.5F, "fluid interaction.effects.volume");
        float pitch = optionalPositive(value.get("pitch"), 1.0F, "fluid interaction.effects.pitch");
        String particle = value.get("particle").isnil() ? null : value.get("particle").checkjstring();
        int count = value.get("particleCount").isnil() ? (particle == null ? 0 : 8)
                : integer(value.get("particleCount"), "fluid interaction.effects.particleCount", 0, 64);
        String timingName = value.get("when").optjstring("changed");
        FluidInteractionEffects.Timing timing;
        if ("changed".equals(timingName)) {
            timing = FluidInteractionEffects.Timing.CHANGED;
        } else if ("contact".equals(timingName)) {
            timing = FluidInteractionEffects.Timing.CONTACT;
        } else {
            throw new LuaError("fluid interaction.effects.when must be changed or contact.");
        }
        return new FluidInteractionEffects(sound, volume, pitch, particle, count, timing);
    }

    private static FluidRegistration fluid(LuaValue value, String path) {
        if (value instanceof FluidReference) {
            return ((FluidReference) value).registration();
        }
        try {
            ContentKey key = ContentKey.parseForType(value.checkjstring(), ContentType.FLUID);
            FluidRegistration registration = FluidRegistry.instance().get(key);
            if (registration == null) {
                throw new LuaError(path + ": fluid '" + key + "' is not registered.");
            }
            return registration;
        } catch (ContentKeyException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static ContentKey key(LuaValue value) {
        try {
            ContentKey key = ContentKey.parseForType(value.checkjstring(), ContentType.FLUID_INTERACTION);
            LuaContentNamespaces.requireDeclarationNamespace(key);
            return key;
        } catch (ContentKeyException error) {
            throw new LuaError("fluid interaction.key: " + error.getMessage());
        }
    }

    private static Boolean optionalBoolean(LuaValue value, String path) {
        if (value.isnil()) {
            return null;
        }
        if (!value.isboolean()) {
            throw new LuaError(path + " must be a boolean.");
        }
        return Boolean.valueOf(value.toboolean());
    }

    private static float optionalPositive(LuaValue value, float fallback, String path) {
        if (value.isnil()) {
            return fallback;
        }
        double result = number(value, path);
        if (result < 0.0D) {
            throw new LuaError(path + " must be nonnegative.");
        }
        return (float) result;
    }
}
