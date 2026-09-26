# Changelog

## 0.7.2

BetaMoon 0.7.2 expands the Lua engine with reusable fuel rules, persistent capabilities,
connected and wireless logical networks, interactive container controls, configurable
explosions, manifest-based multi-file mods, and broader debug exports.

### Gameplay systems

- Add custom fuel rules with item and metadata matching, configurable burn times,
  priorities, predicates, and machine scopes. Vanilla furnaces and custom machines can
  share rules or accept different fuel sets.
- Add typed, persistent capabilities for blocks, items, tile entities, entities, players,
  worlds, and scripts. Capability values support validation, lifecycle callbacks, and
  controlled mutation through Lua handles.
- Add persistent world services and logical networks for connected or wireless systems.
  Scripts can model producers, consumers, storage, relays, channels, remote triggers,
  and other shared systems.
- Add seven Minecraft-style container GUI controls: buttons, icon buttons, checkboxes,
  toggle buttons, sliders, text boxes, and choice selectors. The built-in sprites cover
  interaction, selection, disabled, and invalid states and can be replaced by texture
  packs under `bm_assets/betamoon/textures/gui/controls/`.
- Keep container behavior separate from GUI presentation, with keyboard focus, text
  editing, drag input, script-defined controls, per-state asset overrides, and reusable
  nine-slice frames.
- Add configurable world explosions with source attribution, fire and terrain policies,
  affected-block inspection, and before/after callbacks.

### Mod packaging, tools, and fixes

- Add manifest-based directory and ZIP mods with arbitrary entrypoints, shared manifest
  metadata, isolated private `require` modules, complete-package preflight, and reload
  tracking. Existing single-file mods remain supported.
- Give each manifested mod an isolated `assets/` root for textures, sounds, models, and
  animations. Directory and ZIP layouts resolve identically, while texture-pack
  overrides retain their logical paths beneath `bm_assets/`.
- Expand debug exports with script ownership, expected asset paths, fuels, capabilities,
  systems, logical networks, container controls, and explosion-related information.
- Package the multi-script Advanced 03 and 08-11 examples as manifested mods that use
  private `require` modules. The Beginner 19 export/import lesson remains two separate
  mods to demonstrate deliberate cross-mod sharing.
- Fix hot reload so changing a registered block, tool, or armor `displayName` immediately
  replaces its localization while preserving the native object and existing world state.
- Fix enriched `ctx.world:getInfo()` snapshots so world-service and item callbacks can
  read day, time, light-cycle, difficulty, and height data without disabling themselves.
