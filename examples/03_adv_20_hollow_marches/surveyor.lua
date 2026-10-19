return function(worldgen)
  local surveyor = betamoon.items:add {
    id = 5050,
    key = "example:item/marches_surveyor",
    displayName = "Marches Surveyor",
    icon = { x = 6, y = 3 },
    maxStackSize = 1,
    onUseOnBlock = {
      action = function(ctx)
        local selected = worldgen.generatorProfiles:getSelected(ctx.world)
        if not selected or selected:getKey() ~= "example:generator/hollow_marches" then
          betamoon.chat:send("Choose Hollow Marches when creating a new world to use this surveyor")
          return betamoon.callbackResults.handled
        end

        local x, z = ctx.position.x, ctx.position.z
        local region, reason = worldgen.spatial.fields:getRequired("example:field/marches_regions"):inspect(ctx.world, x, z)
        if not region then
          betamoon.chat:send("Region sampling unavailable: %s", reason)
          return betamoon.callbackResults.handled
        end
        local shape, shapeReason = worldgen.terrain.sources:getRequired("example:terrain_source/hollow_marches"):sample(ctx.world, x, z)
        local lanternY, heightReason = selected:sampleHeightOutput(ctx.world, "lanterns", x, z)
        if not shape or not lanternY then
          betamoon.chat:send("Terrain sampling unavailable: %s", shapeReason or heightReason or "unknown")
          return betamoon.callbackResults.handled
        end
        -- Precision formatting belongs to Lua's string library; chat receives
        -- the resulting text through its simple %s placeholder.
        betamoon.chat:send("%s", string.format("Region %.3f; envelope Y %.1f; lantern Y %.0f; sample work %d",
          region.value, shape.height, lanternY, region.sampleWork))
        for _, contribution in ipairs(shape.profiles or {}) do
          betamoon.chat:send("%s", string.format("%s: %.1f%%", contribution.key, contribution.weight * 100))
        end

        if ctx.player and ctx.player:isSneaking() then
          -- This performs a raw preview, not chunk loading or population. One
          -- deliberate click bounds its cost; do not run exports every tick.
          local path, exportReason = selected:exportChunk(ctx.world, math.floor(x / 16), math.floor(z / 16))
          betamoon.chat:send("Raw preview: %s", path or exportReason)
        end
        return betamoon.callbackResults.handled
      end
    }
  }
  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(surveyor),
    ingredients = {
      betamoon.items:getRequired(345), -- compass
      betamoon.items:getRequired(339), -- paper
      betamoon.items:getRequired(331)  -- redstone
    }
  }
end
