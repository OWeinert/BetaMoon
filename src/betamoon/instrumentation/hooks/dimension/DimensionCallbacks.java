package betamoon.instrumentation.hooks.dimension;

import betamoon.dimension.DimensionEnvironment;
import betamoon.dimension.DimensionNativePortal;
import betamoon.dimension.DimensionPlayerState;
import betamoon.dimension.DimensionRules;
import betamoon.dimension.DimensionRuntime;
import betamoon.system.WorldServiceRuntime;
import betamoon.teleport.ChunkPreparation;
import betamoon.teleport.TeleportRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.ChunkProvider;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

public final class DimensionCallbacks {
    private DimensionCallbacks() {
    }

    public static int beforeLoad(ChunkProvider provider, int x, int z) {
        return ChunkPreparation.beforeLoad(provider, x, z);
    }

    public static int deferPopulation() {
        return ChunkPreparation.deferPopulation();
    }

    public static void dimensionBoundaryCompleted() {
    }

    public static int entering() {
        return 0;
    }

    public static void readPlayer(EntityPlayer player, NBTTagCompound tag) {
        DimensionPlayerState.read(player, tag);
    }

    public static void writePlayer(EntityPlayer player, NBTTagCompound tag) {
        DimensionPlayerState.write(player, tag);
    }

    public static void bedChanged(EntityPlayer player, ChunkCoordinates position) {
        DimensionPlayerState.bedChanged(player, position);
    }

    public static int disabledBed(World world) {
        DimensionEnvironment environment = DimensionRules.environment(world.worldProvider);
        return environment != null && environment.beds.equals("disabled") ? 1 : 0;
    }

    public static boolean bedResult(boolean original, int disabled) {
        return disabled != 0 || original;
    }

    public static int skipPrewarm() {
        return TeleportRuntime.nativeSwitching() ? 1 : 0;
    }

    public static int frozen() {
        return TeleportRuntime.frozen() ? 1 : 0;
    }

    public static int suppressPortal(Minecraft minecraft) {
        return DimensionNativePortal.dispatch(minecraft);
    }

    public static void saveBeforeNativeChange(World world, IProgressUpdate progress) {
        if (!TeleportRuntime.nativeSwitching() && !DimensionNativePortal.active()) {
            if (!DimensionRuntime.isPreparing(world)) {
                WorldServiceRuntime.prepareToLeave(world);
            }
            world.saveWorldIndirectly(progress);
        }
    }
}
