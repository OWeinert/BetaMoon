# Local structure example

Copy this entire directory into `.minecraft/lua_scripts/`. The package layout is intentional:

- `betamoon.mod.json` selects `main.lua` as the entrypoint.
- `main.lua` registers the structure as a feature and schedules it.
- `assets/example/worldgen/structures/tutorial_shrine.json` is gameplay data resolved from the structure key.
- `assets/example/loot_tables/shrine_supplies.json` defines the shrine chest's reusable deterministic loot.

The JSON template uses signed coordinates around its placement origin, named palette states, compact fill and block geometry, and a reusable pillar template placed as an array. Its processor pipeline first uses smooth noise to replace some cobblestone with mossy cobblestone, then independently removes a small number of exposed stones while excluding the foundation. Processor randomness is derived from the world and placement seeds, so recreating the same world produces the same weathering. The protected glowstone and chest are not eligible for either pass. A dedicated loot element decorates the chest from the external table after the structure blocks exist. Its taller central pillar and glowstone beacon make the compact ruin easier to recognize. Roughly one in sixty-four newly generated Overworld chunks is eligible. An eligible chunk tries up to four surface positions and stops after placing its first shrine, so a tree, water, or another unsuitable position does not discard the entire chunk. The candidate centers keep the complete five-by-five template inside its generating chunk. Edit the template or capture a build with `betamoon.worldgen.structures:export`, then restart Minecraft before testing structural data changes.

The shrine samples its complete automatically detected floor footprint, chooses a median solid-surface anchor, embeds its floor in the top terrain layer, rejects steep or submerged sites, and fills small gaps with a bounded cobblestone foundation. Craft a Shrine Surveyor from a compass and glowstone dust, then use it on a block. It reports the nearest chunk that passes the shrine placement's deterministic rarity roll and lists the four possible shrine centers. Unlike a regional structure lookup, this is a placement candidate rather than a saved structure location: terrain and site conditions can still reject every position, and the locator does not load or generate terrain.
