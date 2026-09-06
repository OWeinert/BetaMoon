# BetaMoon Lua API

The API uses registries, ordinary Lua definition tables, and resource references.
The earlier builder and chained-query functions are no longer exported to Lua.

## Registries and references

Blocks and items share the same lookup workflow:

```lua
local stone = betamoon.blocks:require("minecraft:stone")
local wool = betamoon.blocks:get(35)       -- nil when absent
local tools = betamoon.tools:find { owner = "minecraft" }
local pickaxe = betamoon.tools:one { id = 278 }

for _, item in ipairs(betamoon.items:find {
    nameContains = "ingot",
    ignoreCase = true
}) do
    print(item.id, item.displayName)
end
```

`get` returns a reference or `nil`; `require` raises a descriptive error. `find`
returns a normal 1-based Lua array with `:first()`, `:last()`, `:one()`,
`:isEmpty()`, and `:overrideAll()`. The registry `first` and `one` methods avoid
allocating a result wrapper when only one match is needed.

Block and item criteria support `id`, `damage`, `name`, `displayName`, `type`,
`nameContains`, `ignoreCase`, and `where = function(reference) ... end`. Tool and
armor registries apply their category filter before user criteria.

A reference exposes `id`, `damage`, `key`, `name`, `displayName`, `owner`,
`isVanilla`, `isBetaMoon`, and `exists`. Item references also expose `type`,
`category`, `maxStackSize`, `maxDamage`, `hasSubtypes`, and `icon`. Block
references expose `texture`, `light`, `lightOpacity`, `hardness`, and `resistance`.
References are plain property-oriented values; resource identity is read from
`id` and `damage`.

`betamoon.stack(item, count, damage)` produces the common stack table accepted by
recipes. When `item` is a reference its damage value is the default.

## Declarative content

```lua
local copper = betamoon.blocks:add {
    id = 200,
    material = "rock",
    key = "copper_ore",
    displayName = "Copper Ore",
    hardness = 3.0,
    resistance = 5.0,
    light = 0,
    lightOpacity = 255,
    texture = "example/copper_ore.png",
    harvest = { pickaxe = 1 }
}

local food = betamoon.items:add {
    id = 5000,
    type = "food",
    key = "berry",
    displayName = "Berry",
    food = { healing = 2, wolfFood = false },
    maxStackSize = 64,
    icon = { x = 3, y = 5 }
}
```

`items:add` supports ordinary items and food. `tools:add` accepts `type` values
`pickaxe`, `axe`, `shovel`, `hoe`, and `sword` plus a material. `armor:add` accepts
an armor material and slot. Definitions return the same reference type as a query.

Custom blocks can run gameplay updates and separate client-side display effects:

```lua
betamoon.blocks:add {
    id = 201,
    material = "rock",
    key = "smoking_block",

    onTick = {
        mode = "scheduled",
        schedule = { delay = 20, repeatEvery = 20 },
        action = function(ctx)
            -- Gameplay logic runs once per second.
        end
    },

    onDisplayTick = {
        chance = 0.25,
        attempts = 1,
        action = function(ctx)
            ctx.world:spawnParticle("smoke", {
                x = ctx.x + 0.5, y = ctx.y + 1, z = ctx.z + 0.5
            })
        end
    }
}
```

`onTick.mode` accepts `default`, `random`, or `scheduled`. `default` updates
every game tick. `random` uses Minecraft's random block selection. `scheduled`
requires `schedule.delay`; `schedule.repeatEvery` is optional. An action can call
`ctx:schedule(delay)` to request another update itself.

`onDisplayTick` is independent from gameplay ticking. Its `chance` defaults to
`1`, and `attempts` defaults to `1`. Each attempt makes a separate chance check.
The context exposes the block position, ID, damage, `random()`, and a limited
world facade with `getBlock`, `setBlock`, `spawnParticle`, and `playSound`.

Materials are declared through `materials.tools:add` and `materials.armor:add`:

```lua
local copperTool = betamoon.materials.tools:add {
    key = "COPPER", harvestLevel = 1, durability = 180,
    efficiency = 5.0, damage = 1
}
```

Recipes use one definition shape and return a recipe reference:

```lua
local recipe = betamoon.recipes:add {
    type = "shaped",
    output = betamoon.stack(food, 2),
    pattern = { "SS", "SS" },
    ingredients = { S = stone }
}
```

Supported recipe types are `shaped`, `shapeless`, and `smelting`. Recipe queries
accept `type`, `output`, and `input`. A recipe reference exposes `key`, `type`,
`output`, `owner`, `exists`, `override`, and `disable`.

Ore and biome definitions live under `worldgen.ores:add` and
`worldgen.biomes:add`. Ore definitions use `block`, `veinsPerChunk`, `veinSize`,
`height = { min, max }`, with optional `dimension`, `replace`, and `biomes`.
Biome definitions support `basedOn`, colors, `surface`, temperature/humidity
`range`, `trees`, `weather`, and spawn groups.

## Overrides

Overrides patch supported properties on an existing registry object. The short
form belongs to the reference:

```lua
local stone = betamoon.blocks:require("minecraft:stone")
local patch = stone:override {
    displayName = "Polished Stone",
    texture = "example/polished_stone.png",
    hardness = 2.0
}
```

The full declarative form adds conditions and priority:

```lua
local patch = betamoon.overrides:add {
    target = stone,
    when = {
        owner = "minecraft",
        properties = { hardness = 1.5 }
    },
    priority = 10,
    changes = {
        displayName = "Dense Stone",
        hardness = 2.0
    }
}
```

Conditions never partially apply. A mismatch returns a handle with `active = false`
and a `reason`; it does not change the target. An active handle has `:remove()`.
Removal is idempotent. Reload removes all layers owned by the old script.

Overrides do not require caller-provided keys. A property layer already has a
complete natural identity: the loading script owns it, the reference identifies
the target, and the changed field identifies the property. Requiring another key
would duplicate that information and create avoidable naming and collision rules.
Layers on the same target property are ordered by `priority`, then declaration
order. Removing the winning layer reveals the next layer; removing the last layer
restores the value captured before the first override.

Supported block properties are `displayName`, `texture`, `hardness`, `resistance`,
`light`, and `lightOpacity`. Item properties are `displayName`, `texture`/`icon`,
`maxStackSize`, `maxDamage`, and `hasSubtypes`; food additionally supports
`healing` and `wolfFood`. Recipe overrides support `output` and `enabled`, and
`recipe:disable()` is shorthand for `enabled = false`.

Changing block material, item Java type, tool type/material, or armor material/slot
would require replacing the registry object. Minecraft and mods retain direct Java
references to that object, so doing this during play would leave mixed old/new
instances. BetaMoon therefore treats these fields as registration identity and
requires a restart when they change.

## Events and reload ownership

```lua
local subscription = betamoon.events:on("block_broken", function(event)
    print(event.x, event.y, event.z, event.id)
end)

subscription:unsubscribe()
```

Event names use snake case. The named `onBlockBroken` style remains available.
Subscriptions, recipes, override layers, module exports, ore generators, and biome
overlays belong to the script that declared them and are removed before its next
generation loads. Resource registrations retain their object and numeric ID and
update mutable properties in place.

Event contexts expose values as fields. Depending on the event these include
`name`, `worldName`, `worldInfo`, `x`, `y`, `z`, `position`, `side`,
`id`, `damage`, `displayName`, `type`, `keyCode`, `char`, `button`,
`pressed`, `released`, `action`, `item`, `count`, `oldId`, and `newId`.

## Utilities and script modules

`chat:send(format, ...)` writes to the local client and
`chat:broadcast(format, ...)` broadcasts when a server is available.
`positions:integer(x, y, z)` and `positions:float(x, y, z)` create the position
values accepted by APIs and returned by event contexts.

Scripts share references through a named module:

```lua
local public = betamoon.modules:export("example")
public.block = betamoon.blocks:add { ... }

local dependency = betamoon.modules:require("example")
```

Exports are owned by their declaring script and are removed during reload.
