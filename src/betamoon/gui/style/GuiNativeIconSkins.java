package betamoon.gui.style;

import betamoon.gui.framework.GuiContext;
import java.util.Collections;

/**
 * Native pack atlas regions, borrowed without extracting or color-keying their
 * pixels.
 */
public final class GuiNativeIconSkins {
    public static boolean contains(String name) {
        return name.equals("minecraft:hearts") || name.equals("minecraft:armor") || name.equals("minecraft:air");
    }

    public static GuiIconMeterSkin prepare(GuiContext context, String name) {
        if (!contains(name)) {
            throw new IllegalArgumentException("Unknown native icon skin: " + name);
        }
        int texture = context.getRenderer().preparePackagedTexture("/gui/icons.png");
        GuiAtlasLease atlas = new GuiAtlasLease() {
            public int width() {
                return 256;
            }

            public int height() {
                return 256;
            }

            public int textureId() {
                return texture;
            }

            public void requireActive() {
                if (texture < 0) {
                    throw new IllegalStateException("Native HUD atlas unavailable");
                }
            }
        };
        boolean health = name.equals("minecraft:hearts");
        boolean armor = name.equals("minecraft:armor");
        int y = health ? 0 : armor ? 9 : 18;
        GuiDrawable empty = health || armor ? region(atlas, 16, y) : null;
        GuiDrawable full = region(atlas, health ? 52 : armor ? 34 : 16, y);
        GuiDrawable partial = region(atlas, health ? 61 : 25, y);
        return new GuiIconMeterSkin(9, 9, 8, 9, empty, full, Collections.singletonMap(0.5, partial),
                health ? region(atlas, 70, 0) : null,
                health ? Collections.singletonMap(0.5, region(atlas, 79, 0)) : Collections.emptyMap(),
                health ? region(atlas, 25, 0) : null, -1, -1, -1);
    }

    private static GuiDrawable region(GuiAtlasLease atlas, int x, int y) {
        return GuiDrawable.image(atlas, x, y, 9, 9, 0);
    }

    private GuiNativeIconSkins() {
    }
}
