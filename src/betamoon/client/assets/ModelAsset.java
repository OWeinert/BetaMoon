package betamoon.client.assets;

import betamoon.assets.io.ResolvedAsset;
import java.util.ArrayList;
import java.util.List;

/**
 * Reference-counted decoded model data, with a validated replacement contract.
 */
public final class ModelAsset<T> implements AutoCloseable {
    private final AssetLocation location;
    private ResolvedAsset<T> content;
    private final T contract;
    private final List<Runnable> listeners = new ArrayList<>();
    private int references = 1;

    ModelAsset(AssetLocation location, ResolvedAsset<T> content, T contract) {
        this.location = location;
        this.content = content;
        this.contract = contract;
    }

    public AssetLocation getLocation() {
        return location;
    }

    public ResolvedAsset<T> getContent() {
        return content;
    }

    T contract() {
        return contract;
    }

    ModelAsset<T> retain() {
        if (references <= 0) {
            throw new IllegalStateException("Model asset is closed");
        }
        references++;
        return this;
    }

    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    void replace(ResolvedAsset<T> replacement) {
        content = replacement;
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }

    @Override
    public void close() {
        if (references > 0 && --references == 0) {
            ClientModelAssets.release(this);
            listeners.clear();
        }
    }
}
