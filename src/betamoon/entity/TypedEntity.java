package betamoon.entity;

/** Marker for native bridges carrying a Lua type and recoverable saved state. */
public interface TypedEntity {
    EntityInstanceState entityState();
}
