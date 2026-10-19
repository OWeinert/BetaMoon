package betamoon.luaapi.fluid;

import betamoon.content.ContentKey;
import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidCameraPolicy;
import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidEcologyPolicy;
import betamoon.fluid.FluidEntityPolicy;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.instrumentation.api.RequiredNativeHooks;
import org.luaj.vm2.LuaError;

/** Parsed fluid policy without native block/item identities. */
public final class FluidDraft {
    private final ContentKey key;
    private final String owner;
    private final String preset;
    private final FluidCarrier carrier;
    private final FluidFlowPolicy flow;
    private final FluidAppearance appearance;
    private final FluidDisplacementPolicy displacement;
    private final FluidEntityPolicy entity;
    private final FluidCameraPolicy camera;
    private final FluidEcologyPolicy ecology;
    private final boolean safeTeleportArrival;

    FluidDraft(ContentKey key, String owner, String preset, FluidCarrier carrier, FluidFlowPolicy flow,
            FluidAppearance appearance, FluidDisplacementPolicy displacement, FluidEntityPolicy entity,
            FluidCameraPolicy camera, FluidEcologyPolicy ecology, boolean safeTeleportArrival) {
        this.key = key;
        this.owner = owner;
        this.preset = preset;
        this.carrier = carrier;
        this.flow = flow;
        this.appearance = appearance;
        this.displacement = displacement;
        this.entity = entity;
        this.camera = camera;
        this.ecology = ecology;
        this.safeTeleportArrival = safeTeleportArrival;
    }

    public ContentKey key() {
        return key;
    }

    public String owner() {
        return owner;
    }

    public String preset() {
        return preset;
    }

    public FluidCarrier carrier() {
        return carrier;
    }

    public FluidFlowPolicy flow() {
        return flow;
    }

    public FluidAppearance appearance() {
        return appearance;
    }

    public FluidDisplacementPolicy displacement() {
        return displacement;
    }

    public FluidEntityPolicy entity() {
        return entity;
    }

    public FluidCameraPolicy camera() {
        return camera;
    }

    public void requireHooks() {
        FluidPolicyHookAvailability.requireEntity();
        try {
            RequiredNativeHooks.require("Fluid ecology", true, "betamoon:fluid_ecology:falling",
                    "betamoon:fluid_ecology:farmland", "betamoon:fluid_ecology:reeds", "betamoon:fluid_ecology:habitat",
                    "betamoon:fluid_ecology:habitat_position", "betamoon:fluid_ecology:pathfinding",
                    "betamoon:fluid_ecology:sponge", "betamoon:fluid_ecology:boat");
        } catch (IllegalStateException exception) {
            throw new LuaError(exception.getMessage());
        }
        if (camera.fogColor() != null || camera.fogDensity() != null || camera.hasOverlay()) {
            FluidPolicyHookAvailability.requireCamera();
        }
    }

    public FluidDefinition bind(int flowingBlockId, int stillBlockId, int bucketItemId) {
        return new FluidDefinition(key, owner, preset, carrier, flow, appearance, displacement, entity, camera, ecology,
                flowingBlockId, stillBlockId, bucketItemId, false, safeTeleportArrival);
    }
}
