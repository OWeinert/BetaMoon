package betamoon.capability;

import betamoon.data.DataStore;
import betamoon.tileentity.LuaTileEntity;
import net.minecraft.src.NBTTagCompound;
import java.util.HashSet;
import java.util.Set;

/** Live state for one capability attachment on one placed tile. */
public final class CapabilityInstance {
    public final LuaTileEntity tile;
    public final CapabilityAttachmentDefinition attachment;
    public final DataStore data;
    private final Set<String> disabledOperations = new HashSet<>();

    CapabilityInstance(final LuaTileEntity tile, CapabilityAttachmentDefinition attachment) {
        this.tile = tile;
        this.attachment = attachment;
        this.data = new DataStore(attachment.capability.state, new DataStore.ChangeListener() {
            public void changed(String field) {
                tile.markDirty();
                CapabilityTopologyEvents.changed(tile, attachment.capability.key, field);
            }
        });
    }

    void load(NBTTagCompound tag) {
        data.load(tag);
    }

    public boolean isDisabled(String operation) {
        return disabledOperations.contains(operation);
    }

    public void disable(String operation) {
        disabledOperations.add(operation);
    }
}
