package betamoon.fluid.interaction;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Immutable ordered rule for one directional fluid replacement. */
public final class FluidInteractionDefinition {
    private final ContentKey key;
    private final String owner;
    private final FluidRegistration replaced;
    private final FluidRegistration counterpart;
    private final EnumSet<FluidFace> faces;
    private final List<FluidInteractionOutcome> outcomes;
    private final FluidInteractionEffects effects;

    public FluidInteractionDefinition(ContentKey key, String owner, FluidRegistration replaced,
            FluidRegistration counterpart, EnumSet<FluidFace> faces, List<FluidInteractionOutcome> outcomes,
            FluidInteractionEffects effects) {
        if (key == null || !ContentType.FLUID_INTERACTION.equals(key.type())) {
            throw new IllegalArgumentException("Fluid interaction requires a fluid_interaction content key.");
        }
        if (owner == null || owner.length() == 0 || replaced == null || counterpart == null) {
            throw new IllegalArgumentException("Fluid interaction identity and both fluids are required.");
        }
        if (replaced == counterpart) {
            throw new IllegalArgumentException("Fluid interaction requires two distinct fluids.");
        }
        if (faces == null || faces.isEmpty() || outcomes == null || outcomes.isEmpty()) {
            throw new IllegalArgumentException("Fluid interaction requires faces and at least one outcome.");
        }
        this.key = key;
        this.owner = owner;
        this.replaced = replaced;
        this.counterpart = counterpart;
        this.faces = EnumSet.copyOf(faces);
        this.outcomes = Collections.unmodifiableList(new ArrayList<FluidInteractionOutcome>(outcomes));
        this.effects = effects;
    }

    public ContentKey key() {
        return key;
    }

    public String owner() {
        return owner;
    }

    public FluidRegistration replaced() {
        return replaced;
    }

    public FluidRegistration counterpart() {
        return counterpart;
    }

    public EnumSet<FluidFace> faces() {
        return EnumSet.copyOf(faces);
    }

    public FluidInteractionOutcome outcome(FluidState state) {
        for (FluidInteractionOutcome outcome : outcomes) {
            if (outcome.matches(state)) {
                return outcome;
            }
        }
        return null;
    }

    public FluidInteractionEffects effects() {
        return effects;
    }
}
