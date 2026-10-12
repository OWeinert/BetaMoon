# Regional structures example

Copy this complete folder into `.minecraft/lua_scripts/`, then restart Minecraft after changing its structural data.

The four JSON templates are local structures. Their `connector` markers use the shared `path` pool and cardinal facings:

- `path_ruins_gate.json` is the regional root and exposes one east-facing connector.
- `path_ruins_road.json` has west- and east-facing connectors, so it can extend a route.
- `path_ruins_crossroads.json` has four connectors, so one incoming route can branch in three directions.
- `path_ruins_courtyard.json` has one west-facing connector, so it terminates a route.

The road is deliberately weighted most heavily, the per-connector termination chance is low, and a plan may contain up to twelve pieces. Most ruins therefore form a substantial path network rather than stopping at the gate. Every piece uses `verticalOffset = -1`, embedding its floor in the sampled top terrain layer rather than placing it above the terrain. Each template's decay processor excludes floor elements tagged `foundation`, ensuring the complete ruin floor replaces grass, dirt, or stone rather than exposing retained terrain through random decay. Road columns marked `terrain_conform` follow nearby solid ground with bounded one-block steps and replace the surface blocks beneath them, while the gate, crossroads, and courtyard remain rigid pieces with bounded cobblestone foundations. The regional land-site profile prevents starts on water and may try eight deterministic positions inside the selected chunk. The planner rotates candidate pieces to match opposite connector facings, keeps connected endpoints aligned, rejects colliding or unsuitable pieces, and stops at the configured depth, piece count, distance, or termination chance. The resolved terrain work is saved with the plan. The Ruin Surveyor calls `locate` without loading terrain.
