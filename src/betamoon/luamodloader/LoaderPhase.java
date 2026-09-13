package betamoon.luamodloader;

/** Describes the mutually exclusive phases of the Lua mod loader. */
enum LoaderPhase {
    IDLE(false, false), INITIAL_LOAD(true, false), RELOAD_PREFLIGHT(true, false), HOT_RELOAD(true, true);

    private final boolean busy;
    private final boolean hotReload;

    LoaderPhase(boolean busy, boolean hotReload) {
        this.busy = busy;
        this.hotReload = hotReload;
    }

    boolean isBusy() {
        return busy;
    }

    boolean isHotReload() {
        return hotReload;
    }
}
