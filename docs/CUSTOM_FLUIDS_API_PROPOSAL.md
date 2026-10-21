# Custom fluids API audit and proposal

Status: accepted design with an implementation in progress on
`feature/0.8.0`. The initial registry, custom block simulation,
identity-aware rendering, buckets, Lua creation/lookup, and custom interaction
rules are implemented. Vanilla lava-water hardening now uses a permanent shared
interaction rule when the instrumentation bridge is active. Entity/camera
behavior, ecology hooks, custom materials, and the remaining hard-coded vanilla
seams stay in the later phases described below.

Audited against BetaMoon `feature/input-controller-camera-stack` at `53f172a`
and the bundled remapped Minecraft Beta 1.7.3 and Forge sources on 2026-10-01.

## 1. Executive decision

BetaMoon should add a first-class fluid registry without turning that registry
into a second block or item framework.

- `betamoon.fluids` owns fluid identity, simulation rules, appearance shared by
  the two block roles, container relationships, and cross-fluid interactions.
- `betamoon.blocks:add` remains the only public path which creates the flowing
  and still native blocks.
- `betamoon.items:add` remains the only public path which creates a filled
  bucket item.
- `betamoon.fluids:add` is a convenience transaction. It validates a complete
  declaration and delegates its nested block and item declarations to those
  existing domain APIs.
- Advanced authors may define a fluid first, explicitly add the associated
  blocks and bucket through the block and item APIs, and then bind the resulting
  references.
- Vanilla water and lava should be represented by read-only fluid references so
  that lookup, matching, and interaction rules do not need a second vanilla-only
  vocabulary.

The first implementation should not introduce public custom materials. A custom
fluid must temporarily select either the existing `water` or `lava` material:

```lua
material = betamoon.mc.blockMaterials.water
```

or:

```lua
material = betamoon.mc.blockMaterials.lava
```

This material is a compatibility carrier, not the fluid's identity. All new
BetaMoon-controlled flow, rendering, mixing, and bucket logic must compare the
registered fluid identity or its block IDs rather than comparing
`Material.water` or `Material.lava`. This makes the temporary approach usable and
leaves a direct migration path to custom material references later.

The material compromise is not complete isolation. Unmodified vanilla code may
still classify a water-backed acid as water or a lava-backed oil as lava. The
first release must document that leakage precisely, patch the user-visible and
destructive cases in scope, and never imply that the selected carrier creates a
new material.

## 2. Scope

The complete feature proposed here covers:

1. registered fluid identities and read-only vanilla fluid references;
2. a paired flowing/still block implementation with explicit pairing;
3. Beta 1.7.3-compatible level metadata and deterministic flow;
4. still and flowing textures, tint, render pass, surface height, and flow
   direction;
5. optional filled buckets and vanilla empty-bucket integration;
6. safe displacement and deterministic fluid mixing rules;
7. declarative entity contact, movement, breathing, fire, and camera behavior;
8. explicit environmental compatibility rather than one ambiguous
   `waterLike` switch;
9. reload, persistence, validation, diagnostics, and query behavior;
10. integration with BetaMoon's generic fluid checks in world generation and
    terrain adaptation.

The implementation may be delivered in independently reviewed increments, but
the public contracts should be designed together so early declarations do not
need to be rewritten later.

## 3. Non-goals

The first custom-fluid project should not include:

- a public custom material creation API;
- arbitrary Lua code for every cell considered by the flow algorithm;
- arbitrary Lua code per rendered fluid face or render frame;
- finite-volume or pressure simulation;
- pipes, tanks, fractional container amounts, or a general fluid storage API;
- mixing multiple fluids within one block coordinate;
- arbitrary fluid level counts beyond the native four-bit metadata model;
- replacing Minecraft's entire movement or pathfinding engine;
- dedicated-server delivery or multiplayer synchronization beyond preserving a
  deterministic, server-authoritative design for that later work;
- world-save ID remapping, which remains a concern of the broader content
  identity and registry work;
- animated fluid sprites as a prerequisite for the first usable static-texture
  release.

A future general container system should build on fluid references rather than
stretching bucket fields into tanks, bottles, pipes, or multi-unit storage.

## 4. Terminology

- **Fluid definition** is the validated, keyed declaration of one logical fluid.
- **Fluid reference** is the read-only Lua handle returned by the registry.
- **Carrier material** is the temporary native `water` or `lava` material used by
  both custom block roles until custom materials exist.
- **Flowing block** is the scheduled native block which updates level and spreads.
- **Still block** is the unscheduled or mostly idle native block which wakes when
  a relevant neighbor changes.
- **Source** is a fluid cell with metadata `0`. A source may temporarily be held
  by either block role while an update is pending.
- **Horizontal level** is metadata `1..7`.
- **Falling level** is metadata `8..15`; the high bit describes a falling column
  while the low level information remains part of native-compatible state.
- **Fluid state** is one placed cell's fluid reference, native block role,
  metadata-derived level, source flag, falling flag, and rendered height.
- **Preset** is a copied set of initial defaults. It is not a runtime superclass
  and does not define identity.
- **Mixing rule** is one deterministic rule for contact between two registered
  fluids.

The public API should consistently use `flowing` and `still` for the two native
block roles. It must reserve `source` for metadata-0 cells.

## 5. Minecraft Beta 1.7.3 audit

### 5.1 Native block identities

`Block` constructs four fluid blocks:

| Fluid | Flowing block | Still block | Material |
| --- | ---: | ---: | --- |
| Water | 8 | 9 | `Material.water` |
| Lava | 10 | 11 | `Material.lava` |

`BlockFlowing` changes itself to `blockID + 1` when it settles.
`BlockStationary` changes itself to `blockID - 1` when a neighbor wakes it. Those
arithmetic assumptions happen to fit the vanilla IDs but are unsuitable for a
registry in a scarce, user-assigned block-ID space.

Custom pairs must store explicit references to each other. Their IDs need not be
adjacent and their declaration order must not carry meaning.

### 5.2 Metadata and surface height

The native fluid renderer and flow algorithm interpret metadata as follows:

- `0`: full source;
- `1..7`: decreasing horizontal levels;
- `8..15`: falling cells;
- a falling value is treated as a full-height contributor in several flow and
  render calculations.

`BlockFluid.getPercentAir` converts metadata into the visible surface height.
The renderer samples four neighboring corners, weights source and falling cells,
and produces a sloped top surface.

All sixteen metadata values must therefore be reserved by a fluid block. Fluid
blocks cannot use ordinary BetaMoon block state, directional metadata, tile data,
or variant selection based on author-owned metadata. Fluid-aware contexts should
provide named state fields instead of making scripts reinterpret bits.

### 5.3 Flow algorithm

`BlockFlowing.updateTick` performs these operations:

1. Inspect the four horizontal neighbors and the cell above to calculate the next
   decay value.
2. For water, create a source when at least two adjacent sources exist and the
   cell has appropriate support.
3. For overworld lava, use a level step of two. Lava in the Nether uses the
   water-like step of one.
4. Randomly delay some lava retreat updates.
5. Remove a cell whose next level is invalid, update its metadata, or settle it
   into the still block.
6. Prefer flowing downward.
7. If downward flow is blocked, recursively search horizontal paths for the
   nearest drop, with a native search depth of four.
8. Enter every equally optimal direction.

Native water ticks every 5 ticks and native lava every 30 ticks. The algorithm
uses the block material to decide most of these differences. A generalized
implementation should move them into the fluid definition.

### 5.4 Displacement and settling

Vanilla fluid flow treats doors, signs, ladders, and reeds as explicit blockers,
then relies primarily on material solidity for other blocks. Water normally drops
the displaced block; lava destroys it and plays its mix effect. Fluids with the
same material are treated as one fluid, and lava receives special protection from
displacement.

That behavior is unsafe for multiple custom fluids sharing a carrier material.
BetaMoon needs registry-aware same-fluid checks, an explicit cross-fluid default,
and one bounded displacement policy. Tile entities must be protected by default.

### 5.5 Stationary lava behavior

`BlockStationary` keeps lava ticking and may place fire near flammable material.
This is separate from flow and should be an explicit environmental behavior. A
lava carrier must not automatically force a custom fluid to create fire through
the new block implementation.

### 5.6 Rendering

`BlockFluid.getRenderType()` returns native type 4. `RenderBlocks` then:

- culls faces by material identity;
- calculates corner heights from neighboring cells with the same material;
- chooses still or flowing texture coordinates;
- rotates the top flowing texture from the horizontal flow vector;
- applies block tint and brightness;
- draws the fluid in its block-selected render pass.

The flow-angle helper, `BlockFluid.func_293_a`, only initializes its vector for
`Material.water` or `Material.lava` and always asks the vanilla moving block to
calculate it. It cannot correctly render a separate custom fluid. Reusing type 4
unchanged would also make every custom water-backed fluid visually connect to
vanilla water and every other water-backed fluid.

Custom fluid blocks therefore need a dedicated BetaMoon render type routed
through `mod_BetaMoon.RenderWorldBlock`. The renderer may reuse the native mesh
mathematics, but neighbor matching and flow-vector lookup must use
`FluidRegistry`. Vanilla water and lava continue using native render type 4.

### 5.7 Textures

Vanilla `BlockFluid` assumes hard-coded atlas locations and that its side/flowing
texture is adjacent to its top/still texture. BetaMoon's `AtlasTextures` instead
allocates independent static atlas slots. A custom block must return two explicit
indices and must not assume adjacency.

The BetaMoon asset path supports static and animated textures with hot reload.
Fluid appearances can use independent still and flowing texture assets with
correct refresh behavior. Frame animation uses the shared managed atlas slot
and texture input described in the
[general animated textures proposal](ANIMATED_TEXTURES_API_PROPOSAL.md), not
flow simulation.

### 5.8 Entity movement and contact

`World.handleMaterialAcceleration` can calculate a fluid current for any supplied
material, but it applies a hard-coded strength of `0.014`. Entity callers provide
only `Material.water` or `Material.lava`:

- `Entity.handleWaterMovement` updates water state and current acceleration;
- `Entity.handleLavaMovement` checks a lava material volume;
- `EntityLiving` applies different water and lava movement drag;
- drowning tests only `Material.water` at the entity's eyes;
- player mining penalties and movement statistics use water checks;
- wetness, splashes, extinguishing, and fire paths use water/lava-specific state.

Block collision callbacks are still reached for non-solid fluid blocks, but a
callback alone cannot provide complete swimming, breathing, current, fog, or HUD
semantics. A fluid runtime must aggregate contact by registered block ID and feed
declarative behavior into narrow entity hooks.

### 5.9 Camera and HUD

`EntityRenderer` hard-codes water and lava fog colors and densities.
`ItemRenderer` draws the water overlay only for `Material.water`, and `GuiIngame`
draws the air meter only while the player's eyes are in water. These should
eventually consume one resolved eye-fluid state and its declarative camera and
breathing policies.

Client rendering must never run a Lua callback per frame for this purpose. The
tick path should publish an immutable camera-fluid snapshot.

### 5.10 Buckets

`ItemBucket` stores an integer describing its contents:

- `0`: empty;
- a positive block ID: filled with that block;
- `-1`: milk.

An empty bucket ray-traces liquids, calls `MinecraftForge.fillCustomBucket`, then
falls back to material checks for vanilla water or lava. Vanilla collection only
accepts metadata `0`. A filled bucket offsets the target by the selected face,
checks that the target is air or non-solid, places its stored moving block with
metadata `0`, and returns the empty bucket. Water evaporates in the Nether through
an item-specific special case.

Forge's bucket handler collection has no unregister operation. BetaMoon should
install one process-lifetime handler and have that handler consult the reloadable
fluid registry. Registering one handler per Lua declaration would leak duplicates
across reloads.

The custom handler must recognize every registered custom source before vanilla's
material fallback. Otherwise a water-backed custom source could incorrectly
produce a vanilla water bucket.

### 5.11 Other hard-coded water and lava behavior

The audit found direct checks in, among other places:

- `BlockSand` for falling through liquids;
- `BlockFarmland` for hydration;
- `BlockReed` for valid water-adjacent support;
- `BlockSponge` for absorption;
- `EntityBoat`, `EntitySquid`, and fishing behavior;
- `Pathfinder` and aquatic spawning;
- entity burning-volume checks;
- clay and lake generation;
- player and renderer behavior;
- world generation and biome logic.

Not every check should automatically accept every custom fluid. The API needs
separate compatibility traits and a declared support matrix.

## 6. Current BetaMoon audit

### 6.1 Block registration

`betamoon.blocks:add` currently parses a `BlockDeclaration`, creates or retains a
`BlockWrapper`, applies ordinary properties and textures, registers it through
`ModLoader.RegisterBlock`, and installs tick, component, model, and callback
state. A material is required and the existing material resolver already exposes
`water` and `lava`.

An ordinary `BlockWrapper` is not a fluid block. It lacks the reserved metadata
contract, scheduled flow behavior, non-collision geometry, fluid renderer, pair
transition, and current vector. The block API therefore needs a specialized
`type = "fluid"` declaration path, analogous in principle to the item API's
specialized native types, while preserving the common validation and registration
pipeline.

`ModLoader.RegisterBlock` creates an `ItemBlock` when the numeric item slot is
empty. A fluid block should default to no user-facing inventory form. The
specialized registration path must deliberately decide whether to remove or hide
that generated form rather than accidentally exposing placeable still and flowing
blocks. An advanced `inventoryItem` opt-in may be added later if it can be made
safe and clearly distinguished from a bucket.

### 6.2 Item registration

`betamoon.items:add` already selects native implementations for food, tools, and
armor while keeping parsing and registration in the item package. A filled fluid
bucket should follow the same pattern through `type = "fluid_bucket"` and a native
`FluidBucketItem` implementation.

An ordinary `ItemWrapper` use callback could imitate some bucket behavior, but it
would duplicate ray tracing, permission checks, placement rules, container items,
and Forge interoperability in Lua. The native specialized type is the safer and
more compatible contract.

### 6.3 Content identity

The central content-key foundation supports typed identities such as
`namespace:type/name`, but `ContentType` does not yet contain `FLUID` or
`FLUID_INTERACTION`. The fluid implementation should add those known types and use
`ContentKey` internally from its first commit.

Recommended canonical keys are:

- `example:fluid/acid`;
- `example:fluid_interaction/acid_water`;
- `minecraft:fluid/water`;
- `minecraft:fluid/lava`.

The short typed-parser form may accept `example:acid` at an API boundary, but
references and diagnostics should expose the canonical key.

### 6.4 Assets

`AtlasTextures` gives custom blocks stable static terrain-atlas slots and refreshes
their pixels when the backing asset changes. It is sufficient for static still and
flowing textures and should be reused. Fluid registration must allocate each
texture independently and keep the returned indices in client-side appearance
state rather than in common simulation definitions.

### 6.5 Instrumentation

BetaMoon already has mapped, verified around-method and call-redirect
instrumentation with runtime diagnostics. Fluid-related patches should use the
same hook registry and fail before accepting a dependent public declaration when
a required hook is absent.

The fluid core should nevertheless prefer normal subclassing and ModLoader render
callbacks where those provide a complete boundary. Instrumentation belongs at
hard-coded vanilla classification points, not inside every ordinary block or item
operation.

### 6.6 World-generation and terrain code

Several BetaMoon helpers currently define a fluid as exactly
`Material.water || Material.lava`. That happens to include the proposed carrier
materials, but it would preserve the wrong abstraction and obstruct custom
materials later.

Generic checks such as `any_fluid`, terrain replacement safety, fluid surfaces,
and natural-spawn collision should move to a shared registry-aware helper. Exact
`water` and `lava` conditions should continue to mean those exact vanilla fluids
unless the API explicitly accepts a fluid reference or compatibility trait.

## 7. Design principles

### 7.1 Fluid identity is not material identity

One `FluidDefinition` owns exactly two native block IDs. Registry lookup by either
ID returns the same fluid and its role. Same-fluid tests compare that definition,
not the two blocks' carrier material.

The selected `water` or `lava` carrier exists only because native Minecraft
requires a material and custom materials are deferred.

### 7.2 Blocks and items retain domain ownership

The fluid package may create specialized declaration objects and coordinate their
binding. It must not duplicate block ID checks, retained native identity, item
properties, texture resolution, callback installation, or content ownership.

The shorthand should call the same internal block and item registration entry
points used by direct `blocks:add` and `items:add` declarations.

### 7.3 The simulation is declarative and Java-owned

Flow can touch many cells and run while chunks are loading or no player is nearby.
Its hot loop must not call Lua for path searches, decay calculations, or every
neighbor. Immutable validated Java definitions make behavior deterministic,
bounded, and suitable for later dedicated-server execution.

Lua callbacks may be considered at bounded semantic events, such as one aggregated
entity contact or a successful bucket action, but common effects should be
declarative first.

### 7.4 Simple and advanced paths compile to one model

The convenience declaration and explicit registration path must produce the same
`FluidDefinition`, specialized blocks, bucket item, and registry links. No behavior
may exist only in the shorthand.

### 7.5 Structural identity survives reload

Native block and item objects are embedded in registries and saved worlds. Reload
may replace validated behavior and client appearance snapshots but must not replace
the objects or reinterpret their metadata.

### 7.6 Safe cross-fluid behavior is explicit

Different fluids block each other by default. Destructive replacement or
solidification happens only through a declared interaction rule. This avoids
update-order-dependent deletion and makes a new fluid safe before the author has
defined every possible pair.

## 8. Public registry API

The registry should match existing lookup conventions:

```lua
betamoon.fluids:get("example:fluid/acid")
betamoon.fluids:getRequired("example:fluid/acid")
betamoon.fluids:find { material = "water" }
betamoon.fluids:first { bucket = true }
betamoon.fluids:one { key = "example:fluid/acid" }
```

The creation operations are:

```lua
betamoon.fluids:define(definition)
betamoon.fluids:add(definition)
betamoon.fluids.interactions:add(definition)
```

`define` returns an unpublished fluid-definition token for the explicit advanced
path. It does not create blocks or items and is not visible through registry
lookup. `add` creates and binds the complete resource set through the shorthand.
A staged definition must be bound before the current registration phase finishes.
`bind` publishes the complete registration and returns its read-only fluid
reference.

## 9. Recommended simple declaration

```lua
local acid = betamoon.fluids:add {
    key = "example:fluid/acid",

    -- Temporary compatibility carrier. This is not a unique material.
    material = betamoon.mc.blockMaterials.water,
    preset = "water",

    blocks = {
        flowing = {
            id = 180,
            key = "example:block/acid_flowing"
        },
        still = {
            id = 181,
            key = "example:block/acid_still"
        }
    },

    bucket = {
        id = 5200,
        key = "example:item/acid_bucket",
        displayName = "Acid Bucket",
        texture = "items/acid_bucket.png"
    },

    appearance = {
        still = "blocks/acid_still.png",
        flowing = "blocks/acid_flowing.png",
        tint = 0x83D936,
        renderPass = "translucent",
        light = 0,
        lightOpacity = 3
    },

    flow = {
        tickRate = 8,
        levelStep = 1,
        slopeSearch = 4,
        sourceCreation = "never",
        retreatDelayChance = 0
    },

    displacement = {
        blocks = "drop"
    },

    entity = {
        movement = "water",
        currentScale = 0.6,
        drowning = false,
        countsAsWet = false,
        extinguishesFire = false,
        ignitesEntities = false,
        contactDamage = 2
    },

    camera = {
        fogColor = 0x29451B,
        fogDensity = 0.16,
        overlay = "textures/misc/acid_overlay.png"
    },

    environment = {
        fallingBlocksPassThrough = true,
        hydratesFarmland = false,
        supportsReeds = false,
        absorbedBySponge = false,
        boatsFloat = false,
        aquaticHabitat = false
    }
}
```

The returned reference should expose the generated resources:

```lua
acid.flowingBlock
acid.stillBlock
acid.bucketItem
```

The shorthand must preflight the fluid, both block declarations, the optional
bucket declaration, referenced assets, and all cross-field rules before it
allocates any native content. Native registries cannot be rolled back safely, so
validation after the first allocation is insufficient.

## 10. Advanced explicit declaration

```lua
local acidDefinition = betamoon.fluids:define {
    key = "example:fluid/acid",
    material = betamoon.mc.blockMaterials.water,
    preset = "water",

    appearance = {
        still = "blocks/acid_still.png",
        flowing = "blocks/acid_flowing.png",
        tint = 0x83D936
    },

    flow = {
        tickRate = 8,
        sourceCreation = "never"
    }
}

local flowingAcid = betamoon.blocks:add {
    id = 180,
    key = "example:block/acid_flowing",
    type = "fluid",
    fluid = acidDefinition,
    fluidRole = "flowing"
}

local stillAcid = betamoon.blocks:add {
    id = 181,
    key = "example:block/acid_still",
    type = "fluid",
    fluid = acidDefinition,
    fluidRole = "still"
}

local acidBucket = betamoon.items:add {
    id = 5200,
    key = "example:item/acid_bucket",
    displayName = "Acid Bucket",
    type = "fluid_bucket",
    fluid = acidDefinition,
    texture = "items/acid_bucket.png"
}

local acid = acidDefinition:bind {
    flowing = flowingAcid,
    still = stillAcid,
    bucket = acidBucket
}
```

`bind` finalizes structural relationships, publishes the fluid, and returns its
read-only fluid reference. It must verify that the supplied references were
created with the matching definition token and role rather than accepting
arbitrary ordinary blocks or items.

This path lets an advanced author use the full block and item APIs for compatible
callbacks and presentation fields. It does not permit fields which conflict with
fluid ownership.

## 11. Fluid declaration schema

### 11.1 Identity

| Field | Type | Requirement |
| --- | --- | --- |
| `key` | fluid content key | Required and package-owned. |
| `material` | block material | Required; only `water` or `lava` initially. |
| `preset` | `neutral`, `water`, or `lava` | Defaults to `neutral`. |
| `blocks` | shorthand block declarations | Required by `fluids:add`; forbidden by `define`. |
| `bucket` | shorthand item declaration or `false` | Optional; omitted means no bucket. |

The preset should copy defaults at parse time. Reloading BetaMoon with a changed
preset implementation must not silently alter an already compiled definition
without republishing it. Explicit fields always override preset values.

`neutral` means no renewable sources, no drowning, no burning, no water ecology,
and conservative cross-fluid blocking. It still requires a temporary water or lava
carrier and therefore cannot remove every native compatibility leak.

### 11.2 Specialized block fields

A direct block declaration uses:

| Field | Meaning |
| --- | --- |
| `type = "fluid"` | Selects the specialized native fluid block path. |
| `fluid` | Required unpublished fluid-definition token. |
| `fluidRole` | Required `flowing` or `still`. |
| `id`, `key`, `displayName` | Normal block identity fields. |

The fluid definition supplies material, textures, tint, light, light opacity,
collision behavior, render pass, drops, and tick behavior. The specialized block
declaration must reject conflicting fields rather than silently ignore them.

Initially incompatible block features are:

- `material` on the nested role declaration;
- `state` or metadata variants;
- placement rotation or directional metadata;
- tile, container, GUI, or redstone components;
- custom collision bounds, normal-cube, opaque, or climbable behavior;
- custom drops and harvesting;
- custom model appearances;
- author-owned `onTick` scheduling;
- an ordinary inventory item form.

Common callbacks such as bounded entity collision may be considered only after
their ordering relative to fluid behavior is specified. The first implementation
should prefer the fluid's shared declarative entity policy.

### 11.3 Specialized bucket fields

A direct item declaration uses:

| Field | Meaning |
| --- | --- |
| `type = "fluid_bucket"` | Selects native filled-bucket behavior. |
| `fluid` | Required unpublished fluid-definition token. |
| `id`, `key`, `displayName`, `texture`, `appearance` | Normal item presentation and identity fields. |

The registration fixes maximum stack size to one and installs the vanilla empty
bucket as the crafting remainder. It must reject conflicting `maxStackSize`,
ordinary projectile/use behavior, food/tool/armor kinds, and arbitrary remainder
policy.

An `emptyContainer` field should not be published until an empty-container item
type can invoke the same registry safely. The vanilla Forge hook is called by the
vanilla empty bucket, not by every arbitrary item. General containers remain a
later API instead of a nominal field which works only while emptying.

## 12. Flow policy

```lua
flow = {
    tickRate = 5,
    levelStep = 1,
    slopeSearch = 4,
    finite = false,
    sourceCreation = "two_adjacent",
    retreatDelayChance = 0,
    dimensions = {
        ["minecraft:dimension/hell"] = {
            tickRate = 5,
            levelStep = 1
        }
    }
}
```

### 12.1 Fields

| Field | Range or values | Meaning |
| --- | --- | --- |
| `tickRate` | integer `1..1200` | Ticks between scheduled updates. |
| `levelStep` | integer `1..7` | Level increase for horizontal spreading. |
| `slopeSearch` | integer `0..8` | Maximum recursive search depth for a drop. |
| `finite` | boolean | `true` prevents source renewal; `false` applies the two-adjacent-source rule. Defaults to `false` for water and `true` for neutral/lava. |
| `sourceCreation` | `never`, `two_adjacent` | Renewable-source rule. |
| `retreatDelayChance` | number `0..1` | Chance to keep the old level for one receding update. |
| `dimensions` | map of dimension keys to partial policies | Deterministic dimension-specific overrides. |

`finite` is a concise way to select the existing source-creation rule. If both
fields are supplied, they must agree; contradictory declarations are errors.
`finite = false` uses `sourceCreation = "two_adjacent"`, which requires two
adjacent sources and support below. It does not create sources from a single
source, and `finite = true` does not conserve total fluid volume while fluid
spreads. A later API may add a structured source rule or finite-volume
simulation without changing these source-renewal semantics.

`levelStep` intentionally exposes the native decay concept. A field called
`range` would be easier to read but could not describe falling resets, slopes, or
dimension overrides precisely. Documentation can publish the approximate level
distance produced by common values.

### 12.2 Fixed invariants

The first implementation should keep these rules fixed:

- downward flow has priority;
- horizontal spread uses four cardinal directions;
- every equally optimal direction is used;
- falling state uses the native metadata high range;
- an invalid level removes the fluid cell;
- a stable flowing cell becomes the explicit still block;
- a relevant neighbor change wakes a still cell;
- updates do not load unavailable chunks;
- one update has a strict bounded search and write count.

These invariants preserve predictable Beta 1.7.3 behavior and keep the initial
surface understandable. More exotic cellular automata belong in a later, separate
simulation facility.

## 13. Appearance policy

```lua
appearance = {
    still = "blocks/oil_still.png",
    flowing = "blocks/oil_flowing.png",
    tint = 0xE0A830,
    renderPass = "translucent",
    light = 0,
    lightOpacity = 3
}
```

| Field | Meaning |
| --- | --- |
| `still` | Texture used for a level top with no horizontal flow and for bottoms. |
| `flowing` | Texture used for sides and flow-rotated tops. |
| `tint` | RGB multiplier; defaults to white. |
| `renderPass` | `solid` or `translucent`; affects pass selection, not collision. |
| `light` | Emitted light `0..15`. |
| `lightOpacity` | Native light opacity `0..255`. |

Both textures are required for custom fluids. The implementation should accept the
same texture asset references as ordinary blocks and allocate them independently.

Animated appearances preserve the same `still` and `flowing` fields and use the shared texture input from the
[general animated textures proposal](ANIMATED_TEXTURES_API_PROPOSAL.md):

```lua
appearance = {
    still = {
        asset = "blocks/oil_still_frames.png",
        animation = { frameTicks = 2 }
    },
    flowing = {
        asset = "blocks/oil_flowing_frames.png",
        animation = { frameTicks = 2 }
    }
}
```

The common asset system validates frame layout, timing, and reload behavior.
The fluid registry only supplies the two block texture roles; it does not own a
separate animator. Static texture support must not pretend that a multi-frame
image is already animated.

## 14. Displacement policy

```lua
displacement = {
    blocks = "drop"
}
```

`blocks` supports:

- `drop`: replace eligible non-solid content and drop it;
- `destroy`: replace eligible content without a drop;
- `block`: flow only into air or cells already occupied by the same fluid.

Different fluids block each other unless an explicit interaction rule applies.
An `otherFluids` field should not be published while `block` would be its only
legal value. A future replacement mode would need a declared priority and
conservation semantics before it can be deterministic.

Eligibility must use the target block's actual solidity and replaceability rules,
plus hard safety checks. Bedrock, protected blocks, unreadable cells, and tile
entities cannot be displaced. Tile-entity protection is a fixed invariant, not a
boolean authors can disable.

## 15. Entity and contact policy

```lua
entity = {
    movement = "water",
    currentScale = 1,
    drowning = true,
    countsAsWet = true,
    extinguishesFire = true,
    ignitesEntities = false,
    contactDamage = 0,
    contactIntervalTicks = 10
}
```

### 15.1 Initial fields

| Field | Meaning |
| --- | --- |
| `movement` | `none`, `water`, or `lava` native-compatible movement profile. |
| `currentScale` | `0..8` multiplier for the vanilla current acceleration. |
| `drowning` | Whether living entities consume air while their eyes are inside. |
| `countsAsWet` | Whether contact satisfies BetaMoon-aware wetness checks. |
| `extinguishesFire` | Whether contact clears entity fire. |
| `ignitesEntities` | Whether body contact applies lava-style ignition. |
| `contactDamage` | Non-negative damage applied at a bounded interval. |
| `contactIntervalTicks` | Integer `5..200`; interval for aggregated damage from this fluid, default 10. |

`movement`, breathing, wetness, extinguishing, ignition, and damage remain
independent. This permits oil which swims like water but burns, gas-like liquid
which has no swimming profile, or acid which damages without setting entities on
fire.

The runtime should resolve body-fluid and eye-fluid contact separately. Where an
entity intersects more than one fluid, resolution must be deterministic: highest
covered eye fluid for breathing/camera, then greatest body intersection with a
stable block-coordinate tie-break for movement and contact.

Contact damage uses one timer per entity/fluid, reset on leaving contact, and
generic environmental damage with the fluid registration passed through a
single adapter. The default is once every 10 ticks after entering contact,
not one hit per occupied block. A future shared damage pipeline can consume
that registration without changing the Lua declaration.

### 15.2 Later numeric movement profile

After the native-compatible profiles are proven, `movement` may accept:

```lua
movement = {
    control = 0.02,
    horizontalDrag = 0.8,
    verticalDrag = 0.8,
    downwardAcceleration = 0.02,
    jumpImpulse = 0.04,
    collisionRiseImpulse = 0.3
}
```

The table should compile into Java values and use the same movement hook. It must
not call Lua while moving an entity.

## 16. Camera policy

```lua
camera = {
    fogColor = 0x102080,
    fogDensity = 0.1,
    overlay = "textures/misc/acid_overlay.png"
}
```

| Field | Meaning |
| --- | --- |
| `fogColor` | RGB fog color while the camera eye is inside this fluid. |
| `fogDensity` | Exponential fog density; zero disables fluid fog. |
| `overlay` | Optional full-screen warped overlay texture; `false` disables it. |

The renderer should resolve one eye fluid from the tick snapshot and apply its
camera policy. The overlay asset belongs to the client appearance snapshot and may
hot-reload. Missing client rendering hooks must cause fluid registration with
camera features to fail clearly rather than silently ignore those fields.

The air HUD follows `entity.drowning`, not the carrier material or the presence of
a water-style overlay.

## 17. Environmental compatibility

The public API should avoid `waterLike` and `lavaLike` booleans. Those names hide
which of many unrelated vanilla rules are enabled and would become more ambiguous
as integrations grow.

```lua
environment = {
    fallingBlocksPassThrough = true,
    hydratesFarmland = false,
    supportsReeds = false,
    absorbedBySponge = false,
    boatsFloat = false,
    aquaticHabitat = false,
    pathfinding = "blocked"
}
```

Recommended meanings are:

- `fallingBlocksPassThrough`: sand and gravel may fall through the fluid;
- `hydratesFarmland`: the fluid satisfies farmland moisture checks;
- `supportsReeds`: it satisfies the adjacent-fluid reed rule;
- `absorbedBySponge`: a sponge may remove it;
- `boatsFloat`: boat buoyancy treats it as navigable liquid;
- `aquaticHabitat`: water creatures may survive or spawn in it;
- `pathfinding`: `blocked`, `water`, or `lava` path classification.

Each integration requires a verified native seam. A parsed field must not be
published before its behavior exists. The implementation may stage these fields,
but documentation and Lua definitions must describe only the delivered subset.

The `water` and `lava` presets can populate explicit values. Presets remain
authoring conveniences; querying a definition returns the resolved traits rather
than a magical inheritance relationship.

## 18. Dimension behavior

Dimension-specific flow belongs under `flow.dimensions`. Bucket placement effects
belong under `bucketPlacement`:

```lua
bucketPlacement = {
    evaporatesIn = {
        "minecraft:dimension/hell"
    },
    evaporation = {
        sound = "random.fizz",
        particle = "largesmoke",
        particleCount = 8
    }
}
```

The water preset should reproduce vanilla Nether evaporation. The lava preset
should reproduce the native Nether flow-step difference. Carrier material alone
must not decide either behavior.

Dimension lookup should use stable dimension identities from the relevant future
dimension foundation. Until those keys exist publicly, the implementation may
support the known overworld and Nether constants internally without publishing an
unstable string vocabulary.

## 19. Fluid interaction API

Vanilla lava hardening is not a general material rule. It checks lava adjacent to
water, converts source lava to obsidian and sufficiently shallow flowing lava to
cobblestone, and plays fizz/smoke effects. A general interaction API should model
that behavior without callbacks.

```lua
betamoon.fluids.interactions:add {
    key = "example:fluid_interaction/acid_water",

    fluids = {
        acid,
        "minecraft:fluid/water"
    },

    replace = acid,
    faces = { "north", "south", "east", "west", "up" },

    outcomes = {
        {
            when = { source = true },
            replaceWith = "example:block/acid_crystal"
        },
        {
            when = { source = false },
            replaceWith = "example:block/acid_sludge"
        }
    },

    effects = {
        sound = "random.fizz",
        volume = 0.5,
        pitch = 2.6,
        particle = "largesmoke",
        particleCount = 8
    }
}
```

### 19.1 Rule semantics

- `fluids` contains exactly two distinct registered fluid references.
- `replace` identifies which one is transformed.
- `faces` defaults to all six faces and is normalized into a fixed bit set.
- `outcomes` is an ordered list and the first matching clause wins.
- `when.source` optionally matches metadata `0` or every non-source state.
- `when.falling` optionally distinguishes falling columns.
- `when.minLevel` and `when.maxLevel` optionally restrict the decoded level to
  `0..7`; source is level `0`, while falling remains independently available.
- `replaceWith` is a registered block reference or `false` to replace the cell
  with air. Omitting a matching outcome leaves the fluid unchanged.
- The first release accepts only non-fluid result blocks or air. Producing another
  fluid requires explicit output level and cascade semantics and should be added
  later rather than inferred from a flowing/still block reference.
- Effects are client-visible consequences of an already authoritative contact or
  world change; they do not decide the result. `effects.when` may be `contact` or
  `changed`, defaulting to `changed`.

Rules should trigger when a fluid cell is added, wakes, or attempts to flow into
the other fluid. They must not depend on which of the two receives a scheduled
tick first.

Overlapping rules for the same ordered replacement fluid, counterpart, face, and
state class should be rejected. A future priority system should be added only if a
real composition case requires it; initial ambiguity is better treated as an
authoring error.

Vanilla water/lava hardening should be represented as an internal read-only rule
using the same evaluator. Its outcomes are source lava to obsidian and non-falling
levels 1 through 4 to cobblestone; other lava states remain unchanged. The native
contact effect may still run for unchanged states. This provides one tested
behavior instead of a custom special case plus a public subsystem.

## 20. Bucket contract

### 20.1 Collection

The process-lifetime Forge handler should:

1. Read the block ID and metadata at the ray-traced coordinate.
2. Resolve the block through `FluidRegistry`.
3. Return `null` for an unknown block, a fluid without a bucket, or non-source
   metadata.
4. Verify that the registered bucket item is valid.
5. Remove the source with normal world notification.
6. Return one filled bucket stack.

Because Forge calls this before vanilla material fallback, every registered custom
source must be conclusively handled. Failure must not remove the block.

Pickup is source-only. A public `pickup` field should not be added while that is
the only legal behavior. Allowing partial levels would create a full bucket from
less than one source and introduce volume semantics the world simulation does not
otherwise have.

### 20.2 Placement

`FluidBucketItem` should reproduce native ray tracing and face offset behavior,
then:

1. verify player modification permission at both the selected and destination
   coordinates as appropriate;
2. resolve the configured dimension evaporation policy;
3. require air or an explicitly replaceable, non-protected destination;
4. refuse to replace a tile entity or another fluid unless an explicit bucket
   placement interaction supports it;
5. place the registered flowing block with metadata `0`;
6. schedule the normal fluid update;
7. return the vanilla empty bucket only after success.

Placement should use the flowing role even though metadata `0` represents a
source. The next flow tick can settle it naturally.

### 20.3 Crafting and fuel

A filled bucket uses the vanilla empty bucket as its native crafting remainder.
If a filled fluid bucket is a furnace fuel, that duration belongs in the existing
fuel API. The fluid API should not add a second burn-time field simply because
vanilla special-cases the lava bucket. A later general container API can introduce
custom empty items together with matching fill behavior.

## 21. References and state views

A fluid reference should expose read-only fields:

| Field | Meaning |
| --- | --- |
| `key` | Canonical fluid content key. |
| `owner` | Owning package or `minecraft`. |
| `isVanilla` | True for read-only water or lava. |
| `material` | Current carrier material reference/name. |
| `preset` | Declared preset name. |
| `flowingBlock` | Bound block reference. |
| `stillBlock` | Bound block reference. |
| `bucketItem` | Bound item reference or nil. |
| `exists` | Standard reference validity. |

Fluid block references should additionally expose:

```lua
block.fluid
block.fluidRole -- "flowing" or "still"
```

A fluid-aware world or callback context should expose a detached state view:

```lua
state.fluid
state.block
state.role
state.metadata
state.level
state.source
state.falling
state.height
```

The state view is read-only and expires with its context if it contains world
access. The initial fluid creation release does not need a new coordinate-taking
query API; world position argument unification is now a separate active pre-1.0
proposal in [Position arguments](POSITION_ARGUMENT_EXPANSION_PROPOSAL.md).

## 22. Presets

### 22.1 Neutral

Recommended neutral defaults:

- water-compatible level step and tick rate for a usable baseline;
- no renewable sources;
- no retreat delay;
- block other fluids;
- drop displaced eligible blocks;
- no movement profile, drowning, wetness, ignition, or damage;
- translucent rendering, no light;
- no farmland, reed, sponge, boat, aquatic, or pathfinding compatibility;
- falling blocks pass through.

The selected carrier may still leak native behavior outside implemented hooks.

### 22.2 Water

The water preset should reproduce:

- tick rate 5;
- level step 1;
- slope search 4;
- two-adjacent source creation;
- block drops on displacement;
- water movement and current scale 1;
- drowning and air HUD;
- wetness and extinguishing;
- water fog and overlay;
- farmland, reeds, sponge, boats, aquatic habitat, and water pathfinding as each
  integration becomes available;
- Nether bucket evaporation.

### 22.3 Lava

The lava preset should reproduce:

- tick rate 30 outside the Nether;
- level step 2 outside the Nether and 1 in the Nether;
- no source creation;
- native-like retreat delay;
- destructive displacement;
- lava movement;
- ignition, contact damage, light 15, dense fog, and no water overlay;
- stationary fire attempts if that feature is enabled;
- the internal water/lava hardening rule for vanilla lava only.

A custom fluid using the lava preset does not automatically gain vanilla lava's
hardening rule. Interaction rules remain keyed by fluid identity.

## 23. Temporary carrier-material contract

### 23.1 What the carrier provides

Using `material = water` or `material = lava` provides:

- native liquid/non-solid material flags;
- compatibility with code which asks only whether a material is liquid;
- a workable fallback for existing terrain and collision helpers;
- no need to expand the project into a material registry before fluids are useful.

### 23.2 What it does not provide

It does not provide:

- unique material identity;
- isolation from direct vanilla water/lava checks;
- correct custom-fluid rendering through native type 4;
- distinct same-material neighbor behavior;
- independent vanilla swimming, fog, ecology, or bucket fallback;
- a promise that every third-party mod will distinguish the custom fluid.

### 23.3 Required containment

Before the initial API can be called reliable, these paths must use fluid identity:

- custom flow neighbor matching;
- flowing/still conversion;
- source counting;
- surface height and face culling in the custom renderer;
- flow-angle calculation;
- cross-fluid displacement and mixing;
- custom bucket collection and placement;
- BetaMoon `any_fluid` classification;
- fluid reference and state lookup.

Entity, camera, HUD, and environmental paths can be delivered incrementally, but
unsupported carrier leakage must be listed in the compatibility table for each
release.

### 23.4 Future custom-material migration

The future material API should allow the same field to accept a material
reference:

```lua
material = betamoon.materials:getRequired("example:material/acid")
```

No other fluid field should need to change. The registry already treats material
as one property, not identity.

Changing the native material of already constructed block objects is unsafe.
Migrating an existing declaration from a carrier to a custom material therefore
requires a restart and may require creating the native block objects with the new
material at startup. It must preserve block IDs, keys, pair roles, and metadata
interpretation so existing world cells remain valid.

## 24. Internal architecture

### 24.1 Common fluid domain

Recommended common-side classes are conceptually:

- `FluidDefinition`: immutable resolved policy snapshot;
- `FluidRegistry`: lookup by content key, block ID, and bucket item ID;
- `FluidRegistration`: one retained structural slot plus current definition;
- `FluidBlockRole`: `FLOWING` or `STILL`;
- `FluidState`: detached metadata interpretation;
- `FluidInteractionDefinition` and registry;
- `FluidSimulation`: bounded flow, settling, displacement, and mixing;
- `FluidContactRuntime`: server-authoritative entity contact resolution;
- `FluidBucketHandler`: the single Forge handler.

Declaration parsing, native allocation, registry publication, Lua references, and
world simulation should remain separate.

### 24.2 Block package

The block package should own specialized construction:

- `FlowingFluidBlock` schedules and delegates flow updates;
- `StillFluidBlock` wakes and delegates stationary behavior;
- a shared base implements no collision box, source-only ray collision when
  requested, no drops, explicit textures, tint, render pass, and current vector;
- `BlockRegistration` selects this path for `type = "fluid"` while retaining ID,
  ownership, name, and hot-reload checks.

The classes should extend an appropriate native `Block` base without inheriting
the hard-coded `BlockFlowing` or `BlockStationary` transition code. Shared
behavior belongs in BetaMoon composition rather than duplicating two large
subclasses.

### 24.3 Item package

The item package should own `FluidBucketItem`, common item presentation, container
item setup, retained native identity, and item callbacks whose ordering is
explicitly supported.

Bucket behavior reads the current immutable fluid definition by stable registry
slot. Reload can therefore update placement effects without replacing the item.

### 24.4 Client rendering

Client-only state should contain texture slots, tint, render pass, fog, and
overlay assets. Common classes must not load client render types.

A dedicated fluid renderer should:

1. resolve the placed block through the fluid registry;
2. compare neighbors by fluid registration;
3. interpret their metadata through `FluidState`;
4. compute the four corner heights;
5. use the same fluid's flow vector;
6. select independent still and flowing atlas slots;
7. apply tint and brightness;
8. restore renderer state on every exit path.

Vanilla water and lava remain on native type 4 unless a later parity-tested
unification provides a concrete benefit.

### 24.5 Registry publication

Registration should use prepare/commit semantics:

1. Parse all declarations into detached values.
2. Validate identities, ID availability, structural compatibility, assets, and
   cross-field constraints.
3. Prepare native resources without publishing a partial fluid reference where
   possible.
4. Register the block and item resources through their domain paths.
5. Bind all structural roles.
6. Publish one immutable definition snapshot atomically.

If native allocation fails after unavoidable registry mutation, report the
partial retained identity clearly and do not publish an apparently usable fluid.
The next reload may reattach only when its structural signature matches.

## 25. Required native seams

### 25.1 No-agent core

The following should use ordinary subclasses or Forge/ModLoader extension points:

- fluid block ticking and neighbor changes;
- collision shape and block properties;
- dedicated block render ID and `RenderWorldBlock` delegation;
- bucket collection through one `IBucketHandler`;
- filled bucket right-click behavior;
- static asset slots;
- server-authoritative mixing and displacement.

### 25.2 Instrumented compatibility

Likely verified hooks are needed for:

- generalized living-entity movement selection;
- aggregated water-state replacement where carrier material would otherwise
  classify unrelated fluids together;
- eye-fluid drowning and air restoration;
- wetness, extinguishing, ignition, and burning-volume checks;
- camera fog color/density;
- first-person fluid overlay;
- air HUD visibility;
- boat buoyancy and aquatic entity checks;
- farmland, reeds, sponge, and falling-block compatibility;
- pathfinding classification.

Each concern should have one cohesive hook module with mapped source names,
production verification, idempotence tests, diagnostics, and native fallthrough
when no custom fluid is involved.

Registration must require only the hooks used by the selected fields. For example,
a static neutral fluid without camera policy should not fail because an optional
boat hook is absent. A declaration which requests `boatsFloat = true` must fail
if that behavior cannot be installed.

## 26. Lifecycle and hot reload

### 26.1 Structural fields

These require a restart once their native objects have been registered:

- fluid key and owner;
- flowing and still block IDs and keys;
- role assignment;
- carrier material;
- bucket item ID and key;
- adding or removing the native bucket type;
- changing an existing ordinary block/item into a fluid block/bucket or back.

### 26.2 Reloadable fields

These may replace the immutable runtime snapshot after complete validation:

- flow timing and decay settings;
- source creation and retreat policy;
- displacement policy;
- interaction rules;
- entity and environmental traits whose hooks are already available;
- dimension overrides;
- textures, tint, fog, overlay, effects, light, and opacity where native arrays
  can be updated safely.

Changing light or opacity must mark affected render regions dirty as required.
Changing a texture refreshes rendering data only and never changes world cells,
metadata, tile data, or content identity.

### 26.3 Failure and removal

A failed reload leaves the last complete definition and appearance active. It must
not partially update flow while retaining old interactions or publish one block's
new texture without the other.

If a script stops declaring a fluid, its native resources and last safe structural
definition remain retained until restart so existing world cells do not crash or
turn into a different resource. Callbacks and package-owned runtime handles may be
detached, and diagnostics should report the orphaned retained content.

## 27. Determinism, threading, and performance

- World simulation and registry mutation run on the game/server thread.
- Render frames consume immutable client snapshots and do not call Lua.
- Flow uses only world state, immutable definition values, the world random source
  where a declared probability requires it, and stable direction ordering.
- Interaction resolution has a unique rule or fails during publication.
- No flow search loads a chunk or exceeds its validated depth and write bounds.
- Entity contact scans the bounded entity volume once and groups cells by fluid;
  it does not dispatch one callback per block.
- Fluid lookup by block ID is an array or equivalent constant-time table, not a
  key-map search in a hot loop.
- Renderer lookup by block ID is constant time and allocation-free.
- Particle and sound counts are bounded.
- Diagnostics and debug counters should aggregate rather than log every tick.

The implementation should expose debug metrics for scheduled updates, cells
written, flow searches, maximum search depth, mixing events, bucket actions,
entity contacts, and rejected unsafe displacement.

## 28. Validation and diagnostics

Errors should name the fluid key and exact field path. Required validation includes:

- valid package-owned fluid and interaction keys;
- exactly one flowing and one still block;
- distinct, available block IDs in `1..255`;
- a valid shifted bucket item ID when present;
- matching fluid and role references during `bind`;
- only `water` or `lava` material initially;
- compatible retained native structural signatures;
- all metadata-dependent ordinary block features rejected;
- texture assets resolvable and client requirements available;
- integer/range checks for flow and effects;
- referenced result blocks, items, fluids, and dimensions exist;
- no duplicate or overlapping interaction rule;
- selected compatibility traits have installed native support;
- shorthand declarations completely preflighted before allocation.

Useful publication messages include:

- carrier-material compatibility warning for every custom fluid;
- restart-required structural mismatch;
- staged fluid left unbound at the end of registration;
- orphaned retained fluid after script removal;
- requested integration unavailable because its hook failed;
- block ID pressure warning when few IDs remain.

## 29. Compatibility matrix

The implementation and user documentation should maintain a concrete matrix for
each release rather than saying a fluid is simply water-like or lava-like.

| Concern | Initial expectation |
| --- | --- |
| Flow and settling | Fully registry-aware. |
| Rendering | Fully registry-aware dedicated renderer. |
| Buckets | Fully registry-aware for registered custom sources/items. |
| Mixing | Fully registry-aware declarative rules. |
| Block displacement | Registry-aware and tile-safe. |
| Generic BetaMoon `any_fluid` | Registry-aware. |
| Entity movement | Delivered in its own phase; carrier behavior documented until then. |
| Drowning and HUD | Delivered together; never publish one without the other. |
| Fog and overlay | Delivered together through eye-fluid state. |
| Farmland/reeds/sponge | Explicit traits only after each hook exists. |
| Boats/aquatic spawning | Explicit traits only after each hook exists. |
| Third-party material checks | May see the carrier as water or lava. |
| Custom materials | Deferred. |
| Dedicated server/multiplayer | Later, but common definitions remain client-free. |

## 30. Implementation plan

### Phase 0: contracts and parity fixtures

1. Add fluid and interaction content types.
2. Create immutable definitions, metadata interpretation, and registry contracts.
3. Register read-only vanilla water and lava references.
4. Build source-level parity fixtures for water and lava metadata, flow, and
   bucket behavior.
5. Add structural-signature and prepare/commit tests before native allocation.

No public custom fluid should be accepted in this phase.

### Phase 1: blocks and simulation

1. Add `type = "fluid"` to the block API.
2. Implement explicit paired roles without adjacent-ID assumptions.
3. Implement bounded flow, settling, waking, source creation, dimension overrides,
   and safe displacement.
4. Reserve all metadata and reject incompatible block features.
5. Add shorthand preflight and explicit bind validation.
6. Test save/load and retained identities.

### Phase 2: rendering and assets

1. Allocate a dedicated BetaMoon render type.
2. Implement identity-aware fluid mesh generation.
3. Support independent static still and flowing texture assets, tint, light,
   opacity, and render pass.
4. Verify pass ordering, anaglyph behavior, texture refresh, chunk rebuilds, and
   GL/render-state restoration.
5. Require the render bridge before publishing a visible custom fluid.

At the end of this phase, a custom fluid is visually and mechanically usable but
may not yet have a bucket or custom entity semantics.

### Phase 3: buckets and interactions

1. Install the single Forge bucket handler.
2. Add `type = "fluid_bucket"` to the item API.
3. Implement source-only collection, safe placement, container results, and
   dimension evaporation.
4. Add deterministic interaction rules.
5. Express vanilla lava-water hardening through the shared evaluator.
6. Test permission failure and inventory/container edge cases.

Implementation status: items 1–6 are implemented. Item 5 uses a common client
and dedicated-server instrumentation seam, with the untouched native method as
the safe fallback when the agent is unavailable. Bucket tests exercise source
and partial collection, no-bucket carrier containment, permission failure,
rejected world mutations, solid/fluid destinations, crafting remainder, source
placement, and Nether evaporation through real in-memory `World` mutation paths.

### Phase 4: entity and camera behavior

1. Add aggregated body-fluid and eye-fluid resolution.
2. Add native-compatible movement and current scaling.
3. Add drowning, air HUD, wetness, extinguishing, ignition, and contact damage.
4. Add fog and overlay snapshots without render-frame Lua.
5. Verify player, ordinary living entity, dropped item, projectile, and custom
   BetaMoon entity behavior.

### Phase 5: environment and world integration

1. Add validated falling-block, farmland, reed, sponge, boat, aquatic habitat, and
   pathfinding traits one seam at a time.
2. Replace BetaMoon generic fluid checks with the shared helper.
3. Route existing exact `water` and `lava` worldgen conditions through vanilla
   fluid identity so a custom carrier does not satisfy them accidentally.
4. Add explicit fluid-reference filters to relevant worldgen APIs without
   changing existing exact vanilla filters silently.
5. Add diagnostics, debug export, Lua definitions, and user-facing wiki pages.

### Phase 6: optional visual polish

1. Adopt the shared animated texture capability for both fluid block texture
   roles (implemented); see [General animated textures API](ANIMATED_TEXTURES_API_PROPOSAL.md).
2. Add bounded display sounds and particles.
3. Add authoring previews and fluid-state inspection tools.

This phase must not delay the reliable static-texture release.

## 31. Verification plan

### 31.1 Flow unit tests

- source, horizontal, and falling metadata interpretation;
- corner-height inputs and effective levels;
- water and lava preset parity;
- no adjacency requirement for block IDs;
- deterministic equal-cost direction selection;
- downward priority;
- maximum slope-search depth;
- two-adjacent source creation and support rule;
- no source creation for neutral/lava rules;
- retreat probability boundaries;
- overworld and Nether overrides;
- settling and neighbor wake-up;
- chunk-boundary refusal without forced loading.

### 31.2 Isolation tests

- two custom water-backed fluids do not connect, cull shared faces, count each
  other's sources, or exchange levels;
- custom water-backed fluid does not connect visually to vanilla water;
- custom lava-backed fluids require explicit mixing rules;
- carrier material never selects the paired block ID;
- vanilla water and lava behavior remains unchanged when no custom fluid exists.

### 31.3 Displacement and interaction tests

- drop, destroy, and block semantics for eligible content;
- doors, signs, ladders, plants, replaceable blocks, and solid blocks;
- tile entity, protected block, bedrock, and unreadable-cell rejection;
- source, bounded-level, and catch-all interaction outcomes;
- horizontal and falling-state outcome selection;
- face restrictions;
- duplicate-rule rejection;
- deterministic rule execution independent of scheduled update order;
- sound and particle bounds.

### 31.4 Bucket tests

- vanilla empty bucket collects only a registered custom source;
- non-source custom fluid is not collected;
- the handler prevents vanilla water/lava fallback misclassification;
- successful collection removes exactly one cell;
- failed collection changes neither world nor held stack;
- placement chooses the flowing role with metadata `0`;
- solid, protected, tile, and disallowed fluid targets reject placement;
- dimension evaporation returns the empty container without placing fluid;
- crafting and furnace container behavior returns the vanilla empty bucket;
- reload does not register duplicate Forge handlers.

### 31.5 Render tests

- independent still and flowing atlas slots;
- source, horizontal, and falling surface heights;
- top UV rotation from the correct custom flow vector;
- no faces between the two roles of one fluid;
- faces remain between distinct same-carrier fluids;
- tint, brightness, light, opacity, and both render passes;
- chunk rebuild after appearance reload;
- missing render hook fails registration clearly;
- no native type-4 null-vector path for a custom block.

### 31.6 Entity and camera tests

- body and eye contact resolved independently;
- deterministic dominant-fluid selection;
- movement modes and current scaling;
- drowning, air reset, and HUD synchronization;
- wetness and fire behavior combinations;
- bounded contact-damage interval;
- fog and overlay selection;
- camera leaves the fluid without stale state;
- no Lua call from a render frame.

### 31.7 Reload and persistence tests

- behavior snapshot changes atomically;
- failed reload preserves the previous complete definition;
- textures reload without replacing block or item identity;
- structural changes report restart requirements;
- removed scripts leave safe retained native identities;
- world save/load preserves every metadata level and pair role;
- bucket and interaction references remain stable across reload.

### 31.8 Performance tests

- large fluid update queues stay within per-tick budgets;
- maximum-slope searches allocate no per-cell collections;
- render lookup and state interpretation allocate nothing per face;
- entity contact cost scales with intersected cells rather than registered fluid
  count;
- debug metrics do not emit per-tick log spam.

## 32. Documentation plan

The wiki should be updated only as public phases become usable. Internal Java
architecture and unfinished future fields remain in this document, not in the
user wiki.

The eventual user documentation should include:

- a custom fluids overview;
- the simple `fluids:add` path;
- the explicit `define` plus block/item binding path;
- complete field tables for the delivered surface;
- metadata/source/falling concepts in user-facing terms;
- bucket and mixing examples;
- hot-reload and restart rules;
- a visible carrier-material compatibility warning;
- the current compatibility matrix;
- links from block, item, asset, worldgen, fuel, and Minecraft-constants pages;
- static versus animated texture support as actually implemented.

The Lua addon definitions must be updated from the canonical ignored
`internal/api.lua`, synchronized, checked, and validated with relevant LuaLS tests
in the same public-API change.

## 33. Rejected alternatives

### 33.1 Reuse `BlockFlowing` and `BlockStationary`

Rejected because they assume adjacent IDs, branch directly on water/lava material,
contain private algorithm details, hard-code lava behavior, and feed the native
renderer's vanilla-only flow-angle function.

### 33.2 Treat material as fluid identity

Rejected because the first release has only two carrier materials and must support
more than two fluids. It would cause visual joining, source counting, flow, and
mixing across unrelated fluids.

### 33.3 Implement custom materials first

Rejected as a prerequisite. Unique materials would improve isolation, but a public
material system has its own construction, behavior, query, compatibility, reload,
and documentation design. Requiring it would substantially expand and delay the
fluid project. The proposed identity containment makes fluids useful first and
keeps the future migration local.

### 33.4 Build all behavior with ordinary block/item callbacks

Rejected because flow is a high-frequency deterministic simulation, and bucket
behavior has native interoperability and inventory contracts. Lua callbacks remain
useful at bounded semantic boundaries, not as the engine.

### 33.5 Let the fluid API register blocks and items independently

Rejected because it would duplicate validation, retained identity, callbacks,
assets, and future improvements from the established block and item APIs.

### 33.6 Use one native block instead of a pair

Rejected for the first release. Metadata is already fully occupied by level and
falling state, and a separate persisted active-position index would be more fragile
than the native paired model. Two explicit IDs are expensive but predictable in
Beta 1.7.3.

### 33.7 Pick up partial levels with a bucket

Rejected because the world has no conserved fractional volume. Source-only pickup
matches native behavior and prevents duplication.

### 33.8 Add a broad `waterLike` flag

Rejected because it would conflate flow, swimming, drowning, fog, farmland, reeds,
sponge, boats, spawning, pathfinding, and wetness. Presets provide convenience;
resolved traits provide control.

## 34. Acceptance criteria

The first complete custom-fluid feature is acceptable when:

1. A Lua package can create one fluid with non-adjacent flowing/still block IDs
   and an optional bucket.
2. Both the shorthand and explicit paths use the existing block and item APIs.
3. Two custom fluids sharing `Material.water` remain distinct in simulation,
   rendering, mixing, and buckets.
4. Metadata, source creation, downward flow, horizontal flow, settling, and waking
   behave deterministically and preserve saved worlds.
5. The dedicated renderer never calls the vanilla custom-material-unsafe flow
   helper.
6. Vanilla empty buckets collect registered custom sources without falling through
   to a vanilla water/lava result.
7. Cross-fluid replacement happens only through one unambiguous interaction rule.
8. Tile entities and protected blocks are not destroyed by default.
9. Reload replaces only complete validated behavior/appearance snapshots and
   preserves native identities.
10. Every delivered entity or environment trait has a verified native seam and a
    compatibility test.
11. Vanilla water and lava pass parity and regression tests with no custom fluid
    registered.
12. Public documentation explicitly states that water/lava materials are temporary
    carriers and lists remaining compatibility leakage.

## 35. Final recommendation

Proceed with fluids before a public custom-material API.

The reliable boundary is a keyed `FluidRegistry` with explicit flowing/still block
roles, dedicated rendering, native bucket integration, and declarative behavior.
Use `material = water` or `material = lava` temporarily, but isolate every new
core fluid operation by registered fluid identity. Do not advertise carrier
materials as unique or fully isolated.

This approach delivers useful custom fluids without expanding the current project
into another foundation first, while preserving a clean later migration to custom
material references.
