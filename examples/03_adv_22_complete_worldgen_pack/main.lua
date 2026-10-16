-- This capstone keeps declarations in domain-focused private modules. main.lua
-- owns registration order and passes references between those modules explicitly.
-- Copy the complete folder so require paths and structure gameplay data remain intact.

function modInit()
  local features = require("features")()
  local structures = require("structures")()
  require("biomes")(features, structures)
end
