-- Overrides change content that is already registered instead of creating a new block or item.
-- This example changes only the visible name of vanilla stone, making the result easy to inspect.
-- BetaMoon owns the change for this script and restores the old value when the script unloads.

name = "Simple Override Example"
version = "1.0.0"
description = "Renames vanilla stone to Polished Stone using a tracked display-name override. The example " ..
    "shows how to change an existing resource without registering a replacement block.\n\n" ..
    "Inspect a stone block item in your inventory to see the new name. Use stone itself, not the " ..
    "cobblestone normally dropped when stone is mined. Its texture and other gameplay properties " ..
    "are unchanged. Unloading the script removes the override so the original name can be " ..
    "restored."

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
