package betamoon.capability;

import betamoon.assets.AssetKey;
import betamoon.tileentity.LuaTileEntity;

/** Decoupled invalidation bridge; the logical-network runtime installs its listener later. */
public final class CapabilityTopologyEvents {
    public interface Listener {
        void changed(LuaTileEntity tile, AssetKey capability, String field);
    }

    private static volatile Listener listener;

    private CapabilityTopologyEvents() {
    }

    public static void setListener(Listener next) {
        listener = next;
    }

    public static void changed(LuaTileEntity tile, AssetKey capability, String field) {
        Listener current = listener;
        if (current != null && tile != null && tile.worldObj != null) {
            current.changed(tile, capability, field);
        }
    }
}
