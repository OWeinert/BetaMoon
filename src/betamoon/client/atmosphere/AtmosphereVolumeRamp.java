package betamoon.client.atmosphere;

/**
 * A retargetable linear fade whose duration does not depend on the requested
 * volume.
 */
public final class AtmosphereVolumeRamp {
    private float value;
    private float target = Float.NaN;
    private int remaining;
    private int duration = -1;

    public float tick(float desired, int fadeTicks) {
        if (!Float.isFinite(desired) || desired < 0 || desired > 4 || fadeTicks < 0 || fadeTicks > 1200) {
            throw new IllegalArgumentException("Invalid atmosphere volume or fade duration");
        }
        if (desired != target || fadeTicks != duration) {
            target = desired;
            remaining = fadeTicks;
            duration = fadeTicks;
        }
        if (remaining > 0) {
            value += (target - value) / remaining;
            remaining--;
        }
        if (remaining == 0) {
            value = target;
        }
        return value;
    }
}
