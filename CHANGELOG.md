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
