package betamoon.fluid;

import betamoon.content.ContentKey;

/** Stable registry slot whose immutable runtime snapshot can be replaced on reload. */
public final class FluidRegistration {
    private final ContentKey key;
    private final String owner;
    private final String structuralSignature;
    private volatile FluidDefinition definition;

    FluidRegistration(FluidDefinition definition) {
        key = definition.key();
        owner = definition.owner();
        structuralSignature = definition.structuralSignature();
        this.definition = definition;
    }

    void update(FluidDefinition replacement) {
        if (!key.equals(replacement.key()) || !owner.equals(replacement.owner())) {
            throw new IllegalArgumentException("Cannot replace a fluid registration with another identity or owner.");
        }
        if (!structuralSignature.equals(replacement.structuralSignature())) {
            throw new IllegalStateException("Fluid '" + key
                    + "' changed structural fields. Restart Minecraft to apply this change.");
        }
        definition = replacement;
    }

    public ContentKey key() {
        return key;
    }

    public String owner() {
        return owner;
    }

    public FluidDefinition definition() {
        return definition;
    }

    public FluidBlockRole roleOf(int blockId) {
        FluidDefinition current = definition;
        if (blockId == current.flowingBlockId()) {
            return FluidBlockRole.FLOWING;
        }
        if (blockId == current.stillBlockId()) {
            return FluidBlockRole.STILL;
        }
        return null;
    }
}
