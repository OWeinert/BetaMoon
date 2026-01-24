package betamoon.utils;

import org.lwjgl.input.Keyboard;

public final class QwertzKeyLayout extends AbstractKeyLayoutMapper {
    @Override
    protected void registerLetters() {
        add(Keyboard.KEY_A, 'a', 'A');
        add(Keyboard.KEY_B, 'b', 'B');
        add(Keyboard.KEY_C, 'c', 'C');
        add(Keyboard.KEY_D, 'd', 'D');
        add(Keyboard.KEY_E, 'e', 'E');
        add(Keyboard.KEY_F, 'f', 'F');
        add(Keyboard.KEY_G, 'g', 'G');
        add(Keyboard.KEY_H, 'h', 'H');
        add(Keyboard.KEY_I, 'i', 'I');
        add(Keyboard.KEY_J, 'j', 'J');
        add(Keyboard.KEY_K, 'k', 'K');
        add(Keyboard.KEY_L, 'l', 'L');
        add(Keyboard.KEY_M, 'm', 'M');
        add(Keyboard.KEY_N, 'n', 'N');
        add(Keyboard.KEY_O, 'o', 'O');
        add(Keyboard.KEY_P, 'p', 'P');
        add(Keyboard.KEY_Q, 'q', 'Q');
        add(Keyboard.KEY_R, 'r', 'R');
        add(Keyboard.KEY_S, 's', 'S');
        add(Keyboard.KEY_T, 't', 'T');
        add(Keyboard.KEY_U, 'u', 'U');
        add(Keyboard.KEY_V, 'v', 'V');
        add(Keyboard.KEY_W, 'w', 'W');
        add(Keyboard.KEY_X, 'x', 'X');
        add(Keyboard.KEY_Y, 'z', 'Z');
        add(Keyboard.KEY_Z, 'y', 'Y');
    }
}
