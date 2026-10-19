package betamoon.dimension;

import betamoon.instrumentation.api.RequiredNativeHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.src.BlockBed;
import net.minecraft.src.BlockFlowing;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.ChunkProvider;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.ItemBucket;
import net.minecraft.src.ItemMap;
import net.minecraft.src.MapData;
import net.minecraft.src.RenderGlobal;
import net.minecraft.src.TextureCompassFX;
import net.minecraft.src.TextureWatchFX;
import net.minecraft.src.World;

/**
 * One contract for declaration, restoration and transfer; pending hooks never
 * reach activation.
 */
public final class DimensionCapabilities {
    private static final String[] TRANSFER = {"betamoon:world_generator:prepare", "betamoon:world_join:session",
            "betamoon:dimensions:player_read", "betamoon:dimensions:player_write", "betamoon:dimensions:respawn",
            "betamoon:dimensions:chunk_budget", "betamoon:dimensions:chunk_access",
            "betamoon:dimensions:population_budget", "betamoon:dimensions:prewarm", "betamoon:dimensions:source_save",
            "betamoon:dimensions:load_screen", "betamoon:dimensions:native_portal", "betamoon:dimensions:portal_charge",
            "betamoon:dimensions:freeze_updateEntities", "betamoon:dimensions:freeze_joinEntityInSurroundings",
            "betamoon:dimensions:freeze_randomDisplayUpdates", "betamoon:dimensions:structure_protection_read",
            "betamoon:dimensions:structure_protection_write", "betamoon:dimensions:bed_record"};
    private static final String[] MAPS = {"read", "write", "func_28012_a", "func_28013_a", "created", "scope_terrain",
            "scope_markers", "terrain_identity", "marker_identity"};
    private static final String[] ENVIRONMENT = {"sky", "clouds", "compass", "clock", "water", "lava", "weather",
            "sleep", "bed_explosion", "bed_disabled"};

    private DimensionCapabilities() {
    }

    public static void requireDeclaration() {
        require(true, true);
    }

    public static void requireReady(boolean customEnvironment) {
        // Class literals follow the packaged mappings. Loading without initialization
        // forces each deferred transformer target without starting rendering or
        // gameplay.
        for (Class<?> target : new Class<?>[]{Minecraft.class, World.class, ChunkLoader.class, ChunkProvider.class,
                EntityPlayer.class, EntityPlayerSP.class, ItemMap.class, MapData.class, BlockBed.class,
                ItemBucket.class, BlockFlowing.class, RenderGlobal.class, TextureCompassFX.class,
                TextureWatchFX.class}) {
            try {
                Class.forName(target.getName(), false, target.getClassLoader());
            } catch (ClassNotFoundException | LinkageError failure) {
                throw new Unavailable("Required dimension target is unavailable: " + target.getName(), failure);
            }
        }
        require(false, customEnvironment);
    }

    private static void require(boolean allowPending, boolean customEnvironment) {
        try {
            RequiredNativeHooks.require("Dimension transfer and storage", allowPending, TRANSFER);
            for (String operation : MAPS) {
                RequiredNativeHooks.require("Dimension map identity", allowPending,
                        "betamoon:dimensions:map:" + operation);
            }
            if (customEnvironment) {
                for (String rule : ENVIRONMENT) {
                    RequiredNativeHooks.require("Dimension environment", allowPending, "betamoon:dimensions:" + rule);
                }
            }
        } catch (IllegalStateException failure) {
            throw new Unavailable(failure.getMessage(), failure);
        }
    }

    public static final class Unavailable extends IllegalStateException {
        private Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
