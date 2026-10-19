package betamoon.teleport;

import java.util.Map;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/**
 * The only client-specific boundary; the task engine also runs under
 * fault-injecting verification adapters.
 */
public interface TeleportClientAccess {
    World world();

    EntityPlayer player();

    void beginPreparation(TeleportTicket ticket, String destinationName);

    default IProgressUpdate loadingProgress() {
        return null;
    }

    default void showPreparation(String stage, int percent) {
    }

    void closeContainer();

    void finishPreparation(boolean success);

    void switchWorld(World destination, EntityPlayer player, NBTTagCompound target);

    default EntityPlayer createRespawnPlayer(World destination) {
        throw new IllegalStateException("unsupported_environment: native player replacement is unavailable");
    }

    default void prepareRespawnPlayer(EntityPlayer replacement, TeleportLocation arrival) {
        replacement.setLocationAndAngles(arrival.x, arrival.y, arrival.z, arrival.yaw, arrival.pitch);
        replacement.preparePlayerToSpawn();
    }

    default void switchRespawnPlayer(World destination, EntityPlayer replacement, NBTTagCompound target) {
        throw new IllegalStateException("unsupported_environment: native player replacement is unavailable");
    }

    void publish(Map<String, Object> result);

    void recovery(String detail);
}
