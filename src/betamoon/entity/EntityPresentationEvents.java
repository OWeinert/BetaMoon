package betamoon.entity;

import betamoon.assets.AssetKey;
import net.minecraft.src.Entity;

/** Neutral presentation event boundary; gameplay remains valid without a client sink. */
public final class EntityPresentationEvents {
    public interface Sink {
        void sound(SoundRequest request);
    }

    private static final Sink SILENT = request -> {
    };
    private static Sink sink = SILENT;

    private EntityPresentationEvents() {
    }

    public static void install(Sink next) {
        sink = next == null ? SILENT : next;
    }

    public static void sound(Entity entity, EntitySoundsDefinition.Binding binding) {
        sound(entity, binding, entity.posX, entity.posY, entity.posZ);
    }

    public static void sound(Entity entity, EntitySoundsDefinition.Binding binding,
            double x, double y, double z) {
        if (entity == null || binding == null) {
            return;
        }
        sink.sound(new SoundRequest(binding.event, x, y, z, binding.volume, binding.pitch, binding.range));
    }

    public static void sound(Entity entity, AssetKey event, float volume, float pitch, float range) {
        if (entity == null || event == null) {
            return;
        }
        sink.sound(new SoundRequest(event, entity.posX, entity.posY, entity.posZ, volume, pitch, range));
    }

    public static void sound(AssetKey event, double x, double y, double z,
            float volume, float pitch, float range) {
        if (event == null) {
            return;
        }
        sink.sound(new SoundRequest(event, x, y, z, volume, pitch, range));
    }

    public static final class SoundRequest {
        public final AssetKey event;
        public final double x;
        public final double y;
        public final double z;
        public final float volume;
        public final float pitch;
        public final float range;

        private SoundRequest(AssetKey event, double x, double y, double z,
                float volume, float pitch, float range) {
            this.event = event;
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = volume;
            this.pitch = pitch;
            this.range = range;
        }
    }
}
