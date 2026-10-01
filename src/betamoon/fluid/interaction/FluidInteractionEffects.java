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

    public FluidInteractionEffects(String sound, float volume, float pitch, String particle, int particleCount,
            Timing timing) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.particle = particle;
        this.particleCount = particleCount;
        this.timing = timing;
    }

    public Timing timing() {
        return timing;
    }

    public void play(World world, int x, int y, int z) {
        if (sound != null) {
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, sound, volume, pitch);
        }
        if (particle != null) {
            for (int index = 0; index < particleCount; index++) {
                world.spawnParticle(particle, x + world.rand.nextDouble(), y + world.rand.nextDouble(),
                        z + world.rand.nextDouble(), 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
