# Complete worldgen pack example

Copy this whole directory into `.minecraft/lua_scripts/`. It is a capstone rather than a minimal snippet:

- `main.lua` controls registration order and dependency injection.
- `features.lua` registers a procedural tree and weighted ground-accent features.
- `structures.lua` constructs and registers a processed local structure with the table-backed Lua builder.
- `biomes.lua` creates the surface, decorator placement templates, keyed biome, and active climate source.

The active source has priority 30 so it wins if the earlier biome tutorial examples are installed at the same time. An application should normally coordinate active source ownership deliberately rather than relying on tutorial priorities.
