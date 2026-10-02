package betamoon.fluid.interaction;

import net.minecraft.src.World;

/** Bounded audiovisual consequences of a resolved interaction. */
public final class FluidInteractionEffects {
    public enum Timing {
        CONTACT,
        CHANGED
    }

    private final String sound;
    private final float volume;
    private final float pitch;
    private final String particle;
    private final int particleCount;
    private final Timing timing;
    private final float pitchVariation;
    private final double fixedParticleY;

    public FluidInteractionEffects(String sound, float volume, float pitch, String particle, int particleCount,
            Timing timing) {
        this(sound, volume, pitch, particle, particleCount, timing, 0.0F, Double.NaN);
    }

    private FluidInteractionEffects(String sound, float volume, float pitch, String particle, int particleCount,
            Timing timing, float pitchVariation, double fixedParticleY) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.particle = particle;
        this.particleCount = particleCount;
        this.timing = timing;
        this.pitchVariation = pitchVariation;
        this.fixedParticleY = fixedParticleY;
    }

    static FluidInteractionEffects vanillaLavaMix() {
        return new FluidInteractionEffects("random.fizz", 0.5F, 2.6F, "largesmoke", 8, Timing.CONTACT,
                0.8F, 1.2D);
    }

    public Timing timing() {
        return timing;
    }

    public void play(World world, int x, int y, int z) {
        if (sound != null) {
            float resolvedPitch = pitchVariation == 0.0F
                    ? pitch
                    : pitch + (world.rand.nextFloat() - world.rand.nextFloat()) * pitchVariation;
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, sound, volume, resolvedPitch);
        }
        if (particle != null) {
            for (int index = 0; index < particleCount; index++) {
                double particleY = Double.isNaN(fixedParticleY) ? y + world.rand.nextDouble() : y + fixedParticleY;
                world.spawnParticle(particle, x + world.rand.nextDouble(), particleY,
                        z + world.rand.nextDouble(), 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
