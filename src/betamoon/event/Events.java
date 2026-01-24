package betamoon.event;

public final class Events {
    public static final WorldJoinEvent WORLD_JOIN = new WorldJoinEvent();
    public static final WorldLeaveEvent WORLD_LEAVE = new WorldLeaveEvent();
    public static final GuiOpenEvent GUI_OPENED = new GuiOpenEvent();
    public static final GuiCloseEvent GUI_CLOSED = new GuiCloseEvent();
    public static final GuiTickEvent GUI_TICK = new GuiTickEvent();
    public static final GameTickEvent GAME_TICK = new GameTickEvent();
    public static final PlayerJoinEvent PLAYER_JOIN = new PlayerJoinEvent();
    public static final PlayerLeaveEvent PLAYER_LEAVE = new PlayerLeaveEvent();
    public static final ScreenChangedEvent SCREEN_CHANGED = new ScreenChangedEvent();
    public static final KeyInputEvent KEY_INPUT = new KeyInputEvent();
    public static final MouseInputEvent MOUSE_INPUT = new MouseInputEvent();
    public static final BlockBreakAttemptEvent BLOCK_BREAK_ATTEMPT = new BlockBreakAttemptEvent();
    public static final BlockPlaceAttemptEvent BLOCK_PLACE_ATTEMPT = new BlockPlaceAttemptEvent();
    public static final ItemUseEvent ITEM_USE = new ItemUseEvent();
    public static final DimensionChangeEvent DIMENSION_CHANGE = new DimensionChangeEvent();

    private Events() {
    }
}
