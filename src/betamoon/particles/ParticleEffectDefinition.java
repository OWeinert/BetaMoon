package betamoon.particles;

import betamoon.content.ContentKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Compiled, bounded emitter sampling independent of Lua dispatch and rendering.
 */
public final class ParticleEffectDefinition {
    public final ContentKey key;
    public final String owner;
    public final List<Emitter> emitters;
    public final double viewDistance;
    public final boolean decorative;

    public ParticleEffectDefinition(ContentKey key, String owner, List<Emitter> emitters, double viewDistance,
            boolean decorative) {
        this.key = key;
        this.owner = owner;
        this.emitters = Collections.unmodifiableList(new ArrayList<Emitter>(emitters));
        this.viewDistance = viewDistance;
        this.decorative = decorative;
    }

    public static final class Range {
        public final double min;
        public final double max;

        public Range(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public double sample(Random random) {
            return min == max ? min : min + random.nextDouble() * (max - min);
        }
    }

    public static final class Emitter {
        public final ParticleDefinition particle;
        public final String shape;
        public final boolean surface;
        private final double[] dimensions;
        public final int burst;
        public final double rate;
        public final int duration;
        public final Range velocityX;
        public final Range velocityY;
        public final Range velocityZ;
        public final Range outward;
        public final Range scale;
        private final double[] color;
        public final int seedOffset;

        public Emitter(ParticleDefinition particle, String shape, boolean surface, double[] dimensions, int burst,
                double rate, int duration, Range velocityX, Range velocityY, Range velocityZ, Range outward,
                Range scale, double[] color, int seedOffset) {
            this.particle = particle;
            this.shape = shape;
            this.surface = surface;
            this.dimensions = dimensions.clone();
            this.burst = burst;
            this.rate = rate;
            this.duration = duration;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.outward = outward;
            this.scale = scale;
            this.color = color.clone();
            this.seedOffset = seedOffset;
        }

        public double[] color() {
            return color.clone();
        }

        /**
         * Resolve dependencies for a new play without retaining a replaced particle
         * generation.
         */
        public Emitter withParticle(ParticleDefinition replacement) {
            return new Emitter(replacement, shape, surface, dimensions, burst, rate, duration, velocityX, velocityY,
                    velocityZ, outward, scale, color, seedOffset);
        }

        public void position(Random random, double[] result) {
            if ("sphere".equals(shape)) {
                double y = random.nextDouble() * 2 - 1;
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = dimensions[0] * (surface ? 1 : Math.cbrt(random.nextDouble()));
                double horizontal = Math.sqrt(Math.max(0, 1 - y * y));
                result[0] = Math.cos(angle) * horizontal * radius;
                result[1] = y * radius;
                result[2] = Math.sin(angle) * horizontal * radius;
            } else if ("box".equals(shape)) {
                for (int axis = 0; axis < 3; axis++) {
                    result[axis] = (random.nextDouble() * 2 - 1) * dimensions[axis];
                }
                if (surface) {
                    double yz = dimensions[1] * dimensions[2];
                    double xz = dimensions[0] * dimensions[2];
                    double xy = dimensions[0] * dimensions[1];
                    double choice = random.nextDouble() * (yz + xz + xy);
                    int axis = choice < yz ? 0 : choice < yz + xz ? 1 : 2;
                    result[axis] = (random.nextBoolean() ? 1 : -1) * dimensions[axis];
                }
            } else if ("line".equals(shape)) {
                double progress = random.nextDouble();
                for (int axis = 0; axis < 3; axis++) {
                    result[axis] = dimensions[axis] + (dimensions[axis + 3] - dimensions[axis]) * progress;
                }
            } else {
                result[0] = 0;
                result[1] = 0;
                result[2] = 0;
            }
        }
    }
}
