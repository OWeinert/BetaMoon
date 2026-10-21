# BetaMoon 0.11 pre-1.0 implementation record

Date: 2026-10-21. Code, Lua addon and wiki work branches: `feature/0.11-pre-1.0`.

| Stage | Result |
| --- | --- |
| 1 | Merged material/step-sound and script-config branches in all three repositories. `Integrated011Test` loads one script with a custom solid block, custom-material fluid pair, packaged WAV step sound and live config callback. It saves and reopens a world, performs a compatible reload, rejects a changed native material on a second reload, and preserves a rejected invalid config edit. The same test passed with the packaged client Java agent active. An interactive GUI playthrough was not performed. |
| 2 | Defined reference equality, durable comparison and stale-generation behavior in [the reference contract](REFERENCE_IDENTITY_AND_LABEL_CONTRACT.md). Fluid, asset and sound-event references retain identity while held; block/item views remain value-like. Tests cover lookup/query equality and held asset/event behavior after unload. |
| 3 | Added optional config `schemaVersion` and `migrate(old, fromVersion)`. The old snapshot is recursively read-only; full validation and revision checking precede an atomic replacement. Failed migration or a newer version leaves the file unchanged and blocks saving until repair and reload. Unversioned two-argument configs remain supported. |
| 4 | Indexed block and item names to native IDs and invalidated the index on BetaMoon registration and loader passes. Alias and first-match behavior are covered by tests. No performance measurement was required for this stage. |
| 5 | Added optional manifest dependency objects with `name` and `minVersion`. Comparison accepts only three non-negative numeric components. Name-only dependencies remain valid; mismatches fail before `modInit` and show installed and required versions. |
| 6 | Skipped as requested. |
| 7 | Kept config labels and choice captions separate from persisted keys/values, retained validation paths, and kept dependency-version diagnostic components structured until presentation. No localization API was added. |

## Verification

- `gradlew check agentSmokeTest --offline --quiet` passed, including the packaged client agent integration scenario.
- `gradlew integrated011Test --offline --quiet` and `gradlew integrated011AgentTest --offline --quiet` passed locally. These task definitions live in the ignored local `build.gradle`; the test source is tracked.
- Addon `npm run sync-api`, `npm run check-api`, `npm test`, `npm run package`, and the LuaLS check passed. The canonical `internal/api.lua` remains in the addon's ignored local `internal/` folder as required.
- Changed wiki pages passed local-link checking; their JSON examples parsed and all 31 Lua snippets parsed with LuaJ.

The wiki work remains on its matching development branch. No wiki default branch was published.
