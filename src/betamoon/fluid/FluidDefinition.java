package betamoon.fluid;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;

/** Complete immutable runtime snapshot for a published fluid. */
public final class FluidDefinition {
    private final ContentKey key;
    private final String owner;
    private final String preset;
    private final FluidCarrier carrier;
    private final FluidFlowPolicy flow;
    private final FluidAppearance appearance;
    private final FluidDisplacementPolicy displacement;
    private final FluidEntityPolicy entity;
    private final FluidCameraPolicy camera;
    private final int flowingBlockId;
    private final int stillBlockId;
    private final int bucketItemId;
    private final boolean vanilla;

    public FluidDefinition(ContentKey key, String owner, String preset, FluidCarrier carrier, FluidFlowPolicy flow,
            FluidAppearance appearance, FluidDisplacementPolicy displacement, int flowingBlockId, int stillBlockId,
            int bucketItemId, boolean vanilla) {
        this(key, owner, preset, carrier, flow, appearance, displacement, FluidEntityPolicy.forPreset(preset),
                FluidCameraPolicy.forPreset(preset), flowingBlockId, stillBlockId, bucketItemId, vanilla);
    }

    public FluidDefinition(ContentKey key, String owner, String preset, FluidCarrier carrier, FluidFlowPolicy flow,
            FluidAppearance appearance, FluidDisplacementPolicy displacement, FluidEntityPolicy entity,
            int flowingBlockId, int stillBlockId, int bucketItemId, boolean vanilla) {
        this(key, owner, preset, carrier, flow, appearance, displacement, entity, FluidCameraPolicy.forPreset(preset),
                flowingBlockId, stillBlockId, bucketItemId, vanilla);
    }

    public FluidDefinition(ContentKey key, String owner, String preset, FluidCarrier carrier, FluidFlowPolicy flow,
            FluidAppearance appearance, FluidDisplacementPolicy displacement, FluidEntityPolicy entity,
            FluidCameraPolicy camera, int flowingBlockId, int stillBlockId, int bucketItemId, boolean vanilla) {
        if (key == null || !ContentType.FLUID.equals(key.type())) {
            throw new IllegalArgumentException("Fluid definition requires a fluid content key.");
        }
        if (owner == null || owner.length() == 0) {
            throw new IllegalArgumentException("Fluid definition requires an owner.");
        }
        if (!"neutral".equals(preset) && !"water".equals(preset) && !"lava".equals(preset)) {
            throw new IllegalArgumentException("Fluid preset must be neutral, water, or lava.");
        }
        if (carrier == null || flow == null || appearance == null || displacement == null || entity == null
                || camera == null) {
            throw new NullPointerException("Fluid definition policies");
        }
        validateBlockId(flowingBlockId, "flowing");
        validateBlockId(stillBlockId, "still");
        if (flowingBlockId == stillBlockId) {
            throw new IllegalArgumentException("Flowing and still fluid blocks must have distinct IDs.");
        }
        if (bucketItemId != -1 && bucketItemId < 256) {
            throw new IllegalArgumentException("Fluid bucket item ID must be -1 or at least 256.");
        }
        this.key = key;
        this.owner = owner;
        this.preset = preset;
        this.carrier = carrier;
        this.flow = flow;
        this.appearance = appearance;
        this.displacement = displacement;
        this.entity = entity;
        this.camera = camera;
        this.flowingBlockId = flowingBlockId;
        this.stillBlockId = stillBlockId;
        this.bucketItemId = bucketItemId;
        this.vanilla = vanilla;
    }

    private static void validateBlockId(int id, String role) {
        if (id < 1 || id > 255) {
            throw new IllegalArgumentException("Fluid " + role + " block ID must be between 1 and 255.");
        }
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

    public int flowingBlockId() {
        return flowingBlockId;
    }

    public int stillBlockId() {
        return stillBlockId;
    }

    public int bucketItemId() {
        return bucketItemId;
    }

    public boolean hasBucket() {
        return bucketItemId >= 0;
    }

    public boolean isVanilla() {
        return vanilla;
    }

    /** Fields which cannot change without replacing native objects and corrupting retained identities. */
    public String structuralSignature() {
        return key + "|" + carrier.value() + "|" + flowingBlockId + "|" + stillBlockId + "|" + bucketItemId;
    }
}
