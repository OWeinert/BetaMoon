# Changelog

## 0.9.0 (in development)

### Player input and controls

- Add named input actions with configurable keyboard, mouse-button, and wheel bindings, Hold/Toggle modes, action state, and priority-based capture of native controls.
- Add staged player controllers for movement, looking, and world actions, with safe fallback to Minecraft behavior.
- Add Minecraft and BetaMoon tabs to the Controls screen, including search, binding capture, conflict handling, per-action reset, and mode controls where an action allows both modes.

### Script configuration

- Add `betamoon.config(key, defaults)` for JSON settings in the instance's `config/betamoon` folder. A short key creates one file; a named key places a file under that key's namespace folder.
- Return an ordinary Lua table with direct field access and explicit `config.save()` and `config.reload()` methods. Preserve unsaved values on failed reload and reject saves that would overwrite a file changed on disk.
