package betamoon.gui.api.util;

import betamoon.utils.McColors;

public final class GuiColors {
    public static final int ALPHA_OPAQUE_MASK = 0xFF000000;
    public static final int LINE_WHITE = McColors.WHITE.getArgb(0xFF);
    public static final int TEXT_PRIMARY = McColors.WHITE.getArgb(0xFF);
    public static final int TEXT_MUTED = McColors.GRAY.getArgb(0xFF);
    public static final int TEXT_ERROR = McColors.RED.getArgb(0xFF);
    public static final int TEXT_WARNING = McColors.GOLD.getArgb(0xFF);
    public static final int LIST_SEPARATOR = McColors.DARK_GRAY.getArgb(0xAA);
    public static final int LIST_SELECTED_BG = 0xCC3B6DD1;
    public static final int LIST_HOVER_BG = 0x88444444;
    public static final int SCROLLBAR_TRACK = 0x55222222;
    public static final int SCROLLBAR_THUMB = 0xCCAAAAAA;
    public static final int POPUP_SHADOW = McColors.BLACK.getArgb(0xDD);
    public static final int POPUP_PANEL = McColors.BLACK.getArgb(0xFA);
    public static final int TOOLTIP_BG = McColors.BLACK.getArgb(0xCC);
    public static final int TOOLTIP_BORDER = McColors.DARK_GRAY.getArgb(0xFF);
    public static final int LINK_PATH = McColors.AQUA.getArgb(0x7F);
    public static final int LINK_PATH_HOVER = 0xBFE8FF;
    public static final int LINK_PATH_HOVER_UNDERLINE = 0xFFBFE8FF;
    public static final int BUTTON_BG = 0xFF3A3A3A;
    public static final int BUTTON_BG_HOVER = 0xFF4A4A4A;
    public static final int BUTTON_BG_DISABLED = 0xFF222222;
    public static final int BUTTON_TEXT = McColors.WHITE.getArgb(0xFF);

    private GuiColors() {
    }
}
