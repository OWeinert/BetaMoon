package betamoon.system;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.NBTBase;
import net.minecraft.src.MapDataBase;
import net.minecraft.src.NBTTagCompound;

/** Single MapStorage payload containing namespaced world-service and network state. */
public final class BetaMoonWorldData extends MapDataBase {
    private NBTTagCompound services = new NBTTagCompound();
    private NBTTagCompound networks = new NBTTagCompound();
    private final Map<String, NBTBase> unknown = new LinkedHashMap<>();

    public BetaMoonWorldData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        unknown.clear();
        for (Object value : tag.func_28110_c()) {
            NBTBase saved = (NBTBase) value;
            if (!("Services".equals(saved.getKey()) || "Networks".equals(saved.getKey()))) {
                unknown.put(saved.getKey(), saved);
            }
        }
        services = tag.hasKey("Services") ? tag.getCompoundTag("Services") : new NBTTagCompound();
        networks = tag.hasKey("Networks") ? tag.getCompoundTag("Networks") : new NBTTagCompound();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        for (Map.Entry<String, NBTBase> entry : unknown.entrySet()) {
            tag.setTag(entry.getKey(), entry.getValue());
        }
        tag.setCompoundTag("Services", services);
        tag.setCompoundTag("Networks", networks);
    }

    public NBTTagCompound services() {
        return services;
    }

    public NBTTagCompound networks() {
        return networks;
    }
}
