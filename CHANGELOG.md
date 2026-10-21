# Changelog

## 0.11.0

### Materials and step sounds

- Add custom block materials for blocks and fluids while retaining vanilla material support.
- Add sound-event-backed step sounds for blocks alongside vanilla step sounds.
- Preserve material and custom step sound reference identity across lookups and compatible reloads.
- Include material and step sound details in block debug exports.

### Script configuration

- Add an in-game editor for script configuration, with staged changes, defaults, and validation.
- Support boolean, number, string, list, dictionary, and fixed nested settings with optional labels, descriptions,
  bounds, choices, and read-only entries.
- Let scripts select text fields, sliders, dropdowns, or cycling buttons for values. Lists and dictionaries can use
  individual controls or comma-separated text.
- Make nested sections and collections collapsible. They start closed unless declared with `expanded = true`.
- Save changes atomically, detect conflicting file edits, and preserve unknown JSON fields.
- Update live Lua values after saving and support `onChange` callbacks with error reporting and retry.

### Position arguments

- Accept named position tables and position objects alongside numeric coordinates in world, entity, multiblock,
  particle, teleport, and worldgen runtime APIs. Preserve each operation's coordinate units and limits.
- Require exact integers for block coordinates and integer position construction; reject non-finite and out-of-range
  float positions.

### 0.11 integration and reliability

- Integrate materials, step sounds and the script config editor on one development branch.
- Keep fluid, asset and sound-event Lua reference identity stable while held; preserve value-like block/item views.
- Allow opt-in config schema versions with validated, atomic migration and protection for newer or failed files.
- Index block/item names for repeated lookups while preserving aliases and first-match behavior.
- Allow packaged dependencies to require a minimum three-part numeric version.
- Keep stable keys and field paths distinct from presentation labels in new diagnostics.
