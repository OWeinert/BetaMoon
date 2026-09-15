package betamoon.client.audio;

import betamoon.assets.io.ResolvedAsset;
import betamoon.client.assets.AssetLocation;

/**
 * Shared decoded clip binding, independently owned by declarations and event
 * definitions.
 */
public final class SoundAsset implements AutoCloseable {
    private final AssetLocation location;
    private ResolvedAsset<SoundClip> content;
    private int references = 1;

    SoundAsset(AssetLocation location, ResolvedAsset<SoundClip> content) {
        this.location = location;
        this.content = content;
    }

    public AssetLocation getLocation() {
        return location;
    }

    public ResolvedAsset<SoundClip> getContent() {
        return content;
    }

    public SoundAsset retain() {
        if (references == 0) {
            throw new IllegalStateException("Sound binding was released");
        }
        references++;
        return this;
    }

    void replace(ResolvedAsset<SoundClip> next) {
        content = content.getValue().sameContent(next.getValue()) ? next.withValue(content.getValue()) : next;
    }

    @Override
    public void close() {
        if (references <= 0) {
            throw new IllegalStateException("Sound binding released too many times");
        }
        if (--references == 0) {
            ClientSounds.release(this);
        }
    }
}
