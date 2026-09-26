package betamoon.world.explosion;

import net.minecraft.src.Entity;

/** Validated immutable description of one configurable explosion. */
public final class ExplosionRequest {
    public enum BlockMode {
        DESTROY,
        NONE
    }

    public final double x;
    public final double y;
    public final double z;
    public final float strength;
    public final Entity source;
    public final boolean fire;
    public final BlockMode blockMode;
    public final boolean damageEntities;
    public final boolean knockback;
    public final boolean drops;
    public final float dropChance;
    public final boolean sound;
    public final boolean particles;
    public final boolean includeAffectedBlocks;

    public ExplosionRequest(double x, double y, double z, float strength, Entity source, boolean fire,
            BlockMode blockMode, boolean damageEntities, boolean knockback, boolean drops, float dropChance,
            boolean sound, boolean particles, boolean includeAffectedBlocks) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30000000 || Math.abs(y) > 30000000 || Math.abs(z) > 30000000) {
            throw new IllegalArgumentException("Explosion position is outside supported world bounds");
        }
        if (!Float.isFinite(strength) || strength < 0.1F || strength > 16.0F) {
            throw new IllegalArgumentException("Explosion strength must be within 0.1..16");
        }
        if (blockMode == null) {
            throw new IllegalArgumentException("Explosion block mode is required");
        }
        if (!Float.isFinite(dropChance) || dropChance < 0.0F || dropChance > 1.0F) {
            throw new IllegalArgumentException("Explosion drop chance must be within 0..1");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.strength = strength;
        this.source = source;
        this.fire = fire;
        this.blockMode = blockMode;
        this.damageEntities = damageEntities;
        this.knockback = knockback;
        this.drops = drops;
        this.dropChance = dropChance;
        this.sound = sound;
        this.particles = particles;
        this.includeAffectedBlocks = includeAffectedBlocks;
    }
}
