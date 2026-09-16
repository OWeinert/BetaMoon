package betamoon.assets.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Numeric keyframes sampled in seconds, before conversion to the public pose
 * frame.
 */
public final class AnimationTrack {
    public static final class Key {
        public final double time;
        public final ModelVector before;
        public final ModelVector after;
        public final String interpolation;

        Key(double time, ModelVector before, ModelVector after, String interpolation) {
            this.time = time;
            this.before = before;
            this.after = after;
            this.interpolation = interpolation;
        }
    }

    private final List<Key> keys;

    AnimationTrack(List<Key> keys) {
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    }

    public double duration() {
        return keys.get(keys.size() - 1).time;
    }

    public ModelVector sample(double time) {
        Key first = keys.get(0);
        if (time < first.time) {
            return first.before;
        }
        int low = 0;
        int high = keys.size();
        while (low + 1 < high) {
            int middle = (low + high) / 2;
            if (keys.get(middle).time <= time) {
                low = middle;
            } else {
                high = middle;
            }
        }
        Key left = keys.get(low);
        if (low == keys.size() - 1 || time == left.time) {
            return left.after;
        }
        Key right = keys.get(low + 1);
        double weight = (time - left.time) / (right.time - left.time);
        if (left.interpolation.equals("step")) {
            return left.after;
        }
        if (left.interpolation.equals("catmullrom") || right.interpolation.equals("catmullrom")) {
            ModelVector previous = low > 0 ? keys.get(low - 1).after : left.after;
            ModelVector next = low + 2 < keys.size() ? keys.get(low + 2).before : right.before;
            double squared = weight * weight;
            double cubed = squared * weight;
            return left.after.times(2).add(right.before.add(previous.times(-1)).times(weight))
                    .add(previous.times(2).add(left.after.times(-5)).add(right.before.times(4)).add(next.times(-1))
                            .times(squared))
                    .add(previous.times(-1).add(left.after.times(3)).add(right.before.times(-3)).add(next).times(cubed))
                    .times(0.5);
        }
        return left.after.mix(right.before, weight);
    }
}
