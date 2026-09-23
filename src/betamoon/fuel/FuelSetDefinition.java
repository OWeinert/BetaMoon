package betamoon.fuel;

import betamoon.assets.AssetKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable identity and composition rules for a named fuel set. */
public final class FuelSetDefinition {
    public final AssetKey key;
    public final String owner;
    public final List<AssetKey> includes;
    public final boolean builtIn;

    FuelSetDefinition(AssetKey key, String owner, List<AssetKey> includes, boolean builtIn) {
        this.key = key;
        this.owner = owner;
        this.includes = Collections.unmodifiableList(new ArrayList<AssetKey>(includes));
        this.builtIn = builtIn;
    }
}
