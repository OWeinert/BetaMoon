# Changelog

## 0.8.0

BetaMoon 0.8.0 expands world generation into a deterministic, reusable Lua API.

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

### Chat

- Prefix script chat messages with the mod name declared by its metadata instead of its entrypoint filename, and avoid
  applying the prefix twice when a singleplayer broadcast falls back to local chat.
