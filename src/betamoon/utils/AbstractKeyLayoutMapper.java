package betamoon.utils;

import java.util.HashMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public abstract class AbstractKeyLayoutMapper implements KeyLayoutMapper {
    private final Map<Integer, Character> normalMap = new HashMap<Integer, Character>();
    private final Map<Integer, Character> shiftedMap = new HashMap<Integer, Character>();

    protected AbstractKeyLayoutMapper() {
        registerLetters();
        registerDigits();
        registerSymbols();
        registerNumpad();
        registerSpace();
    }

    @Override
    public char map(int keyCode, boolean shift, boolean alt, boolean ctrl) {
        Character ch = shift ? shiftedMap.get(Integer.valueOf(keyCode)) : normalMap.get(Integer.valueOf(keyCode));
        if (ch == null && shift) {
            ch = normalMap.get(Integer.valueOf(keyCode));
        }
        return ch == null ? '\0' : ch.charValue();
    }

    protected abstract void registerLetters();

    protected void registerDigits() {
        add(Keyboard.KEY_1, '1', '!');
        add(Keyboard.KEY_2, '2', '@');
        add(Keyboard.KEY_3, '3', '#');
        add(Keyboard.KEY_4, '4', '$');
        add(Keyboard.KEY_5, '5', '%');
        add(Keyboard.KEY_6, '6', '^');
        add(Keyboard.KEY_7, '7', '&');
        add(Keyboard.KEY_8, '8', '*');
        add(Keyboard.KEY_9, '9', '(');
        add(Keyboard.KEY_0, '0', ')');
    }

    protected void registerSymbols() {
        add(Keyboard.KEY_MINUS, '-', '_');
        add(Keyboard.KEY_EQUALS, '=', '+');
        add(Keyboard.KEY_LBRACKET, '[', '{');
        add(Keyboard.KEY_RBRACKET, ']', '}');
        add(Keyboard.KEY_BACKSLASH, '\\', '|');
        add(Keyboard.KEY_SEMICOLON, ';', ':');
        add(Keyboard.KEY_APOSTROPHE, '\'', '"');
        add(Keyboard.KEY_GRAVE, '`', '~');
        add(Keyboard.KEY_COMMA, ',', '<');
        add(Keyboard.KEY_PERIOD, '.', '>');
        add(Keyboard.KEY_SLASH, '/', '?');
    }

    protected void registerNumpad() {
        add(Keyboard.KEY_NUMPAD0, '0', '0');
        add(Keyboard.KEY_NUMPAD1, '1', '1');
        add(Keyboard.KEY_NUMPAD2, '2', '2');
        add(Keyboard.KEY_NUMPAD3, '3', '3');
        add(Keyboard.KEY_NUMPAD4, '4', '4');
        add(Keyboard.KEY_NUMPAD5, '5', '5');
        add(Keyboard.KEY_NUMPAD6, '6', '6');
        add(Keyboard.KEY_NUMPAD7, '7', '7');
        add(Keyboard.KEY_NUMPAD8, '8', '8');
        add(Keyboard.KEY_NUMPAD9, '9', '9');
        add(Keyboard.KEY_DECIMAL, '.', '.');
        add(Keyboard.KEY_ADD, '+', '+');
        add(Keyboard.KEY_SUBTRACT, '-', '-');
        add(Keyboard.KEY_MULTIPLY, '*', '*');
        add(Keyboard.KEY_DIVIDE, '/', '/');
    }

    protected void registerSpace() {
        add(Keyboard.KEY_SPACE, ' ', ' ');
    }

    protected void add(int keyCode, char normal, char shifted) {
        normalMap.put(Integer.valueOf(keyCode), Character.valueOf(normal));
        shiftedMap.put(Integer.valueOf(keyCode), Character.valueOf(shifted));
    }
}
