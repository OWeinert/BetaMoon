# Biome and region worldgen overrides: delivery proposal

Status: active pre-1.0 proposal, 2026-10-21. [World creation proposal section 13](WORLD_CREATION_GENERATION_SETTINGS_PROPOSAL.md#13-biome--and-region-scoped-worldgen-overrides) remains the accepted feature scope. This document turns that scope into implementation boundaries and decisions to settle. No override API is implemented here.

## Player and author contract

Scripts register an independently keyed override package targeting eligible biomes, regions and base presets. A player can select **Minecraft**, then toggle the package under Generation Settings. The package may change terrain height/density, materials/surfaces, carvers and population while the biome's identity remains intact unless another explicit biome selection feature changes it. The first UI exposes package toggles only.

Use a single declaration such as the section 13 example's proposed `betamoon.worldgen.terrain.overrides:add { ... }`, with optional `displayName` falling back to the canonical key. Reuse existing terrain sources, fields, materials, surfaces, carvers and placements. Keep declaration parsing, the compiled registry, saved-world selection and Lua context access separate. Public fields and reference lifetime must be finalized before Lua definitions or wiki pages present the API as implemented.

The existing world-creation draft lifetime, modified asterisk, remembered child switches and parent override rules also apply to these toggles. An override with an inactive parent remains a remembered choice but contributes nothing. Installing a new package never enables it in an existing world.

## Native pipeline boundary

The current `WorldGenerationRuntime` reads `GenerationSelection` before provider setup, pins profile snapshots and preserves a native path for Minecraft. The new binding must resolve override selection and signatures at that point, before terrain or population starts. The no-effective-override case keeps the current native fast path.

A profile-map approximation cannot satisfy the native-preservation promise. Establish native-stage access to base raw terrain, caves, surface and population decisions. Prove byte-for-byte or block/metadata-equivalent raw output for zero influence against the unmodified native generator over representative seeds and chunk orders. Preserve native climate/biome identity and never ask a neighboring world chunk to generate merely to compute an influence mask.

The compiled immutable override plan is world-owned. It evaluates seed, saved biome source and bounded spatial fields, then supplies a deterministic influence and per-channel operation. It may sample a bounded halo, but neither call Lua per voxel nor depend on generation order or wall-clock state. Native and custom caves run once, in an explicit stage before surfaces. Materials and surfaces act only in their declared channels; an omitted channel inherits the base exactly.

### Influence and composition

- Use section 13's `inside` and explicit `both` blend policies. At weight zero, preserve raw base output exactly. At weight one, apply the authored operation fully. Width zero creates a deliberate hard edge.
- Treat height and calibrated density as numeric fields, with versioned scale and rounding. Treat blocks, materials and biome IDs as discrete states using a deterministic seeded transition rule. Do not average IDs or draw independent per-voxel random gates that fragment caves.
- Resolve additive channels in stable key order. Compile explicit `before`/`after` relationships for noncommutative operations, reject cycles and unresolved competing replacements before generation, and never use script registration order as a tie breaker.
- Apply each parent generation switch and locked preset constraint after remembering the player's choices and before composing the effective plan. An override cannot re-enable disabled ores, structures, stages or biomes.
- Define carver footprints, including starts outside a chunk. A masked cave must not leak writes past the declared influence; native cave replacement cannot be implemented by filling carved blocks afterward.
- Population decisions use a documented origin and footprint rule. A feature originating inside the target may extend outside it; diagnostics must show that reach instead of claiming exact final-block preservation everywhere.

## Save and compatibility contract

Extend the versioned `GenerationSelection` record with the player's remembered override keys and the authoritative enabled set for the chosen preset. Persist the resolved order, target selectors, blend policy/width, channel operations and transitive definition signatures. Include biome source, field algorithms, native seam semantics and relevant dependency versions in those signatures. Presentation names do not affect identity.

Read and validate the record before provider construction on every load. Changed or missing effective definitions, unsupported format versions, unavailable native hooks and conflicting channel plans must fail before a new chunk is published. Use the existing explicit world-generation recovery path where applicable; do not silently fall back to Minecraft or recompute the saved selection from today's defaults. Preserve old worlds that have no override record. This feature does not retrogen or rewrite existing chunks.

## Proposed delivery sequence

1. **Native seam proof:** isolate the raw/cave/surface/population boundaries and demonstrate exact native output with no override and zero influence. Add seed and border fixtures, including negative coordinates and reversed chunk order.
2. **Registry and resolution:** compile bounded immutable declarations, targeting, ordering, capability checks and dependency signatures. Build the world-owned plan without exposing a public toggle until persistence and preflight work.
3. **First vertical slice:** enable a cave-only Plains package on Minecraft with a bounded blend. Verify that Plains remains Plains, unrelated native raw terrain remains exact, and native caves are neither lost nor doubled.
4. **Remaining channels:** add height/density, material/surface, fluid interaction and population composition with per-channel acceptance tests. Calibrate density blending before exposing it; unsupported channels fail at declaration/preflight rather than being ignored.
5. **Selection and presentation:** connect package toggles to Generation Settings drafts, parent switches and the modified marker. Persist effective and remembered state, add read-only point/chunk diagnostics and verify reopen/mismatch recovery.
6. **Public API completion:** update the canonical ignored addon `internal/api.lua`, sync/check split definitions, run addon tests and LuaLS checks, then publish concise user-facing wiki documentation on a matching work branch.

Incremental internal slices must not claim that a published override declaration supports channels that are still ignored. A public first release may expose a documented subset only if the remaining section 13 scope stays planned before the 1.0 contract freeze and the save format can extend without changing existing worlds' effective generation.

## Acceptance matrix

- Minecraft plus an enabled mountain/cave Plains override keeps biome identity and creates the declared terrain/caves; an unrelated biome and points outside `inside` influence retain native raw output.
- Cave-only, height-only and surface-only packages leave omitted channels unchanged. Additive packages compose deterministically; conflicting replacements and dependency cycles fail before generation.
- Negative coordinates, narrow biomes, region corners, chunk borders and reversed generation order produce the same outputs and saved signatures.
- Disabled parents override remembered children. New registrations do not activate in saved worlds; reopen restores the saved plan and detects missing/changed effective dependencies.
- Native caves execute once; custom and native carvers obey their masks. Metadata, heightmaps, lighting, protected cells, fluids and NBT survive save/reopen.
- A disabled population category cannot be re-enabled by an override. Placement reach outside the target follows its documented footprint rule.

## Decisions to resolve in design

1. Specify the native-stage seam for each base preset and prove exact zero-influence inheritance. If a base lacks a required seam, declare it incompatible instead of approximating silently.
2. Choose the density normalization and rounding version, then define how a height transform composes with an existing 3D density source.
3. Define native cave start masking and cross-chunk writes without generating neighbors or erasing caves after the fact.
4. Freeze overlap ordering syntax, conflict errors and the stable key-based fallback for commuting additive operations.
5. Set bounds for override count, blend width, mask halo, carver reach and population work, with preflight and runtime enforcement.
6. Decide how a missing override is presented in world recovery while preserving saved effective selection and existing chunk data.
