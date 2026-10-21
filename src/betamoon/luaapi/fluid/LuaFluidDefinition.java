package betamoon.luaapi.fluid;

import betamoon.fluid.FluidBlockRole;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.block.FluidBlock;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.resource.ResourceApi;
import net.minecraft.src.Item;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/** Unpublished capability token used to construct and bind one advanced fluid. */
public final class LuaFluidDefinition extends LuaTable {
    private final FluidDraft draft;
    private FluidBlock flowing;
    private FluidBlock still;
    private Item bucket;
    private boolean published;

    LuaFluidDefinition(FluidDraft draft) {
        this.draft = draft;
        set("key", LuaValue.valueOf(draft.key().toString()));
        set("material", LuaValue.valueOf(draft.carrier().value()));
        set("preset", LuaValue.valueOf(draft.preset()));
        set("bind", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue bindings = arguments.arg(arguments.arg1() == LuaFluidDefinition.this ? 2 : 1);
                return LuaFluidDefinition.this.bind(bindings);
            }
        });
        FluidDefinitionTracker.track(this);
    }

    public static LuaFluidDefinition require(LuaValue value) {
        if (!(value instanceof LuaFluidDefinition)) {
            throw new LuaError("fluid must be a definition returned by betamoon.fluids:define.");
        }
        return (LuaFluidDefinition) value;
    }

    public FluidDraft draft() {
        return draft;
    }

    public synchronized void attachBlock(FluidBlockRole role, FluidBlock block) {
        ensureUnpublished();
        if (role == FluidBlockRole.FLOWING) {
            if (flowing != null && flowing != block) {
                throw new LuaError("Fluid '" + draft.key() + "' already has a flowing block.");
            }
            flowing = block;
        } else {
            if (still != null && still != block) {
                throw new LuaError("Fluid '" + draft.key() + "' already has a still block.");
            }
            still = block;
        }
    }

    public synchronized void attachBucket(Item item) {
        ensureUnpublished();
        if (bucket != null && bucket != item) {
            throw new LuaError("Fluid '" + draft.key() + "' already has a bucket item.");
        }
        bucket = item;
    }

    synchronized FluidBlock flowing() {
        return flowing;
    }

    synchronized FluidBlock still() {
        return still;
    }

    synchronized Item bucket() {
        return bucket;
    }

    public synchronized FluidReference bindStored() {
        return publish();
    }

    private synchronized FluidReference bind(LuaValue bindings) {
        if (!bindings.istable()) {
            throw new LuaError("Fluid bind declaration must be a table.");
        }
        fields(bindings, "fluid bind", "flowing", "still", "bucket");
        if (flowing == null || still == null) {
            throw new LuaError("Fluid '" + draft.key() + "' requires one flowing and one still block before bind.");
        }
        if (ResourceApi.requireBlockReference(bindings.get("flowing")) != flowing) {
            throw new LuaError("fluid bind.flowing was not created for this definition token.");
        }
        if (ResourceApi.requireBlockReference(bindings.get("still")) != still) {
            throw new LuaError("fluid bind.still was not created for this definition token.");
        }
        LuaValue bucketReference = bindings.get("bucket");
        if (bucket == null && !bucketReference.isnil()) {
            throw new LuaError("fluid bind.bucket was not created for this definition token.");
        }
        if (bucket != null
                && (bucketReference.isnil() || ResourceApi.requireItemReference(bucketReference) != bucket)) {
            throw new LuaError("fluid bind.bucket was not created for this definition token.");
        }
        return publish();
    }

    private FluidReference publish() {
        ensureUnpublished();
        if (flowing == null || still == null) {
            throw new LuaError("Fluid '" + draft.key() + "' requires one flowing and one still block before bind.");
        }
        draft.requireHooks();
        try {
            FluidDefinition definition = draft.bind(flowing.blockID, still.blockID,
                    bucket == null ? -1 : bucket.shiftedIndex);
            FluidRegistration registration = FluidRegistry.instance().publish(definition);
            flowing.applyAppearance(definition.appearance());
            still.applyAppearance(definition.appearance());
            markPublished();
            if (draft.carrier() == betamoon.fluid.FluidCarrier.WATER
                    || draft.carrier() == betamoon.fluid.FluidCarrier.LAVA) {
                LuaApiUtils.warnForScript(draft.owner(), "Fluids", draft.key()
                        + " uses vanilla '" + draft.carrier().value()
                        + "' as its carrier material. Native material checks may treat it as vanilla fluid.");
            }
            return FluidReference.of(registration);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new LuaError("Fluid '" + draft.key() + "': " + error.getMessage());
        }
    }

    private synchronized void markPublished() {
        ensureUnpublished();
        published = true;
        FluidDefinitionTracker.published(this);
    }

    private void ensureUnpublished() {
        if (published) {
            throw new LuaError("Fluid definition '" + draft.key() + "' has already been bound.");
        }
    }
}
