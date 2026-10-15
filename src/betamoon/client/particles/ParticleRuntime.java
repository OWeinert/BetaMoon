package betamoon.client.particles;

import betamoon.luamodloader.LuaScriptErrors;
import betamoon.particles.ParticleDefinition;
import betamoon.particles.ParticleEffectDefinition;
import betamoon.particles.ParticleEffectDefinition.Emitter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.Entity;
import net.minecraft.src.World;

/**
 * Main-client-thread simulation, admission, and emitter ownership. Native FX
 * lists are never touched.
 */
public final class ParticleRuntime {
    public static final int OWNER_LIMIT = 1024;
    public static final int HANDLE_LIMIT = 64;
    private final List<ParticleSprite> sprites = new ArrayList<ParticleSprite>();
    private final List<ParticleSprite> pending = new ArrayList<ParticleSprite>();
    private final List<Effect> effects = new ArrayList<Effect>();
    private final Map<String, Counters> counters = new LinkedHashMap<String, Counters>();
    private final Set<String> warned = new HashSet<String>();
    private World world;
    private Entity camera;
    private int maxActive = 4096;
    private int maxPerTick = 1024;
    private double viewDistance = 64;
    private int admitted;

    public void configure(int maxActive, int maxPerTick, double viewDistance) {
        if (maxActive < 1 || maxActive > 65535 || maxPerTick < 1 || maxPerTick > 65535 || !Double.isFinite(viewDistance)
                || viewDistance < 1 || viewDistance > 64) {
            throw new IllegalArgumentException("Invalid particle budget");
        }
        this.maxActive = maxActive;
        this.maxPerTick = maxPerTick;
        this.viewDistance = viewDistance;
    }

    public World world() {
        return world;
    }

    public void camera(Entity camera) {
        this.camera = camera;
    }

    public void clear(World replacement) {
        for (ParticleSprite sprite : sprites) {
            sprite.setEntityDead();
        }
        for (ParticleSprite sprite : pending) {
            sprite.setEntityDead();
        }
        for (Effect effect : effects) {
            effect.stop();
        }
        sprites.clear();
        pending.clear();
        effects.clear();
        admitted = 0;
        world = replacement;
        camera = null;
        counters.clear();
        warned.clear();
    }

    public Effect play(ParticleEffectDefinition definition, String owner, double x, double y, double z,
            Entity attachment, double offsetX, double offsetY, double offsetZ, long seed) {
        int count = 0;
        for (Effect effect : effects) {
            if (effect.owner.equals(owner) && effect.active) {
                count++;
            }
        }
        if (count >= HANDLE_LIMIT) {
            budgetDrop(owner);
            return null;
        }
        Effect effect = new Effect(definition, owner, x, y, z, attachment, offsetX, offsetY, offsetZ, seed);
        effects.add(effect);
        return effect;
    }

    public boolean spawn(ParticleDefinition definition, String owner, double x, double y, double z, double vx,
            double vy, double vz, double scale, double[] color, long seed, double distance) {
        if (!positionValid(x) || !positionValid(y) || !positionValid(z) || !velocityValid(vx) || !velocityValid(vy)
                || !velocityValid(vz) || !Double.isFinite(scale) || scale <= 0 || scale > 4 || !colorValid(color)) {
            failure(owner, "Invalid native custom particle request: " + definition.key);
            return false;
        }
        if (world == null || camera == null || camera.worldObj != world) {
            return false;
        }
        double allowed = Math.min(distance, viewDistance);
        double dx = x - camera.posX;
        double dy = y - camera.posY;
        double dz = z - camera.posZ;
        if (dx * dx + dy * dy + dz * dz > allowed * allowed) {
            stats(owner).distanceCulled++;
            return false;
        }
        if (admitted >= maxPerTick || sprites.size() + pending.size() >= maxActive || active(owner) >= OWNER_LIMIT) {
            budgetDrop(owner);
            return false;
        }
        if (!world.blockExists((int) Math.floor(x), 64, (int) Math.floor(z))) {
            stats(owner).distanceCulled++;
            return false;
        }
        ParticleSprite sprite = new ParticleSprite(world, definition, owner, x, y, z, vx, vy, vz, scale, color, seed,
                error -> callbackFailure(definition, error));
        pending.add(sprite);
        admitted++;
        stats(owner).emitted++;
        return true;
    }

    private static boolean positionValid(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30000000;
    }

    private static boolean velocityValid(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 16;
    }

    private static boolean colorValid(double[] color) {
        if (color == null || color.length != 4) {
            return false;
        }
        for (double component : color) {
            if (!Double.isFinite(component) || component < 0 || component > 1) {
                return false;
            }
        }
        return true;
    }

    public void tick(boolean paused) {
        if (paused || world == null) {
            return;
        }
        // New requests are displayed after this tick and first update on the following
        // tick.
        for (ParticleSprite sprite : new ArrayList<ParticleSprite>(sprites)) {
            if (!sprite.isDead) {
                sprite.onUpdate();
            }
        }
        sprites.removeIf(sprite -> sprite.isDead);
        List<Effect> ordered = new ArrayList<Effect>(effects);
        Collections.sort(ordered, Comparator.comparing(effect -> effect.definition.decorative));
        for (Effect effect : ordered) {
            if (effect.active) {
                effect.tick();
            }
        }
        effects.removeIf(effect -> !effect.active);
        sprites.addAll(pending);
        pending.clear();
        admitted = 0;
    }

    public void removeOwner(String owner) {
        for (Effect effect : effects) {
            if (effect.owner.equals(owner) || effect.definition.owner.equals(owner) || effect.uses(owner)) {
                effect.stop();
            }
        }
        effects.removeIf(effect -> !effect.active);
        removeSprites(sprites, owner);
        removeSprites(pending, owner);
        warned.removeIf(key -> key.startsWith(owner + "\n"));
    }

    private void removeSprites(List<ParticleSprite> values, String owner) {
        Iterator<ParticleSprite> iterator = values.iterator();
        while (iterator.hasNext()) {
            ParticleSprite sprite = iterator.next();
            if (sprite.owner.equals(owner) || sprite.definition.owner.equals(owner)) {
                sprite.setEntityDead();
                iterator.remove();
            }
        }
    }

    public List<ParticleSprite> sprites() {
        return Collections.unmodifiableList(sprites);
    }

    public Map<String, Counters> snapshot() {
        Map<String, Counters> result = new LinkedHashMap<String, Counters>();
        for (Map.Entry<String, Counters> entry : counters.entrySet()) {
            Counters copy = new Counters(entry.getValue());
            copy.active = active(entry.getKey());
            result.put(entry.getKey(), copy);
        }
        return result;
    }

    private int active(String owner) {
        int count = 0;
        for (ParticleSprite sprite : sprites) {
            if (!sprite.isDead && sprite.owner.equals(owner)) {
                count++;
            }
        }
        for (ParticleSprite sprite : pending) {
            if (!sprite.isDead && sprite.owner.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public void failure(String owner, String message) {
        if (!warned.contains(owner + "\n" + "failure:" + message)) {
            stats(owner).failedDefinitions++;
        }
        warn(owner, "failure:" + message, message);
    }

    private void callbackFailure(ParticleDefinition definition, RuntimeException error) {
        String key = "callback:" + definition.key;
        if (!warned.contains(definition.owner + "\n" + key)) {
            stats(definition.owner).failedDefinitions++;
        }
        warn(definition.owner, key,
                "Particle " + definition.key + " onTick disabled for the affected instance: " + error.getMessage());
    }

    private Counters stats(String owner) {
        return counters.computeIfAbsent(owner, key -> new Counters());
    }

    private void budgetDrop(String owner) {
        stats(owner).budgetDropped++;
        warn(owner, "budget", "Particle budget reached; additional visuals were omitted.");
    }

    private void warn(String owner, String key, String message) {
        if (warned.add(owner + "\n" + key)) {
            LuaScriptErrors.addWarning(owner, message);
        }
    }

    public static final class Counters {
        public int active;
        public long emitted;
        public long distanceCulled;
        public long budgetDropped;
        public long failedDefinitions;

        private Counters() {
        }

        private Counters(Counters other) {
            emitted = other.emitted;
            distanceCulled = other.distanceCulled;
            budgetDropped = other.budgetDropped;
            failedDefinitions = other.failedDefinitions;
        }
    }

    public final class Effect {
        public final ParticleEffectDefinition definition;
        public final String owner;
        private Entity attachment;
        private final boolean attached;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final Random[] random;
        private final double[] accumulators;
        private double x;
        private double y;
        private double z;
        private int age;
        private boolean active = true;

        private Effect(ParticleEffectDefinition definition, String owner, double x, double y, double z,
                Entity attachment, double offsetX, double offsetY, double offsetZ, long seed) {
            this.definition = definition;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
            this.attachment = attachment;
            attached = attachment != null;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            random = new Random[definition.emitters.size()];
            accumulators = new double[random.length];
            for (int i = 0; i < random.length; i++) {
                random[i] = new Random(seed + definition.emitters.get(i).seedOffset + 31L * i);
            }
        }

        public boolean isActive() {
            return active;
        }

        public void stop() {
            active = false;
            attachment = null;
        }

        public void position(double x, double y, double z) {
            if (attached) {
                throw new IllegalStateException("Attached particle effects cannot be repositioned");
            }
            if (!active) {
                throw new IllegalStateException("Particle effect is no longer active");
            }
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private boolean uses(String owner) {
            for (Emitter emitter : definition.emitters) {
                if (emitter.particle.owner.equals(owner)) {
                    return true;
                }
            }
            return false;
        }

        private void tick() {
            if (attachment != null) {
                if (attachment.isDead || attachment.worldObj != world || !world.loadedEntityList.contains(attachment)) {
                    stop();
                    return;
                }
                x = attachment.posX + offsetX;
                y = attachment.posY + offsetY;
                z = attachment.posZ + offsetZ;
            }
            boolean continues = false;
            for (int i = 0; i < definition.emitters.size(); i++) {
                Emitter emitter = definition.emitters.get(i);
                int count = age == 0 ? emitter.burst : 0;
                if (age < emitter.duration) {
                    accumulators[i] += emitter.rate;
                    int continuous = (int) Math.floor(accumulators[i]);
                    accumulators[i] -= continuous;
                    count += continuous;
                }
                continues |= age + 1 < emitter.duration;
                for (int emission = 0; emission < count; emission++) {
                    emit(emitter, random[i]);
                }
            }
            age++;
            if (!continues) {
                stop();
            }
        }

        private void emit(Emitter emitter, Random random) {
            double[] position = new double[3];
            emitter.position(random, position);
            double vx = emitter.velocityX.sample(random);
            double vy = emitter.velocityY.sample(random);
            double vz = emitter.velocityZ.sample(random);
            if (emitter.outward != null) {
                double length = Math
                        .sqrt(position[0] * position[0] + position[1] * position[1] + position[2] * position[2]);
                double speed = emitter.outward.sample(random);
                if (length > 0) {
                    vx += position[0] / length * speed;
                    vy += position[1] / length * speed;
                    vz += position[2] / length * speed;
                }
            }
            double scale = emitter.scale.sample(random);
            long seed = random.nextLong();
            spawn(emitter.particle, owner, x + position[0], y + position[1], z + position[2],
                    Math.max(-16, Math.min(16, vx)), Math.max(-16, Math.min(16, vy)), Math.max(-16, Math.min(16, vz)),
                    scale, emitter.color(), seed, definition.viewDistance);
        }
    }
}
