# Regional structures example

Copy this complete folder into `.minecraft/lua_scripts/`, then restart Minecraft after changing its structural data.

The three JSON templates are local structures. Their `connector` markers use the shared `path` pool and cardinal facings:

- `path_ruins_gate.json` is the regional root and exposes one east-facing connector.
- `path_ruins_road.json` has west- and east-facing connectors, so it can extend a route.
- `path_ruins_courtyard.json` has one west-facing connector, so it terminates a route.

The planner rotates candidate pieces to match opposite connector facings, rejects colliding or out-of-bounds pieces, and stops at the configured depth, piece count, distance, or termination chance. The Ruin Surveyor calls `locate` without loading terrain.
