package betamoon.luaapi.fluid;

import betamoon.content.ContentKey;
import betamoon.content.ContentKeyException;
import betamoon.content.ContentType;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.luaapi.block.BlockApi;
import betamoon.luaapi.block.FluidBlockRegistration;
import betamoon.luaapi.item.FluidBucketRegistration;
import betamoon.luaapi.item.ItemApi;
import betamoon.luaapi.material.BlockMaterialRegistry;
import betamoon.luaapi.material.BlockMaterialReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/** First-class Lua registry and transactional shorthand for custom fluids. */
public final class FluidApi {
    private FluidApi() {
    }

    public static void attach(LuaTable module) {
        FluidRegistry.instance().registerVanilla();
        LuaTable fluids = new LuaTable();
        fluids.set("define", function(fluids, arguments -> define(arguments)));
        fluids.set("add", function(fluids, arguments -> add(arguments)));
        fluids.set("get", function(fluids, arguments -> get(arguments, false)));
        fluids.set("getRequired", function(fluids, arguments -> get(arguments, true)));
        fluids.set("find", function(fluids, FluidApi::find));
        fluids.set("first", function(fluids, FluidApi::first));
        fluids.set("one", function(fluids, FluidApi::one));
        fluids.set("interactions", FluidInteractionApi.create());
        module.set("fluids", fluids);
    }

    private static LuaValue define(LuaValue value) {
        return new LuaFluidDefinition(FluidDeclaration.parse(value, false).draft);
    }

    private static LuaValue add(LuaValue value) {
        FluidDeclaration declaration = FluidDeclaration.parse(value, true);
        LuaFluidDefinition token = new LuaFluidDefinition(declaration.draft);
        LuaValue flowingDefinition = roleDefinition(declaration.blocks, "flowing", token);
        LuaValue stillDefinition = roleDefinition(declaration.blocks, "still", token);
        LuaValue bucketDefinition = bucketDefinition(declaration.bucket, token);

        FluidBlockRegistration.preflight(flowingDefinition);
        FluidBlockRegistration.preflight(stillDefinition);
        int flowingId = flowingDefinition.get("id").checkint();
        int stillId = stillDefinition.get("id").checkint();
        if (flowingId == stillId) {
            throw new LuaError("fluid.blocks.flowing and fluid.blocks.still must use distinct block IDs.");
        }
        if (!bucketDefinition.isnil()) {
            FluidBucketRegistration.preflight(bucketDefinition);
        }

        int bucketId = bucketDefinition.isnil() ? -1 : bucketDefinition.get("id").checkint();
        declaration.draft.requireHooks();
        try {
            FluidRegistry.instance().preflight(declaration.draft.bind(flowingId, stillId, bucketId));
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new LuaError("Fluid '" + declaration.draft.key() + "': " + error.getMessage());
        }

        BlockApi.add(flowingDefinition);
        BlockApi.add(stillDefinition);
        if (!bucketDefinition.isnil()) {
            ItemApi.add(bucketDefinition);
        }
        return token.bindStored();
    }

    private static LuaValue roleDefinition(LuaValue blocks, String role, LuaFluidDefinition token) {
        fields(blocks, "fluid.blocks", "flowing", "still");
        LuaValue value = blocks.get(role);
        if (!value.istable()) {
            throw new LuaError("fluid.blocks." + role + " must be a table.");
        }
        LuaTable result = copy(value);
        result.set("type", "fluid");
        result.set("fluid", token);
        result.set("fluidRole", role);
        return result;
    }

    private static LuaValue bucketDefinition(LuaValue value, LuaFluidDefinition token) {
        if (value.isnil() || value.isboolean() && !value.toboolean()) {
            return LuaValue.NIL;
        }
        if (!value.istable()) {
            throw new LuaError("fluid.bucket must be a table or false.");
        }
        LuaTable result = copy(value);
        result.set("type", "fluid_bucket");
        result.set("fluid", token);
        return result;
    }

    private static LuaTable copy(LuaValue source) {
        LuaTable result = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = source.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return result;
            }
            result.set(key, next.arg(2));
        }
    }

    private static LuaValue get(LuaValue value, boolean required) {
        ContentKey key = key(value);
        FluidRegistration registration = FluidRegistry.instance().get(key);
        if (registration == null && required) {
            throw new LuaError("Fluid '" + key + "' is not registered.");
        }
        return registration == null ? LuaValue.NIL : new FluidReference(registration);
    }

    private static LuaValue find(LuaValue criteria) {
        List<FluidRegistration> matches = matches(criteria);
        LuaTable result = new LuaTable();
        for (int index = 0; index < matches.size(); index++) {
            result.set(index + 1, new FluidReference(matches.get(index)));
        }
        return result;
    }

    private static LuaValue first(LuaValue criteria) {
        List<FluidRegistration> matches = matches(criteria);
        return matches.isEmpty() ? LuaValue.NIL : new FluidReference(matches.get(0));
    }

    private static LuaValue one(LuaValue criteria) {
        List<FluidRegistration> matches = matches(criteria);
        if (matches.isEmpty()) {
            return LuaValue.NIL;
        }
        if (matches.size() != 1) {
            throw new LuaError("Expected exactly one fluid, found " + matches.size() + ".");
        }
        return new FluidReference(matches.get(0));
    }

    private static List<FluidRegistration> matches(LuaValue criteria) {
        if (criteria.isnil()) {
            criteria = new LuaTable();
        }
        if (!criteria.istable()) {
            throw new LuaError("Fluid find criteria must be a table.");
        }
        fields(criteria, "fluid find", "key", "material", "preset", "bucket", "isVanilla");
        List<FluidRegistration> result = new ArrayList<FluidRegistration>();
        for (FluidRegistration registration : FluidRegistry.instance().snapshot()) {
            if (matches(registration.definition(), criteria)) {
                result.add(registration);
            }
        }
        return result;
    }

    private static boolean matches(FluidDefinition fluid, LuaValue criteria) {
        LuaValue key = criteria.get("key");
        if (!key.isnil() && !fluid.key().equals(key(key))) {
            return false;
        }
        LuaValue material = criteria.get("material");
        if (!material.isnil()) {
            String materialKey = material instanceof BlockMaterialReference
                    ? BlockMaterialRegistry.resolve(material, "fluid find.material").key
                    : material.checkjstring();
            if (!fluid.carrier().value().equals(materialKey)) {
                return false;
            }
        }
        LuaValue preset = criteria.get("preset");
        if (!preset.isnil() && !fluid.preset().equals(preset.checkjstring())) {
            return false;
        }
        LuaValue bucket = criteria.get("bucket");
        if (!bucket.isnil() && fluid.hasBucket() != bucket.checkboolean()) {
            return false;
        }
        LuaValue vanilla = criteria.get("isVanilla");
        return vanilla.isnil() || fluid.isVanilla() == vanilla.checkboolean();
    }

    private static ContentKey key(LuaValue value) {
        try {
            return ContentKey.parseForType(value.checkjstring(), ContentType.FLUID);
        } catch (ContentKeyException error) {
            throw new LuaError("Fluid key: " + error.getMessage());
        }
    }

    private static VarArgFunction function(final LuaTable receiver, final Call call) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue value = arguments.arg(arguments.arg1() == receiver ? 2 : 1);
                return call.invoke(value);
            }
        };
    }

    private interface Call {
        LuaValue invoke(LuaValue value);
    }
}
