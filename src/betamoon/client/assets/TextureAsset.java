package betamoon.client.assets;

import betamoon.assets.TextureAnimation;
import betamoon.assets.io.ResolvedAsset;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared texture binding. Consumers retain/release it; refresh preserves its
 * logical identity.
 */
public final class TextureAsset implements AutoCloseable {
    private final AssetLocation location;
    private final List<Consumer<TextureImage>> listeners = new ArrayList<>();
    private ResolvedAsset<TextureImage> content;
    private TextureFrames frames;
    private int framePosition;
    private int frameTicks;
    private int references = 1;

    TextureAsset(AssetLocation location, ResolvedAsset<TextureImage> content) throws IOException {
        this.location = location;
        this.content = content;
        frames = TextureFrames.decode(content.getValue(), location.getTextureAnimation());
    }

    public AssetLocation getLocation() {
        return location;
    }

    public ResolvedAsset<TextureImage> getContent() {
        return content;
    }

    public TextureFrames getFrames() {
        return frames;
    }

    public BufferedImage currentFrame() {
        return frames.frame(framePosition);
    }

    public int framePosition() {
        return framePosition;
    }

    boolean tick(boolean paused) {
        TextureAnimation animation = frames.animation();
        if (animation == null || animation.firstFrameOnly() || frames.sequenceLength() < 2
                || paused && animation.unpausedOnly()) {
            return false;
        }
        if (++frameTicks < animation.frameTicks()) {
            return false;
        }
        frameTicks = 0;
        int previous = framePosition;
        framePosition = (framePosition + 1) % frames.sequenceLength();
        if (frames.frame(previous) == frames.frame(framePosition)) {
            return false;
        }
        notifyListeners();
        return true;
    }

    public TextureAsset retain() {
        if (references == 0) {
            throw new IllegalStateException("Texture binding has been released");
        }
        references++;
        return this;
    }

    public void listen(Consumer<TextureImage> listener) {
        listeners.add(listener);
    }

    public void unlisten(Consumer<TextureImage> listener) {
        listeners.remove(listener);
    }

    void replace(ResolvedAsset<TextureImage> next) throws IOException {
        TextureFrames replacement = TextureFrames.decode(next.getValue(), location.getTextureAnimation());
        boolean changed = !content.getValue().sameContent(next.getValue()) || framePosition != 0;
        content = next;
        frames = replacement;
        framePosition = 0;
        frameTicks = 0;
        if (changed) {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        for (Consumer<TextureImage> listener : new ArrayList<>(listeners)) {
            listener.accept(content.getValue());
        }
    }

    @Override
    public void close() {
        if (references <= 0) {
            throw new IllegalStateException("Texture binding released too many times");
        }
        if (--references == 0) {
            listeners.clear();
            ClientAssets.releaseTexture(this);
        }
    }
}
