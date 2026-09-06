name = "Chat Examples"
version = "2.0.0"
description = "Shows local messages, broadcasts, and message formatting."

function modInit()
  -- chat:send displays a message for the local player.
  -- It can be called from modInit or from an event callback.
  -- If no player exists yet, BetaMoon saves the message and shows it after
  -- the player enters a world. Messages sent during gameplay appear at once.
  betamoon.chat:send("Chat example loaded")

  -- A message can contain placeholders. Add one value after the message for
  -- each placeholder, in the same order:
  --   %s inserts a string.
  --   %i inserts a whole number.
  --   %d inserts any number, including a decimal number.
  --   %b inserts true or false.
  --   %o converts any value to text.
  --   %% inserts a percent sign and does not need a value.
  betamoon.chat:send(
    "%s has %i blocks, speed %d, enabled: %b (%o%% ready)",
    "Player",
    4,
    1.5,
    true,
    100
  )

  -- The number and type of the values must match the placeholders.
  -- BetaMoon reports a warning instead of showing a malformed message.

  -- Minecraft color codes begin with the section sign followed by a color.
  -- Use §0 through §9 or §a through §f. Use §r to return to normal text.
  betamoon.chat:send("§aGreen text§r and normal text")

  -- broadcast sends a message to every player when the script can access the
  -- server. In singleplayer it displays the message to the local player.
  -- A client connected to a remote server cannot broadcast by itself.
  -- betamoon.chat:broadcast("Visible to every player")

  -- BetaMoon adds this script's filename before every chat message, making it
  -- clear which script sent it.
end
