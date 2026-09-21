package betamoon.entity;

/** Selects native category ticking or explicit script-controlled ticking. */
public enum EntityLifecycle {
    NATIVE,
    MANUAL;

    public static EntityLifecycle parse(String value) {
        if ("native".equals(value)) {
            return NATIVE;
        }
        if ("manual".equals(value)) {
            return MANUAL;
        }
        throw new IllegalArgumentException("expected native or manual");
    }
}
