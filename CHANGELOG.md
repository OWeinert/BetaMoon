# Changelog

## 0.11.0

### Script configuration

- Add a Configure button for loaded scripts with registered config files, with one editor section per file.
- Add staged editing for booleans, numbers, strings, nested objects and bounded arrays, with defaults and optional
  labels, descriptions, bounds, choices and read-only explanations.
- Keep config saves atomic and reject conflicting disk revisions. Preserve unknown JSON fields and require explicit
  resolution before replacing an unreadable config file in the editor.
- Update the live Lua table after a successful Save and deliver optional `onChange` callbacks on the next safe update.
  Report callback failures and allow a retry from the editor.
- Add a beginner script demonstrating persistent settings, in-game editing and live changes.
- Center the config editor's actions and show Configure as a compact gear beside the script icon.
- Let scripts choose text, slider, dropdown or cycling controls for config values, with typed and labeled choices.
