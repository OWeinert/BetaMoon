package betamoon.worldgen;

import org.luaj.vm2.LuaError;

/** Named logical stage used for ordering compiled placements. */
public enum GenerationStage {
    UNDERGROUND_FEATURES("underground_features"),
    SURFACE_FEATURES("surface_features"),
    ENTITIES("entities"),
    POST_PROCESS("post_process");

    private final String name;

    GenerationStage(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static GenerationStage parse(String value) {
        for (GenerationStage stage : values()) {
            if (stage.name.equals(value)) {
                return stage;
            }
        }
        throw new LuaError("Placement.stage: unknown population stage: " + value);
    }
}
