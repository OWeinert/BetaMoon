# Changelog

## 0.10.0

- Preserve teleport completion notifications if a loading screen fails to close.

- Detect incompatible saved generation before loading and preserve Minecraft's backup recovery for ordinary saves.
- Correct native-family generation settings, duplicate Nether placements and dimension-scoped spatial eligibility.
- Keep profile-restricted carvers within terrain contributed by the selected profiles.

- Add the Flat Dimension advanced example with classic superflat terrain, a reusable key, activation anchors and return travel.
- Preserve remaining custom item use cooldowns when teleporting between dimensions with different world times.
- Fix custom dimension teleportation being incorrectly refused as an unsupported environment.
- Speed up dimension transfers by saving changed terrain once and avoiding repeated writes of unchanged chunks and world data.
- Show full-screen Minecraft-style loading with terrain preparation and saving progress during dimension transfers and loading.
- Add persistent keyed custom dimensions with native or custom generation, independent environment rules and configurable spawn/respawn.
- Add queued teleportation, safe arrivals, explicit platforms, progress/cancellation and persistent per-script return history.
- Restore saved custom dimensions on load and provide explicit backup-and-rescue recovery for missing or incompatible definitions.
- Extend generation, natural-spawn and atmosphere selectors with canonical dimension identities, and expose scoped players to event/input callbacks.
- Preserve protected local-structure cells, including air, across saves and respect them during teleport platform creation.

- Reduce cave-generation work and reuse neighboring base terrain for fluid-safety checks.
- Improve custom terrain generation and lighting performance while preserving terrain output.
- Correct emitted light spreading into already loaded chunks during custom terrain generation.

- Use displayName consistently for biomes while preserving name as a backwards-compatibility alias.

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
