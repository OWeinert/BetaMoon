package betamoon.multiblock;

import net.minecraft.src.MapDataBase;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;

/**
 * Recognition bookkeeping has its own save payload; tiles and inventories
 * remain untouched.
 */
public final class MultiblockWorldData extends MapDataBase {
    private NBTTagCompound records = new NBTTagCompound();

    public MultiblockWorldData(String name) {
        super(name);
    }

    public void readFromNBT(NBTTagCompound tag) {
        records = tag;
    }

    public void writeToNBT(NBTTagCompound tag) {
        for (Object value : records.func_28110_c()) {
            NBTBase saved = (NBTBase) value;
            tag.setTag(saved.getKey(), saved);
        }
    }

    public NBTTagCompound records() {
        if (!records.hasKey("Records")) {
            records.setCompoundTag("Records", new NBTTagCompound());
        }
        return records.getCompoundTag("Records");
    }
}
