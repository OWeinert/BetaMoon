package betamoon.worldgen;

/** Optional controls supplied by an explicit feature placement or preview. */
public final class FeatureOptions {
    public static final FeatureOptions DEFAULT = new FeatureOptions(null, null);

    public final String rotation;
    public final String mirror;

    public FeatureOptions(String rotation, String mirror) {
        this.rotation = rotation;
        this.mirror = mirror;
    }
}
