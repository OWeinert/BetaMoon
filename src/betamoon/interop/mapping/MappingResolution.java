package betamoon.interop.mapping;

/** Evidence for one canonical symbol in one compiled target; never silently substitutes a missing cell. */
public final class MappingResolution {
    public enum State {
        VERIFIED_MAPPED,
        VERIFIED_UNCHANGED,
        NOT_CATALOGUED,
        SIDE_ABSENT,
        NAMED_TYPE_MISSING,
        NAMED_MEMBER_MISSING,
        MAPPING_MISSING,
        MAPPING_CONFLICT,
        TARGET_TYPE_MISSING,
        TARGET_MEMBER_MISSING,
        DESCRIPTOR_UNAVAILABLE
    }

    private final State state;
    private final String owner;
    private final String name;
    private final String descriptor;
    private final String detail;
    private final String mappingOwner;

    MappingResolution(State state, String owner, String name, String descriptor, String detail) {
        this(state, owner, name, descriptor, detail, null);
    }

    MappingResolution(State state, String owner, String name, String descriptor, String detail, String mappingOwner) {
        this.state = state;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.detail = detail;
        this.mappingOwner = mappingOwner;
    }

    public State getState() {
        return state;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getDetail() {
        return detail;
    }

    public String getMappingOwner() {
        return mappingOwner;
    }

    public boolean isVerified() {
        return state == State.VERIFIED_MAPPED || state == State.VERIFIED_UNCHANGED;
    }
}
