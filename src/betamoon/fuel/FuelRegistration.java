package betamoon.fuel;

import betamoon.assets.AssetKey;

/** One fixed burn-time rule owned by a Lua script. */
public final class FuelRegistration {
    public final long id;
    public final String owner;
    public final AssetKey setKey;
    public final int itemId;
    public final Integer damage;
    public final int burnTime;

    FuelRegistration(long id, String owner, AssetKey setKey, int itemId, Integer damage, int burnTime) {
        this.id = id;
        this.owner = owner;
        this.setKey = setKey;
        this.itemId = itemId;
        this.damage = damage;
        this.burnTime = burnTime;
    }
}
