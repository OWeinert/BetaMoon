package betamoon.utils;

import net.minecraft.src.ModLoader;

public final class MinecraftUtils {
    private MinecraftUtils() {
    }

    public static boolean isSingleplayerClient() {
        try {
            net.minecraft.client.Minecraft mc = ModLoader.getMinecraftInstance();
            return mc != null && mc.theWorld != null && !mc.theWorld.multiplayerWorld;
        } catch (Throwable t) {
            return false;
        }
    }
}
