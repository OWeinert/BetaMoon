-- This is a private module. BetaMoon checks it as part of the package, but it
-- is not a separate mod and does not receive lifecycle callbacks on its own.
local lifecycle = {}

function lifecycle.init()
end

function lifecycle.reload()
end

function lifecycle.unload()
end

return lifecycle
