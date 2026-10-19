# Changelog

## 0.10.0

- Add scoped local-player access, atomic entity transforms, bounded collision displacement, impulses and support probes.

- Add source-tracked container GUI bindings, independent property transitions, temporary state events and runtime element handles.
- Share animated geometry across GUI rendering, clipping, pointer input and panel click protection; retain container state during compatible visual refreshes.
- Add consistent RGB artwork tinting, independent progress/icon tints, opacity and GUI interpolation helpers.

- Add resizable, color-tinted panel elements for container GUI sections and expandable side tabs.
- Add resizable visual slot elements with inner dimensions and fixed Minecraft-style borders to container GUIs.

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
