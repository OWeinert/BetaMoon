# BetaMoon 0.11 pre-1.0 implementation record

Updated: 2026-10-22. Code, Lua addon and wiki work branches: `feature/0.11`.

| Stage | Result |
| --- | --- |
| 1 | Merged material/step-sound and script-config branches in all three repositories. `Integrated011Test` loads one script with a custom solid block, custom-material fluid pair, packaged WAV step sound and live config callback. It saves and reopens a world, performs a compatible reload, rejects a changed native material on a second reload, and preserves a rejected invalid config edit. The same test passed with the packaged client Java agent active. The user confirmed the interactive playthrough completed on 2026-10-22. |
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

## Integrated branch verification (2026-10-22)

Committed and pushed the Minecraft built-in asset work on `feature/minecraft-builtin-assets` in all three
repositories, then merged it into `feature/0.11`. Integration commits are `b287dc4` (BetaMoon), `86d27e1`
(Lua addon), and `1c51936` (wiki). After fetching each origin, no other local or remote branch tip was missing
from its repository's integrated branch.

The first full Java run exposed a regression in numeric X/Z spatial-field queries: the shared position reader
required Z to be an integer equal to zero. Fix `028774c` applies the reader's existing numeric policy to Z as
well as X. Regression coverage checks numeric/table parity, receiver handling, fractional coordinates, argument
consumption, non-finite rejection, and retained integer-coordinate validation. The real Hollow Marches surveyor
integration test passes again.

Final verification on the integrated branch:

- Ran `build` and every registered task ending in `Test`, including `agentSmokeTest`, with
  `--offline --continue --console=plain`. All **98 explicit test tasks** were present in the successful log;
  none failed or were skipped. Gradle completed **114 tasks: 108 executed, six up-to-date**, in 1m40s.
  This includes the GUI, model/native-texture, particle and atmosphere OpenGL checks, performance fixture,
  packaged integration, RetroMCP packaging, and the regular `check` gate.
- Audited standalone test entry points. The seven without direct Gradle registration are invoked by the
  existing capability, input, declarative-domain and terrain-pipeline umbrella tests.
- Addon: all 21 generated API categories match the canonical reference; all **19 Node tests**, VSIX packaging,
  and real packaged LuaLS diagnostics/completion/hover checks pass.
- Wiki: **1,120 local links**, **348 Lua snippets**, **36 JSON snippets**, pose-method coverage, and
  **270 tables / 1,942 data rows** pass. Commit `e7f6b9b` fixes unescaped alternative-value separators in three
  biome/region override table rows.
- Interactive playthrough: **completed**, confirmed by the user on 2026-10-22. This records user acceptance;
  no additional assistant-run gameplay session is claimed.

Local Java evidence and the requested task manifest are under `build/verification-0.11/`. The existing local
Gradle configuration and canonical addon reference retain their ignored-file status.
