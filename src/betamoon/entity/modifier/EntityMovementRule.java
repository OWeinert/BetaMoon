package betamoon.entity.modifier;

/** Movement contributions understood by the shared entity modifier resolver. */
public enum EntityMovementRule {
    GRAVITY_MULTIPLIER("physics.gravityMultiplier", Combination.MULTIPLIER, 8),
    AIR_DRAG_HORIZONTAL("physics.airDrag.horizontal", Combination.NUMBER_OVERRIDE, 1),
    AIR_DRAG_VERTICAL("physics.airDrag.vertical", Combination.NUMBER_OVERRIDE, 1),
    FLUID_DRAG_HORIZONTAL("physics.fluidDrag.horizontal", Combination.NUMBER_OVERRIDE, 1),
    FLUID_DRAG_VERTICAL("physics.fluidDrag.vertical", Combination.NUMBER_OVERRIDE, 1),
    TRACK_FALL_DISTANCE("physics.trackFallDistance", Combination.BOOLEAN_OVERRIDE, 0),
    ACCELERATION_MULTIPLIER("movement.accelerationMultiplier", Combination.MULTIPLIER, 8),
    JUMP_MULTIPLIER("movement.jumpMultiplier", Combination.MULTIPLIER, 8),
    JUMP_ENABLED("movement.jumpEnabled", Combination.BOOLEAN_VETO, 0),
    SWIM_LIFT_ENABLED("movement.swimLiftEnabled", Combination.BOOLEAN_VETO, 0),
    SNEAK_SLOWDOWN("movement.sneakSlowdown", Combination.NUMBER_OVERRIDE, 1),
    SNEAK_EDGE_GUARD("movement.sneakEdgeGuard", Combination.BOOLEAN_OVERRIDE, 0),
    SNEAK_BODY_ADJUSTMENT("movement.sneakBodyAdjustment", Combination.BOOLEAN_OVERRIDE, 0),
    FALL_DAMAGE_MULTIPLIER("damage.fallMultiplier", Combination.MULTIPLIER, 8);

    public enum Combination {
        MULTIPLIER,
        NUMBER_OVERRIDE,
        BOOLEAN_OVERRIDE,
        BOOLEAN_VETO
    }

    private final String path;
    private final Combination combination;
    private final double maximum;

    EntityMovementRule(String path, Combination combination, double maximum) {
        this.path = path;
        this.combination = combination;
        this.maximum = maximum;
    }

    public String path() {
        return path;
    }

    public Combination combination() {
        return combination;
    }

    public boolean isNumeric() {
        return combination == Combination.MULTIPLIER || combination == Combination.NUMBER_OVERRIDE;
    }

    public double maximum() {
        return maximum;
    }

    public Object validate(Object value) {
        if (!isNumeric()) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException(path + ": expected a boolean");
            }
            return value;
        }

        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(path + ": expected a finite number in 0.." + maximum);
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < 0 || number > maximum) {
            throw new IllegalArgumentException(path + ": expected a finite number in 0.." + maximum);
        }
        return Double.valueOf(number);
    }

    public static EntityMovementRule find(String path) {
        for (EntityMovementRule rule : values()) {
            if (rule.path.equals(path)) {
                return rule;
            }
        }
        return null;
    }

    public static EntityMovementRule require(String path) {
        EntityMovementRule rule = find(path);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown entity movement rule: " + path);
        }
        return rule;
    }
}
