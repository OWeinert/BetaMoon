# Entity movement foundations for BetaMoon 0.11

Date: 2026-10-22.
Status: M1-M6 implemented and automated acceptance verified on the dedicated feature branch.
Branch: `feature/entity-motion-foundations`, based on integrated `dev` in BetaMoon, the Lua addon and wiki.

## 1. Outcome and scope

Complete the remaining entity rule and motion foundations in 0.11 so 1.0 can build on a usable contract.
Scripts must be able to implement toggleable flight, altered gravity, impulses and custom locomotion through
general entity APIs. Flight activation, controls, state, restrictions and termination belong to scripts.

Reuse Minecraft's existing entity bodies, native update lifecycle and collision solver. Do not add a physics
engine, replacement body hierarchy or independent collision solver. A manual motion command selects velocity;
the existing native collision path executes it once. Keep visual models independent of gameplay state.

This plan specializes the accepted [player/entity modification contract](PLAYER_ENTITY_MODIFICATION_API_PROPOSAL.md)
for the current 0.11 baseline. Its ownership, composition, motion, force and lifecycle requirements remain the
basis. This document supersedes older release assignments that describe the unfinished work as a completed
0.10 foundation. Dedicated-server/multiplayer authority, arbitrary body resizing, collision bypass, vehicles,
new physical body types, persistent stats/effects and saved modifier callbacks remain later work.

The existing access/actions increment is already implemented. Preserve its numeric and position-table forms,
validation, callback expiry, loaded-only queries, physical bounds, interpolation and native save behavior.

## 2. Supported targets

| Target | Planned 0.11 support |
| --- | --- |
| Local singleplayer player | Shared rules and motion; local input observation and automatic player rebinding. |
| BetaMoon living entity with native lifecycle | Shared rules and motion while preserving native/composed AI intent. |
| Audited vanilla walking living entities | Shared living integration with explicit subtype eligibility checks. |
| Static/manual-lifecycle BetaMoon entities | Existing applicable actions; no second simulation loop. |
| Flying/swimming subclasses or foreign movement overrides | Existing applicable actions; rules/motion require a separately verified native movement path. |
| Props, pickups, projectiles, vehicles and multipart proxies | Existing applicable actions; their distinct update paths are outside initial living-step coverage. |
| Multiplayer proxies | Existing observations; no new authoritative mutation. |

No `TypedEntity` requirement is added to native bodies. Determine support from verified native methods and
the active lifecycle, not just an `EntityLiving` type check. Known unsupported rules/hooks report unsupported;
unknown paths are errors. Registration must reject unsupported declarations atomically. A local registration
can await a player, but may not claim a capability whose hooks are missing or failed.

Player input and mob AI feed the same motion contract. Player-only conveniences are local access, automatic
rebinding, routed input and the native sneak body adjustment. Supported entity movement remains shared.

## 3. Delivered Lua surface

These methods are implemented together with the rule, lifecycle and movement contracts below.

| Entry point | Contract |
| --- | --- |
| `entity:modify(definition)` | Attach an owned source to this entity/world generation; return its handle. |
| `betamoon.players:modifyLocal(definition)` | Declare an owned source that binds to the eligible current local player. |
| `entity:supportsModification(path)` | Report support for a known rule or hook on this target. |
| `entity:getModifications(options?)` | Detached resolved values and diagnostics; optional source details. |

Definitions have a canonical `namespace:modifier/path` key, priority in -10000..10000, optional `enabled`,
supported rule groups, and optional `onAttach`, `onDetach`, `onMotion`, `onForces`, `onAfterMotion` callbacks.
Duplicate owner/key/target declarations fail. A script retains and updates its handle rather than registering
a new source each tick. Namespace shorthand follows the shared typed content-key reader.

Handles provide atomic `update(patch)`, `clear(path)`, `setEnabled(value)`, `isEnabled()`, `isValid()`,
`getStatus()`, `retry()` and idempotent `close()`. Omitted patch fields preserve their contributions; explicit
zero and false remain values. Clearing a leaf removes this source's contribution. Keys remain immutable.

Status distinguishes awaiting player, attached, unsupported environment, failed and closed. It also reports
requested enablement, effective application, temporary suspension and diagnostics. Losing motion arbitration
does not remove a source's static rules. Disabling preserves binding state and faults; retry explicitly
recovers a failed binding. Invalid updates preserve the complete previous definition.

Illustrative intended use:

```lua
local gravity = betamoon.players:modifyLocal {
    key = "example:modifier/light_gravity",
    physics = { gravityMultiplier = 0.5 }
}

gravity:setEnabled(false)
```

## 4. Rule values and composition

| Path | Accepted value | Combination |
| --- | --- | --- |
| `physics.gravityMultiplier` | Finite 0..8 | Product, final cap 8. |
| `physics.airDrag.horizontal` | Finite 0..1 | Highest-priority absolute retention. |
| `physics.airDrag.vertical` | Finite 0..1 | Highest-priority absolute retention. |
| `physics.fluidDrag.horizontal` | Finite 0..1 | Highest-priority absolute retention. |
| `physics.fluidDrag.vertical` | Finite 0..1 | Highest-priority absolute retention. |
| `physics.trackFallDistance` | Boolean | Highest-priority override. |
| `movement.accelerationMultiplier` | Finite 0..8 | Product, final cap 8. |
| `movement.jumpMultiplier` | Finite 0..8 | Product, final cap 8. |
| `movement.jumpEnabled` | Boolean | Any explicit false vetoes native ground jump. |
| `movement.swimLiftEnabled` | Boolean | Any explicit false vetoes native jump-held fluid lift. |
| `movement.sneakSlowdown` | Finite 0..1 | Highest-priority horizontal input factor; native default 0.3. |
| `movement.sneakEdgeGuard` | Boolean | Highest-priority override. |
| `movement.sneakBodyAdjustment` | Boolean | Highest-priority override; eligible local player only. |
| `damage.fallMultiplier` | Finite 0..8 | Product, final cap 8. |

Multiply against the current native baseline. Never accumulate changes into last tick's modified fields or
cache and later restore an old native value. Resolve products in lexical owner/key order, avoiding intermediate
overflow/underflow and applying capping only after the complete product. An explicit zero wins even after
large factors; report final capping. Absolute ties use lexical owner/key after descending priority. Native
defaults are not competing sources. Explicit true cannot defeat a jump/lift veto or invent a native jump.

Air drag overrides apply only in the eligible airborne land branch; grounded block slipperiness remains native.
Fluid drag applies in its native medium. Gravity scales the native downward contribution, not existing motion,
currents or external impulses. Jump scaling occurs once before native jump bookkeeping. Disabling jump skips
that consumption; a permitted zero-multiplier jump retains the agreed native bookkeeping.

Fall tracking is separate from fall damage. Disabling tracking clears accumulated descent before collision
and prevents accumulation for that step. Fall damage scales its cause-specific computed amount once, rounds
upward and preserves unrelated damage. All native consumers must retain their existing order and medium rules.

## 5. Native movement step

Prepare one step after input/controller or AI intent resolution and before jump, fluid lift, player sneak
body adjustment and heading motion. Player input arbitration remains separate from body physics. Existing
controller `manual` continues to mean input ownership; it does not become a second movement solver.

1. Validate source, owner, world and entity generations and the supported native movement path.
2. Snapshot eligible definitions, native intent, movement state and input observations.
3. Evaluate motion producers by priority and lexical owner/key; nil yields, the first valid table claims.
4. Resolve static rules plus the winning source's temporary replacements; prepare rotation and fall rebasing.
5. Prepare the prospective post-jump/pre-heading state and gather independent force contributions against it.
6. Commit the validated decision once, consume enabled native jump/fluid lift and apply forces before heading,
   then execute native or manual motion through native collision. Native jump/statistics are never replayed.
7. Publish fresh post-step observations and clear all temporary state in `finally`.

A command has required `mode="native"` or `mode="manual"`, optional complete `velocity`, optional partial
`rotation`, optional `fallDistance` and optional temporary `rules`. Velocity and force components are finite
and bounded to -16..16 blocks/tick; fall rebasing is finite 0..65535. Validate the whole command before mutation.
Temporary rules replace that source's static leaf for one step, rather than multiplying it twice.

Native mode runs the original eligible integration once. Manual mode skips native heading acceleration,
jump/fluid lift, gravity, drag, ground friction and ladder velocity adjustments, and calls native `moveEntity`
once with the chosen motion. Preserve native collision, block contacts, shared limb bookkeeping and player
statistics. Aging, portals, damage, drowning, fire and actual fluid identity/contact continue normally.
Manual mode grants no implicit fall immunity. No extra relocation or `moveBy` occurs inside a producer.

An unclaimed, unmodified entity follows the original native path. Script-selected displacement is preflighted
against loaded chunks and bounded collision work. Refuse unsafe requested displacement explicitly; do not
fall back to the same unsafe motion. Existing unmodified native movement retains its behavior.

## 6. Callback authority, input and lifecycle

Callbacks receive fresh read-only entity/world facades, a monotonic simulated tick, `dt=0.05`, an opaque target
generation, per-binding transient `state`, native intent and detached movement state. Local player input adds
logical axes, held actions and ordered routed edges; mobs use native/AI intent. Existing input captures,
rebinding and look ownership remain authoritative. Synthesized intent cannot invent physical press edges.
Screen/focus suspension reports unavailable neutral input; pausing produces no motion step or catch-up.

All eligible `onForces` callbacks inspect the same post-jump/pre-heading snapshot and contribute additive
`velocityDelta`, independent of the winning motion producer. Validate the aggregate once. If individually
valid contributions exceed aggregate bounds, discard that force batch and report `limit_exceeded`.

A producer/force error disables the whole failed source for that binding, including its rules, and recomputes
the uncommitted step from remaining sources. An observer error disables only that observer. Do not retry broken
callbacks every tick. Immutable committed decisions survive contact callbacks; edits during collision affect
the next step. Unloaded owners receive no remaining callbacks.

Close, unload/reload, removal, death and world/target replacement release that source generation. Local sources
rebind before eligible movement on the replacement player with fresh binding state. Riding/sleeping suspend
independent locomotion. Attach/detach notification is best effort; cleanup never depends on Lua success.
Runtime state, modifier registrations and callbacks are not persisted into world/player saves.

Bound the runtime to 64 sources per entity, 256 local registrations and 4096 attached sources per world.
Cache unchanged static resolutions by source/binding revision. The no-source path retains native behavior
without Lua callbacks; motion and force callbacks run only for eligible simulated steps, never render frames.

## 7. Code boundaries and native integration audit

- `betamoon.entity.modifier`: immutable rule catalog/values, deterministic resolution, owned source runtime,
  binding lifecycle and detached diagnostics. No Lua parsing or client singleton access here.
- Existing `betamoon.entity.motion`: step preparation, arbitration, force batching and native-step coordination.
- Existing `betamoon.luaapi.entity`: strict declaration readers and scoped public entity facade extensions.
- Existing `betamoon.luaapi.player`: local-player registration convenience and automatic binding.
- Existing client input package: logical/processed intent and ordered edges; preserve current controller behavior.
- Existing instrumentation framework: mapped, verified native seams with exact matching and idempotence.

Inspect `EntityPlayerSP.onLivingUpdate`, `MovementInputFromOptions.updatePlayerMoveState`,
`EntityLiving.onLivingUpdate`, `EntityLiving.moveEntityWithHeading`, `EntityLiving.jump`, `Entity.moveEntity`,
`Entity.updateFallState`, `EntityLiving.fall` and the player statistics wrapper. Audit subclass overrides before
advertising each target. Include BetaMoon fluid/lifecycle hooks in ordering checks.

The implementation adds exact mapped field-write/operand transformations to the existing around-method,
call-redirect and field-read-redirect definitions. Match the assigned native field, arithmetic opcode and exact
consumer count, rather than global constants or instruction indexes. Normal completion and exceptional cleanup
use distinct handlers. Java 7+ targets recompute verification frames from resource metadata without defining
classes during transformation. Named and obfuscated fixtures verify exact matches, idempotence and JVM behavior.
Missing hooks prevent registration rather than creating ineffective sources.

## 8. Implementation milestones

| Milestone | Deliverable | Exit gate |
| --- | --- | --- |
| M1 | Headless rule catalog, immutable values, strict separate Lua rule reader, deterministic bounded composition | Numerical edge cases, priority/ties, vetoes, omission/zero/false, atomic patching and removal reveal current baselines. |
| M2 | Owned sources, generation-aware handles and target/local binding lifecycle | Ownership, source limits, reversible enablement, retry, cleanup and snapshots without retained callback handles. |
| M3 | Verified native consumers for rule groups and initial supported living targets | Neutral native parity, exact ordering and named/obfuscated transformation fixtures; reject missing capabilities. |
| M4 | Usable public rule API with canonical definitions and wiki | End-to-end scripted rules work; addon sync/check/tests/LuaLS, wiki coverage/links/snippets, full Java/package gate. |
| M5 | Motion producers, shared input/AI intent, additive forces and post-step observations | One native integration, manual native collisions, arbitration/failure isolation, immutable commits and bounded loaded movement. |
| M6 | Public motion API and 0.11 acceptance | Lua flight scenario, player/mob coverage, lifecycle matrix, packaged runtime and complete synchronized documentation. |

M1 is internal preparation. It adds no public method and cannot be advertised as working gravity/flight support.
Each later milestone must honor the complete contract it exposes. Update the canonical addon reference and wiki
when a usable public surface lands. Keep engineering notes in this repository. Do not write/rename distributed
example scripts before presenting the proposed additions and full ordering to the user.

## 9. Verification and completion criteria

Rule tests must cover final-only capping, zero after large factors, subnormal factors recovering before final
rounding, registration-order independence, absolute priority/ties, boolean veto, changing native baselines,
removal/disable reveal, duplicates, limits, wrong types, non-finite numbers and atomic invalid updates.

Native parity covers no sources, neutral rules, yielding producers and removed sources across land, ground,
jump, sneak, ladders, water/lava/custom fluids, collision, statistics and save/load. Exercise vanilla walking
mobs and native-lifecycle BetaMoon living entities while preserving AI intent. Verify explicit refusal for
unsupported subclasses/lifecycles. Compare named and packaged obfuscated behavior.

Motion tests cover walls/floors/ceilings/steps, existing impulses, force aggregation, support/ground flags,
fall rebasing/tracking/damage, input captures/rebinding/edges, focus and screens, riding/sleeping, source ties,
Lua faults, observer isolation, owner unload, generation replacement, death/respawn and dimension changes.

Completion requires a Lua-authored toggleable flight acceptance scenario using the general APIs: hover,
horizontal movement, ascent/descent, collisions, deliberate fall handling and normal movement restoration
after disable/unload/error/death/world change. Record automated results and live gameplay acceptance separately.
Run the full Java check/build, relevant offscreen tests, RetroMCP packaging/agent verification, addon gates and
wiki validation on the final integrated feature tree before merge.

## 10. Development record

- 2026-10-22: proposal written before implementation; matching feature branches created from integrated `dev`.
- M1 implemented: internal typed rule catalog, immutable rule sets and eligible source snapshots, deterministic
  bounded resolution with immutable provenance, and a separate strict Lua rule-group reader. Source snapshots
  represent already-eligible contributions; enablement, owner generations and binding lifecycle belong to M2.
- `EntityRuleFoundationTest` covers rule ranges/types, nested field diagnostics, immutable/atomic patches,
  final-only capping, subnormal recovery, registration-order parity, absolute priority/ties, false vetoes,
  current native baselines, source identity/capacity and comparison with an exact BigDecimal product oracle.
  An isolated classloader resolves real contributions while rejecting Minecraft and Lua classes.
- Initial focused verification: `gradlew entityRuleFoundationTest --offline --console=plain` passed. The task
  is registered under `check` in the existing ignored local `build.gradle`.
- Full M1 gate: `gradlew build agentSmokeTest --offline --continue --console=plain` passed, including the new
  foundation test, existing integration suites, RetroMCP packaging and packaged Java 8 agent verification:
  110 tasks, 108 executed and two up-to-date, in 1m35s. Proposal links and its Lua snippet syntax passed.
  Evidence: `build/flight-feasibility-audit/foundation-full.log` and `rule-foundation-final.log`.
- M2-M6 implemented: owned source generations and bounded target/local bindings; atomic handle updates and
  recovery; native rule consumers; public declarations and diagnostics; one prepared/committed living step;
  prioritized producers, independent forces, read-only callbacks and post-collision observations.
- Source failures remove the whole source before commit; observer failure is isolated. Replacing/clearing the
  specifically failed callback or explicit retry recovers it. Contact-time edits preserve committed decisions;
  closed/unloaded owners receive no later callback. Cleanup uses detached snapshots and does not read old worlds.
- Force callbacks read the prospective committed numbers from `ctx.movement`, including jump, selected rotation
  and fall rebasing. Live facade queries inspect the uncommitted body. Native bookkeeping runs once only after
  preparation succeeds. Remaining force callbacks may be reevaluated when another source fails before commit.
- Runtime self-review fixed unsupported lifecycle/remote/no-clip rule leakage, stale detach world reads,
  observer publication after native exceptions, and modern obfuscated class verification frames. Static rules
  use revision-cached resolutions; class movement audits are cached without caching mutable lifecycle eligibility.
- `EntityMotionApiTest` runs actual transformed native bodies and the public Lua facade. Coverage includes
  native/neutral/removed-source parity on airborne land, ground, water, lava and ladders; non-default drag and
  fluid lift; jump veto/scaling/statistics; producer order and temporary replacements; force bounds/faults;
  observers; atomic invalid updates; ownership/read-only/thread checks; native save isolation; death cleanup;
  source limits (64/256/4096); BetaMoon native-lifecycle AI intent; explicit unsupported targets; walls, floors,
  ceilings, slab stepping and loaded-only refusal; contact-time edits/unload and exceptional cleanup.
- `EntityPlayerFlightTest` is a test fixture, not a new distributed example. Its Lua source exercises hover,
  logical movement, ascent/descent, sneak controls, captured/rebound input, ordered edges, focus/screens,
  deliberate fall handling, native restoration after yield/disable/unload/error, fresh respawn/world bindings,
  unsupported remote environment and look ownership. The wiki documents the API with short snippets;
  the full flight scenario remains in the automated fixture rather than the API reference.
- `PackagedEntityMotionTest` remaps these same fixtures and runs them in a separate Java 8 JVM containing the
  shipped mod/agent and recompiled obfuscated client, without named game classes or a vanilla-class fallback.
  The vanilla client supplies only non-class resources. Both named and packaged movement scenarios pass.
- Full verification: `gradlew build agentSmokeTest --offline --continue --console=plain` passed **112 tasks**
  (110 executed, two up-to-date) in 1m44s. Final additional coverage and packaged replay passed in
  `motion-final-native.log`; GUI offscreen acceptance passed 800 ticks, five selectors and five rendered pages
  in `motion-review.log`. Model rendering and atmosphere OpenGL restoration also passed in `motion-render.log`.
  Final native/instrumentation/packaged/agent replay passed 19 tasks in 31s in `motion-final-native.log`.
  Java evidence is under `build/flight-feasibility-audit/`.
- Addon: canonical reference and split definitions synchronized, 19 Node tests passed, VSIX rebuilt, and real
  packaged LuaLS diagnostics/completion/hover checks passed. Wiki: 275 tables / 1,979 rows, 1,128 local links
  and 350 Lua snippets passed. Final link validation is recorded in `motion-wiki.log` in the same evidence
  directory. The movement-specific coverage check covers all 14 leaves, four entry points,
  eight handle methods and five callbacks.
- Interactive acceptance: no new human gameplay/flight playthrough is claimed. The user's earlier completed
  0.11 playthrough remains baseline acceptance. Automated native and packaged flight acceptance is complete.
- Documentation correction (2026-10-22): removed the full flight implementation from the wiki after user
  feedback. A complete gameplay example belongs in a separate script package; no standalone package has yet
  been created. API behavior and automated flight coverage are unchanged. Movement API coverage, 275 wiki
  tables, 1,128 local links and 349 Lua snippets pass after removal (`motion-wiki-concise.log`).
