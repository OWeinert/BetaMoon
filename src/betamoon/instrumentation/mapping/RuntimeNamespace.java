package betamoon.instrumentation.mapping;

/** Namespaces supported by the bundled RetroMCP Tiny mappings. */
public enum RuntimeNamespace {
    NAMED("named"),
    CLIENT("client"),
    SERVER("server");

    private final String mappingName;

    RuntimeNamespace(String mappingName) {
        this.mappingName = mappingName;
    }

    public String getMappingName() {
        return mappingName;
    }
}
