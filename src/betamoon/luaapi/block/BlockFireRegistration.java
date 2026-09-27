package betamoon.luaapi.block;

import java.lang.reflect.Field;
import net.minecraft.src.Block;
import net.minecraft.src.BlockFire;

/**
 * Registers custom burn rates in the vanilla fire block's ID-indexed tables.
 */
public final class BlockFireRegistration {
    private BlockFireRegistration() {
    }

    static void apply(int blockId, int spread, int burn) {
        values("chanceToEncourageFire", "a")[blockId] = spread;
        values("abilityToCatchFire", "b")[blockId] = burn;
    }

    public static int spread(int blockId) {
        return values("chanceToEncourageFire", "a")[blockId];
    }

    public static void spread(int blockId, int value) {
        values("chanceToEncourageFire", "a")[blockId] = value;
    }

    public static int burn(int blockId) {
        return values("abilityToCatchFire", "b")[blockId];
    }

    public static void burn(int blockId, int value) {
        values("abilityToCatchFire", "b")[blockId] = value;
    }

    private static int[] values(String named, String obfuscated) {
        for (String name : new String[]{named, obfuscated}) {
            try {
                Field field = BlockFire.class.getDeclaredField(name);
                field.setAccessible(true);
                return (int[]) field.get(Block.fire);
            } catch (NoSuchFieldException missing) {
                // Try the other runtime namespace.
            } catch (IllegalAccessException inaccessible) {
                throw new IllegalStateException("Cannot access vanilla fire rates", inaccessible);
            }
        }
        throw new IllegalStateException("Vanilla fire rates are unavailable: " + named);
    }
}
