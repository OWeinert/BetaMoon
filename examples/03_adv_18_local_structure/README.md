# Local structure example

Copy this entire directory into `.minecraft/lua_scripts/`. The package layout is intentional:

- `betamoon.mod.json` selects `main.lua` as the entrypoint.
- `main.lua` registers the structure as a feature and schedules it.
- `assets/example/worldgen/structures/tutorial_shrine.json` is gameplay data resolved from the structure key.

The JSON template has a bounded size, an origin used as the placement anchor, a weighted palette, explicit block positions, and an empty marker list. Its taller central pillar and glowstone beacon make the compact ruin easier to recognize. Roughly one in six newly generated Overworld chunks is eligible. An eligible chunk tries up to four surface positions and stops after placing its first shrine, so a tree, water, or another unsuitable position does not discard the entire chunk. The candidate centers keep the complete five-by-five template inside its generating chunk. Edit the template or capture a build with `betamoon.worldgen.structures:export`, then restart Minecraft before testing structural data changes.

Craft a Shrine Surveyor from a compass and glowstone dust, then use it on a block. It reports the nearest chunk that passes the shrine placement's deterministic rarity roll and lists the four possible shrine centers. Unlike a regional structure lookup, this is a placement candidate rather than a saved structure location: surface conditions can still reject every position, and the locator does not load or generate terrain.
