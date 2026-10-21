# Position arguments across Lua APIs

Status: active pre-1.0 proposal, 2026-10-21. This document defines a proposed compatibility contract; no Java or Lua API change is implemented here.

## Goal

An author should be able to pass one position or vector table wherever a public operation currently consumes separate X/Y/Z numbers. Keep the numeric form. The objects returned by `betamoon.positions:integer(x, y, z)` and `betamoon.positions:float(x, y, z)` are already Lua tables with `x`, `y` and `z`; ordinary tables with those fields should work identically.

```lua
local blockPos = betamoon.positions:integer(10, 64, 20)
world:setBlock(blockPos, block, 0)
world:setBlock(10, 64, 20, block, 0) -- existing form

local impulse = betamoon.positions:float(0.2, 0.4, 0)
entity:addVelocity(impulse)
entity:addVelocity(0.2, 0.4, 0) -- existing form
```

The numeric calls already exist; their position-table counterparts are proposed here.

## Proposed call contract

1. At an API's coordinate slot, three separate numbers mean the existing numeric form. One table means the position form. The table must provide named `x`, `y`, `z` numeric fields; array-only `{10, 64, 20}` is not a position. No implicit `unpack`, string conversion or coordinate rounding.
2. Parse the table's visible fields even when its Java type is `PositionI` or `PositionF`. The objects are mutable Lua tables today; a changed visible field must be validated as the value the caller supplied. Do not read hidden constructor fields instead.
3. A block-cell API requires exact integral X/Y/Z after numeric conversion, with its current X/Z bounds and Y range. A `PositionF` is accepted there only if all three visible components are integral. `1.5` is an error, never silently truncated or rounded. Finite float-world positions remain available to motion, teleport, audio, particles and similar APIs according to each operation's existing limits.
4. Keep domain limits and error paths local to the operation. The shared reader determines the shape and returns three checked components; the caller applies world bounds, movement magnitude, loaded-chunk rules, authority, and any other existing validation. Errors should identify `operation.position.x` (or the matching numeric argument path) so malformed input is actionable.
5. `:` and `.` calls must each skip their receiver exactly once. An object table in an unrelated argument slot, such as a block or options value, must not be mistaken for a position.
6. Trailing operands shift only for the table form: `setBlock(position, block, damage)` maps to `setBlock(x, y, z, block, damage)`. Optional operands preserve their current defaults. Ambiguous shapes fail rather than selecting an overload from a later operand's type.
7. Existing APIs that already accept a table retain their table-specific behavior. Do not change the meaning of a chunk-coordinate `x,z` pair, a six-component box, or a model-space vector simply because all contain coordinates.

The recommended default for newly added position-table overloads is to reject unknown fields, catching spelling mistakes. Existing table-accepting APIs keep their current extra-field policy until individually reviewed. This is a policy parameter on the reader, not two competing parsers.

## Shared reader

Add a small `LuaPositionArguments` reader in `betamoon.luaapi.utils`, separate from declaration parsing and Lua context access. It should accept `Varargs`, an optional receiver identity, the first coordinate index, the operation's two- or three-axis layout, a field path, and an integer/finite-number policy. Return checked components plus the number of consumed arguments. For a two-axis operation that accepts a full position, `y` is an explicitly permitted ignored field. The caller then reads trailing arguments at the returned next index.

Use `LuaDeclarationValues.number` and exact integer validation, or equivalent shared helpers, to reject `NaN`, infinity, fractions in cell coordinates and out-of-range Java integer conversion. Keep `PositionI`/`PositionF` construction separate from argument reading; neither position class should become a mutable global cache or a native-world handle.

Do not install an implicit Lua metatable conversion. A detached `{x, y, z}` result from another API remains portable as long as the target operation permits that coordinate domain.

## Inventory and rollout

| Existing surface | Proposed treatment |
| --- | --- |
| World block reads/writes and block callbacks (`getBlock`, `setBlock`, light, power and related operations) | Add a three-component position form, preserving loaded-chunk and Y checks. Audit callback-relative defaults separately. |
| World data, capability and multiblock lookups | Consolidate current mixed numeric/table parsing through the shared reader without changing their saved keys or lookup semantics. |
| Two-axis biome, height and region queries | Accept a named `x,z` table or full `{x,y,z}` position where useful; ignore `y` only for operations whose current contract has no Y. Keep `getChunk` coordinates in chunk units. |
| Entity movement/velocity and teleport locations | Add the three-component form while retaining finite, motion-size, destination and safety checks. `setTransform` already accepts a declaration table; do not add an overlapping positional overload there. |
| Worldgen placement, feature and spatial queries | Add only where a runtime call has a separate numeric coordinate group. Preserve deterministic generation, bounded query and coordinate-unit rules. |
| Audio, particles, model poses and appearance declarations | Audit existing position/vector table forms first; reuse validation where appropriate. Do not replace declarative `position` fields or pose-specific bounds with world-cell rules. |

Before coding, inventory every public signature in `../BetaMoon-VSC-Lua-Addon/internal/api.lua` and the wiki, tagging the coordinate slot, units, bounds, optional tails, receiver convention and current table behavior. Convert related API families in small batches, then update the canonical Lua definitions, run `npm run sync-api` and `npm run check-api`, addon tests and relevant LuaLS checks. Update affected wiki signatures/examples only when the APIs actually change.

## Verification gates

- Numeric and table forms produce the same result for equivalent valid inputs, in both `:` and supported `.` calls.
- `PositionI`, `PositionF` and plain named tables work in eligible APIs. Mutating a visible component changes the input and still passes exact validation.
- Fractional block coordinates, non-finite components, missing or mistyped fields, extra fields under a strict policy and ambiguous trailing arguments fail without mutation.
- Native block Y limits, X/Z bounds, chunk units, loaded-area behavior and callback authority are unchanged.
- Public LuaLS overloads and wiki examples match the implemented methods; old numeric examples continue to run.

## Decisions to settle before implementation

- Confirm whether newly added table overloads reject every extra field or allow unrelated fields from larger snapshots. Strict named triples are recommended; current table-accepting APIs remain compatible.
- Confirm which X/Z-only APIs should accept a full three-component position, and document explicitly when Y is ignored. Chunk units must remain distinguishable from block units.
- Decide whether `betamoon.positions:integer` itself needs stricter exact-integer construction. The shared reader will enforce exactness at cell APIs regardless, but constructor behavior should not invite a silently truncated value.
