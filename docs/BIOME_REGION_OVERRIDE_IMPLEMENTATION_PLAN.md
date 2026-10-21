# Biome and region worldgen overrides: development plan

Status: implemented first contract on `feature/biome-region-overrides`, 2026-10-21.
Companion contract: `WORLD_CREATION_GENERATION_SETTINGS_PROPOSAL.md` section 13.

## Existing seams and constraints

- `GenerationCatalog` builds per-preset toggles; `GenerationDraft` remembers choices; `GenerationSelection`
  resolves parents and dependency fingerprints; `GenerationSelectionStore` writes the checked atomic save record.
- `WorldGenerationRuntime.prepare` reads that record before provider creation. Unmodified Minecraft worlds retain
  the native provider. Configured worlds use a pinned `WorldGeneratorProfile` and `BetaMoonChunkProvider`.
- Native Overworld `ChunkProviderGenerate` performs raw density, surfaces, then caves inside `provideChunk`.
  `WorldgenSurfaceHook` runs after the native surface method; `WorldPopulationHook` gates native categories.
  A new raw-stage seam and a cave call redirect are required before native cave replacement can be honest.
- Owned terrain uses `TerrainSource`, `ChunkBlockBuffer`, `CompiledCarver`, `ProfileSurfaceRules`, and detached
  `GenerationContext`. Existing `profile_map` is Beta-derived and cannot substitute for native raw terrain.

## Public declaration and compilation

1. Add typed `terrain_override` keys and `betamoon.worldgen.terrain.overrides:add/get/getRequired` to the
   existing terrain API. The first contract is declarative: `key`, `displayName?`, `bases`, `biomes?`, `region?`,
   `blend?`, `priority?`, `before?`, `after?`, and explicit channel operations. Missing channels inherit.
2. Compile overrides in `TerrainRegistry`'s owner transaction. Resolve referenced terrain sources, fields,
   material rules, surfaces and carvers while checking cycles, budgets, base compatibility and replacement
   conflicts. Publish immutable snapshots atomically. Population filters are keyed suppression lists.
3. Normalize biome keys and tags against the saved biome source; exclude wins. Region masks use bounded X/Z
   boxes or existing seed-based spatial selectors that do not depend on generated blocks or original spawn.
4. Sort by explicit dependencies/priority then canonical key. Reject unordered competing replacements.
   Pin complete transitive signatures, stage algorithms and order in the world selection.

## Saved player choice

5. Add override toggles to `GenerationCatalog` for compatible bases, labeled with affected channels.
   `GenerationDraft` uses the same edited-preset asterisk. Population operations only suppress; no override
   can reactivate a disabled world-level category or placement.
6. Advance `GenerationSelectionStore` to schema 3 while reading schema 1/2 exactly. Save selected keys,
   remembered choices, effective order, target/blend/channel signatures and concrete dependencies. New
   registrations remain disabled in existing saves. Missing or modified enabled packages stop generation with
   a clear compatibility error; no silent native fallback.
7. Create a pinned per-world override plan before provider selection. Effective parent state suppresses a
   channel without discarding its remembered toggle. Existing chunks are not rewritten.

## Generation pipeline

8. Compute one deterministic per-column influence from saved biome and region masks. `inside` is zero outside
   the target; `both` has an explicit exterior footprint. Width, sampling and rounding have a versioned
   signature. Reuse the same influence for all channels and diagnostics.
9. Keep the native fast path when no override is selected or no transition cell in the chunk is selected.
   For native Overworld generation, capture raw density bytes before surfaces; run inherited native surfaces
   once; then generate authored raw/surface candidates in a detached buffer and overlay only selected columns.
   Intercept the native cave call separately. When the authored raw candidate may change cave decisions,
   compute a native baseline cave result as well and restore that result outside selected columns. This preserves
   byte-for-byte native columns outside the footprint even when authored surfaces consume a different amount of
   RNG or authored fluids affect cave avoidance. Owned profiles apply the plan in `generateRaw`.
10. Use one versioned, deterministic 4×4 transition-cell selection against the continuous influence. Entire
    columns take base or authored terrain, including height/density shape, fluids and metadata. The same decision
    gates material, cave, surface and population channels. This discrete contract avoids pretending unrelated
    density magnitudes or block IDs can be interpolated. A future numeric height adapter would be a separately
    versioned operation, without changing saved worlds using this one.
11. Apply add/replace/none carvers to bounded masks. Suppressed native caves restore their precise pre-cave
    bytes; added caves clip to selected transition columns. Preserve protected cells and fluid avoidance.
    Finalize lighting and heightmaps only after all raw stages.
12. Filter native and owned population by effective world settings and target, at each native feature origin,
    owned placement origin or regional-structure start. The first contract offers suppression only; it cannot
    bypass world-level parent switches. Keep explicit origin/footprint rules.
    Native category gates remain authoritative. An origin may write outside a target by its documented
    structure/feature footprint; do not promise final populated bytes are unchanged there.

## Verification and delivery

13. Test Minecraft with no override against the native raw hash; test full and zero influence, overlapping
    replacements, negative coordinates, cave-only and cave-suppression paths, material/surface replacement,
    owned terrain metadata, scoped native population, save/reopen order and changed-signature rejection.
    Extend fixtures for reversed neighboring generation order, biome-edge blends and disabled parent switches
    before considering the contract frozen for 1.0.
14. Add read-only point/chunk diagnostics for base biome, applicable packages, channel order, influence and
    parent reasons. Update the canonical addon Lua API, split definitions, wiki API pages and changelog after
    each exposed API is implemented. Run Java gates, addon sync/check/tests/LuaLS, and wiki link/snippet checks.

Implementation rule: reject a requested channel at declaration/selection time if its native or owned stage
cannot honor this contract. Never accept a package that is silently ignored during generation.

## First-contract implementation record

- A package supports owned terrain source replacement (including density/heightmap source fluids), material
  `add`/`replace`, carver `add`/`replace`/`none`, surface `replace`/`none`, and native/owned population suppression.
  Omitted channels inherit. The package and selected order are persisted in schema 3 with transitive signatures.
- Native hooks capture pre-surface raw bytes and redirect the native cave pass. The test fixture checks modified
  columns and exact unaffected columns; a separate cave-only fixture checks negative-coordinate clipping.
  An owned fixture checks occupancy and metadata. Reopened worlds reproduce the selected native chunk.
- `terrain.overrides:inspect` and `:inspectChunk` return read-only target, order, influence and signature data.
  The canonical addon definitions and matching wiki branch document the accepted first contract.
- The source-replacement contract deliberately uses discrete transition cells. Independent numeric height
  transforms, aquifers and population inclusion are possible later APIs; they are not accepted declaration
  fields in this first contract and are rejected if supplied.
