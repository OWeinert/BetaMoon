package betamoon.capability;

import betamoon.assets.AssetKey;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityDefinition;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.NBTTagCompound;

/** Binds a tile definition's attachments while preserving unavailable saved capability payloads. */
public final class TileCapabilityContainer {
    private final LuaTileEntity tile;
    private final Map<AssetKey, CapabilityInstance> instances = new LinkedHashMap<>();
    private NBTTagCompound raw = new NBTTagCompound();

    public TileCapabilityContainer(LuaTileEntity tile) {
        this.tile = tile;
    }

    public void bind(TileEntityDefinition definition) {
        if (definition == null) {
            instances.clear();
            return;
        }
        for (CapabilityAttachmentDefinition attachment : definition.capabilities.values()) {
            CapabilityInstance instance = instances.get(attachment.capability.key);
            if (instance == null || instance.attachment != attachment) {
                instance = new CapabilityInstance(tile, attachment);
                if (raw.hasKey(attachment.capability.key.toString())) {
                    instance.load(raw.getCompoundTag(attachment.capability.key.toString()));
                }
                instances.put(attachment.capability.key, instance);
            }
        }
        instances.keySet().retainAll(definition.capabilities.keySet());
    }

    public void load(NBTTagCompound tag, TileEntityDefinition definition) {
        raw = tag == null ? new NBTTagCompound() : tag;
        instances.clear();
        bind(definition);
    }

    public NBTTagCompound save() {
        for (Map.Entry<AssetKey, CapabilityInstance> entry : instances.entrySet()) {
            raw.setCompoundTag(entry.getKey().toString(), entry.getValue().data.raw());
        }
        return raw;
    }

    public CapabilityInstance get(AssetKey key) {
        return instances.get(key);
    }

    public Collection<CapabilityInstance> all() {
        return Collections.unmodifiableCollection(instances.values());
    }
}
