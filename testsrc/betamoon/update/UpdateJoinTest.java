package betamoon.update;

import betamoon.BetaMoonMain;
import betamoon.config.BetaMoonConfig;
import betamoon.config.ConfigField;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiIngame;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import sun.misc.Unsafe;

/**
 * Exercises the real notification method without launching a game or creating
 * saved worlds.
 */
public final class UpdateJoinTest {
    public static void main(String[] args) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        BetaMoonMain main = (BetaMoonMain) unsafe.allocateInstance(BetaMoonMain.class);
        BetaMoonConfig config = (BetaMoonConfig) unsafe.allocateInstance(BetaMoonConfig.class);
        set(config, "checkForUpdates", new ConfigField<Boolean>("checkForUpdates", false));
        set(config, "notifyUpdatesOnWorldJoin", new ConfigField<Boolean>("notifyUpdatesOnWorldJoin", true));
        UpdateChecker checker = new UpdateChecker((url, agent, deadline) -> {
            throw new AssertionError("Disabled startup must not request updates");
        }, Logger.getAnonymousLogger());
        set(main, "config", config);
        set(main, "updateChecker", checker);
        main.modsLoaded();

        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(TestMinecraft.class);
        minecraft.ingameGUI = (GuiIngame) unsafe.allocateInstance(GuiIngame.class);
        World firstWorld = (World) unsafe.allocateInstance(World.class);
        World otherWorld = (World) unsafe.allocateInstance(World.class);
        RecordingPlayer player = (RecordingPlayer) unsafe.allocateInstance(RecordingPlayer.class);
        player.messages = new ArrayList<>();
        Method notify = BetaMoonMain.class.getDeclaredMethod("updateJoinNotification", Minecraft.class);
        notify.setAccessible(true);

        minecraft.theWorld = firstWorld;
        minecraft.thePlayer = player;
        notify.invoke(main, minecraft);
        require(player.messages.isEmpty(), "Wait for startup result");
        UpdateRelease release = new UpdateRelease(SemanticVersion.parse("0.7.0"), "Modrinth",
                URI.create("https://modrinth.com/mod/betamoon/version/Test"));
        set(checker, "availableUpdate", release);
        notify.invoke(main, minecraft);
        require(player.messages.size() == 2, "Late result delivered once");
        notify.invoke(main, minecraft);
        minecraft.theWorld = otherWorld;
        notify.invoke(main, minecraft);
        minecraft.thePlayer = null;
        notify.invoke(main, minecraft);
        minecraft.thePlayer = player;
        notify.invoke(main, minecraft);
        require(player.messages.size() == 2, "Ticks, dimension changes and respawn do not repeat");
        minecraft.theWorld = null;
        notify.invoke(main, minecraft);
        minecraft.theWorld = firstWorld;
        minecraft.thePlayer = null;
        notify.invoke(main, minecraft);
        require(player.messages.size() == 2, "Wait for local player");
        minecraft.thePlayer = player;
        notify.invoke(main, minecraft);
        require(player.messages.size() == 4, "Rejoin announces cached result");
        minecraft.theWorld = null;
        notify.invoke(main, minecraft);
        set(config, "notifyUpdatesOnWorldJoin", new ConfigField<Boolean>("notifyUpdatesOnWorldJoin", false));
        minecraft.theWorld = firstWorld;
        notify.invoke(main, minecraft);
        require(player.messages.size() == 4, "Chat setting respected");
        System.out.println("Update join delivery and disabled startup passed.");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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

    private static final class RecordingPlayer extends EntityPlayerSP {
        private List<String> messages;

        private RecordingPlayer() {
            super(null, null, null, 0);
        }

        @Override
        public void addChatMessage(String text) {
            messages.add(text);
        }
    }
}
