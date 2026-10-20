# Changelog

## 0.11.0

### Script configuration

- Add an in-game editor for script configuration, with staged changes, defaults, and validation.
- Support boolean, number, string, list, dictionary, and fixed nested settings with optional labels, descriptions,
  bounds, choices, and read-only entries.
- Let scripts select text fields, sliders, dropdowns, or cycling buttons for values. Lists and dictionaries can use
  individual controls or comma-separated text.
- Make nested sections and collections collapsible. They start closed unless declared with `expanded = true`.
- Save changes atomically, detect conflicting file edits, and preserve unknown JSON fields.
- Update live Lua values after saving and support `onChange` callbacks with error reporting and retry.
