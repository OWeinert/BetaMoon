package betamoon.gui.interop;

import java.util.HashMap;
import java.util.Map;

/**
 * Conservative owned resource estimates include revisions awaiting native
 * deferred disposal.
 */
public final class GuiResourceBudget {
    private static final long OWNER_LIMIT = 64L * 1024 * 1024;
    private static final long TOTAL_LIMIT = 256L * 1024 * 1024;
    private static final Map<String, Long> OWNERS = new HashMap<>();
    private static long total;

    public static Reservation reserve(String owner, long bytes) {
        if (bytes < 0 || bytes > OWNER_LIMIT || total > TOTAL_LIMIT - bytes
                || OWNERS.getOrDefault(owner, 0L) > OWNER_LIMIT - bytes) {
            throw new IllegalArgumentException("Owned UI resource byte budget exceeded");
        }
        total += bytes;
        OWNERS.put(owner, OWNERS.getOrDefault(owner, 0L) + bytes);
        return new Reservation(owner, bytes);
    }

    public static long allocated() {
        return total;
    }
    public static final class Reservation implements AutoCloseable {
        private final String owner;
        private final long bytes;
        private boolean active = true;
        private Reservation(String owner, long bytes) {
            this.owner = owner;
            this.bytes = bytes;
        }

        public void close() {
            if (!active) {
                return;
            }
            active = false;
            long remaining = OWNERS.get(owner) - bytes;
            if (remaining == 0) {
                OWNERS.remove(owner);
            } else {
                OWNERS.put(owner, remaining);
            }
            total -= bytes;
        }
    }
    private GuiResourceBudget() {
    }
}
