package betamoon.fluid;

/** Immutable, validated policy for the bounded fluid-flow algorithm. */
public final class FluidFlowPolicy {
    public enum SourceCreation {
        NEVER,
        TWO_ADJACENT
    }

    private final int tickRate;
    private final int levelStep;
    private final int slopeSearch;
    private final SourceCreation sourceCreation;
    private final float retreatDelayChance;
    private final Integer hellTickRate;
    private final Integer hellLevelStep;

    public FluidFlowPolicy(int tickRate, int levelStep, int slopeSearch, SourceCreation sourceCreation,
            float retreatDelayChance) {
        this(tickRate, levelStep, slopeSearch, sourceCreation, retreatDelayChance, null);
    }

    public FluidFlowPolicy(int tickRate, int levelStep, int slopeSearch, SourceCreation sourceCreation,
            float retreatDelayChance, Integer hellLevelStep) {
        this(tickRate, levelStep, slopeSearch, sourceCreation, retreatDelayChance, null, hellLevelStep);
    }

    public FluidFlowPolicy(int tickRate, int levelStep, int slopeSearch, SourceCreation sourceCreation,
            float retreatDelayChance, Integer hellTickRate, Integer hellLevelStep) {
        if (tickRate < 1 || tickRate > 1200) {
            throw new IllegalArgumentException("Fluid tick rate must be between 1 and 1200.");
        }
        if (levelStep < 1 || levelStep > 7) {
            throw new IllegalArgumentException("Fluid level step must be between 1 and 7.");
        }
        if (slopeSearch < 0 || slopeSearch > 8) {
            throw new IllegalArgumentException("Fluid slope search must be between 0 and 8.");
        }
        if (sourceCreation == null) {
            throw new NullPointerException("Fluid source creation policy");
        }
        if (Float.isNaN(retreatDelayChance) || Float.isInfinite(retreatDelayChance)
                || retreatDelayChance < 0.0F || retreatDelayChance > 1.0F) {
            throw new IllegalArgumentException("Fluid retreat delay chance must be between 0 and 1.");
        }
        if (hellLevelStep != null && (hellLevelStep.intValue() < 1 || hellLevelStep.intValue() > 7)) {
            throw new IllegalArgumentException("Fluid Nether level step must be between 1 and 7.");
        }
        if (hellTickRate != null && (hellTickRate.intValue() < 1 || hellTickRate.intValue() > 1200)) {
            throw new IllegalArgumentException("Fluid Nether tick rate must be between 1 and 1200.");
        }
        this.tickRate = tickRate;
        this.levelStep = levelStep;
        this.slopeSearch = slopeSearch;
        this.sourceCreation = sourceCreation;
        this.retreatDelayChance = retreatDelayChance;
        this.hellTickRate = hellTickRate;
        this.hellLevelStep = hellLevelStep;
    }

    public static FluidFlowPolicy water() {
        return new FluidFlowPolicy(5, 1, 4, SourceCreation.TWO_ADJACENT, 0.0F);
    }

    public static FluidFlowPolicy lava() {
        return new FluidFlowPolicy(30, 2, 4, SourceCreation.NEVER, 0.75F, Integer.valueOf(1));
    }

    public static FluidFlowPolicy neutral() {
        return new FluidFlowPolicy(5, 1, 4, SourceCreation.NEVER, 0.0F);
    }

    public int tickRate() {
        return tickRate;
    }

    public int tickRate(boolean hellWorld) {
        return hellWorld && hellTickRate != null ? hellTickRate.intValue() : tickRate;
    }

    public int levelStep() {
        return levelStep;
    }

    public int slopeSearch() {
        return slopeSearch;
    }

    public SourceCreation sourceCreation() {
        return sourceCreation;
    }

    public float retreatDelayChance() {
        return retreatDelayChance;
    }

    public int levelStep(boolean hellWorld) {
        return hellWorld && hellLevelStep != null ? hellLevelStep.intValue() : levelStep;
    }

    public Integer hellLevelStep() {
        return hellLevelStep;
    }

    public Integer hellTickRate() {
        return hellTickRate;
    }
}
