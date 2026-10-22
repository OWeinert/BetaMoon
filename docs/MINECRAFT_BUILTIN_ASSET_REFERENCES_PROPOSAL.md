# Minecraft built-in assets through the BetaMoon asset API

Status: accepted, 2026-10-21. The first texture delivery is implemented on `feature/minecraft-builtin-assets`.

Implemented: the verified catalog of 77 whole client PNGs, 512 terrain/item cell keys and the sand alias; vanilla pack stream lookup; native whole-image and cell source metadata; live model bindings in dynamic and static rendering; matching ordinary block/item atlas fields; whole-image GUI/armor fields; refresh, API definitions and wiki documentation. The ignored falling-sand demo now uses the sand reference. Tests cover actual vanilla pack ZIP/JAR fallback, bounds, HD cell mapping, native GL subimage updates, static chunk tessellation, and headless catalog lookup.

Remaining extensions: derived snapshots for consumers requiring CPU pixels, additional semantic aliases and tint metadata, native sound/music references, and dedicated-server/multiplayer delivery. Native asset keys are not accepted in particle texture fields; particles retain their existing native atlas descriptors.

## 1. Recommendation and first useful result

Register **references to Minecraft's client assets** in the existing `betamoon.assets` registries. Keep Minecraft's images in the installed client JAR and selected texture pack. Do not ship copied PNGs or require scripts to extract them. The first delivery should let a model appearance use a live terrain-atlas cell, so the ignored falling-sand demo can replace its temporary white texture with the active pack's sand texture:

```lua
local sandTexture = betamoon.assets.textures:getRequired("minecraft:block/sand")
local fallingAppearance = {
    model = "minecraft:block/full_block",
    texture = sandTexture
}
```

`minecraft:block/sand` would be a documented alias for terrain cell 18, using the same native atlas and texture-pack path as `minecraft:atlas/terrain/18`. This is a **texture** key, independent of the model key and block registry. The alias denotes that one image, not a promise to reproduce every behavior of native sand. Existing numeric atlas options must retain their present meaning.

The initial usable slice must include the complete path from Lua lookup through block, item, and entity model rendering, including static chunk rendering and refresh. Merely returning an asset reference while model appearances still load a separate PNG would not solve the falling-sand use case.

## 2. Where the assets actually are

The local Beta 1.7.3 client JAR at `tools/rmcp/jars/minecraft.jar` contains image resources at classpath paths such as `/terrain.png`, `/gui/items.png`, `/gui/icons.png`, `/gui/particles.png`, `/mob/cow.png`, `/art/kz.png`, `/misc/grasscolor.png`, and `/environment/rain.png`. These are entries in the JAR, not modern `assets/minecraft/textures/...` files. Native `TexturePackDefault` uses classpath resources; `TexturePackCustom.getResourceAsStream("/terrain.png")` first reads `terrain.png` from the selected ZIP's root and then falls back to the classpath resource. `TexturePackList` obtains ZIPs from the Minecraft data directory's `texturepacks/` folder. The runtime should use the active game's resource selection, not the development copy under `tools/rmcp`.

| Resource class | Beta 1.7.3 location | Meaning for BetaMoon |
| --- | --- | --- |
| Terrain tiles | `/terrain.png`, selected pack ZIP root or client JAR | 16 columns by 16 rows, cell indices 0–255; `Block.sand` uses 18. The default 256 × 256 PNG has 16 × 16 pixel cells. |
| Item tiles | `/gui/items.png`, selected pack ZIP root or client JAR | Another 16-column by 16-row atlas; index and atlas identity both matter. |
| Whole images | `/gui/...`, `/mob/...`, `/art/...`, `/misc/...`, `/environment/...`, etc. | Some are sprite sheets or effect masks, so whole-image access does not imply a ready-made item or model UV layout. |
| Dynamic atlas pixels | Native `RenderEngine.updateDynamicTextures()` updates GL atlas regions after loading the PNG | Water, lava, fire, portal, compass, and watch can change without the PNG file changing. |
| Sounds and music | `<Minecraft data directory>/resources/`, under `sound/`, `newsound/`, `music/`, `newmusic/`, `streaming/` | Local files installed into native sound pools; not bundled as PNG-like JAR resources and may be absent offline. |

The BetaMoon files under `resources/builtin/minecraft/models/` are BetaMoon Bedrock-geometry versions of native block *shapes*. Beta 1.7.3 does not provide matching JSON model files in its JAR. `minecraft:block/full_block` is now one such model; its geometry is identical to the existing double-slab model except for its identifier.

Native rendering can choose a cell by face, metadata, and world state. For example, `BlockGrass` chooses different top, bottom, side, and snowy-side cells and also applies a world-dependent grass color. A `minecraft:block/grass` texture alias, if offered later, must name a specific cell/face. It cannot silently stand for the complete native appearance. Avoid generating a supposedly universal alias for every native block ID.

## 3. Public key and source contract

Use the current `betamoon.assets.textures:get` and `getRequired` methods and immutable asset references. No new registration call is needed. Reserve the `minecraft:` namespace within the built-in texture catalog, and reject script registration that conflicts with a built-in key. A missing catalog key returns `nil` from `get` and errors from `getRequired`, as today. The lookup itself stays headless and does not open the JAR or allocate GL resources.

| Proposed key | Default source | Purpose |
| --- | --- | --- |
| `minecraft:atlas/terrain/18` | Cell 18 of `/terrain.png` | Exact terrain cell reference; any integer 0–255 is cataloged. |
| `minecraft:atlas/items/0` | Cell 0 of `/gui/items.png` | Exact item cell reference; any integer 0–255 is cataloged. |
| `minecraft:block/sand` | Canonical alias of `minecraft:atlas/terrain/18` | Common, clearly defined convenience name. |
| `minecraft:image/terrain` | Entire `/terrain.png` | Whole image, including all 256 cells; no cell UV selection. |
| `minecraft:image/gui/items` | Entire `/gui/items.png` | Whole item atlas as one image. |
| `minecraft:image/gui/icons` | Entire `/gui/icons.png` | Direct native image; similarly catalog other verified client PNG paths. |
| `minecraft:image/mob/cow` | Entire `/mob/cow.png` | Whole sheet, with its native UV layout. |

An `image/` key is a reference to the **whole source image as a texture**, not an API to read PNG bytes. If the source image happens to be an atlas, the consumer's local UVs still address the whole image. An `atlas/` key is a view of one cell within a known atlas: it binds the same native texture and remaps local UVs into that cell. It does not extract, register, or upload a separate per-cell PNG.

Map each alias to one canonical built-in `AssetDefinition`, as built-in models already do. The alias keeps its own lookup key and reference identity, while its content cache identity and native resource path are canonical. The manifest should list the physical JAR/pack path, image type, atlas grid and index when present, canonical key, aliases, and allowed consumer modes. Generate/check the static image inventory against the supported client JAR during development, then check metadata into source control; runtime does not enumerate an arbitrary classpath or filesystem. Add curated semantic aliases only where a stable mapping is verified in native source. Unsupported or non-PNG resources are not auto-exposed by their path.

Keep the existing reference introspection meaningful:

- `getKey()` returns the key used for lookup, preserving the existing built-in model alias behavior.
- `getPath()` returns the actual default relative native resource path, such as `terrain.png`, not a synthetic extracted file name. An atlas index is separately described by the source region metadata.
- `getOverridePath()` returns the path a texture pack must provide. For native cell 18 and its sand alias that is `terrain.png` at the ZIP root; for the whole icons image it is `gui/icons.png`. This extends the method's present `bm_assets/` convention for BetaMoon-supplied assets without changing those existing paths.
- `getSource()` keeps `kind`, `name`, and `path`, and adds an optional `region` table for an atlas cell (`atlas`, `index`, `column`, `row`, `columns`, `rows`). A native reference reports its selected texture-pack name and native path. If pack-versus-JAR provenance can be verified, expose it as additional metadata; otherwise report it as unknown rather than guessing. An alias reports the same underlying native source as its canonical cell.

This needs a tagged source description in Java: bundled BetaMoon file, native whole image, or native atlas cell. Do not encode `terrain.png#18` as an `AssetPath` or require a fake on-disk 16 × 16 PNG. The current `AssetDefinition` assumes one fallback path plus extension; add source metadata without merging Lua declaration parsing, the registry, and client loading.

## 4. Native lookup and texture-pack paths

For a built-in native texture, pass its manifest path with a leading slash to the **currently selected `TexturePackBase.getResourceAsStream`**. `TexturePackCustom` reads the ZIP-root entry, such as `terrain.png`, and falls back to the client JAR resource when that entry is missing. `TexturePackDefault` inherits the classpath lookup. `RenderEngine.getTexture("/terrain.png")` uses that same selected-pack mechanism for the GL texture. This is the authoritative selection path for native references.

Do **not** ask `AssetResolver` or `ZipAssetProvider` to look under `bm_assets/` for these native references. That directory continues to serve registered script assets and BetaMoon-bundled models/textures. A pack author changes native sand by replacing `terrain.png` at the pack ZIP root, exactly as vanilla does; that change also affects vanilla sand. A per-reference replacement would require an explicitly separate future BetaMoon feature and key contract, not an implicit search before vanilla lookup.

Follow the native distinction between a missing ZIP entry and a broken one. A missing entry uses `TexturePackCustom`'s JAR fallback. An invalid image that the pack did provide does not automatically become the JAR image: `RenderEngine` may show its missing texture on first load or retain the previous GL contents after a failed refresh. BetaMoon should report that failure and avoid claiming a fallback it did not render. Validation can be used to give clearer diagnostics, but the live rendering source remains the native renderer.

The `TexturePackBase` stream API does not report whether a custom pack supplied an entry or fell back to the JAR. For optional exact provenance, inspect only the selected pack's entry presence through a narrow adapter, and mark the origin unknown if the native stream's origin cannot be verified; never use that inspection as a competing loader. The existing reflection used to find a custom pack ZIP for BetaMoon's `bm_assets/` overrides is unrelated to native texture acquisition and should not become a second lookup path. A native resource adapter should accept only manifest paths, close streams, bound any validation read, and never accept arbitrary user-provided classpath, filesystem, or ZIP paths.

## 5. Render binding, atlas cells, and animation

The main technical change is a typed texture binding, not a PNG extraction step. Today `TextureAsset` holds a decoded `TextureImage`, `LuaTextureResources` publishes a virtual PNG path, and `ModelRenderer` binds a string with `RenderEngine.getTexture(resource)` and sends each model UV in the range normally used for a standalone image. A native cell reference instead needs the GL texture for `/terrain.png` or `/gui/items.png` and an atlas-region UV transform. For terrain index `i`, `column = i % 16`, `row = floor(i / 16)`, and local `(u, v)` maps to `((column + u) / 16, (row + v) / 16)` in atlas coordinates. The divisor 16 is the number of columns/rows, **not a fixed cell width in pixels**: a compatible 512 × 512 replacement has 32 × 32 pixel cells. Use the actual atlas dimensions and filtering behavior for sampling, including pixel insets where needed to avoid neighboring-cell bleed.

The render representation should expose a small set of operations: bind source, map or emit local UVs, describe the source, and release retained resources. A standalone PNG binding can continue through existing `TextureAsset` and virtual-path machinery. A native atlas binding uses `RenderEngine.getTexture` for the active native atlas and retains no copied cell pixels. Its GL name is reacquired after pack refresh, not saved permanently. Both block/item model renderers and entity model rendering must consume the same binding abstraction. Static chunk batches must include atlas source and region in their render/material identity so they neither bind the wrong texture nor force a gameplay-state rebuild on a visual refresh.

The live atlas path is necessary for native dynamic cells. `RenderEngine.updateDynamicTextures()` writes directly to the atlas GL texture each tick, so a model bound to that atlas sees the same live pixels as vanilla blocks. Cropping `terrain.png` once would freeze water, lava, fire, and similar effects. A separate consumer that requires an independent PNG or CPU pixels should use an explicit derived snapshot mode; it must not be silently presented as live. Such a mode needs its own invalidation on pack selection and, for animated cells, a defined update/copy path before it is advertised as animated. Do not automatically copy every referenced cell from GL every frame.

Materials can scale, offset, or repeat UVs. A simple atlas transform is correct only while the local UV samples the intended cell. Either split repeated geometry into per-cell UV spans or route those cases through an explicit standalone/derived texture path. Reject an unsupported atlas UV mode with a clear error in the first slice instead of sampling neighboring atlas cells. Preserve existing blending, alpha testing, tint, lighting, and model geometry. Native image references that represent whole sheets keep their existing UV coordinates and do not receive the cell transform.

Validate that the atlas image is square, its width and height are divisible by 16, and its 16 × 16 cells are square; calculate cell pixel dimensions from the selected image. Native `RenderEngine.updateDynamicTextures()` has 16-pixel updates hardcoded for the original atlas, so an HD texture pack can inherit vanilla's limitations for animated cells even though static cell UVs scale correctly. BetaMoon should match the game's observed atlas behavior and report unsupported layouts rather than invent different animation. The existing `TextureImage` limit is 262,144 pixels with an 8 MiB input cap in `ClientAssets`: decoding a complete high-resolution atlas as a regular script texture may exceed that cap. The native atlas binding should not pass the entire atlas through `TextureImage`; metadata/provenance validation can inspect bounded headers and stream size, while native `RenderEngine` owns GL upload. Any derived cell snapshot must limit both source decode and output size explicitly.

## 6. Refresh, lifetime, and game state

Keep a built-in reference's Lua identity stable across texture-pack changes and `betamoon.assets:refresh()`. At a client tick boundary, prepare and validate candidate source descriptions, let Minecraft refresh its native textures, then commit or invalidate render bindings and cached static geometry. `ClientAssets.poll` already detects selected-pack identity and coordinates `ClientAssets.refresh()`, `RenderEngine.refreshTextures()`, and `AtlasTextures.refresh()`; extend that one sequence with explicit ordering and a single refresh generation, rather than starting a second reload loop. Account for the `preparedRefresh` guard and direct native refresh callbacks.

On a malformed replacement, keep the previously usable render state when possible, issue one useful diagnostic, and allow a later refresh to recover. A changed pack may remove an old entry; after native refresh, reacquire the selected native texture, including its built-in fallback for a missing entry, rather than holding a stale GL ID. Create/release cache entries by canonical asset identity and consumer lifetime, with no per-frame PNG decoding or repeated ZIP reads. Ensure script unload releases only script-owned bindings; a built-in catalog entry remains available.

Visual reload must rebuild rendering data only. It must preserve block ID, metadata, Lua tile data, entity position/velocity/data, and any world save content. Switching between static and dynamic rendering must work without restarting. The ignored falling-sand package is a good end-to-end regression case: its 3D cube should use the new texture reference while its fall physics and 0.98-wide collision body remain unchanged.

Built-in texture declarations and lookup metadata should be safe to load in a headless context. Client-only providers, image decoding, GL calls, and pack selection must stay behind client boundaries. Dedicated-server and multiplayer asset delivery are separate later work; no server-side client-JAR dependency should be introduced.

## 7. Sounds and other resources

Do not assume every vanilla asset follows PNG rules. `ThreadDownloadResources` stores sounds under the Minecraft data directory's `resources/` folder and registers them through `Minecraft.installResource`; the client JAR does not contain those sound clips. A later `minecraft:` sound catalog can map **native sound event identities** to the native sound pools where that is the actual user intent, and separately expose local raw clips only when a verified file exists. Missing files must give a useful result or diagnostic; BetaMoon must not initiate a network download to satisfy an asset lookup. Streaming music needs a distinct lifetime/path contract. Do not infer raw file names from sound event names without checking native source and installation rules.

Likewise, a native model reference is not the same thing as a native texture reference. The BetaMoon shape models remain bundled geometry; native block rendering is code with atlas indices and contextual rules. The proposed catalog is a source of images and cells, with optional stable aliases, rather than a promise to translate arbitrary Minecraft behavior into one asset.

## 8. Incremental implementation plan

1. **Catalog and metadata.** Add source variants and a checked static manifest of verified client PNG paths; reserve built-in keys and aliases; expose headless lookup and honest `getPath`/`getOverridePath`/`getSource` metadata. Test canonical aliases, unknown keys, path validation, and collisions. No Java-side native GL work belongs in the registry.
2. **Native pack adapter.** Resolve only manifest paths through the selected `TexturePackBase.getResourceAsStream` and bind through `RenderEngine.getTexture`. Cover default pack, custom pack, missing entry and JAR fallback, malformed image, and pack selection changes. Keep ordinary `bm_assets/` script asset resolution intact and separate.
3. **Live atlas binding.** Add typed bindings and atlas UV handling across entity, item, and block model rendering, including static chunk rendering. Preserve all existing standalone texture paths. Test sand cell 18 with `minecraft:block/full_block`, dynamic atlas updates, and pack switch. This is the first public feature-complete delivery.
4. **Whole-image catalog and consumer support.** Expose manifest-backed direct PNG keys to consumers whose UV and sizing contracts are known. Add derived snapshot support only for consumers that actually need it, with explicit behavior for dynamic cells.
5. **Follow-on native resources.** Consider semantic face aliases, tint metadata, sounds/music, and more resource classes as separate reviewed additions, each with its own source and reload contract.

When the public Lua surface lands, update `../BetaMoon-VSC-Lua-Addon/internal/api.lua`, regenerate and check its split library definitions, run addon tests and relevant LuaLS checks, then update the user-facing wiki asset, texture, model-appearance, and texture-pack pages with key syntax, native lookup paths, dynamic-cell behavior, and examples. Keep implementation details in this proposal rather than in the wiki.

## 9. Required acceptance checks

- Default pack and a selected custom pack render different sand pixels on the same running 3D falling entity and placed model; changing back restores the default. A pack that contains only root-level `terrain.png` works. A `bm_assets/minecraft/textures/atlas/terrain/18.png` entry does not override a native reference.
- A whole-image key maps local UV 0–1 across the entire PNG. An atlas-cell key maps local UV 0–1 only across its indexed cell in both a 256 × 256 atlas with 16-pixel cells and a compatible 512 × 512 atlas with 32-pixel cells.
- The full-block geometry remains 1 × 1 × 1 visually, while the falling entity retains vanilla's 0.98 × 0.98 collision dimensions. No first-frame position flicker returns when its texture changes.
- A model referencing an animated native atlas cell follows Minecraft's atlas updates. A derived/static snapshot, if exposed, reports its different semantics. Material UV behavior at boundaries and mipmapped/filtering modes does not bleed adjacent tiles.
- Missing pack entries use the native client-JAR fallback. Invalid images and malformed atlas layouts produce source-specific diagnostics without claiming a fallback that Minecraft did not select; unknown keys never permit arbitrary JAR paths, filesystem paths, or ZIP traversal.
- Reload and pack selection preserve existing block identity, metadata, Lua tile data, entity state, and reference identity. Static/dynamic render switching and script unload do not leave stale GL IDs or resource references.
- Headless tests can enumerate keys and inspect metadata without Minecraft client classes. Rendering tests cover the native pack selection and JAR fallback, alias canonicalization, UV mapping, atlas refresh, and normal standalone texture regression.

## 10. Decision record

This proposal recommends **live references** to native assets through the selected vanilla `TexturePackBase` and `RenderEngine`, a static verified catalog, `minecraft:` keys in existing registries, canonical aliases for stable cells, and explicit source metadata. Native references do not search `bm_assets/`. It rejects copying vanilla image bytes into BetaMoon, pretending a one-time crop remains animated, and treating modern Minecraft resource paths as Beta 1.7.3 paths. The first implementation boundary is the sand cell rendered on the new full-block model; broader image and sound coverage follows the same foundation.
