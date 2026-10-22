# Changelog

## 0.11.0

### Materials and step sounds

- Add custom block materials for blocks and fluids while retaining vanilla material support.
- Add sound-event-backed step sounds for blocks alongside vanilla step sounds.
- Include material and step sound details in block debug exports.

### Script configuration

- Add an in-game editor for script configuration, with staged changes, defaults, and validation.
- Support boolean, number, string, list, dictionary, and fixed nested settings with optional labels, descriptions,
  bounds, choices, and read-only entries.
- Let scripts select text fields, sliders, dropdowns, or cycling buttons for values. Lists and dictionaries can use
  individual controls or comma-separated text.
- Make nested sections and collections collapsible. They start closed unless declared with `expanded = true`.
- Add fixed-choice multi-select lists, optional editor headings/dividers and field order, and draft-only per-field Reset buttons.
- Save changes atomically, detect conflicting file edits, and preserve unknown JSON fields.
- Update live Lua values after saving and support `onChange` callbacks with error reporting and retry.

### Biome and region generation overrides

- Let scripts register selectable Overworld biome/region packages while Minecraft remains the base generator.
- Apply scoped terrain sources, material rules, carvers, surfaces and population suppression through native and
  owned generation stages. Keep biome identity and unaffected native raw columns intact.
- Save selected packages, effective order and dependency signatures with generation settings; reject changed or
  missing enabled definitions on reopen. Add point/chunk inspection for target influence and selected channels.

### Position arguments

- Accept named position tables and position objects alongside numeric coordinates in world, entity, multiblock,
  particle, teleport, and worldgen runtime APIs. Preserve each operation's coordinate units and limits.
- Require exact integers for block coordinates and integer position construction; reject non-finite and out-of-range
  float positions.
- Preserve nonzero and fractional Z coordinates in numeric X/Z spatial-field queries.

### Optimization and reliability

- Keep fluid, asset and sound-event Lua reference identity stable while held; preserve value-like block/item views.
- Allow opt-in config schema versions with validated, atomic migration and protection for newer or failed files.
- Index block/item names for repeated lookups while preserving aliases and first-match behavior.
- Allow packaged dependencies to require a minimum three-part numeric version.

### Built-in assets

- Add `minecraft:block/full_block`, a full-cube model with the `default` material slot.
- Expose Minecraft's whole client PNG images and terrain/item atlas cells through the texture asset API.
- Resolve native references through the selected vanilla texture pack and its client-JAR fallback, using native
  paths such as `terrain.png` and `gui/items.png`.
- Support live native atlas cells in block, item and entity model materials.
