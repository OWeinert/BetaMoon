# Rolling Meadows — advanced 17

Copy this entire folder into `.minecraft/betamoon/scripts/`, then select **Rolling
Meadows** with the **World Generation** button in Create World. Minecraft remains
the initial choice. **Generation Settings...** can toggle the preset's content.

This is a small complete terrain preset: broad grassy hills meet water at Y=60,
with sparse oaks, coal veins and bounded tunnel caves. It uses native blocks and
needs no companion example. Generation profiles require the BetaMoon Java agent.

## Read in this order

1. `main.lua`: register the biome, JSON inputs, content and finally the preset.
2. `hills_noise.json`: a seeded, bounded three-octave Perlin noise.
3. `height.json`: convert the noise's -1..1 range into heights 56..76.
4. `terrain.json`: fill below that height, with a bedrock floor and water level.
5. `caves.json`: carve tunnels only through stone, preserving the surface layers.
6. `generator.json`: bind the source, biome, caves and independent population choices.

All JSON files are under `assets/worldgen/`. The biome supplies ordinary Minecraft
surface rules; the source supplies the shape. Coal and trees use their own
placements, so native ores and trees are disabled to avoid doubling them. Other
native population categories retain their defaults. The preset's include lists
isolate its scheduled content from unrelated installed examples.

## Try changing it

Change the height field's `66` to move the land upward, or its `10` multiplier to
change hill amplitude. Keep a bounded height within Minecraft's 0..127 range.
Change `seaLevel` separately to compare land and water. Create another world for
each terrain edit: existing saves retain their choices and reject incompatible
changes to required generation definitions. Editing a display name is safe.

An unchecked ore parent disables the coal rule even if its individual checkmark
is remembered. Re-enabling it restores the individual choice. This example does
not alter Minecraft generation unless you select its preset for a new world.
