package betamoon.luaapi.fluid;

import betamoon.content.ContentKey;
import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidFlowPolicy;

/** Parsed fluid policy without native block/item identities. */
public final class FluidDraft {
    private final ContentKey key;
    private final String owner;
    private final String preset;
    private final FluidCarrier carrier;
    private final FluidFlowPolicy flow;
    private final FluidAppearance appearance;
    private final FluidDisplacementPolicy displacement;

    FluidDraft(ContentKey key, String owner, String preset, FluidCarrier carrier, FluidFlowPolicy flow,
            FluidAppearance appearance, FluidDisplacementPolicy displacement) {
        this.key = key;
        this.owner = owner;
        this.preset = preset;
        this.carrier = carrier;
        this.flow = flow;
        this.appearance = appearance;
        this.displacement = displacement;
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

    public FluidDefinition bind(int flowingBlockId, int stillBlockId, int bucketItemId) {
        return new FluidDefinition(key, owner, preset, carrier, flow, appearance, displacement, flowingBlockId,
                stillBlockId, bucketItemId, false);
    }
}
