package betamoon;

import betamoon.event.Events;
import betamoon.event.context.DimensionEventCtx;
import betamoon.event.context.GameEventCtx;
import betamoon.event.context.GuiEventCtx;
import betamoon.event.context.InputEventCtx;
import betamoon.event.context.ItemUseEventCtx;
import betamoon.event.context.PlayerEventCtx;
import betamoon.event.context.PressAction;
import betamoon.event.context.WorldEventCtx;
import betamoon.utils.KeyInputMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class BetaMoonEventHandler {
    private World lastWorld;
    private GuiScreen lastGui;
    private GuiScreen lastScreen;
    private EntityPlayer lastPlayer;
    private Integer lastDimension;
    private boolean[] lastKeyStates;
    private boolean[] lastMouseStates;
    private char[] lastKeyChars;

    public void handleGuiEvents(Minecraft mc, GuiScreen current) {
        // screen changed event
        if (current != lastScreen) {
            Events.SCREEN_CHANGED.publish(new GuiEventCtx(mc, lastScreen, current));
            lastScreen = current;
        }
        // Gui closed/opened events
        if (current != lastGui) {
            if (lastGui != null) {
                Events.GUI_CLOSED.publish(new GuiEventCtx(mc, lastGui));
            }
            if (current != null) {
                Events.GUI_OPENED.publish(new GuiEventCtx(mc, current));
            }
            lastGui = current;
        }
        // world leave event (makes sure that the event fires, because world leaving is triggered from the esc GUI)
        World worldForLeave = lastWorld;
        if (mc.theWorld == null && worldForLeave != null) {
            Events.WORLD_LEAVE.publish(new WorldEventCtx(mc, worldForLeave));
            lastWorld = null;
        }
        // player leave event when leaving to GUI
        if (mc.thePlayer == null && lastPlayer != null) {
            Events.PLAYER_LEAVE.publish(new PlayerEventCtx(mc, lastPlayer, worldForLeave));
            lastPlayer = null;
        }
        // tick event
        Events.GUI_TICK.publish(new GuiEventCtx(mc, current));
    }

    public void handleGameEvents(Minecraft mc) {
        // screen changed event
        GuiScreen currentScreen = mc.currentScreen;
        if (currentScreen != lastScreen) {
            Events.SCREEN_CHANGED.publish(new GuiEventCtx(mc, lastScreen, currentScreen));
            lastScreen = currentScreen;
        }
        // gui closed event (this prevents in-game esc menu event spam)
        if (lastGui != null && mc.currentScreen == null) {
            Events.GUI_CLOSED.publish(new GuiEventCtx(mc, lastGui));
            lastGui = null;
        }
        // world leave/join events
        World currentWorld = mc.theWorld;
        World previousWorld = lastWorld;
        if (currentWorld != lastWorld) {
            // The world can be left unintentionally (server disconnect, etc.), so publish WORLD_LEAVE here as well.
            if (previousWorld != null) {
                Events.WORLD_LEAVE.publish(new WorldEventCtx(mc, previousWorld));
            }
            if (currentWorld != null) {
                Events.WORLD_JOIN.publish(new WorldEventCtx(mc, currentWorld));
            }
            lastWorld = currentWorld;
        }
        // player join/leave events
        EntityPlayer currentPlayer = mc.thePlayer;
        if (currentPlayer != lastPlayer) {
            if (lastPlayer != null) {
                Events.PLAYER_LEAVE.publish(new PlayerEventCtx(mc, lastPlayer, previousWorld));
            }
            if (currentPlayer != null) {
                Events.PLAYER_JOIN.publish(new PlayerEventCtx(mc, currentPlayer, currentWorld));
            }
            lastPlayer = currentPlayer;
        }
        // dimension change event
        if (currentWorld != null) {
            int dimension = currentWorld.getWorldInfo().getDimension();
            if (lastDimension != null && dimension != lastDimension.intValue()) {
                Events.DIMENSION_CHANGE.publish(new DimensionEventCtx(mc, lastDimension.intValue(), dimension));
            }
            lastDimension = Integer.valueOf(dimension);
        } else {
            lastDimension = null;
        }
        // input events
        publishKeyInputEvents(mc);
        publishMouseInputEvents(mc, currentWorld);
        // tick event
        Events.GAME_TICK.publish(new GameEventCtx(mc, currentWorld));
    }

    private void publishKeyInputEvents(Minecraft mc) {
        if (!Keyboard.isCreated()) {
            return;
        }
        int keyCount = Keyboard.getKeyCount();
        if (lastKeyStates == null || lastKeyStates.length != keyCount) {
            lastKeyStates = new boolean[keyCount];
        }
        if (lastKeyChars == null || lastKeyChars.length != keyCount) {
            lastKeyChars = new char[keyCount];
        }
        for (int i = 0; i < keyCount; i++) {
            boolean down = Keyboard.isKeyDown(i);
            if (down != lastKeyStates[i]) {
                char keyChar;
                if (down) {
                    keyChar = mapKeyChar(i);
                    lastKeyChars[i] = keyChar;
                } else {
                    keyChar = lastKeyChars[i];
                    lastKeyChars[i] = '\0';
                }
                Events.KEY_INPUT.publish(new InputEventCtx(mc, i, down, keyChar));
            }
            if (down) {
                char heldChar = lastKeyChars[i];
                if (heldChar == '\0') {
                    heldChar = mapKeyChar(i);
                    lastKeyChars[i] = heldChar;
                }
                Events.KEY_INPUT.publish(new InputEventCtx(mc, i, PressAction.HELD, heldChar));
            }
            lastKeyStates[i] = down;
        }
    }

    private void publishMouseInputEvents(Minecraft mc, World world) {
        if (!Mouse.isCreated()) {
            return;
        }
        int buttonCount = Mouse.getButtonCount();
        if (lastMouseStates == null || lastMouseStates.length != buttonCount) {
            lastMouseStates = new boolean[buttonCount];
        }
        int mouseX = Mouse.getX();
        int mouseY = Mouse.getY();
        for (int i = 0; i < buttonCount; i++) {
            boolean down = Mouse.isButtonDown(i);
            boolean wasDown = lastMouseStates[i];
            if (down != wasDown) {
                Events.MOUSE_INPUT.publish(new InputEventCtx(mc, i, down, mouseX, mouseY));
                if (down) {
                    handleMousePress(mc, i);
                }
            }
            if (down) {
                Events.MOUSE_INPUT.publish(new InputEventCtx(mc, i, PressAction.HELD, mouseX, mouseY));
            }
            lastMouseStates[i] = down;
        }
    }

    private void handleMousePress(Minecraft mc, int button) {
        if (button == 1 && mc.thePlayer != null) {
            ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
            if (stack != null) {
                Events.ITEM_USE.publish(new ItemUseEventCtx(mc, stack));
            }
        }
    }

    private char mapKeyChar(int keyCode) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        return KeyInputMapper.map(keyCode, shift, alt, ctrl);
    }
}
