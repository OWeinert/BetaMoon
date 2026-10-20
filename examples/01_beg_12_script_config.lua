-- A config keeps simple settings between Minecraft launches.
-- Copy this file into .minecraft/betamoon/scripts, then select it on the
-- Scripts screen and click Configure. Edit the greeting or its toggle and Save.
-- The changed greeting appears in chat immediately; reload the script to see
-- that the saved values are used again.

name = "Script Configuration Example"
version = "1.0.0"
description = "Saves a greeting and a show/hide toggle in betamoon/config/example/beginner_settings.json. " ..
    "Select this script on the Scripts screen, click Configure, edit a setting, and Save. " ..
    "Changes take effect in chat without reloading; saved values also survive a restart."

function modInit()
  -- The first load creates the JSON file from these defaults. Later loads use
  -- the saved values, filling in any newly added fields from the defaults.
  local settings = betamoon.config("example:beginner_settings", {
    greeting = "Hello from BetaMoon!",
    showGreeting = true
  }, {
    -- Labels help players understand the generated Configure screen. They are
    -- editor metadata, so they are not written into the JSON settings file.
    label = "Greeting",
    fields = {
      greeting = { label = "Greeting text" },
      showGreeting = { label = "Show greeting" }
    },
    -- Saving changed settings also runs this function on the next game update.
    -- values is a snapshot of the new settings. No script reload is needed.
    onChange = function(values)
      betamoon.chat:send("Greeting settings saved")
      if values.showGreeting then
        betamoon.chat:send(values.greeting)
      end
    end
  })

  -- Read the current settings each time the script loads. Editing the config
  -- file outside the game takes effect after reloading the script.
  if settings.showGreeting then
    betamoon.chat:send(settings.greeting)
  end
end
