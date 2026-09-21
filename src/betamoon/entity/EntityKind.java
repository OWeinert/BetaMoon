package betamoon.entity;

import java.util.Locale;

/** Stable gameplay category; optional capabilities are validated against it. */
public enum EntityKind {
    PROP,
    PROJECTILE,
    LIVING,
    PICKUP;

    public static EntityKind parse(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Expected entity kind prop, projectile, living, or pickup");
        }
    }
}
