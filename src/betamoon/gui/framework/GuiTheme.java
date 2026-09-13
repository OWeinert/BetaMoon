package betamoon.gui.framework;

import betamoon.utils.McColors;

/** Visual constants for BetaMoon's Minecraft-style interface. */
public final class GuiTheme {
    public static final GuiTheme DEFAULT = new GuiTheme();

    public final int textPrimary = McColors.WHITE.getArgb(0xFF);
    public final int textMuted = McColors.GRAY.getArgb(0xFF);
    public final int textError = McColors.RED.getArgb(0xFF);
    public final int textWarning = McColors.GOLD.getArgb(0xFF);
    public final int line = McColors.WHITE.getArgb(0xFF);
    public final int listSeparator = McColors.DARK_GRAY.getArgb(0xAA);
    public final int listSelectedBackground = 0xCC3B6DD1;
    public final int listHoverBackground = 0x88444444;
    public final int scrollbarTrack = 0x55222222;
    public final int scrollbarThumb = 0xCCAAAAAA;
    public final int popupShadow = McColors.BLACK.getArgb(0xDD);
    public final int popupPanel = McColors.BLACK.getArgb(0xFA);
    public final int tooltipBackground = McColors.BLACK.getArgb(0xCC);
    public final int tooltipBorder = McColors.DARK_GRAY.getArgb(0xFF);
    public final int link = McColors.AQUA.getArgb(0x7F);
    public final int linkHover = 0xFFBFE8FF;
    public final int linkUnderline = 0xFFBFE8FF;
    public final int buttonBackground = 0xFF3A3A3A;

    public final int screenPadding = 10;
    public final int controlHeight = 20;
    public final int standardGap = 6;
    public final int popupFramePadding = 4;

    private GuiTheme() {
    }
}
