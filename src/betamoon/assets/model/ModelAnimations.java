package betamoon.assets.model;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable clip collection. Explicit times keep rendering independent of
 * gameplay ticks.
 */
public final class ModelAnimations {
    public static final class Channels {
        public final AnimationTrack position;
        public final AnimationTrack rotation;
        public final AnimationTrack scale;

        Channels(AnimationTrack position, AnimationTrack rotation, AnimationTrack scale) {
            this.position = position;
            this.rotation = rotation;
            this.scale = scale;
        }
    }

    public static final class Clip {
        public final double duration;
        public final String loop;
        public final Map<String, Channels> bones;

        Clip(double duration, String loop, Map<String, Channels> bones) {
            this.duration = duration;
            this.loop = loop;
            this.bones = Collections.unmodifiableMap(new LinkedHashMap<>(bones));
        }

        public void validate(ModelGeometry geometry) throws IOException {
            for (String name : bones.keySet()) {
                if (!geometry.hasPart(name)) {
                    throw new IOException("Animation requires missing model part: " + name);
                }
            }
        }

        /**
         * Blend only declared channels, in caller order; mask null includes every part.
         */
        public void apply(ModelPose pose, double seconds, double weight, Set<String> mask) {
            if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(weight) || weight < 0 || weight > 1) {
                throw new IllegalArgumentException("Animation time must be nonnegative and weight must be in 0-1");
            }
            if (loop.equals("once") && seconds > duration) {
                return;
            }
            double time = loop.equals("loop") && duration > 0 ? seconds % duration : Math.min(seconds, duration);
            for (Map.Entry<String, Channels> entry : bones.entrySet()) {
                String name = entry.getKey();
                if (mask != null && !mask.contains(name)) {
                    continue;
                }
                Channels channels = entry.getValue();
                if (channels.position != null) {
                    pose.setPosition(name,
                            pose.getPosition(name).mix(channels.position.sample(time).fromBedrockPosition(), weight));
                }
                if (channels.rotation != null) {
                    ModelGeometry.Bone bone = pose.getGeometry().bones.get(pose.getGeometry().index(name));
                    ModelRotation absolute = ModelRotation
                            .bedrock(bone.sourceRotation.add(channels.rotation.sample(time)));
                    pose.setRotation(name, pose.getRotation(name).mix(bone.rotation.inverse().then(absolute), weight));
                }
                if (channels.scale != null) {
                    pose.setScale(name, pose.getScale(name).mix(channels.scale.sample(time), weight));
                }
            }
        }
    }

    public final Map<String, Clip> clips;

    ModelAnimations(Map<String, Clip> clips) {
        this.clips = Collections.unmodifiableMap(new LinkedHashMap<>(clips));
    }

    public Clip clip(String name) {
        Clip clip = clips.get(name);
        if (clip == null) {
            throw new IllegalArgumentException("Unknown animation clip: " + name);
        }
        return clip;
    }
}
