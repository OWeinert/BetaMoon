package betamoon.entity;

import java.util.Random;
import betamoon.network.protocol.PresentationSnapshot;
import net.minecraft.src.Entity;

/** Transient per-instance animation, visual overrides, and automatic effect cadence. */
public final class EntityPresentationState {
    private final Random random;
    private String animationClip;
    private double animationStartAge;
    private double animationStartSeconds;
    private double animationSpeed = 1;
    private Boolean visible;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private float rotationYaw;
    private float rotationPitch;
    private float rotationRoll;
    private double scaleX = 1;
    private double scaleY = 1;
    private double scaleZ = 1;
    private EntityTypeDefinition scheduledDefinition;
    private int nextAmbientAge = -1;
    private boolean hasPreviousPosition;
    private double previousX;
    private double previousZ;
    private double stepProgress;
    private long revision;

    EntityPresentationState(String identity) {
        random = new Random(identity.hashCode() * 31L + 0x4f1bbcdcL);
    }

    public void playAnimation(String clip, double speed, double startSeconds, boolean restart, double age) {
        if (!restart && clip.equals(animationClip) && animationSpeed == speed) {
            return;
        }
        animationClip = clip;
        animationSpeed = speed;
        animationStartSeconds = startSeconds;
        animationStartAge = age;
        revision++;
    }

    public void stopAnimation() {
        if (animationClip == null) {
            return;
        }
        animationClip = null;
        revision++;
    }

    public Animation animation() {
        return animationClip == null ? null
                : new Animation(animationClip, animationStartAge, animationStartSeconds, animationSpeed);
    }

    public void visible(boolean value) {
        if (visible != null && visible.booleanValue() == value) {
            return;
        }
        visible = value;
        revision++;
    }

    public boolean visible(EntityRenderDefinition defaults) {
        return visible == null ? defaults.visible : visible;
    }

    public void visualOffset(double x, double y, double z) {
        if (offsetX == x && offsetY == y && offsetZ == z) {
            return;
        }
        offsetX = x;
        offsetY = y;
        offsetZ = z;
        revision++;
    }

    public void visualRotation(float yaw, float pitch, float roll) {
        if (rotationYaw == yaw && rotationPitch == pitch && rotationRoll == roll) {
            return;
        }
        rotationYaw = yaw;
        rotationPitch = pitch;
        rotationRoll = roll;
        revision++;
    }

    public void visualScale(double x, double y, double z) {
        if (scaleX == x && scaleY == y && scaleZ == z) {
            return;
        }
        scaleX = x;
        scaleY = y;
        scaleZ = z;
        revision++;
    }

    public void resetVisualTransform() {
        boolean changed = offsetX != 0 || offsetY != 0 || offsetZ != 0
                || rotationYaw != 0 || rotationPitch != 0 || rotationRoll != 0
                || scaleX != 1 || scaleY != 1 || scaleZ != 1;
        offsetX = offsetY = offsetZ = 0;
        rotationYaw = rotationPitch = rotationRoll = 0;
        scaleX = scaleY = scaleZ = 1;
        if (changed) {
            revision++;
        }
    }

    public void resetVisuals() {
        boolean visibilityChanged = visible != null;
        visible = null;
        resetVisualTransform();
        if (visibilityChanged) {
            revision++;
        }
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public double offsetZ() {
        return offsetZ;
    }

    public float rotationYaw() {
        return rotationYaw;
    }

    public float rotationPitch() {
        return rotationPitch;
    }

    public float rotationRoll() {
        return rotationRoll;
    }

    public double scaleX() {
        return scaleX;
    }

    public double scaleY() {
        return scaleY;
    }

    public double scaleZ() {
        return scaleZ;
    }

    public long revision() {
        return revision;
    }

    public PresentationSnapshot networkSnapshot(EntityRenderDefinition defaults, double age) {
        Animation animation = animation();
        return new PresentationSnapshot(animation == null ? null : animation.clip,
                animation == null ? 0 : animation.seconds(age), animation == null ? 1 : animation.speed,
                visible(defaults), offsetX, offsetY, offsetZ, rotationYaw, rotationPitch, rotationRoll,
                scaleX, scaleY, scaleZ);
    }

    public void applyNetworkSnapshot(PresentationSnapshot snapshot, double age, long nextRevision) {
        if (snapshot.animation == null) {
            animationClip = null;
        } else {
            animationClip = snapshot.animation;
            animationStartAge = age;
            animationStartSeconds = snapshot.animationSeconds;
            animationSpeed = snapshot.animationSpeed;
        }
        visible = Boolean.valueOf(snapshot.visible);
        offsetX = snapshot.offsetX;
        offsetY = snapshot.offsetY;
        offsetZ = snapshot.offsetZ;
        rotationYaw = snapshot.rotationYaw;
        rotationPitch = snapshot.rotationPitch;
        rotationRoll = snapshot.rotationRoll;
        scaleX = snapshot.scaleX;
        scaleY = snapshot.scaleY;
        scaleZ = snapshot.scaleZ;
        revision = nextRevision;
    }

    void tick(Entity entity, EntityTypeDefinition definition) {
        if (entity.isDead || entity.worldObj == null || entity.worldObj.multiplayerWorld) {
            return;
        }
        if (scheduledDefinition != definition) {
            scheduledDefinition = definition;
            nextAmbientAge = -1;
            hasPreviousPosition = false;
            stepProgress = 0;
        }
        ambient(entity, definition.sounds.get(EntitySoundsDefinition.Event.AMBIENT));
        step(entity, definition.sounds.get(EntitySoundsDefinition.Event.STEP));
    }

    void deactivate() {
        scheduledDefinition = null;
        nextAmbientAge = -1;
        hasPreviousPosition = false;
        stepProgress = 0;
    }

    private void ambient(Entity entity, EntitySoundsDefinition.Binding binding) {
        if (binding == null) {
            return;
        }
        if (nextAmbientAge < 0) {
            nextAmbientAge = entity.ticksExisted + interval(binding);
            return;
        }
        if (entity.ticksExisted >= nextAmbientAge) {
            EntityPresentationEvents.sound(entity, binding);
            nextAmbientAge = entity.ticksExisted + interval(binding);
        }
    }

    private int interval(EntitySoundsDefinition.Binding binding) {
        if (binding.intervalMin == binding.intervalMax) {
            return binding.intervalMin;
        }
        return binding.intervalMin + random.nextInt(binding.intervalMax - binding.intervalMin + 1);
    }

    private void step(Entity entity, EntitySoundsDefinition.Binding binding) {
        if (binding == null) {
            return;
        }
        if (!hasPreviousPosition) {
            previousX = entity.posX;
            previousZ = entity.posZ;
            hasPreviousPosition = true;
            return;
        }
        double dx = entity.posX - previousX;
        double dz = entity.posZ - previousZ;
        previousX = entity.posX;
        previousZ = entity.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (!entity.onGround || distance > Math.max(4, binding.stepDistance * 4)) {
            return;
        }
        stepProgress += distance;
        if (stepProgress >= binding.stepDistance) {
            stepProgress %= binding.stepDistance;
            EntityPresentationEvents.sound(entity, binding);
        }
    }

    public static final class Animation {
        public final String clip;
        public final double startAge;
        public final double startSeconds;
        public final double speed;

        private Animation(String clip, double startAge, double startSeconds, double speed) {
            this.clip = clip;
            this.startAge = startAge;
            this.startSeconds = startSeconds;
            this.speed = speed;
        }

        public double seconds(double age) {
            return startSeconds + Math.max(0, age - startAge) / 20 * speed;
        }
    }
}
