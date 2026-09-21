package betamoon.network.protocol;

/** Transient visual state included in spawns and presentation updates. */
public final class PresentationSnapshot {
    public final String animation;
    public final double animationSeconds;
    public final double animationSpeed;
    public final boolean visible;
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;
    public final float rotationYaw;
    public final float rotationPitch;
    public final float rotationRoll;
    public final double scaleX;
    public final double scaleY;
    public final double scaleZ;

    public PresentationSnapshot(String animation, double animationSeconds, double animationSpeed,
            boolean visible, double offsetX, double offsetY, double offsetZ,
            float rotationYaw, float rotationPitch, float rotationRoll,
            double scaleX, double scaleY, double scaleZ) {
        this.animation = animation;
        this.animationSeconds = animationSeconds;
        this.animationSpeed = animationSpeed;
        this.visible = visible;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.rotationYaw = rotationYaw;
        this.rotationPitch = rotationPitch;
        this.rotationRoll = rotationRoll;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        validateFinite();
    }

    private void validateFinite() {
        double[] values = {animationSeconds, animationSpeed, offsetX, offsetY, offsetZ,
                rotationYaw, rotationPitch, rotationRoll, scaleX, scaleY, scaleZ};
        for (double value : values) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Presentation values must be finite");
            }
        }
    }
}
