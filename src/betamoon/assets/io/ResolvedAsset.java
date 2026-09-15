package betamoon.assets.io;

import betamoon.assets.AssetPath;

/**
 * One validated version together with its actual source, for diagnostics and
 * inspection.
 */
public final class ResolvedAsset<T> {
    private final T value;
    private final String sourceKind;
    private final String sourceName;
    private final AssetPath sourcePath;

    ResolvedAsset(T value, String sourceKind, String sourceName, AssetPath sourcePath) {
        this.value = value;
        this.sourceKind = sourceKind;
        this.sourceName = sourceName;
        this.sourcePath = sourcePath;
    }

    /**
     * Keeps source provenance while retaining an equivalent already-decoded content
     * object.
     */
    public ResolvedAsset<T> withValue(T replacement) {
        return new ResolvedAsset<>(replacement, sourceKind, sourceName, sourcePath);
    }

    public T getValue() {
        return value;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public String getSourceName() {
        return sourceName;
    }

    public AssetPath getSourcePath() {
        return sourcePath;
    }
}
