package betamoon.client.assets;

import betamoon.assets.io.ResolvedAsset;
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
    private int references = 1;

    TextureAsset(AssetLocation location, ResolvedAsset<TextureImage> content) {
        this.location = location;
        this.content = content;
    }

    public AssetLocation getLocation() {
        return location;
    }

    public ResolvedAsset<TextureImage> getContent() {
        return content;
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

    void replace(ResolvedAsset<TextureImage> next) {
        boolean changed = !content.getValue().sameContent(next.getValue());
        content = next;
        if (changed) {
            for (Consumer<TextureImage> listener : new ArrayList<>(listeners)) {
                listener.accept(next.getValue());
            }
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
