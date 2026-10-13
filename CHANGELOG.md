# Changelog

## 0.9.0 (in development)

### Engine settings

- Change `config/betamoon.cfg` for BetaMoon's own settings. On startup, an existing `config/betamoon.config` is migrated automatically to the new filename when no `.cfg` file exists.

### Player input and controls

- Add named input actions with configurable keyboard, mouse-button, and wheel bindings, Hold/Toggle modes, action state, and priority-based capture of native controls.
- Add staged player controllers for movement, looking, and world actions, with safe fallback to Minecraft behavior.
- Add Minecraft and BetaMoon tabs to the Controls screen, including search, binding capture, conflict handling, per-action reset, and mode controls where an action allows both modes.

### Script configuration

- Add `betamoon.config(key, defaults)` for JSON settings in the instance's `betamoon/config` folder. A short key creates one file; a named key places a file under that key's namespace folder.
- Return an ordinary Lua table with direct field access and explicit `config.save()` and `config.reload()` methods. Preserve unsaved values on failed reload and reject saves that would overwrite a file changed on disk.

### Content directory

- Change script/mod package directory to `betamoon/scripts`. On startup, an existing `lua_scripts` directory will be migrated automatically to the new `betamoon/scripts` layout.
