-- This is a plain inventory item, not yet a tool or a usable gadget.
-- Registration makes item 5000 available; it does not put a stack in your inventory.
-- The vanilla recipe example shows how to make registered items obtainable through crafting.
-- Durability is a property, not an action: a plain item needs suitable callbacks
-- before using it will spend durability. The item interaction example introduces custom actions.

name = "Custom Item Example"
version = "2.0.0"
description = "Declares a normal item with durability and stack settings."

function modInit()
  -- items:add creates a normal item. Pick an unused item ID of 256 or higher.
  betamoon.items:add {
    -- Keep these identity fields unique, just like a block declaration.
    id = 5000,
    key = "example_item",
    displayName = "Example Item",
    -- maxStackSize limits how many fit in one inventory slot. maxDamage stores
    -- durability, although a plain item needs an action before it spends durability.
    maxStackSize = 16,
    maxDamage = 32,
    -- icon picks a picture from Minecraft's item texture sheet.
    -- Atlas x/y are zero-based cell coordinates, not individual pixels.
    -- Changing the icon does not change what the item does.
    icon = { x = 7, y = 3 }
  }
end
