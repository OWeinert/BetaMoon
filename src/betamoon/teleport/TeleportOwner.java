package betamoon.teleport;

/**
 * One publication's ownership token; discarded Lua callbacks cannot complete
 * requests after reload.
 */
public final class TeleportOwner implements AutoCloseable {
    public final String name;
    private boolean active = true;

    public TeleportOwner(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Teleportation requires a script owner");
        }
        this.name = name;
    }

    public boolean active() {
        return active;
    }

    public void close() {
        active = false;
    }
}
