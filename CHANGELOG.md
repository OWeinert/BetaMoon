# Changelog

## 0.9.0 (in development)

### Logical tick events

- Run `game_tick` once per logical client tick, including menus, screens, and paused gameplay.
- Run `gui_tick` after `game_tick` while a screen is open, and add `world_tick` after actual client-world
  simulation updates. Non-pausing screens and remote-world updates continue to receive world ticks.
- Require the Java agent for logical tick subscriptions. These callbacks no longer follow rendered frames.
- Detect runtime side without loading Minecraft before the agent transformer is installed.
- Advance world services and logical networks from the world simulation boundary, with interval counters
  independent of mutable world time. Preserve declaring-script identity during event/service/network callbacks.

### Script directory

- Change script/mod package directory to `betamoon/scripts`. On startup, an existing `lua_scripts` directory will be migrated automatically to the new `betamoon/scripts` layout.

### Engine settings

- Change `config/betamoon.cfg` for BetaMoon's own settings. On startup, an existing `config/betamoon.config` is migrated automatically to the new filename when no `.cfg` file exists.
- Group engine settings with commented `notifications.`, `scripts.`, `diagnostics.`, and `updates.` keys, migrating values from older flat keys.
- Add startup error popup, reload debounce, successful reload notice, disabled script, load timing, automatic error report, slow callback warning, and verbose asset diagnostic settings.
- Write Scripts screen debug exports under `betamoon/debug` and automatic script error reports as `betamoon/logs/script_err_<timestamp>.log`.

### Player input and controls

- Add named input actions with configurable keyboard, mouse-button, and wheel bindings, Hold/Toggle modes, action state, and priority-based capture of native controls.
- Add staged player controllers for movement, looking, and world actions, with safe fallback to Minecraft behavior.
- Add Minecraft and BetaMoon tabs to the Controls screen, including search, binding capture, conflict handling, per-action reset, and mode controls where an action allows both modes.
- Store saved player controls in `betamoon/controls.json`.

### Script configuration

- Add `betamoon.config(key, defaults)` for JSON settings in the instance's `betamoon/config` folder. A short key creates one file; a named key places a file under that key's namespace folder.
- Return an ordinary Lua table with direct field access and explicit `config.save()` and `config.reload()` methods. Preserve unsaved values on failed reload and reject saves that would overwrite a file changed on disk.
