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
function modInit()
end

-- Optional: BetaMoon calls modReload after modInit when this script is reloaded.
-- Use it when you need to restore temporary information after a reload.
-- It is not called when the game first starts.
function modReload(previousState)
    return previousState
end

-- Optional: BetaMoon calls modUnload just before this script is reloaded.
-- Most features are cleaned up automatically. Use this only for extra cleanup.
function modUnload()
end
