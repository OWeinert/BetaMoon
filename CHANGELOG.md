# Changelog

## 0.6.0

Compared with **0.5.0 at the previous `main` tip, `cf8ab67`**, through the 0.6.0 development
tip, `b3c002d`. [Full code comparison](https://github.com/OWeinert/BetaMoon/compare/cf8ab67f47a3db7596026259c6c9ed233694edb8...b3c002d5c9fb66f435030bddb2b1d31298077405).

BetaMoon 0.6.0 introduces a declarative Lua API, script hot reload, interactive block and
item behavior, and persistent machines with custom recipes and container screens.
Minecraft Beta 1.7.3 and Java 8 remain the target runtime.

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
  and callback overrides with access to original behavior through `ctx:base()`.
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
- Reworked BetaMoon's menus into reusable GUI components with shared layout and input
  routing. Improved the pause-menu Scripts button, long-name trimming and full-name
  hover display.
- Extended debug exports with recipe-type schemas and formatting for custom recipes,
  role values and item stacks.

### Documentation, examples and maintenance

- Replaced the old API page with a Lua-only reference organized by topic, with short
  snippets, cross-links, a sidebar, page navigation and a visible version target.
- Updated installation, script setup, reload and troubleshooting guidance. Added a
  getting-started guide and an index of the example scripts.
- Reorganized the examples into **62 Lua files** across beginner, intermediate and
  advanced lessons, including storage, furnaces, redstone, GUI and custom-matcher examples.
- Added showcase image assets and an explicit MIT license for those PNGs. BetaMoon
  remains MIT-licensed.
- Added regression coverage for Lua examples, block/item behavior, tile persistence,
  recipe processing, loader cleanup, events, instrumentation, GUI behavior and exports.
- Applied Java 8 source conventions, typed collections, defensive definition copies,
  and clearer separation of declarations, registries, contexts and runtime policies.
