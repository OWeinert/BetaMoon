local bm = betamoon
local function fails(fragment, action)
    local ok, message = pcall(action)
    assert(not ok and tostring(message):find(fragment, 1, true), tostring(message))
end
local function reason(expected, ok, why)
    assert(not ok and why == expected, tostring(ok) .. ": " .. tostring(why))
end

owner("module-test.lua")
local exported = { value = 42 }
assert(select("#", bm.modules:export("test_import", exported)) == 0)
fails("not exported", function() bm.modules:import("test_import") end)
publish("module-test.lua")
assert(bm.modules:import("test_import") == exported)
unload("module-test.lua")
fails("not exported", function() bm.modules:import("test_import") end)

owner("types.lua")
local schema = {
    name = "test:alloying",
    ingredients = { base = {}, additive = {}, mold = { optional = true, consume = false } },
    outputs = { result = {}, slag = { optional = true } }, primaryOutput = "result",
    data = { duration = { type = "integer", default = 100, min = 1 }, label = { type = "string", default = "test" } },
    context = { heat = { type = "number" }, powered = { type = "boolean" } }
}
local kind = bm.recipeTypes:add(schema)
assert(kind.name == "test:alloying" and kind.owner == "types.lua" and kind.exists)
assert(bm.recipeTypes:add(schema) == kind)
local schemaCopy = kind.ingredients
schemaCopy.base.consume = false
assert(kind.ingredients.base.consume)
assert(bm.recipeTypes:get("test:absent") == nil)
fails("unknown type", function() bm.recipeTypes:getRequired("test:absent") end)
assert(bm.recipeTypes:find { nameContains = "ALLOY", ignoreCase = true }:one() == kind)
assert(bm.recipeTypes:find { owner = "types.lua" }.overrideAll == nil)
schema.data.duration.default = 200
fails("restart Minecraft", function() bm.recipeTypes:add(schema) end)
schema.data.duration.default = 100
schema.ingredients.base.typ = "item"
fails("unknown field", function() bm.recipeTypes:add(schema) end)
schema.ingredients.base.typ = nil
owner("foreign.lua")
fails("already owned", function() bm.recipeTypes:add(schema) end)

-- A public stack includes both id and item; it is not an ingredient descriptor.
owner("stack-regression.lua")
local stackRecipe = bm.recipes:add {
    key = "test:stack_input", type = kind,
    ingredients = { base = bm.stack(bm.blocks:getRequired(14)), additive = 263 },
    output = 266
}
assert(stackRecipe.ingredients.base.count == 1)
assert(bm.recipes:match { type = kind, ingredients = { base = 14, additive = 263 } }.recipe == stackRecipe)
unload("stack-regression.lua")

owner("recipes.lua")
local declaration = {
    key = "test:iron", type = kind,
    ingredients = { base = { anyOf = { 15, 14, 15 }, count = 3 }, additive = 263 },
    outputs = { result = bm.stack(265, 4), slag = bm.stack(4, 1) }
}
local recipe = bm.recipes:add(declaration)
assert(recipe.output.count == 4 and recipe.data.duration == 100)
assert(recipe.recipeType == kind and recipe.type == kind.name)
assert(#recipe.ingredients.base.anyOf == 2)
fails("duplicate recipe key", function() bm.recipes:add(declaration) end)
declaration.key = "test:bad"
declaration.data = { duration = "10" }
fails("data.duration", function() bm.recipes:add(declaration) end)
declaration.data = { duration = 0 }
fails("data.duration", function() bm.recipes:add(declaration) end)
declaration.data = { duration = 1.5 }
fails("32-bit integer", function() bm.recipes:add(declaration) end)
declaration.data = nil
declaration.ingredients.base.count = 0
fails("positive integer", function() bm.recipes:add(declaration) end)
declaration.ingredients.base.count = 3
declaration.outputs.slag = bm.stack(4, 65)
fails("capacity", function() bm.recipes:add(declaration) end)
declaration.outputs.slag = bm.stack(4, 1)
declaration.key = "test:iron"
assert(bm.recipes:get(recipe.key) == recipe)
assert(bm.recipes:one { type = { "smelting", kind }, output = bm.stack(265, 4), owner = "recipes.lua" } == recipe)
assert(bm.recipes:find { type = kind, input = 14, ingredients = { additive = 263 }, outputs = { slag = bm.stack(4, 0) }, data = { duration = { min = 100 } } }:one() == recipe)
assert(bm.recipes:find { type = kind, data = { absent = 1 } }:isEmpty())
assert(not bm.recipes:find { type = { kind, "smelting" }, input = { item = 15, damage = "any" }, output = { item = 265, count = 0, damage = "any" } }:isEmpty())
fails("expected a number", function() bm.recipes:find { type = "test:absent", data = { duration = { min = "bad" } } } end)
local pure = bm.recipes:match { type = kind, ingredients = { base = bm.stack(15, 3), additive = bm.stack(263, 1) } }
assert(pure and pure.apply == nil and pure.output.count == 4)
pure.output.count = 50
assert(recipe.output.count == 4)
assert(bm.recipes:match { type = kind, ingredients = { base = bm.stack(15, 2), additive = bm.stack(263, 1) } } == nil)
assert(bm.recipes:match { type = kind, ingredients = { base = bm.stack(15, 3), additive = bm.stack(263, 1), mold = bm.stack(280) } } == nil)

owner("machine.lua")
local notifications, reentrant = 0, nil
local slots = { ingredients = { base = "left", additive = "right", mold = "mold" }, outputs = { result = "out", slag = "slag" } }
local notify = false
local tile = bm.tileEntities:add {
    name = "test:machine",
    inventory = { slots = { left = { index = 0 }, right = { index = 1 }, mold = { index = 2 }, out = { index = 3 }, slag = { index = 4 } } },
    data = { signature = { type = "string", default = "" }, progress = { type = "integer", default = 0 } },
    onInventoryChanged = { action = function(ctx)
        notifications = notifications + 1
        if notify then
            local nextMatch = ctx.recipes:match { type = kind, slots = slots }
            if nextMatch then reentrant = select(2, nextMatch:apply()) end
        end
    end }
}
local ctx = entity(tile.name)
local inv = ctx.entity.inventory
local function fill()
    inv:set("left", bm.stack(15, 6)); inv:set("right", bm.stack(263, 2))
    inv:set("mold", nil); inv:set("out", nil); inv:set("slag", nil)
end
local function match() return ctx.recipes:match { type = kind, slots = slots } end
fill()
fails("already bound", function() ctx.recipes:match { type = kind, slots = { ingredients = slots.ingredients, outputs = { result = "left", slag = "slag" } } } end)
local plan = match()
local sig = plan.signature
inv:set("left", bm.stack(15, 9))
assert(match().signature == sig)
inv:set("slag", bm.stack(4, 64))
reason("output_full", plan:apply())
assert(inv:get("left").count == 9 and inv:get("out") == nil)
inv:set("slag", nil)
local before = notifications
notify = true
assert(plan:apply())
notify = false
assert(notifications == before + 1 and reentrant == "reentrant_apply")
assert(inv:get("left").count == 6 and inv:get("right").count == 1 and inv:get("out").count == 4)
reason("already_applied", plan:apply())
plan = match()
inv:set("left", bm.stack(14, 6))
reason("inputs_changed", plan:apply())
fill()
plan = match()

owner("patches.lua")
local patch = bm.overrides:add { target = recipe, priority = 10, changes = { output = bm.stack(265, 5), data = { duration = 60 } } }
reason("stale_recipe", plan:apply())
assert(recipe.output.count == 5 and recipe.data.duration == 60)
local lower = recipe:override { priority = 0, changes = { outputs = { result = bm.stack(265, 2) } } }
assert(recipe.output.count == 5 and recipe.outputs.slag == nil)
patch:remove(); patch:remove()
assert(recipe.output.count == 2 and recipe.data.duration == 100)
lower:remove()
assert(recipe.output.count == 4 and recipe.outputs.slag.count == 1)
local originalSignature = match().signature
fails("data.duration", function() recipe:override { changes = { output = bm.stack(265, 9), data = { duration = -1 } } } end)
assert(recipe.output.count == 4 and match().signature == originalSignature)
local inactive = recipe:override { when = { type = "smelting" }, changes = { output = bm.stack(265, 8) } }
assert(not inactive.active and inactive.reason)
local disabled = recipe:disable()
assert(recipe.exists and not recipe.enabled and match() == nil)
assert(bm.recipes:find { type = kind }:isEmpty())
assert(bm.recipes:find { type = kind, enabled = false }:one() == recipe)
disabled:remove()
local bulk = bm.recipes:find { type = kind }:overrideAll { output = bm.stack(265, 6) }
assert(recipe.output.count == 6)
bulk[1]:remove()

owner("extra.lua")
local catalyst = bm.recipes:add { key = "test:catalyst", type = kind,
    ingredients = { base = bm.stack(15, 3), additive = 263, mold = { item = 280, damage = "any" } },
    output = bm.stack(265, 7), conditions = { heat = { min = 600, max = 1000 }, powered = true } }
fill(); inv:set("mold", bm.stack(280, 1, 3))
assert(match() == nil)
local hot = ctx.recipes:match { type = kind, slots = slots, context = { heat = 600, powered = true } }
assert(hot and hot.recipe == catalyst)
fails("fresh context", function() hot:apply() end)
reason("conditions_changed", hot:apply { context = { heat = 599, powered = true } })
assert(hot:apply { context = { heat = 700, powered = true } })
assert(inv:get("mold").damage == 3 and inv:get("out").count == 7)
fill()
plan = match()
local preferred = bm.recipes:add { key = "test:preferred", type = kind, priority = 10,
    ingredients = { base = bm.stack(15, 3), additive = 263 }, output = bm.stack(266, 2) }
reason("stale_recipe", plan:apply())
inv:set("out", bm.stack(265, 1))
assert(match().recipe == preferred)
reason("output_full", match():canApply())
unload("extra.lua")
assert(not preferred.exists and not catalyst.exists)

owner("containers.lua")
local containers = bm.recipeTypes:add { name = "test:containers", ingredients = { liquid = {} }, outputs = { result = {}, containers = { optional = true } }, primaryOutput = "result" }
local milk = bm.recipes:add { key = "test:milk", type = containers, input = 335, output = bm.stack(265) }
local containerSlots = { ingredients = { liquid = "left" }, outputs = { result = "out", containers = "slag" } }
fill(); inv:set("left", bm.stack(335))
local milkMatch = ctx.recipes:match { type = containers, slots = containerSlots }
assert(milkMatch.remainders[1].stack.id == 325)
assert(milkMatch:apply())
assert(inv:get("left").id == 325 and inv:get("out").id == 265)
local route = milk:override { changes = { ingredients = { liquid = { item = 335, remainder = { output = "containers", stack = bm.stack(325) } } } } }
fill(); inv:set("left", bm.stack(335)); inv:set("slag", bm.stack(4))
reason("remainder_blocked", ctx.recipes:match { type = containers, slots = containerSlots }:apply())
assert(inv:get("left").id == 335 and inv:get("out") == nil)
inv:set("slag", nil)
assert(ctx.recipes:match { type = containers, slots = containerSlots }:apply())
assert(inv:get("left") == nil and inv:get("slag").id == 325)
route:remove()
-- Removing a prerequisite patch deactivates a now-invalid dependent patch.
local prerequisite = milk:override { priority = 1, changes = { output = bm.stack(4) } }
local dependent = milk:override { priority = 2, changes = {
    ingredients = { liquid = { item = 335, remainder = { output = "result", stack = bm.stack(4) } } }
} }
assert(dependent.active)
prerequisite:remove()
assert(not dependent.active and dependent.reason and milk.output.id == 265)
dependent:remove()

owner("fabrication.lua")
local generatedPool = bm.recipeBindings:pool { prefix = "input_", count = 3 }
assert(generatedPool.kind == "item_pool" and generatedPool.count == 3)
assert(generatedPool[2] == "input_2" and generatedPool:toTable()[3] == "input_3")
generatedPool[2] = "changed"; generatedPool.count = 99
assert(generatedPool[2] == "input_2" and generatedPool.count == 3)
local generatedGrid = bm.recipeBindings:grid { prefix = "cell_", width = 2, height = 2 }
assert(generatedGrid.kind == "item_grid" and generatedGrid.width == 2 and generatedGrid.height == 2)
assert(generatedGrid[2][1] == "cell_2_1" and generatedGrid:toTable()[1][2] == "cell_1_2")
generatedGrid.width = 99
assert(generatedGrid.width == 2)

local pooledType = bm.recipeTypes:add {
    name = "test:pooled", ingredients = { materials = { type = "item_pool" } },
    outputs = { result = {}, byproducts = { type = "item_output_pool" } }, primaryOutput = "result"
}
local pooledRecipe = bm.recipes:add {
    key = "test:pooled_recipe", type = pooledType,
    ingredients = { materials = { { anyOf = { 263, 265 }, count = 2 }, 263 } },
    outputs = { result = 266, byproducts = { 4 } }
}
assert(bm.recipes:find { type = pooledType, input = 265 }:one() == pooledRecipe)
assert(bm.recipes:find { type = pooledType, ingredients = { materials = 263 },
    outputs = { byproducts = 4 } }:one() == pooledRecipe)
local pooledSlots = {
    ingredients = { materials = bm.recipeBindings:pool { "left", "right", "mold" } },
    outputs = { result = "out", byproducts = bm.recipeBindings:pool { "slag" } }
}
inv:set("left", bm.stack(263)); inv:set("right", bm.stack(265, 2)); inv:set("mold", nil)
inv:set("out", nil); inv:set("slag", nil)
local pooledMatch = ctx.recipes:match { type = pooledType, slots = pooledSlots }
assert(pooledMatch and pooledMatch.recipe == pooledRecipe and #pooledMatch.allocations.materials == 2)
inv:set("slag", bm.stack(3))
reason("output_full", pooledMatch:apply())
assert(inv:get("left").id == 263 and inv:get("right").count == 2 and inv:get("out") == nil)
inv:set("slag", nil)
assert(pooledMatch:apply())
assert(inv:get("left") == nil and inv:get("right") == nil)
assert(inv:get("out").id == 266 and inv:get("slag").id == 4)
inv:set("left", bm.stack(263)); inv:set("right", bm.stack(265, 2)); inv:set("mold", bm.stack(280))
inv:set("out", nil); inv:set("slag", nil)
assert(ctx.recipes:match { type = pooledType, slots = pooledSlots } == nil)

local gridType = bm.recipeTypes:add {
    name = "test:grid_fabrication",
    ingredients = { grid = { type = "item_grid", width = 2, height = 2,
        transformations = { "rotate_90" } } },
    outputs = { result = {} }
}
local gridRecipe = bm.recipes:add {
    key = "test:grid_recipe", type = gridType,
    ingredients = { grid = { pattern = { "X ", "XX" }, key = { X = 4 } } }, output = 265
}
local gridSlots = {
    ingredients = { grid = bm.recipeBindings:grid { { "left", "right" }, { "mold", "out" } } },
    outputs = { result = "slag" }
}
inv:set("left", bm.stack(4)); inv:set("right", bm.stack(4)); inv:set("mold", bm.stack(4))
inv:set("out", nil); inv:set("slag", nil)
local gridMatch = ctx.recipes:match { type = gridType, slots = gridSlots }
assert(gridMatch and gridMatch.recipe == gridRecipe)
assert(gridMatch.allocations.grid.transformation == "rotate_90")
assert(gridMatch:apply() and inv:get("slag").id == 265)
assert(inv:get("left") == nil and inv:get("right") == nil and inv:get("mold") == nil)
inv:set("left", bm.stack(4)); inv:set("right", bm.stack(4)); inv:set("mold", bm.stack(4))
inv:set("out", bm.stack(4)); inv:set("slag", nil)
assert(ctx.recipes:match { type = gridType, slots = gridSlots } == nil)

local orderedMatcher = bm.recipeMatchers:add {
    name = "test:ordered_pool",
    match = function(recipeDefinition, snapshot, recipeContext, planBuilder)
        assert(recipeDefinition.key == "test:ordered_recipe" and recipeDefinition.type == "test:ordered")
        local materials = snapshot.materials
        if not materials[1].stack or materials[1].stack.id ~= 263
                or not materials[2].stack or materials[2].stack.id ~= 265 then
            return false
        end
        planBuilder:use { role = "materials", slot = 1, requirement = 2 }
        planBuilder:use { role = "materials", slot = 2, requirement = 1 }
        return true
    end
}
assert(orderedMatcher.exists and bm.recipeMatchers:get("test:ordered_pool") == orderedMatcher)
local orderedType = bm.recipeTypes:add {
    name = "test:ordered", matcher = orderedMatcher,
    ingredients = { materials = { type = "item_pool", allowExtra = true } }, outputs = { result = {} }
}
local orderedRecipe = bm.recipes:add {
    key = "test:ordered_recipe", type = orderedType, ingredients = { materials = { 265, 263 } }, output = 266
}
local orderedSlots = {
    ingredients = { materials = bm.recipeBindings:pool { "left", "right" } }, outputs = { result = "out" }
}
inv:set("left", bm.stack(263)); inv:set("right", bm.stack(265)); inv:set("out", nil)
local orderedMatch = ctx.recipes:match { type = orderedType, slots = orderedSlots }
assert(orderedMatch and orderedMatch.recipe == orderedRecipe and orderedMatch:apply())
inv:set("left", bm.stack(265)); inv:set("right", bm.stack(263)); inv:set("out", nil)
assert(ctx.recipes:match { type = orderedType, slots = orderedSlots } == nil)

local invalidMatcher = bm.recipeMatchers:add { name = "test:invalid_plan", match = function() return true end }
local invalidType = bm.recipeTypes:add {
    name = "test:invalid_matcher_type", matcher = invalidMatcher,
    ingredients = { material = {} }, outputs = { result = {} }
}
bm.recipes:add { key = "test:invalid_matcher_recipe", type = invalidType, input = 4, output = 265 }
inv:set("left", bm.stack(4)); inv:set("out", nil)
assert(ctx.recipes:match { type = invalidType,
    slots = { ingredients = { material = "left" }, outputs = { result = "out" } } } == nil)
assert(not invalidMatcher.exists)

owner("native.lua")
local native = bm.recipes:add { type = "smelting", input = 15, output = bm.stack(265, 2) }
assert(native and native.output.count == 2 and native.type == "smelting")
assert(ctx.recipes:getSmeltingResult(nil) == nil)
fill(); inv:set("left", bm.stack(15, 2, 7))
local furnaceSlots = { ingredients = { input = "left" }, outputs = { output = "out" } }
local furnace = ctx.recipes:match { type = "smelting", slots = furnaceSlots }
assert(furnace.output.count == 2)
assert(furnace:apply())
assert(inv:get("left").count == 1 and inv:get("left").damage == 7)
furnace = ctx.recipes:match { type = "smelting", slots = furnaceSlots }
nativeSmelting(3)
reason("stale_recipe", furnace:apply())
local shape = bm.recipes:add { type = "shaped", output = bm.stack(265, 3), pattern = { "XX", "XX" }, key = { X = 4 } }
local duplicate = bm.recipes:add { type = "shaped", output = bm.stack(265, 3), pattern = { "XX", "XX" }, ingredients = { X = 3 } }
assert(shape ~= duplicate and shape.key ~= duplicate.key)
local shapePatch = shape:override { output = bm.stack(265, 4) }
assert(shape.output.count == 4 and duplicate.output.count == 3)
assert(bm.recipes:get(shape.key) == shape)
shapePatch:remove()
local shapeDisabled = bm.overrides:add { target = shape, changes = { enabled = false } }
assert(shape.exists and not shape.enabled)
assert(bm.recipes:find { type = "shaped", enabled = false, input = 4, owner = "native.lua" }:one() == shape)
shapeDisabled:remove()
opaqueRecipe()
assert(not bm.recipes:find { output = bm.stack(264, 7) }:isEmpty())
fails("grid/pool", function() bm.recipes:match { type = "shapeless", ingredients = {} } end)
fails("expected a boolean", function() shape:override { changes = { output = bm.stack(265, 9), enabled = "false" } } end)
assert(shape.output.count == 3)

owner("recipes.lua")
fill()
local saved = match().signature
ctx.entity.data:set("signature", saved); ctx.entity.data:set("progress", 30)
ctx = saveLoad(); inv = ctx.entity.inventory
assert(match().signature == saved and ctx.entity.data:get("progress") == 30)
local stale = match()
unload("recipes.lua")
assert(not recipe.exists and not patch.active and match() == nil)
reason("stale_recipe", stale:apply())
local replacement = bm.recipes:add(declaration)
assert(replacement ~= recipe and replacement.exists and not recipe.exists)
assert(match().signature == saved)
unload("types.lua")
owner("types.lua")
assert(bm.recipeTypes:add(schema) == kind)
local detached = match()
detach()
reason("invalid_inventory", detached:apply())
unload("native.lua")
assert(not shape.exists and not duplicate.exists)
print("All custom recipe Lua assertions passed")

-- Removing a shadowed native declaration must not resurrect it later; foreign
-- patches must not overwrite the currently active native registration on cleanup.
for pass = 1, 2 do
    owner("native_a.lua")
    local a = bm.recipes:add { type = "smelting", input = 340, output = bm.stack(263, 1) }
    owner("native_patch.lua")
    local aPatch = a:override { output = bm.stack(263, 2) }
    owner("native_b.lua")
    local b = bm.recipes:add { type = "smelting", input = 340, output = bm.stack(263, 3) }
    if pass == 1 then unload("native_a.lua") else unload("native_patch.lua") end
    assert(ctx.recipes:getSmeltingResult(bm.stack(340)).count == 3)
    unload("native_b.lua")
    if pass == 1 then
        assert(ctx.recipes:getSmeltingResult(bm.stack(340)) == nil and not aPatch.active)
        unload("native_patch.lua")
    else
        assert(ctx.recipes:getSmeltingResult(bm.stack(340)).count == 1 and a.exists)
        unload("native_a.lua")
    end
end
