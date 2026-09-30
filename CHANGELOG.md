# Changelog

## 0.8.0

BetaMoon 0.8.0 expands world generation into a deterministic, reusable Lua API and begins the player-facing control
stack.

### Input and control foundation

- Add owner-scoped named input maps with keyboard, mouse-button, wheel, and modifier bindings; exact per-cycle
  pressed/held/released state; priority-ordered consumable contexts; native-family capture; runtime rebinding and
  conflict validation.
- Add script-owned native hotkeys through ModLoader's key registration, including Controls-screen labels, persisted
  player remaps, optional held repeat, reload-safe native binding reuse, and isolated callback failures.
- Capture queued gameplay and GUI events before their native consumers, preserve native first-person behavior when no
  context handles them, synthesize releases across focus/screen/world/player changes, and clean layers up on unload.

### World-generation foundation

- Add typed worldgen keys, deterministic per-placement seeds, atomic publication, bounded placement plans, previews,
  stable rejection diagnostics, and direct feature placement.
- Add reusable feature and placement registries with ordering, biome and dimension filters, probability, grid sampling,
  composite features, lookup references, and placement candidate location.
- Preserve the approachable ore API while routing declarations through the shared worldgen foundation.

### Trees, structures, biomes, and surfaces

- Add procedural, vanilla-adapter, and structure-authored trees.
- Add local structures from strict JSON, plain Lua tables, or a table-backed Lua builder, with named palettes,
  deterministic variants and conditional geometry, compact shapes, reusable templates, ordered weathering processors,
  transforms, tile data, metadata adapters, capture/export, and marker masks.
- Protect tile-entity states, tile-data and loot targets, connectors, and terrain supports from document processors by
  default, with `allowProtected` as an explicit opt-in override.
- Add keyed biomes, reusable surface stacks, biome decorators, tags, climate biome sources, weather, and spawn lists.
- Add persistent regional structures with random-spread starts, connector pools, weighted piece graphs, chunk-sliced
  recovery, entity markers, definition signatures, and location queries.
- Add reusable deterministic loot tables and dedicated structure loot elements with fixed, inline, or referenced
  contents, item metadata and ranges, configurable insertion policies, and atomic inventory rollback for local and
  regional placement.
- Allow block and entity drops to reuse registered loot tables or declare the same weighted pools inline, with a fresh
  runtime RNG sample for every drop event while preserving legacy drop lists as shorthand.

### Terrain-aware structures

- Add distinct world-surface, solid-surface, ocean-floor, fluid-surface, underground, and cave-floor sampling.
- Add opt-in `fit`, `foundation`, `terrace`, and marker-driven `conform` structure modes with transformed support,
  clearance, ignore, blend, and conform masks.
- Add footprint-aware land, underwater, underground, cave, and fluid-surface site profiles for local and regional
  structures.
- Add bounded regional site fallback search, connector vertical tolerance, and persisted terrain mutations and
  conform offsets for deterministic recovery.
- Add terrain planning details to direct placement and preview results, plus focused site and terrain rejection reasons.
- Add reusable transformed footprints, named and authored masks, rounded and convex foundation/terrace cores,
  deterministic materials, hard/stepped/blended/natural/authored edges, and bounded transition grading.
- Add selector-based conform columns with support/tag/state/bounds/height/marker filters, precise include/exclude
  markers, deterministic smoothing, focused diagnostics, and persisted regional offsets.
- Add independent atomic excavation for every terrain mode with footprint, box, cylinder, ellipsoid, authored voxel,
  local/surface-relative bounds, replacement/fluid policies, protected-cell checks, regional recovery, and explicit
  excavation < adaptation < structure write precedence.

### Chat

- Prefix script chat messages with the mod name declared by its metadata instead of its entrypoint filename, and avoid
  applying the prefix twice when a singleplayer broadcast falls back to local chat.
