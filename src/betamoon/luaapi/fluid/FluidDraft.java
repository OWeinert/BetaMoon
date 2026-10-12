package betamoon.luaapi.fluid;

import betamoon.content.ContentKey;
import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.fluid.FluidEntityPolicy;
import betamoon.fluid.FluidCameraPolicy;

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

    FluidDraft(ContentKey key, String owner, String preset, FluidCarrier carrier, FluidFlowPolicy flow,
            FluidAppearance appearance, FluidDisplacementPolicy displacement, FluidEntityPolicy entity,
            FluidCameraPolicy camera) {
        this.key = key;
        this.owner = owner;
        this.preset = preset;
        this.carrier = carrier;
        this.flow = flow;
        this.appearance = appearance;
        this.displacement = displacement;
        this.entity = entity;
        this.camera = camera;
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
        if (camera.fogColor() != null || camera.fogDensity() != null || camera.hasOverlay()) {
            FluidPolicyHookAvailability.requireCamera();
        }
    }

    public FluidDefinition bind(int flowingBlockId, int stillBlockId, int bucketItemId) {
        return new FluidDefinition(key, owner, preset, carrier, flow, appearance, displacement, entity, camera,
                flowingBlockId, stillBlockId, bucketItemId, false);
    }
}
