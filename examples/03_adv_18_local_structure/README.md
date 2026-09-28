# Local structure example

Copy this entire directory into `.minecraft/lua_scripts/`. The package layout is intentional:

- `betamoon.mod.json` selects `main.lua` as the entrypoint.
- `main.lua` registers the structure as a feature and schedules it.
- `assets/example/worldgen/structures/tutorial_shrine.json` is gameplay data resolved from the structure key.

The JSON template has a bounded size, an origin used as the placement anchor, a weighted palette, explicit block positions, and an empty marker list. Its taller central pillar and glowstone beacon make the compact ruin easier to recognize. The placement makes one attempt in roughly every six newly generated Overworld chunks, although unsuitable ground or blocked sky can reject an attempt. Edit the template or capture a build with `betamoon.worldgen.structures:export`, then restart Minecraft before testing structural data changes.
