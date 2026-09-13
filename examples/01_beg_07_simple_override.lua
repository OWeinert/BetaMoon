-- Overrides change content that is already registered instead of creating a new block or item.
-- This example changes only the visible name of vanilla stone, making the result easy to inspect.
-- BetaMoon owns the change for this script and restores the old value when the script unloads.

name = "Simple Override Example"
version = "1.0.0"
description = "Changes one property of an existing Minecraft block."

function modInit()
  -- getRequired stops the script with a clear error if the requested block is unavailable.
  local stone = betamoon.blocks:getRequired("minecraft:stone")

  -- override applies the supplied fields to that existing block. Keep the returned
  -- handle when the script needs to remove its change before unloading.
  local change = stone:override {
    displayName = "Polished Stone"
  }

  -- active confirms that BetaMoon accepted and installed the change.
  assert(change.active)
end
