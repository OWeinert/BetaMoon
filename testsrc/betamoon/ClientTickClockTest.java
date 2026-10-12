package betamoon;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Timer;
import net.minecraft.src.UnexpectedThrowable;
import sun.misc.Unsafe;

/** Verifies the render callback can recover Minecraft's actual tick count. */
public final class ClientTickClockTest {
    private ClientTickClockTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(TestMinecraft.class);
        Timer timer = new Timer(20.0F);
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() == Timer.class) {
                field.setAccessible(true);
                field.set(minecraft, timer);
            }
        }

        timer.elapsedTicks = 0;
        require(ClientTickClock.elapsedTicks(minecraft) == 0, "A render without ticks must not advance animations");
        timer.elapsedTicks = 3;
        require(ClientTickClock.elapsedTicks(minecraft) == 3, "Catch up all ticks completed before this render");
        System.out.println("Client animation clock passed: native timer count across render callbacks.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestMinecraft extends Minecraft {
        private TestMinecraft() {
            super(null, null, null, 320, 240, false);
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }
}
