package betamoon.gui.framework;

import betamoon.utils.McColors;

/** Visual constants for BetaMoon's Minecraft-style interface. */
public final class GuiTheme {
    public static final GuiTheme DEFAULT = new GuiTheme();

    public final int textPrimary = McColors.WHITE.getArgb(0xFF);
    public final int textMuted = McColors.GRAY.getArgb(0xFF);
    public final int textError = McColors.RED.getArgb(0xFF);
    public final int textWarning = McColors.GOLD.getArgb(0xFF);
    public final int line = 0xFF555555;
    public final int listSeparator = McColors.DARK_GRAY.getArgb(0xAA);
    public final int listSelectedBackground = 0xFF808080;
    public final int listHoverBackground = 0x66333333;
    public final int scrollbarTrack = 0xFF151515;
    public final int scrollbarThumb = 0xFFAAAAAA;
    public final int popupShadow = McColors.BLACK.getArgb(0xDD);
    public final int popupPanel = 0xFF1B1B1B;
    public final int tooltipBackground = 0xF0100010;
    public final int tooltipBorder = 0xFF503080;
    public final int link = McColors.AQUA.getArgb(0xFF);
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
