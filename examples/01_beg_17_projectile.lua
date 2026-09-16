-- Craft two iron ingots + a snowball, then carry extra snowballs and right-click the launcher.
-- The use table is enough to launch a vanilla projectile; no custom entity or onUse is needed.
-- The crafting ingredients are paid once to make the launcher. Ammunition is paid per shot.
-- Try firing with an empty ammunition supply to see the use rejected without spending the launcher.

name = "Projectile Item Example"
version = "1.0.0"
description = "Adds the Snowball Launcher, a reusable item that fires vanilla snowballs using ammunition " ..
    "from your inventory. Craft it by combining two iron ingots and one snowball in any " ..
    "arrangement.\n\n" ..
    "Keep additional snowballs in your inventory, select the launcher, and right-click to fire. " ..
    "Each shot consumes one ammunition snowball, while the launcher remains in your hand. " ..
    "Successful shots have a 10-tick cooldown, about half a second at normal game speed. Try again " ..
    "with no snowballs to see the ammunition requirement. Compare the projectile, consumption, and " ..
    "cooldown settings in the source."

function modInit()
  local snowball = betamoon.items:getRequired(332)
  local launcher = betamoon.items:add {
    id = 5023,
    key = "example_snowball_launcher",
    displayName = "Snowball Launcher",
    icon = { x = 5, y = 1 },
    maxStackSize = 1,
    full3D = true,
    -- Uses the existing vanilla projectile and inventory rules.
    -- The launcher is retained; each shot spends one snowball.
    use = {
      -- projectiles lists the native projectile implementations supported here.
      -- The constant below has the same value as the accepted string "snowball".
      projectile = betamoon.mc.projectiles.snowball,
      -- ammunition references an already registered item to search for in the
      -- player inventory. consume = 0 retains the launcher itself; cooldown is
      -- 10 game ticks, approximately half a second.
      ammunition = snowball,
      consume = 0,
      cooldown = 10
    }
  }

  local iron = betamoon.items:getRequired(265)
  betamoon.recipes:add {
    type = "shapeless", output = betamoon.stack(launcher),
    ingredients = { iron, iron, snowball }
  }
end
