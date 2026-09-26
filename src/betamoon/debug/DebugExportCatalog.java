package betamoon.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ordered source of truth shared by the debug menu and Export All. */
public final class DebugExportCatalog {
    private static final List<DebugExportDefinition> DEFINITIONS = create();

    private DebugExportCatalog() {
    }

    public static List<DebugExportDefinition> definitions() {
        return DEFINITIONS;
    }

    public static DebugExportDefinition find(String id) {
        for (DebugExportDefinition definition : DEFINITIONS) {
            if (definition.getId().equals(id)) {
                return definition;
            }
        }
        return null;
    }

    static List<DebugExportDefinition> completeDefinitions() {
        List<DebugExportDefinition> result = new ArrayList<DebugExportDefinition>();
        for (DebugExportDefinition definition : DEFINITIONS) {
            if (definition.isIncludedInComplete()) {
                result.add(definition);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<DebugExportDefinition> create() {
        List<DebugExportDefinition> values = new ArrayList<DebugExportDefinition>();
        values.add(new DebugExportDefinition("content", "Content keys and IDs",
                "Blocks, items, canonical keys, numeric IDs, owners, and lookup names.", true,
                new DebugContentExporter(), "blocks.txt", "items.txt", "content_keys.txt"));
        values.add(new DebugExportDefinition("assets", "Assets and sounds",
                "Asset keys, exact default and texture-pack paths, sound events, and built-in models.", true,
                new DebugAssetExporter(), "assets.txt", "sound_events.txt", "builtin_models.txt"));
        values.add(new DebugExportDefinition("recipes", "Recipes",
                "Built-in and custom recipe types plus every currently registered recipe.", true,
                new DebugRecipesExporter(), "recipe_types.txt", "recipes.txt"));
        values.add(new DebugExportDefinition("entities-machines", "Entities and machines",
                "Entity capabilities, tile data, slots, containers, GUIs, and block bindings.", true,
                new DebugEntityMachineExporter(), "entities.txt", "tile_entities.txt", "containers.txt", "guis.txt"));
        values.add(new DebugExportDefinition("world", "World content",
                "Biome lookup names, Lua climate overlays, and world-generation declarations.", true,
                new DebugWorldExporter(), "biomes.txt", "world_generation.txt"));
        values.add(new DebugExportDefinition("scripts", "Scripts and modules",
                "Package layouts, entrypoints, private modules, cross-mod exports, and load issues.", true,
                new DebugScriptExporter(), "scripts.txt", "modules.txt", "script_issues.txt"));
        values.add(new DebugExportDefinition("runtime", "Runtime diagnostics",
                "Java-agent hook outcomes and read-only counts for live runtime registrations.", true,
                new DebugRuntimeExporter(), "instrumentation.txt", "runtime_registrations.txt"));
        return Collections.unmodifiableList(values);
    }
}
