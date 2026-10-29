package betamoon.debug;

import java.io.File;
import java.io.IOException;

/** Worker control over detached native exports only. */
interface DebugExportControl {
    void checkpoint() throws IOException;

    void fileStarted(String name);

    void fileCompleted();

    void publish(File temporary, File destination) throws IOException;
}
