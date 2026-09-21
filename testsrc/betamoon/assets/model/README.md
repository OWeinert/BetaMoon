# Model and animation verification

`ModelFoundationTest` uses small, hand-authored geometry 1.12.0 and animation 1.8.0
fixtures that isolate exported cuboid and numeric-keyframe behavior. It checks
hierarchical pivots, UV orientation and mirroring, independent poses, source Euler
composition, locators, interpolation, blending, and rejected input. It has no Lua,
Minecraft, or OpenGL runtime dependency.

The import conventions were checked against Blockbench's Bedrock geometry codec,
cube UV layout, default rotation order, and bone animator:

- https://github.com/JannisX11/blockbench/blob/master/js/formats/bedrock/bedrock.js
- https://github.com/JannisX11/blockbench/blob/master/js/outliner/types/cube.js
- https://github.com/JannisX11/blockbench/blob/master/js/io/format.ts
- https://github.com/JannisX11/blockbench/blob/master/js/animations/timeline_animators.js

`ModelLuaApiTest` exercises real Lua declarations, callback-scoped poses, resource
ownership, incompatible-pack fallback, compatible replacement, and restoration of
bundled defaults. Provider-read counts guard against file loading during pose callbacks.

`BlockModelAdapterTest` captures native tessellator input to verify model units,
breaking-overlay UVs, texture-pack invalidation, and separation of dynamic visuals
from chunk geometry. Instrumentation tests transform the mapped and obfuscated
Minecraft rendering targets and check repeat application.

`ModelRenderTest` requires LWJGL natives and an OpenGL-capable desktop. It renders
distinct poses into an off-screen Pbuffer, checks visible pixel differences and GL
state restoration, and writes images under `build/model-render/`. Its callback-error
warning is intentional. These checks do not replace a live Minecraft test of block
placement, chunk rebuilds, inventory/held/dropped display, and texture-pack switching,
or a visual comparison with a model opened in Blockbench.

`ModelVariantTest` verifies metadata selection, default inheritance, explicit ordinary
fallback, static chunk geometry, and the block-wide dynamic policy. The OpenGL test
also runs `ModelVariantRenderTest`: it draws static, animated, and ordinary variants
through the dynamic scene, switches both ways on reload, and verifies that native
block identity, metadata, tile identity, inventories, and saved-chunk dirty state
remain unchanged. Chunk loading, removal, and new positions update only the draw index.
