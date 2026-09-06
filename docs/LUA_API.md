# BetaMoon Lua API

The API uses registries, ordinary Lua definition tables, and resource references.
The earlier builder and chained-query functions are no longer exported to Lua.

## Registries and references

Blocks and items share the same lookup workflow:

```lua
local stone = betamoon.blocks:getRequired("minecraft:stone")
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

`get` returns a reference or `nil`; `getRequired` raises a descriptive error. `find`
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
local stone = betamoon.blocks:getRequired("minecraft:stone")
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

## Tile entities and containers

Stateful blocks use three standalone definitions. A tile entity owns persistent
data and named inventory slots, a container defines item interaction, and a
container GUI draws that container. The block explicitly connects all three:

```lua
local entity = betamoon.tileEntities:add {
    name = "machine",
    inventory = { slots = { input = {}, output = {} } },
    data = { progress = { type = "integer", default = 0, sync = true } },
    onTick = {
        mode = "continuous",
        action = function(ctx)
            local progress = ctx.entity.data:get("progress")
            ctx.entity.data:set("progress", progress + 1)
        end
    }
}

local container = betamoon.containers:add {
    name = "machine", tileEntity = entity,
    slots = {
        { slot = "input", x = 56, y = 17 },
        { slot = "output", x = 116, y = 35, outputOnly = true }
    },
    playerInventory = { x = 8, y = 84, includeHotbar = true }
}

local gui = betamoon.containerGuis:add {
    name = "machine", container = container,
    layout = {
        preset = "minecraft:furnace",
        title = "Machine"
    },
    elements = {{
        type = "progress", value = "progress", maximum = 100,
        x = 79, y = 34,
        builtin = "minecraft:furnace_arrow",
        tooltip = "Progress: {progress}/100"
    }}
}

betamoon.blocks:add {
    id = 204, material = "rock", key = "machine",
    tileEntity = entity, container = container, gui = gui
}
```

Tile data supports `integer`, `number`, `boolean`, and `string`. Inventory
access provides `get`, `set`, `remove`, `canAdd`, `add`, and `consumeFuel`.
Tick contexts expose Minecraft smelting recipes through
`ctx.recipes:getSmeltingResult(stack)` and fuel behavior through
`ctx.fuels:getBurnTime(stack)`.

Container GUIs are declarative. Lua describes the appearance, while BetaMoon
handles textures, clipping, mouse hover, and rendering. GUI elements can read
integer and boolean tile data fields marked `sync = true`.

`layout.preset` accepts `minecraft:container`, `minecraft:chest`,
`minecraft:dispenser`, `minecraft:furnace`, `minecraft:crafting`, and
`minecraft:inventory`. A chest layout also accepts `rows` from 1 to 6. The
`title` and `playerInventoryLabel` may be text, a label table, or `false`.

A custom background uses one complete PNG relative to the scripts directory:

```lua
background = { image = "my_mod/gui/machine.png" }
```

Its size is detected automatically. `background = { style = "minecraft",
drawSlotFrames = true }` creates a simple panel and draws frames around the
container's slots.

Available element types are `image`, `text`, `progress`, `state_image`,
`rectangle`, `item`, `tooltip`, and `group`. Custom element images are complete
PNG files, so scripts never supply texture-atlas coordinates. Built-in sprites
include `minecraft:furnace_flame`, `minecraft:furnace_arrow`,
`minecraft:crafting_arrow`, `minecraft:slot`, and `minecraft:output_slot`.

Progress elements accept a positive integer maximum or another synced field.
Directions are `left_to_right`, `right_to_left`, `top_to_bottom`, and
`bottom_to_top`. `background` draws an empty image below the fill image,
`minimumPixels` keeps a nonzero value visible, and `hideWhenEmpty` hides the fill
at zero.

Text elements accept either `text` or a synced field in `value`. A `format`
such as `"Heat: %d"` formats a field value. Colors may be an RGB/ARGB number or
a named color such as `dark_gray`, `red`, `green`, `yellow`, or `aqua`.

Every element accepts `anchor`, `layer`, `tooltip`, and `visibleWhen`. Anchors
cover the four corners, top/bottom center, and screen center. Layers are
`background`, `content`, and `foreground`. Tooltip text may contain synchronized
field placeholders such as `{progress}`.

Conditions use a field and one comparison:

```lua
visibleWhen = { field = "powered", equals = true }
visibleWhen = { field = "heat", greaterOrEqual = 100 }
visibleWhen = {
    all = {
        { field = "powered", equals = true },
        { field = "progress", greaterThan = 0 }
    }
}
```

The supported comparisons are `equals`, `notEquals`, `greaterThan`,
`greaterOrEqual`, `lessThan`, and `lessOrEqual`. Condition groups use `all` or
`any`. Groups also let several elements share a position offset and condition.

Tile entities and their connected definitions are structural content. Their
owning script remains active and is skipped during hot reload. Restart
Minecraft to apply changes to that script.

A tile-entity block may omit both `container` and `gui` when it has no screen.
Structural redstone behavior reads integer data fields and can react to neighbor
changes:

```lua
redstone = {
    weakPower = "power",
    strongPower = "power",
    onNeighborChanged = {
        action = function(ctx)
            ctx.entity.data:set("powered", ctx.powered)
            ctx.world:notifyNeighbors()
        end
    }
}
```

Minecraft Beta uses powered/unpowered booleans for these block methods, so a
field value from 1 through 15 means powered and zero means unpowered.

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

local dependency = betamoon.modules:getRequired("example")
```

Exports are owned by their declaring script and are removed during reload.
