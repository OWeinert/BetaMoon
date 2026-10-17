# Hollow Marches — advanced 20

Copy this entire folder into `.minecraft/betamoon/scripts/`, then select **Hollow
Marches** in Create World. The BetaMoon Java agent is required. No other example
is required. This adds one item, **Marches Surveyor** (ID 5050), and uses native
blocks for the landscape. Choose another ID if an installed mod owns it.

## What to look for

Spawn is surrounded by green lowlands. Travel a few hundred blocks outward:
terraced sandstone mesas, horizontal clay bands and intersecting cave sheets
appear in selected regions. Floating glowstone lanterns sit at Y=116 over the
strongest regions; they deliberately demonstrate an authored placement height,
not a surface offset. They do not form a solid ceiling.

Craft the surveyor from a compass, paper and redstone. Use it on a block to print
the region strength, terrain envelope, contributing height profiles and estimated
field sampling work. Sneak-use exports one raw chunk preview to BetaMoon's debug
directory. Previews do not load chunks, run population, or mutate the world.
The tool explains when used outside its preset.

## Read in this order

1. `main.lua` and `generation.lua`: register JSON in dependency order.
2. `region_noise.json`, `warp.json`, `regions.json`: domain-warp a horizontal
   noise, then multiply it by a smooth distance ramp from the **saved original
   spawn**, with no mesa influence within 192 blocks. Moving the respawn point
   does not move the regions. Keys and salts keep the spatial layout stable.
3. `lowlands.json`, `mesas.json`, `terrain.json`: blend two height envelopes on a
   chunk-independent 48-block grid. The mesas add ridges and six-block terraces.
   Region weights control influence; biome boundaries themselves remain discrete.
4. `materials.json`: choose stone below Y=61, clay bands and sandstone in strong
   regions, and grass/dirt in lowlands. `surfaces="none"` preserves these authored
   materials rather than replacing them afterward with biome surfaces.
5. `cave_noise.json`, `cave_field.json`, `caves.json`: warp a 3D field and carve
   near its zero crossings only in columns with a positive mesa-profile blend.
   Stone, sandstone and clay are replaceable; bedrock remains protected. Deep
   cavities use lava below Y=10 and base-fluid neighbors are protected.
6. `biomes.lua` and `generator.json`: the same region field selects a desert-based
   mesa biome, with a required forest-based lowland fallback. Original-spawn
   dependent selection is used with owned terrain, not a native terrain delegate.
7. `content.lua`: reuse the region selector for redstone and lantern placements.
   `lantern_height.json` is exposed as the `lanterns` height output. Native ores
   are disabled, while native trees/vegetation/animals remain available.
8. `surveyor.lua`: query detached diagnostics through a scoped item callback.

All listed JSON files are under `assets/worldgen/`. **Generation Settings...**
can disable individual placements or the mesa biome. Disabling the biome changes
biome eligibility, not the independently authored terrain shape or material rule.
The lowland fallback stays locked. The ore parent always takes precedence over
an individual vein's remembered checkmark. Native ores are one aggregate toggle.

## Experiments and limits

Try a different `blendWidth`, terrace `stepHeight`, or cave threshold. Change the
region ramp's outer distance to make mesas start farther away. Keep noise ranges,
field node/work limits and carver budgets valid. Do not export raw previews from
continuous tick callbacks. A raw preview includes materials/caves but excludes
population, so lanterns and ore placements are absent from it.

Use a new world for generation-definition edits. Saved worlds keep their choices,
original anchor and required dependency identities; incompatible edits are
rejected on reopening. Display-name edits do not change generation identity.
These are preset-owned definitions, not biome/region overrides of Minecraft's
native generator.
