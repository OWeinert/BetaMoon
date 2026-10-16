package betamoon.multiblock;

import betamoon.data.DataStore;
import java.util.UUID;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/**
 * Shared live state; attachment and discovery remain responsibilities of each
 * runtime.
 */
public abstract class MultiblockInstance {
    public final MultiblockPosition position;
    public final MultiblockDefinition definition;
    public final String identity;
    public final DataStore data;
    public MultiblockReport report = new MultiblockReport();
    public String status = "checking";
    public String callbackError;
    public MultiblockPosition conflict;
    public int facing;
    public boolean mirror;
    public boolean dirty = true;
    public boolean expired;

    protected MultiblockInstance(MultiblockPosition position, MultiblockDefinition definition, NBTTagCompound saved,
            Runnable markDirty) {
        this.position = position;
        this.definition = definition;
        identity = saved.hasKey("Identity") ? saved.getString("Identity") : UUID.randomUUID().toString();
        saved.setString("Identity", identity);
        data = new DataStore(definition.dataSchema, field -> markDirty.run());
        data.load(saved.getCompoundTag("Data"));
        saved.setCompoundTag("Data", data.raw());
    }

    public boolean isFormed() {
        return !expired && !dirty && status.equals("formed");
    }

    public boolean isActive() {
        return isFormed();
    }

    public boolean activationSaved() {
        return isActive();
    }

    public boolean manuallyInhibited() {
        return false;
    }

    public abstract World world();

    public abstract void requireCurrent();

    public abstract void requestCheck();

    public MultiblockActivationResult activate() {
        requireCurrent();
        return new MultiblockActivationResult(false, false, "controller_managed", this);
    }

    public MultiblockActivationResult deactivate() {
        requireCurrent();
        return new MultiblockActivationResult(false, false, "controller_managed", this);
    }
}
