package betamoon.gui.api.component;

import betamoon.io.IoUtils;
import java.io.File;

/**
 * Clickable link-like text for opening files or directories.
 */
public class GuiTextFileLink extends GuiTextClickable {
    private File path;

    /**
     * Creates a clickable text that opens the assigned path or file.
     */
    public GuiTextFileLink() {
        super();
        setAction(() -> IoUtils.openPath(GuiTextFileLink.this.path));
    }

    /**
     * Sets the file or directory to open on click.
     *
     * @param path
     *            file or directory to open
     */
    public void setPath(File path) {
        this.path = path;
    }

}
