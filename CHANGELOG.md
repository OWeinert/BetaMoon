# Changelog

## 0.7.0 (development)

BetaMoon 0.7.0 adds a model, animation, audio, and custom-entity foundation. Scripts can
now define anything from a static prop or dropped pickup to a multipart living creature
with persistent data and composed AI. The current entity runtime is complete for local
singleplayer worlds; dedicated-server delivery and full multiplayer synchronization are
still under development.

### Upgrading from 0.6.0

- **Move texture-pack overrides from `betamoon/` to `bm_assets/`.** Registered assets,
  direct paths, and built-in model replacements all use the new ZIP-root directory.
  The old `betamoon/` entries are no longer consulted.
- Automatic reload after saving a Lua file is now optional and **disabled by default**.
  Set `hotReloadOnFileChange=true` in BetaMoon's configuration to restore that workflow;
  manual reload remains available from the Scripts screen.
- Asset registration paths are now optional. A key-only asset uses its conventional
  `<namespace>/<category>/<key-path>` location. When `path` is supplied, both the script
  default and its texture-pack override preserve that path beneath `lua_scripts/` and
  `bm_assets/` respectively.

### Custom entities

- Add `betamoon.entities:add`, `get`, and `getRequired` with four explicit entity kinds:
  general-purpose `prop`, configurable `projectile`, collectible `pickup`, and `living`.
  Declarations are script-owned, validated before publication, saved with the world,
  and recover safely when a definition is temporarily missing.
- Add native and fully manual lifecycle modes. Scripts can keep each kind's standard
  simulation or take over every tick while retaining interactions and lifecycle events.
  Spawn, load, activation, deactivation, tick, interaction, pickup, impact, damage,
  death, and removal callbacks have scoped world and entity access.
- Add direct control over position, rotation, facing, velocity, movement input, health,
  damage, attacks, targets, paths, visibility, animation, and visual transforms. Common
  inspection and control methods also work with vanilla and externally added entities
  when they are exposed through a callback handle.
- Add saved typed entity data for booleans, integers, numbers, strings, vectors, item
  stacks, stable entity references, bounded lists, and nested records. Add a separate
  transient memory store for AI routes and short-lived runtime state. Incompatible data
  schema changes are rejected atomically during reload.
- Add optional health to nonliving entities and before/after-damage callbacks. `kill()`
  enters the death lifecycle, `remove()` removes without death, `dropLoot()` explicitly
  resamples declared loot, and `dropItem()` emits one requested stack. Declared totals
  split into legal native stack sizes, including separate stacks for nonstackable items.
- Add saved inventories and logical equipment, optional death drops, owner and team
  relations, alliance checks, native single-passenger mounts, saved behavior states,
  and simulation-time one-shot or repeating timers.
- Add living AI presets for idle, wandering, and directed ground behavior. Advanced
  declarations can replace targeting, path choice, attacks, and movement independently,
  or run a wholly manual AI routine with access to entity, world, sensing, health, data,
  and native pathfinding information.
- Add optional multipart hit and interaction shapes tied to named model parts. Each part
  may be visual-only or have independent boxes and callbacks. Add bounded overlap sensors
  with player/living filters and enter, stay, and leave callbacks.
- Add natural spawning for living types with passive/hostile categories, weights, group
  sizes, per-type caps, light and height ranges, dimension/biome/substrate filters, and
  native or persistent despawning. Spawn attempts inspect loaded terrain only.
- Add swept projectile collision, configurable speed/gravity/drag/lifetime/damage,
  owner grace, impact responses, and owner-aware damage attribution. Items can launch a
  registered custom projectile while retaining ammunition, cooldown, and consumption
  behavior. Player and BetaMoon-entity owners survive save/load through stable identity.
- Add pickup entities that use native dropped-item movement and inventory collection or
  an optional custom appearance. Partial collection keeps the remainder; manual
  lifecycles can trigger collection explicitly.
- Add entity model rendering, per-instance animation selection, visual offsets/rotation/
  scale, optional pickup bobbing and spin, positional sound bindings, and direct sound
  playback. Gameplay state and presentation state remain separate.
- Add bounded world queries for time, weather, dimension, spawn, biome, height, light,
  terrain, redstone, nearby players and entities, plus safe block changes, effects, and
  entity spawning. Nearby queries use true spherical distance; `getBlock` returns `nil`
  for unloaded chunks without loading terrain and enforces Beta 1.7.3's Y range.

### Assets, audio, and texture packs

- Add script-owned texture, sound, model, and animation registries with stable namespaced
  references, atomic publication, lookup, source/path inspection, deferred refresh, and
  cleanup after failed initialization, unload, or reload.
- Resolve key-only textures as PNG, models as JSON, animations as `.animation.json`, and
  sounds as exactly one matching OGG or WAV file. Explicit relative paths remain
  available for projects with their own directory layout.
- Accept registered texture references and keys in block/item textures, worn armor,
  texture overrides, and container GUI images. Atlas-backed content keeps stable slots
  and uploads again only when its content or relevant display state changes.
- Add weighted `betamoon.soundEvents` and local one-shot `betamoon.audio:play`, including
  volume, pitch, range, world-lifetime cleanup, and use by entity presentation events.
- Refresh registered content when requested or when the selected texture pack changes.
  Invalid replacements fall back to the script default; an already usable asset remains
  active if every new source fails. Diagnostics report the selected source and path.

### Models, animation, and appearances

- Add Bedrock geometry JSON model importing and a bounded Bedrock/GeckoLib-style numeric
  animation JSON subset. Geometry supports named bones, parent hierarchies, pivots,
  cuboids, UVs, mirroring, inflation, and locators; model files use the `.json` suffix.
- Add independent poses, named-part translation/rotation/scaling, clip sampling, loop
  modes, weighted blending, bone masks, procedural `onPose` callbacks, and locator
  inspection through Lua.
- Add reusable model appearances for blocks, items, and entities with material textures,
  render layers, lighting/tint controls, and GUI, held, dropped, and world transforms.
- Add per-variant block and item appearances with default inheritance and explicit
  ordinary-rendering fallback. Blocks with any dynamic variant always use dynamic
  rendering; blocks whose variants are all static remain chunk-rendered. Visual hot
  reload rebuilds rendering data without replacing blocks, metadata, or Lua tile data.
- Add bundled `minecraft:block/*` Beta 1.7.3 models for stairs, every fence connection,
  trapdoors, doors, beds, levers, torches, rails, ladders, slabs, buttons, snow, and
  pressure plates. Directional models use one base geometry plus appearance rotation.
- Validate required rendering hooks before accepting model content, isolate failing pose
  callbacks, and restore graphics state after rendering errors.

### Blocks, items, Lua API, and fixes

- Add `betamoon.callbackResults.pass`, `.deny`, and `.handled` as read-only constants for
  interaction callbacks while retaining the equivalent strings for compatibility.
- Rotate custom collision, selection, and ray-trace shapes with horizontal block facing.
  Facing-model examples now orient toward the placing player and stair geometry is
  walkable instead of retaining a full-block collision box.
- Fix face culling between adjacent non-opaque custom blocks while preserving ordinary
  rendering and model-rendered blocks.
- Fix Lua projectile owners, legal loot stack splitting, consistent spherical nearby
  queries, and unloaded-chunk handling for `world:getBlock`.
- Fix quoting of the update-notice menu action and improve installation path examples.

### Interface, configuration, and platform foundations

- Add semantic-version update checks with configuration switches for checking and
  world-join notices. Available releases appear on the main menu with browser and
  copy-link fallbacks.
- Split startup responsibilities into client, common, and server entry points so shared
  gameplay code no longer depends on client presentation classes.
- Add bounded handshake, content-digest, entity snapshot/delta, presentation, sound, and
  resynchronization protocol foundations. These are infrastructure for later dedicated
  server support and do not yet make custom entities multiplayer-ready.

### Documentation, examples, and verification

- Expand the public LuaLS definitions and wiki with the asset, audio, model, animation,
  appearance, entity, AI, lifecycle, data, multipart, sensor, and action APIs.
- Expand the example set from 62 to **75 Lua files**. New lessons cover registered and
  key-only assets, local audio, built-in and Blockbench models, facing appearances,
  animated items and machines, props, custom projectiles, pickups, persistent entity
  data, native composed AI, and manual multipart AI. Example content keys now use the
  consistent `example:<type>/<name>` layout.
- Add regression coverage for asset lifetime and pack fallback, atlas refresh, audio,
  model parsing and rendering, appearance variants, entity behavior and persistence,
  natural spawning, network codecs, configuration, instrumentation, and all examples.

## 0.6.0

Compared with **0.5.0 at the previous `main` tip, `cf8ab67`**, through the 0.6.0 development
tip, `b3c002d`. [Full code comparison](https://github.com/OWeinert/BetaMoon/compare/cf8ab67f47a3db7596026259c6c9ed233694edb8...b3c002d5c9fb66f435030bddb2b1d31298077405).

BetaMoon 0.6.0 introduces a new declarative Lua API, script hot reload, interactive block and
item behavior, and persistent machines with custom recipes and container screens.

### Upgrading from 0.5.0

**The Lua API has breaking changes.** Update existing scripts to the 0.6.0 reference and
examples before loading them with this version.

| Area | 0.5.0 interface | 0.6.0 interface |
| --- | --- | --- |
| Blocks and items | `createBlock` / `createItem` builders | `betamoon.blocks:add` / `betamoon.items:add` declaration tables |
| Tools and armor | `createTool` / `createArmor` builders | `betamoon.tools:add` / `betamoon.armor:add` declaration tables |
| Materials | Root-level material creation functions | `betamoon.materials.tools:add` / `betamoon.materials.armor:add` |
| Recipes | `addShapedRecipe`, `addShapelessRecipe`, `addSmeltingRecipe` | `betamoon.recipes:add` with a recipe `type` |
| World generation | `startWorldGen` and chained generation builders | `betamoon.worldgen.ores:add` / `betamoon.worldgen.biomes:add` |
| Resource queries | `betamoon.query()` builders | Registry `get`, `getRequired`, `find`, `first` and `one` methods |
| Events | Separate methods such as `events:onGameTick` | `betamoon.events:on("game_tick", callback)` |
| Shared modules | `exportModule` / `requireModule` | `betamoon.modules:export(name, table)` / `betamoon.modules:import(name)` |

Add `-javaagent:"/absolute/path/to/betamoon-0.6.0.jar"` to the Minecraft instance's JVM
arguments. The mod JAR also supplies the agent for engine hooks. The packaged 0.6.0 JAR
includes LuaJ 3.0.1 and the agent's runtime dependencies. Both installation methods in
the [README](README.md) explain the setup.

Keep saved block/item IDs, metadata layouts and persistent inventory mappings stable.
Tile entities, containers, GUIs and other retained structural definitions require a
restart after changes. See [reload and compatibility](https://github.com/OWeinert/BetaMoon/wiki/Hot-Reload-and-Compatibility).

### Script lifecycle and shared APIs

- Changed most content related APIs to declarative programming style.
  See the [documentation](https://github.com/OWeinert/BetaMoon/wiki/API-Documentation) for full information on the new APIs.
- Added automatic hot reload after script changes settle, manual reload controls,
  `modUnload()` and `modReload()` hooks, and visible reload/restart status.
- Added ownership tracking and cleanup for subscriptions, overrides, module exports and
  other script resources. Persistent definitions are retained for saved-world safety.
- Reworked dependency resolution and separated script discovery, parsing and lifecycle
  execution. Reload performs a syntax preflight before unloading the current generation.
- Module exports now take an explicit table and publish after successful initialization.
  Consumers import published tables during dependency-ordered initialization.
- Added resource references, detached stack descriptions, position helpers, registry
  lookups, search criteria and result-list helpers.
- Added conditional and prioritized override layers, removable handles, bulk overrides,
  and callback overrides.
- Added read-only named Minecraft constants through `betamoon.mc`, plus biome tree-mode
  constants through `betamoon.worldgen.treeModes`.

### Blocks, items and world generation

- Added saved block metadata schemas, horizontal facing, attachment/support rules and
  automatic dropping when support is lost.
- Added collision and selection boxes, rendering bounds and variants, climbing,
  replaceability, slipperiness, piston reactions and fire behavior.
- Added block interaction, placement, lifecycle, entity-contact, mining-permission and
  drop-query callbacks with scoped world, state, entity and stack access.
- Added default, random and scheduled block ticks, client display ticks, directional
  digital redstone output, wire connection rules and input-change callbacks.
- Added item interaction callbacks, explicit block ray tracing, consumption, remainders,
  cooldowns, projectile ammunition and inventory-tick policies.
- Added tool-class policies, mining/combat durability costs and dynamic harvest/mining
  speed queries. Added custom worn armor textures and item render variants.
- Converted ore and biome registration to validated declarations, including dimension
  and biome filters, climate ranges, surfaces, trees, weather and spawn groups.

### Tile entities, containers and GUIs

- Added persistent tile data and named inventory slots, inventory helpers, tile tick
  callbacks and inventory-change notifications.
- Added container slot layouts, output-only slots and player inventory placement.
- Added declarative container screens with built-in/custom backgrounds, labels, groups,
  images, text, progress indicators, state images, rectangles, tooltips and item previews.
- Added synchronized integer/boolean tile fields, conditional element visibility,
  anchors, drawing layers and tooltip value substitution.

### Recipes and machine processing

- Added custom recipe type schemas with named ingredient/output roles, validated data
  and context fields, optional inputs, retained ingredients and explicit remainders.
- Added ingredient alternatives, damage matching, input pools and grids, transformed
  patterns, pooled outputs and reusable inventory bindings.
- Added recipe matching against snapshots or tile inventories, deterministic candidate
  priority, custom Lua allocation matchers and match signatures.
- Added atomic application with input/revision checks, fresh context revalidation,
  output/remainder capacity checks and protection against repeated or reentrant commits.
- Added recipe/type queries and references, reversible recipe overrides and disabling,
  with ownership-aware cleanup for native and custom recipes.

### Events, interface and diagnostics

- Unified event subscriptions around named events and typed Lua contexts. Subscription
  handles support explicit unsubscription and automatic owner cleanup.
- Added the Java-agent hook system and hook diagnostics for block/item interactions,
  harvesting, redstone, display callbacks and texture resources.
- Added an in-game Scripts screen, reload/restart indicators and a copyable agent
  argument in the agent warning.
- Extended debug exports with recipe-type schemas and formatting for custom recipes,
  role values and item stacks.

### Documentation, examples and maintenance

- Replaced the old API page with a Lua-only reference organized by topic, with short
  snippets, cross-links, a sidebar, page navigation and a visible version target.
- Updated installation, script setup, reload and troubleshooting guidance. Added a
  getting-started guide and an index of the example scripts.
- Reorganized the examples into **62 Lua files** across beginner, intermediate and
  advanced lessons, including storage, furnaces, redstone, GUI and custom-matcher examples.
- Added regression coverage for Lua examples, block/item behavior, tile persistence,
  recipe processing, loader cleanup, events, instrumentation, GUI behavior and exports.
