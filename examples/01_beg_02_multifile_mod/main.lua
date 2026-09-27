-- Copy this complete folder into .minecraft/lua_scripts. The manifest selects
-- this file as the package entrypoint and supplies the metadata shown in the
-- Scripts screen, so this file does not repeat name, version, or description.

-- require only searches this package. "lifecycle" resolves lifecycle.lua once
-- and returns the private table from that file.
local lifecycle = require("lifecycle")

-- BetaMoon calls the entrypoint's lifecycle functions. They delegate to the
-- private module here only to demonstrate how several files can work together.
-- This tutorial deliberately registers no content and changes nothing in-game.
function modInit()
  lifecycle.init()
end

function modReload()
  lifecycle.reload()
end

function modUnload()
  lifecycle.unload()
end
