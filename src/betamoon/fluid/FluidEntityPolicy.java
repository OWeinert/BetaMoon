package betamoon.fluid;

/** Immutable entity-facing behavior compiled from a fluid declaration. */
public final class FluidEntityPolicy {
    public enum Movement {
        NONE("none"),
        WATER("water"),
        LAVA("lava");

        private final String value;

        Movement(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private final Movement movement;
    private final float currentScale;
    private final boolean drowning;
    private final boolean countsAsWet;
    private final boolean extinguishesFire;
    private final boolean ignitesEntities;
    private final int contactDamage;
    private final int contactIntervalTicks;

    public FluidEntityPolicy(Movement movement, float currentScale, boolean drowning, boolean countsAsWet,
            boolean extinguishesFire, boolean ignitesEntities, int contactDamage) {
        this(movement, currentScale, drowning, countsAsWet, extinguishesFire, ignitesEntities, contactDamage, 10);
    }

    public FluidEntityPolicy(Movement movement, float currentScale, boolean drowning, boolean countsAsWet,
            boolean extinguishesFire, boolean ignitesEntities, int contactDamage, int contactIntervalTicks) {
        if (movement == null) {
            throw new NullPointerException("Fluid entity movement");
        }
        if (Float.isNaN(currentScale) || Float.isInfinite(currentScale)
                || currentScale < 0.0F || currentScale > 8.0F) {
            throw new IllegalArgumentException("Fluid current scale must be within 0..8.");
        }
        if (contactDamage < 0) {
            throw new IllegalArgumentException("Fluid contact damage cannot be negative.");
        }
        if (contactIntervalTicks < 5 || contactIntervalTicks > 200) {
            throw new IllegalArgumentException("Fluid contact interval must be within 5..200 ticks.");
        }
        this.movement = movement;
        this.currentScale = currentScale;
        this.drowning = drowning;
        this.countsAsWet = countsAsWet;
        this.extinguishesFire = extinguishesFire;
        this.ignitesEntities = ignitesEntities;
        this.contactDamage = contactDamage;
        this.contactIntervalTicks = contactIntervalTicks;
    }

    public static FluidEntityPolicy forPreset(String preset) {
        if ("water".equals(preset)) {
            return new FluidEntityPolicy(Movement.WATER, 1.0F, true, true, true, false, 0);
        }
        if ("lava".equals(preset)) {
            return new FluidEntityPolicy(Movement.LAVA, 0.0F, false, false, false, true, 4);
        }
        if ("neutral".equals(preset)) {
            return new FluidEntityPolicy(Movement.NONE, 0.0F, false, false, false, false, 0);
        }
        throw new IllegalArgumentException("Fluid preset must be neutral, water, or lava.");
    }

    public Movement movement() {
        return movement;
    }

    public float currentScale() {
        return currentScale;
    }

    public boolean drowning() {
        return drowning;
    }

    public boolean countsAsWet() {
        return countsAsWet;
    }

    public boolean extinguishesFire() {
        return extinguishesFire;
    }

    public boolean ignitesEntities() {
        return ignitesEntities;
    }

    public int contactDamage() {
        return contactDamage;
    }

    public int contactIntervalTicks() {
        return contactIntervalTicks;
    }
}
