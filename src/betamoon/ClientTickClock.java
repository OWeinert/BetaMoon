package betamoon;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Timer;

/** Reads the number of client ticks completed before ModLoader's render callback. */
final class ClientTickClock {
    private static final Field TIMER = findTimer();

    private ClientTickClock() {
    }

    static int elapsedTicks(Minecraft minecraft) {
        try {
            return ((Timer) TIMER.get(minecraft)).elapsedTicks;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read Minecraft's client tick timer", error);
        }
    }

    private static Field findTimer() {
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() == Timer.class) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Minecraft's client tick timer is unavailable");
    }
}
