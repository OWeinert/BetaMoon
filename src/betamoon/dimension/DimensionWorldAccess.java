package betamoon.dimension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldProvider;

/**
 * Audited native field boundary, resolved by unique mapped type rather than
 * obfuscated field names.
 */
public final class DimensionWorldAccess {
    private static final Field SAVE = field(ISaveHandler.class);
    private static final Field INFO = field(WorldInfo.class);
    private static final Field PROVIDER = field(WorldProvider.class);

    private DimensionWorldAccess() {
    }

    private static Field field(Class<?> type) {
        Field found = null;
        for (Field candidate : World.class.getDeclaredFields()) {
            if (!Modifier.isStatic(candidate.getModifiers()) && candidate.getType() == type) {
                if (found != null) {
                    throw new IllegalStateException("Ambiguous native dimension field: " + type.getName());
                }
                candidate.setAccessible(true);
                found = candidate;
            }
        }
        if (found == null) {
            throw new IllegalStateException("Missing native dimension field: " + type.getName());
        }
        return found;
    }

    public static ISaveHandler save(World world) {
        try {
            return (ISaveHandler) SAVE.get(world);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot resolve native save handler", exception);
        }
    }

    public static void replaceStorage(World world, ISaveHandler save, WorldInfo info) {
        try {
            SAVE.set(world, save);
            INFO.set(world, info);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot bind native dimension storage", exception);
        }
    }

    public static void replaceProviderBeforeChunks(World world, WorldProvider provider) {
        try {
            PROVIDER.set(world, provider);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot select the saved dimension provider before chunk access",
                    exception);
        }
        if (world.worldProvider != provider) {
            throw new IllegalStateException("Native world provider selection did not take effect");
        }
    }
}
