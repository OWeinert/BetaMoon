-- Start here: copy this file into .minecraft/lua_scripts and give it a unique name.
-- The assignments below describe the script; modInit is where its content is created.
-- Lines beginning with -- are comments, so you can edit them without changing behavior.
-- This template deliberately creates nothing. The simple block example adds the first block.
-- basic.png is optional: copy an image with that name or remove the image setting.

-- Required: The name shown for your script. Every script should have a different name.
name = "Base Script"

-- Optional: An image shown on the script information screen.
-- Put the image in the lua_scripts folder and write its file name here.
image = "basic.png"

-- Optional: The version shown on the script information screen.
version = "2.0.0"

-- Optional: A short explanation of what your script does.
description = "Base script template with optional metadata."

-- Optional: Scripts that must load before this one.
dependencies = {}

-- Required: BetaMoon calls modInit when this script loads.
-- Create your blocks, items, recipes, events, and other features here.
-- Use local variables inside it for handles needed only while defining this script.
-- Keep the lifecycle function names unchanged; BetaMoon looks them up by name.
function modInit()
end

-- Optional: after a successful hot reload, BetaMoon calls modReload after modInit.
-- It receives no arguments and its return value is ignored. It is not called when
-- the game first starts.
function modReload()
end

-- Optional: BetaMoon calls modUnload just before this script is reloaded.
-- Most features are cleaned up automatically. Use this only for extra cleanup.
function modUnload()
end
