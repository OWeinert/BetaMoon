# Lua hot reload

BetaMoon watches `.minecraft/lua_scripts` while Minecraft is running. A change is
accepted after the directory has remained stable for 500 ms. Use **Reload** on
the Scripts screen to reload immediately.

During normal gameplay, press **Ctrl+Shift+R** to reload immediately. The
primary key appears as **Reload Lua Scripts** in Minecraft's Controls menu and
can be changed there; Ctrl and Shift are always required. The shortcut is
ignored while a menu or chat box is open.

Before replacing active scripts, BetaMoon compiles every Lua file. A syntax
error leaves the active generation running. A successful reload removes the
old generation's event listeners, recipes, module exports, ore generators and
biome lookup overlays, then loads all scripts again in dependency order.

Each script must declare `modInit`; it runs during initial loading and every
successful reload. An optional `modUnload` runs on the active generation just
before automatic cleanup. After the replacement generation's `modInit`
succeeds, its optional `modReload` runs. Errors in either reload hook are
reported without preventing cleanup of the remaining scripts.

Blocks and items keep the same Java object and numeric ID across reloads. Their
ordinary properties can be changed in place. A registered resource cannot
change its Java category during play: block material, normal item/food,
pickaxe/axe/shovel/hoe/sword, armor material and armor slot changes require a
Minecraft restart. BetaMoon reports such a change as a script error instead of
replacing a live registry object.

Removing a block or item declaration does not free its ID during play. The
registration is retained until restart, while its recipes and world generators
are removed. This avoids corrupting loaded chunks, inventories and references
held by Minecraft or another mod. A future world-migration feature can retire
content by replacing blocks with air and deleting item stacks on chunk/player
load; that operation is intentionally separate because it modifies saves.

## API layout

The public API uses declarative definitions and direct registry queries:

```lua
local bm = betamoon

local copper = bm.blocks:add {
    id = 200,
    material = "rock",
    key = "copper_ore",
    displayName = "Copper Ore",
    hardness = 3,
    texture = "example/copper_ore.png"
}

bm.recipes:add {
    type = "shapeless",
    output = bm.stack(copper, 1),
    ingredients = { bm.blocks:require("minecraft:stone") }
}

bm.events:on("game_tick", function(event)
    -- The returned subscription also has :unsubscribe().
end)
```

| Namespace | Functions |
| --- | --- |
| `blocks` | `get`, `require`, `find`, `first`, `one`, `add` |
| `items`, `tools`, `armor` | `get`, `require`, `find`, `first`, `one`, `add` |
| `materials.tools`, `materials.armor` | `get`, `require`, `add` |
| `recipes` | `get`, `require`, `find`, `first`, `one`, `add` |
| `worldgen.ores`, `worldgen.biomes` | declarative `add` |
| `events` | `on(name, callback)` |
| `overrides` | declarative `add` for a queried resource |
| `chat` | `send`, `broadcast` |
| `modules` | `export`, `require` |
| `positions` | `integer`, `float` |

See `LUA_API.md` for definitions, query criteria, and override semantics.
