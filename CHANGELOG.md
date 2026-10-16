# Changelog

## 0.10.0 (in development)

### Minecraft-style interface

- Use a Minecraft-style search textbox in BetaMoon Controls, with a blinking caret, bounded paste, and scrolling long queries.
- Style diagnostic dialogs and update notices with compact charcoal pixel frames, native buttons, readable links, and restrained list selection and scrollbars.
- Keep long dialog messages scrollable, adapt narrow-screen footer layouts, and preserve Scripts selection and scrolling when returning from dialogs.
- Size dialogs to their wrapped content within viewport limits, and identify script issues with explicit severity labels and independently wrapped source links.

### Particles

- Use Minecraft display ticks for the decorative ring block, retaining ordinary Lua per-particle animation callbacks.
- Restore automatic repeating block ticks when saved chunks load, so existing particle ring blocks resume
  emission after rejoining a world. Retry schedules while surrounding chunks are unavailable.
- Add registered particle sprite types and reusable burst/emitter effects, with custom PNGs, Minecraft atlas cells,
  independent animation, color/alpha, rotation, size, motion, and collision policies.
- Add optional per-instance Lua `onTick` updates with scoped particle controls and transient memory.
- Default to admitting up to 1,024 new custom particles per client tick; configurable with `particles.maxPerTick`.
- Allow client-wide particle budgets up to 65,535 active or new sprites per tick, retaining defaults of
  4,096 active and 1,024 new sprites per tick.
- Extend world particle spawning with custom references/keys and add effect handles, actor attachments,
  client budgets, diagnostics, and owner/world cleanup. Custom presentation is local-client only.

### Logical tick events

- Run `game_tick` once per logical client tick, including menus, screens, and paused gameplay.
- Run `gui_tick` after `game_tick` while a screen is open, and add `world_tick` after actual client-world
  simulation updates. Non-pausing screens and remote-world updates continue to receive world ticks.
- Require the Java agent for logical tick subscriptions. These callbacks no longer follow rendered frames.
- Keep world-service and logical-network tick intervals consistent when world time changes.
- Attribute event, world-service and network callback diagnostics to the declaring script.


### Drops and diagnostics

- Remove stale declared block drops when reloading scripts.
- Show declared block/item canonical keys throughout debug reports, including recipe stacks, fuel rules,
  machine bindings, subtype rows, and world-generation references. Identify content without an assigned key.

### Multiblocks

- Define multiblocks in Lua with layers or sparse positions, block/state rules, count constraints,
  horizontal orientation, optional mirrors, repeatable layers, and named groups of matching positions.
- Add controllerless discovery, automatic/item activation, explicit deactivation, bounded manual searches,
  member lookup, and owned activation fills that preserve player replacements.
- Detect controller completion, invalidation, and chunk suspension/resumption automatically. Use scoped
  controller access to gate machine processing and inspect useful mismatch reports without loading terrain.
- Preserve multiblock identity and declared custom data across world reloads without changing controller
  inventories, tile data, metadata, or appearances. Failed multiblock reloads retain working definitions.
- Add optional exclusive membership and configurable pattern, processing, and instance limits.
  Automatic completion targets singleplayer worlds; remote-world checks provide diagnostics only.

- Add Advanced 06, a controllerless visual portal activated with flint and steel, and Advanced 12, a brick charcoal kiln with fuel-free
  log processing, flame progress and an invalid-structure warning. Renumber subsequent advanced lessons.
