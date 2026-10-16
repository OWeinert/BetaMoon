# Changelog

## 0.9.0


### Particles

- Add registered particle sprite types and reusable burst/emitter effects, with custom PNGs, Minecraft atlas cells,
  independent animation, color/alpha, rotation, size, motion, and collision policies.
- Add optional per-instance Lua `onTick` updates with scoped particle controls and transient memory.
- Default to admitting up to 1,024 new custom particles per client tick; configurable with `particles.maxPerTick`.
- Allow client-wide particle budgets up to 65,535 active or new sprites per tick, retaining defaults of
  4,096 active and 1,024 new sprites per tick.
- Extend world particle spawning with custom references/keys and add effect handles, actor attachments,
  client budgets, diagnostics, and owner/world cleanup.

### Multiblocks

- Define multiblocks in Lua with layers or sparse positions, block/state rules, count constraints,
  horizontal orientation, optional mirrors, repeatable layers, and named groups of matching positions.
- Add controllerless discovery, automatic/item activation, explicit deactivation, bounded manual searches,
  member lookup, and owned activation fills that preserve player replacements (mechanics similar to minecraft's Nether Portal).
- Detect controller completion, invalidation, and chunk suspension/resumption automatically.
- Preserve multiblock identity and declared custom data across world reloads without changing controller
  inventories, tile data, metadata, or appearances. Failed multiblock reloads retain working definitions.
- Add optional exclusive membership and configurable pattern, processing, and instance limits.
  Automatic completion targets singleplayer worlds; remote-world checks provide diagnostics only.

### Logical tick events

- Run `game_tick` once per logical client tick, including menus, screens, and paused gameplay.
- Run `gui_tick` after `game_tick` while a screen is open, and add `world_tick` after actual client-world
  simulation updates. Non-pausing screens and remote-world updates continue to receive world ticks.
- Require the Java agent for logical tick subscriptions. These callbacks no longer follow rendered frames.
- Detect runtime side without loading Minecraft before the agent transformer is installed.
- Advance world services and logical networks from the world simulation boundary, with interval counters
  independent of mutable world time. Preserve declaring-script identity during event/service/network callbacks.

### Minecraft-style interface

- GUI style overhaul to match minecraft's native GUI better

### Internal organization

- Move native block, item, and biome implementations into their domain packages and rename them with
  descriptive `LuaBlock`, `Lua*Item`, and `LuaBiome` names.
- Consolidate declared block drops into the active definition, removing stale legacy drop state on reload.
- Share item interaction forwarding while preserving lazy vanilla fallback and callback ordering.
- Resolve and cache mapped/obfuscated biome fields through a biome-specific helper.
- Preserve declared block/item canonical keys for debug reports, including recipe stacks, fuel rules,
  machine bindings, subtype rows, and world-generation references. Mark unkeyed content as not assigned.
