package betamoon.luaapi.fluid;

import betamoon.fluid.FluidBlockRole;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidRegistration;
import betamoon.luaapi.resource.ResourceApi;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Read-only-by-contract Lua view over a stable fluid registration. */
public final class FluidReference extends LuaTable {
    private static final Map<FluidRegistration, WeakReference<FluidReference>> REFERENCES = new WeakHashMap<>();
    private final FluidRegistration registration;
    private final boolean includeResources;

    public FluidReference(FluidRegistration registration) {
        this(registration, true);
    }

    public static synchronized FluidReference of(FluidRegistration registration) {
        WeakReference<FluidReference> cached = REFERENCES.get(registration);
        FluidReference reference = cached == null ? null : cached.get();
        if (reference == null) {
            reference = new FluidReference(registration);
            REFERENCES.put(registration, new WeakReference<>(reference));
        }
        return reference;
    }

    public FluidReference(FluidRegistration registration, boolean includeResources) {
        this.registration = registration;
        this.includeResources = includeResources;
        FluidDefinition definition = registration.definition();
        super.rawset(LuaValue.valueOf("key"), LuaValue.valueOf(definition.key().toString()));
        super.rawset(LuaValue.valueOf("owner"), LuaValue.valueOf(definition.owner()));
        super.rawset(LuaValue.valueOf("isVanilla"), LuaValue.valueOf(definition.isVanilla()));
        super.rawset(LuaValue.valueOf("exists"), LuaValue.TRUE);
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.isstring()) {
            String name = key.tojstring();
            FluidDefinition definition = registration.definition();
            if ("material".equals(name)) {
                return LuaValue.valueOf(definition.carrier().value());
            }
            if ("preset".equals(name)) {
                return LuaValue.valueOf(definition.preset());
            }
            if ("safeTeleportArrival".equals(name)) {
                return LuaValue.valueOf(definition.safeTeleportArrival());
            }
            if (includeResources && "flowingBlock".equals(name)) {
                return blockReference(definition.flowingBlockId());
            }
            if (includeResources && "stillBlock".equals(name)) {
                return blockReference(definition.stillBlockId());
            }
            if (includeResources && "bucketItem".equals(name)) {
                return definition.hasBucket() ? itemReference(definition.bucketItemId()) : NIL;
            }
        }
        return super.rawget(key);
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
    public LuaValue setmetatable(LuaValue metatable) {
        throw readOnly();
    }

    public FluidRegistration registration() {
        return registration;
    }

    private static LuaValue blockReference(int id) {
        return id >= 0 && id < Block.blocksList.length ? ResourceApi.blockReference(Block.blocksList[id]) : NIL;
    }

    private static LuaValue itemReference(int id) {
        return id >= 0 && id < Item.itemsList.length ? ResourceApi.itemReference(Item.itemsList[id]) : NIL;
    }

    public static FluidReference compact(FluidRegistration registration) {
        return new FluidReference(registration, false);
    }

    public static String roleName(FluidBlockRole role) {
        return role == FluidBlockRole.FLOWING ? "flowing" : "still";
    }

    private LuaError readOnly() {
        throw new LuaError("Fluid reference '" + registration.key() + "' is read-only.");
    }
}
