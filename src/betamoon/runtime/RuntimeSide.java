package betamoon.runtime;

/**
 * Physical process side. This is independent from world-level gameplay
 * authority.
 */
public enum RuntimeSide {
    CLIENT, DEDICATED_SERVER, UNKNOWN;

    public boolean hasClientPresentation() {
        return this == CLIENT;
    }
}
