# Changelog

## 0.10.0

- Add metadata-dependent block/item names and texture assets for visual variants.
- Add block inventory subtypes with placement-state separation and explicitly enabled subtype-preserving drops.
- Add metadata-aware resource lookups and discovery of declared custom variants.

- Reduce cave-generation work and reuse neighboring base terrain for fluid-safety checks.
- Improve custom terrain generation and lighting performance while preserving terrain output.
- Correct emitted light spreading into already loaded chunks during custom terrain generation.

- Use displayName consistently for biomes while preserving name as a compatibility alias.

- Preserve custom climate biomes when opening generation settings and include Example Meadow under its display name.

- Organize generation settings into collapsible categories with separate checkboxes and correct wide-button rendering.

- Warn when a saved world generator is missing and offer confirmed, temporary Minecraft generation without replacing saved settings.

- Add two JSON/Lua world-generation tutorials and reorganize the intermediate/advanced example sequence.
- Correct generator chunk exports resolving their preview method.

- Add Minecraft-style world creation preset selection and searchable per-world generation settings with saved choices.

- Add save-bound Overworld generator profiles with void, layered, heightmap, density, Beta-compatible, blended and composed terrain sources.
- Add shared scalar fields, versioned noises, fixed/original-spawn anchors and spatial selectors for terrain, biomes, placements, structures, natural spawns and contextual loot.
- Add height profiles, material rules, deterministic carvers, authored placement heights and independent population controls.
- Add read-only terrain previews, field inspection and bounded CSV exports.
- Add scoped fog, sky/light tints and ambient/weather sound layers.
- Add explicit custom-fluid ecology traits and identity-aware placement filters.
- Correct surface rules applying to transposed chunk columns.
