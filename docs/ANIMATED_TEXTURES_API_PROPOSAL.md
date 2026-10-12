# General animated textures API: audit and proposal

Status: implemented on BetaMoon `feature/0.8.0` for authored vertical frame
strips. The follow-on per-instance and writable-texture capabilities remain
proposals. Audited against the bundled Minecraft Beta 1.7.3 sources on
2026-10-12. The implementation has headless and off-screen GL verification;
in-game visual review is still useful.

## 1. Decision

Add animation to **texture assets**, then let every texture-bearing appearance
consume the same texture input. Blocks, items, fluids, model materials, and GUI
images should not each invent their own frame parser or animation clock. A fluid's
still and flowing faces are ordinary block-atlas texture consumers; fluid identity
and simulation do not own texture animation.

The first useful animation source is a validated vertical PNG frame strip. A
registered texture may declare playback once and be reused through its asset
reference. Direct texture paths may use the same description inline. The runtime
decodes frames at load/reload, advances them on logical client ticks, and updates
the pixels of stable atlas slots or standalone texture objects. It never changes
block IDs, metadata, tile data, item stacks, model geometry, or chunk display
lists merely to show another frame.

This is a shared-image animation: all consumers of one animation binding see the
same frame. Per-block or per-entity timelines, state-controlled clips, writable
canvases, and procedurally generated pixels are separate capabilities that can
build on the same renderer boundary later.

## 2. Current implementation audit

| Surface | Current path | Gap |
| --- | --- | --- |
| Native water/lava | `TextureWaterFX`, `TextureWaterFlowFX`, `TextureLavaFX`, and `TextureLavaFlowFX` produce pixels; `RenderEngine.updateDynamicTextures()` uploads atlas regions on ticks. | Native effects exist, but Lua content cannot declare or share one. |
| Block faces and item icons | `AssetInputs.texture()` resolves a path/reference; `AtlasTextures` allocates a stable atlas index and uploads a scaled 16×16 static image. | There is no frame decoder, animation binding, or per-tick frame scheduler. |
| Custom fluids | `FluidDeclaration.appearance()` registers `still` and `flowing` through the block atlas, also accepting numeric indices. | The parser only accepts the current static texture forms. No fluid-specific animator is needed. |
| Model materials | `ModelAppearance` registers a virtual PNG through `LuaTextureResources`; `ModelRenderer` binds it with normal model UVs. | Native texture caching loads one complete image; displaying successive frames requires a managed standalone texture binding. |
| GUI images and overlays | GUI backgrounds and camera overlays also use `LuaTextureResources` or its virtual PNG path. | Layout currently sees the source PNG's dimensions. An animated strip must expose *frame* dimensions and update the same bound texture. |
| Armor textures | Custom armor also supplies a `LuaTextureResources` path to native armor rendering. | The armor binding needs a managed-texture adapter; it cannot be covered by atlas uploads or model-material changes alone. |
| Asset reload | `ClientAssets` refreshes texture bindings from script defaults and `bm_assets/` overrides, retaining a previous image when refresh fails. | Animation validation must participate in override selection and preserve the previous complete frame set on bad reloads. |

The bundled `ModTextureAnimation` demonstrates authored frame strips, but its
registration and lifetime are separate from BetaMoon's asset ownership. The
existing `AtlasTextures` upload backend is a closer integration point for atlas
sprites. Standalone model and GUI textures need a parallel managed binding rather
than repeatedly replacing a virtual PNG in Minecraft's cache.

Relevant implementation seams:

- `src/betamoon/luaapi/asset/AssetDeclaration.java` and `AssetInputs.java`;
- `src/betamoon/client/assets/TextureAsset.java`, `TextureImage.java`,
  `ClientAssets.java`, and `AtlasTextures.java`;
- `src/betamoon/resources/LuaTextureResources.java`;
- `src/betamoon/luaapi/block/BlockDeclaration.java`,
  `src/betamoon/luaapi/item/ItemDeclaration.java`, and
  `src/betamoon/luaapi/fluid/FluidDeclaration.java`;
- `src/betamoon/luaapi/asset/ModelAppearanceDeclaration.java`,
  `src/betamoon/client/render/ModelAppearance.java`, GUI texture consumers,
  and `src/betamoon/luaapi/item/ItemArmorApi.java`;
- `tools/rmcp/minecraft/src/net/minecraft/src/RenderEngine.java` and the native
  `TextureFX` implementations.

## 3. Public Lua contract

### 3.1 Registered texture: preferred for reuse

```lua
local ember = betamoon.assets.textures:add {
  key = "mymod:effects/ember",
  path = "effects/ember_frames.png",
  animation = {
    layout = "vertical",
    frameTicks = 3
  }
}

-- The same asset reference is valid in each texture-bearing appearance.
betamoon.blocks:add { ..., texture = ember }
betamoon.items:add { ..., texture = ember }

-- A model material uses the same reference and retains ordinary model UVs.
local material = { texture = ember, blend = "cutout" }

-- The fluid declaration continues to provide ordinary block textures.
local fluidAppearance = { still = ember, flowing = ember }
```

The abbreviated declarations above illustrate input compatibility rather than
complete runnable block/item/fluid definitions. `betamoon.assets.textures:add`
retains its current `key` and optional `path`; `animation` is optional metadata
on the registered texture. Existing static declarations and texture references
keep their current meaning.

The asset reference should carry the animation definition, not a newly created
`betamoon.animatedTextures` namespace or a fluid-only setting. Different
consumers may need different native bindings (block atlas, item atlas, standalone
texture), but they read the same source frames and timing contract.

### 3.2 Inline texture description

Every field that currently accepts a texture path or texture asset reference may
also accept:

```lua
texture = {
  asset = "effects/ember_frames.png", -- path, namespaced texture key, or texture reference
  animation = { frameTicks = 3 }
}
```

For a block-face map the table is the value of `textures.top`, `textures.side`,
and so on; for fluids it is the value of `appearance.still` or
`appearance.flowing`; for a model material it is the value of `material.texture`.
The same input also applies to GUI, overlay, and armor texture fields once their
standalone adapters are delivered.
The `layout` default is `"vertical"`. A descriptor without `animation` is
equivalent to its `asset` alone.

If `asset` is a registered texture with its own animation, an inline
`animation` replaces the registered playback definition for that use. This is
intentional: the same art may run at different speeds in two appearances.
`animation = false` selects its first frame as a static texture. No other
per-consumer override fields are implicit. The cache key must include the
effective animation definition as well as the asset location, so distinct
speeds never accidentally share a clock or binding.

### 3.3 Frame-strip schema

| Field | Contract |
| --- | --- |
| `layout` | `"vertical"` in the first release; default `"vertical"`. Unknown layouts fail validation. |
| `frameTicks` | Positive integer client ticks per frame, default `1`; accepted range `1..1200`. At the nominal 20 ticks/s, `2` means 10 frames/s. |
| `sequence` | Optional nonempty dense array of one-based frame numbers. Default is `1, 2, ..., N`. Repeated numbers hold a frame for additional `frameTicks` intervals. Playback loops. |
| `clock` | `"client"` (default) advances once per logical client tick, including menus and paused screens. `"unpaused"` skips ticks while Minecraft reports the game paused. Neither clock uses render-frame count or world ticks. |

Frame one is displayed initially. After exactly `frameTicks` eligible client ticks,
the binding displays the next entry in `sequence`; after its last entry it
returns to the first. This defines the same timing in a loaded world, a menu,
and an in-world GUI.

Example with a held frame and a return motion:

```lua
animation = {
  frameTicks = 2,
  sequence = { 1, 2, 3, 3, 2 },
  clock = "unpaused"
}
```

No public Lua callback runs for each animation frame. The first release has one
looping clip per binding. More clip/state controls need an explicit owner and
timeline API; silently treating a shared atlas sprite as a per-instance state
machine would produce surprising results.

### 3.4 Compatibility rules

- Strings, registered texture references, and existing numeric atlas indices
  retain their current behavior. Numeric indices cannot receive an animation
  descriptor because the slot may be owned by Minecraft or another mod.
- Existing `appearance.animation` for model *bone motion* is unchanged. Texture
  playback lives under a texture asset or texture input, not in that field.
- An animated texture must be accepted by every BetaMoon-owned appearance path
  listed for its delivery phase. Unsupported consumers fail with a precise
  diagnostic; they must not silently squeeze a strip into one static image.
- Animation affects presentation only. Content keys, fluid identities,
  persistence, collision, and behavior are unchanged. A dedicated server needs
  declaration validation but no frame scheduler or OpenGL texture.

## 4. Image format and validation

A vertical strip contains `N` square frames, each `W × W` pixels, in a PNG of
size `W × (W × N)`. `W >= 1` and `N >= 1`; a one-frame animation is valid but
visually static. Frame indices in `sequence` must be integers in `1..N`. Reject
trailing partial frames, empty sequences, unknown fields, unsupported PNGs, and
assets exceeding byte, decoded-pixel, frame-count, or upload budgets. Diagnostics
should name the asset and offending field or dimensions.

The decoder extracts frames **before** any atlas scaling. Block and item atlas
uploads scale each frame separately to the active atlas cell size. This avoids
compressing a whole strip into one sprite. Standalone model and GUI bindings
preserve each frame's original dimensions and UV space; GUI dimension queries
return one frame's width and height. All frames of one binding have one size.
Texture interpolation/filtering and alpha follow the consumer's existing rules.
Mip levels must be updated consistently with the base level when enabled.

Current `ClientAssets` resolution has an 8 MiB input limit, and `TextureImage`
has a 262,144 decoded-pixel limit. The implementation must apply explicit
limits to the *whole strip* and to cached decoded RGBA frames; it should reuse
those limits where practical and report them, not rely on an image decoder's
memory failure. The current atlas uploader assumes 16-pixel cells, so the atlas
adapter must account for the actual texture-pack atlas cell size before claiming
high-resolution pack support. If that work is deferred, the first release must
state and test the 16-pixel-cell limit.

## 5. Internal design and ownership

### 5.1 Parsing, decoding, and runtime bindings

1. Add one `TextureInput` reader in the asset domain for static references and
   animated descriptors. Block and item packages keep their own declarations
   and registration logic; fluids delegate their two texture inputs to the same
   reader. No shared block/item framework is needed.
2. Extend texture asset declaration metadata with an optional immutable animation
   definition. Keep the asset's source PNG, playback definition, and native
   bindings separate. Do not store animation timing in `TextureImage`, which is
   only decoded image content.
3. Decode a validated immutable frame set from the chosen asset source and the
   effective animation definition. Its cache key is asset location plus full
   playback definition. A registered reference can share this frame set across
   block, item, fluid, model, and GUI bindings.
4. Give each distinct asset/playback pair a client-side timeline. Advance it
   once per completed logical client tick. ModLoader invokes `ClientAssets.poll()`
   from render callbacks, so the callback frequency alone is not a tick clock.
   Read Minecraft's elapsed client ticks for that render and advance by that
   count, including ticks that occurred between renders. The broader
   [global client tick proposal](GLOBAL_CLIENT_TICK_HOOKS_AUDIT_AND_PROPOSAL.md)
   remains the foundation for APIs that need per-tick callbacks.
   Frame selection is independent of render frequency and chunk visibility.
   An unchanged frame triggers no GL upload.
5. Each rendering backend owns a stable native binding: an atlas slot for block
   faces and item icons, or a managed standalone texture for model materials,
   GUI images, and overlays. Several native bindings may observe one frame set
   and timeline. GL upload happens on the client render thread only.

`AtlasTextures` should evolve from static slots into managed slots while keeping
its existing allocation and refresh role. Do not register a fresh `TextureFX`
each reload: the native FX list does not express BetaMoon's script ownership or
unregistration. Leave vanilla/third-party FX-owned slots alone. For standalone
textures, a dedicated managed binding should supply a stable texture ID or
resource handle to `ModelRenderer` and GUI adapters; changing virtual PNG bytes
in `LuaTextureResources` each tick would force native cache churn.

### 5.2 Reload and disposal

- On texture-pack change, resolve the override and validate it *as an animated
  strip*, including the current playback sequence. If the override is invalid,
  report why and use the script default. If both sources fail during refresh,
  keep the prior complete frame set and pixels. An incomplete candidate must
  never replace a valid binding.
- `bm_assets/` paths and registered asset keys follow the existing asset
  resolution rules. A texture-pack override changes PNG pixels and may change
  frame count if the declared sequence remains valid; playback metadata remains
  supplied by Lua. Do not silently read modern `.mcmeta` files.
- Preserve atlas indices, standalone binding identity, model UVs, block metadata,
  and tile data across valid visual reload. On a changed frame count, reset to
  frame one; on unchanged dimensions/count, preserving the current phase is
  acceptable if all consumers stay in sync.
- Script reload may change a texture from static to animated or back. Reuse the
  logical binding where safe and update its pixels without replacing content
  objects or forcing a chunk rebuild solely for animation.
- Reference-count active frame sets/timelines and stop them when their last
  consumer closes. Atlas index reuse must respect ModLoader's allocation
  constraints; stable reuse for the same logical key is preferable to leaking a
  new slot on each script reload. Report exhausted atlas capacity explicitly.

### 5.3 Renderer-specific behavior

| Consumer | Required behavior |
| --- | --- |
| Block face, including fluid block | Compiled geometry continues to reference a stable block-atlas slot. The fluid renderer's direction-dependent UV choice is separate from image playback. |
| Item icon | Inventory, held, and dropped render paths see the same current item-atlas pixels. |
| Model material | Its standalone texture changes pixels while static model geometry remains eligible for static rendering. Texture animation alone must not force `appearance.mode = "dynamic"`. |
| GUI image or overlay | Bind the current standalone frame and report frame dimensions to layout/cropping code. Define explicit behavior for unsupported cropped or tiled draws instead of exposing strip height. |
| Armor texture | Bind a stable standalone frame through the native armor renderer without replacing the armor item or altering its durability/appearance selection. |
| Native/third-party texture slot | Remains under its original owner. BetaMoon only animates slots it allocated or a future explicit, permissioned override API. |

One atlas slot has one image at a time. All blocks using it therefore animate in
sync. A texture's `sequence` does not select a block's metadata variant, a fluid
state, or an entity's personal frame. Those are appearance-selection concerns.

## 6. Delivery order

1. **Shared contract and frame decoder.** Add `TextureInput`, texture-asset
   animation metadata, strict strip validation, reload-aware frame resolution,
   and tests with no OpenGL dependency. Static forms remain compatible.
2. **Atlas bindings.** Extend `AtlasTextures` with managed frame uploads and a
   true client-tick scheduler. Integrate block faces, item icons, and both custom
   fluid texture roles through the same reader. Verify mipmaps, texture-pack
   reload, anaglyph, atlas size, and slot lifetime.
3. **Standalone bindings.** Add stable managed textures for model materials,
   then GUI images, overlays, and armor textures. Use the same
   asset/frame/timeline core and preserve static-model eligibility. Cover
   resource cleanup and GUI dimensions.
4. **Public documentation and examples.** Update the Lua addon definitions and
   user-facing wiki for the actual delivered consumers. Add a beginner follow-up
   to the registered-texture lesson using one animated asset on a block and item,
   with a separate intermediate fluid example showing reuse. Do not document an
   adapter before it works.

The authored-frame capability was implemented after the static-fluid foundation.
Fluid animation uses the shared atlas adapter from step 2, with no separate
fluid-only `TextureFX` implementation.

## 7. Verification and acceptance

- One registered animated PNG can appear on a block face, item icon, and both
  custom-fluid roles using the same public texture reference; its tick cadence
  is independent of frame rate. Two descriptors with different speeds do not
  share a timeline accidentally.
- Static blocks and static model geometry do not rebuild each texture frame.
  Animation leaves world data, gameplay state, and save output unchanged.
- A model material and GUI image display individual full frames, not the whole
  strip; GUI dimensions equal frame dimensions.
- Pausing affects `clock = "unpaused"` but not `clock = "client"`. Reloading a
  valid pack keeps bindings stable. A malformed override falls back to the
  default; a failed full refresh retains the previous usable animation.
- Static-to-animated and animated-to-static script refreshes work without a
  restart. Repeated reload does not duplicate timers or grow active GL
  textures/atlas allocations for the same logical bindings.
- Bad dimensions, out-of-range sequence indices, non-integer timing, exhausted
  atlas space, and incompatible consumers produce actionable diagnostics.

## 8. Follow-on capabilities

This proposal establishes a managed texture binding and timeline that later
features can reuse. An explicitly owner-scoped appearance/player presentation
API could add per-instance playback, named clips, pause/seek, and synchronized
state changes. Writable canvases or compiled procedural effects could publish
frames into the same managed binding with strict update and memory budgets.
Neither requires changing the simple frame-strip declaration above.

See also [Custom fluids API audit and proposal](CUSTOM_FLUIDS_API_PROPOSAL.md)
for the fluid appearance consumer, and
[Vanilla content and feature API gap audit](VANILLA_CONTENT_AND_FEATURE_API_GAP_AUDIT.md)
for the broader writable/dynamic texture gap.
