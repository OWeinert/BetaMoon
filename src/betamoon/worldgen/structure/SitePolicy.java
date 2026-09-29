package betamoon.worldgen.structure;

import java.util.Locale;

/** Footprint-aware environment eligibility shared by local and regional structures. */
public final class SitePolicy {
    public enum Type {
        ANY,
        LAND_SURFACE,
        UNDERWATER,
        UNDERGROUND,
        CAVE,
        FLUID_SURFACE;

        public static Type parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown site type: " + value);
            }
        }

        public String luaName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Scope {
        ORIGIN,
        SUPPORT_FOOTPRINT,
        CLEARANCE_MASK,
        FULL_BOUNDS;

        public static Scope parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown site scope: " + value);
            }
        }

        public String luaName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Medium {
        ANY,
        AIR,
        WATER,
        LAVA,
        ANY_FLUID,
        SOLID;

        public static Medium parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown site medium: " + value);
            }
        }

        public String luaName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final SitePolicy ANY = new SitePolicy(Type.ANY, Scope.ORIGIN, Medium.ANY, 0, 127, 0, 127,
            0.0D, 1.0D, 0.0D, 0, 127, null);

    public final Type type;
    public final Scope scope;
    public final Medium medium;
    public final int minDepthBelowSurface;
    public final int maxDepthBelowSurface;
    public final int minFluidDepth;
    public final int maxFluidDepth;
    public final double minFluidCoverage;
    public final double maxFluidCoverage;
    public final double minExistingAirRatio;
    public final int minSolidCover;
    public final int maxSolidCover;
    public final Boolean requireSky;

    public SitePolicy(Type type, Scope scope, Medium medium, int minDepthBelowSurface, int maxDepthBelowSurface,
            int minFluidDepth, int maxFluidDepth, double minFluidCoverage, double maxFluidCoverage,
            double minExistingAirRatio, int minSolidCover, int maxSolidCover, Boolean requireSky) {
        this.type = type;
        this.scope = scope;
        this.medium = medium;
        this.minDepthBelowSurface = minDepthBelowSurface;
        this.maxDepthBelowSurface = maxDepthBelowSurface;
        this.minFluidDepth = minFluidDepth;
        this.maxFluidDepth = maxFluidDepth;
        this.minFluidCoverage = minFluidCoverage;
        this.maxFluidCoverage = maxFluidCoverage;
        this.minExistingAirRatio = minExistingAirRatio;
        this.minSolidCover = minSolidCover;
        this.maxSolidCover = maxSolidCover;
        this.requireSky = requireSky;
    }

    public boolean active() {
        return type != Type.ANY || medium != Medium.ANY || requireSky != null;
    }

    public String signature() {
        return type.luaName() + '|' + scope.luaName() + '|' + medium.luaName() + '|' + minDepthBelowSurface + '|'
                + maxDepthBelowSurface + '|' + minFluidDepth + '|' + maxFluidDepth + '|'
                + Double.doubleToLongBits(minFluidCoverage) + '|' + Double.doubleToLongBits(maxFluidCoverage) + '|'
                + Double.doubleToLongBits(minExistingAirRatio) + '|' + minSolidCover + '|' + maxSolidCover + '|'
                + requireSky;
    }
}
