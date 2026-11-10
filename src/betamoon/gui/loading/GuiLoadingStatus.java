package betamoon.gui.loading;

/**
 * Java-owned progress shared by staged GUI frames and synchronous native
 * callbacks.
 */
public final class GuiLoadingStatus {
    public String title;
    public String message = "Preparing destination";
    private int percent = -1;

    public GuiLoadingStatus(String title) {
        this.title = title;
    }

    public int percent() {
        return percent;
    }

    public void progress(int value) {
        percent = value < 0 ? -1 : Math.min(100, value);
    }
}
