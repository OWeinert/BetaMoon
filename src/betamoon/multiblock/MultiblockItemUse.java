package betamoon.multiblock;

import forge.ForgeHooks;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

/**
 * One synchronous item interaction frame, including nested native/custom
 * dispatch.
 */
public final class MultiblockItemUse {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private MultiblockItemUse() {
    }

    private static final class Frame {
        final ItemStack stack;
        final World world;
        boolean handled;
        boolean charged;

        Frame(ItemStack stack, World world) {
            this.stack = stack;
            this.world = world;
        }
    }

    public static boolean handled() {
        Frame frame = CURRENT.get();
        return frame != null && frame.handled;
    }

    public static void handled(boolean handled) {
        Frame frame = CURRENT.get();
        if (frame != null) {
            frame.handled |= handled;
        }
    }

    static void charged(EntityPlayer player, ItemStack stack) {
        Frame frame = CURRENT.get();
        if (frame != null) {
            frame.charged = true;
        } else if (stack.stackSize == 0) {
            ForgeHooks.onDestroyCurrentItem(player, stack);
        }
    }

    public static boolean block(World world, EntityPlayer player, ItemStack stack, int x, int y, int z, int face,
            boolean automatic, BooleanSupplier original) {
        Frame previous = CURRENT.get();
        if (previous != null && previous.stack == stack && previous.world == world) {
            return previous.handled || original.getAsBoolean() || previous.handled;
        }
        Frame frame = new Frame(stack, world);
        CURRENT.set(frame);
        try {
            if (automatic) {
                frame.handled = ControllerlessMultiblockRuntime.use(world, player, stack, x, y, z, face).handled;
            }
            return frame.handled || original.getAsBoolean() || frame.handled;
        } finally {
            if (!automatic && frame.charged && stack.stackSize == 0) {
                ForgeHooks.onDestroyCurrentItem(player, stack);
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static ItemStack air(World world, ItemStack stack, Supplier<ItemStack> original) {
        Frame previous = CURRENT.get();
        if (previous != null && previous.stack == stack && previous.world == world) {
            return previous.handled ? stack : original.get();
        }
        Frame frame = new Frame(stack, world);
        CURRENT.set(frame);
        try {
            return original.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
