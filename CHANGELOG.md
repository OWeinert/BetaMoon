# Changelog

## 0.10.0

### World generation

- Add saved generator profiles with flat, void, heightmap, density, Beta-style, blended and composed terrain.
- Add shared spatial fields, noise and fixed/original-spawn anchors for terrain, biomes, placements, structures,
  natural spawns and loot.
- Add height profiles, material rules, cave carvers, named placement heights and population controls.
- Let players choose a generator and enable or disable generation content in Create World, with choices saved per world.
- Warn about missing saved generators and offer confirmed, temporary Minecraft generation without replacing saved choices.
- Add read-only terrain previews, field inspection and CSV exports.

### Dimensions and teleportation

- Add persistent custom dimensions with builtin or custom generation, independent environment rules, seeds and
  spawn/respawn settings.
- Add queued player teleportation within and between dimensions, with safe arrival searches, optional platforms and
  saved return history.
- Add Minecraft-style loading progress and saved-dimension recovery with explicit backup and rescue to the Overworld.
- Add custom-dimension selectors to world generation, natural spawning and atmospheres, and scoped player access in
  event/input callbacks.

### Blocks and items

- Add metadata-dependent names and textures, declared block inventory subtypes and metadata-aware resource queries.
- Add opt-in subtype-preserving block drops; metadata zero remains the default.

### Player and entity access

- Add scoped local-player access, position/rotation transforms, collision-aware movement, velocity impulses and support queries.

### Container GUIs

- Add resizable visual slots and panels with Minecraft-style borders.
- Add state-driven element bindings, GUI events, runtime element controls and independent property transitions.
- Add translation, scaling, clipping, tinting, opacity and interpolation/easing helpers for supported elements.

### Atmospheres and fluids

- Add biome-, region- and dimension-scoped fog, sky/light colors and ambient/weather sound layers.
- Add independent custom-fluid ecology traits and fluid-aware placement filters.

### Compatibility and fixes

- Improve script diagnostics with runtime error indicators, grouped issue lists, clipboard actions, and detailed reports.
- Add rotating runtime logs, independent file and console severity settings, and script-owned Lua logging.
- Standardize biome labels on `displayName`, keeping `name` as a deprecated compatibility alias.
- Correct biome key lookups and surface rules applying to the wrong chunk columns.
- Preserve script ownership during callbacks so script-owned operations and cleanup use the declaring script.
