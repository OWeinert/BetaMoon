package betamoon.particles;

import betamoon.client.assets.AssetLocation;
import betamoon.content.ContentKey;
import org.luaj.vm2.LuaValue;

/**
 * Immutable declaration data; no native particle or graphics resource is
 * constructed here.
 */
public final class ParticleDefinition {
    public final ContentKey key;
    public final String owner;
    public final AssetLocation texture;
    public final String atlas;
    private final int[] indices;
    public final int frameTicks;
    public final boolean randomFrame;
    public final int lifetimeMin;
    public final int lifetimeMax;
    public final double sizeStart;
    public final double sizeFinish;
    private final double[] colorStart;
    private final double[] colorFinish;
    public final double gravity;
    public final double drag;
    public final String collision;
    public final double bounce;
    public final boolean vertical;
    public final boolean additive;
    public final boolean fullLight;
    public final double rotationStart;
    public final double rotationSpeed;
    public final LuaValue onTick;

    public ParticleDefinition(ContentKey key, String owner, AssetLocation texture, String atlas, int[] indices,
            int frameTicks, boolean randomFrame, int lifetimeMin, int lifetimeMax, double sizeStart, double sizeFinish,
            double[] colorStart, double[] colorFinish, double gravity, double drag, String collision, double bounce,
            boolean vertical, boolean additive, boolean fullLight, double rotationStart, double rotationSpeed,
            LuaValue onTick) {
        this.key = key;
        this.owner = owner;
        this.texture = texture;
        this.atlas = atlas;
        this.indices = indices == null ? null : indices.clone();
        this.frameTicks = frameTicks;
        this.randomFrame = randomFrame;
        this.lifetimeMin = lifetimeMin;
        this.lifetimeMax = lifetimeMax;
        this.sizeStart = sizeStart;
        this.sizeFinish = sizeFinish;
        this.colorStart = colorStart.clone();
        this.colorFinish = colorFinish.clone();
        this.gravity = gravity;
        this.drag = drag;
        this.collision = collision;
        this.bounce = bounce;
        this.vertical = vertical;
        this.additive = additive;
        this.fullLight = fullLight;
        this.rotationStart = rotationStart;
        this.rotationSpeed = rotationSpeed;
        this.onTick = onTick;
    }

    public int atlasFrameCount() {
        return indices == null ? 0 : indices.length;
    }

    public int atlasIndex(int position) {
        return indices[position];
    }

    public double color(int component, double progress) {
        return colorStart[component] + (colorFinish[component] - colorStart[component]) * progress;
    }
}
