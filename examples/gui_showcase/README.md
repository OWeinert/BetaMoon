# GUI Showcase assets

Copy `03_adv_05_gui_showcase.lua` and this entire `gui_showcase` folder into
`.minecraft/lua_scripts`, then restart Minecraft with the updated BetaMoon JAR.

Craft a chest with one redstone dust in any arrangement to obtain the GUI
Showcase block (ID 208). Place it and right-click to open the gallery.

- Leave **Page** empty to cycle through four pages, eight seconds each.
- Put 1, 2, 3, or 4 items in **Page** to hold the corresponding page.
- Put any item in **Sample** for the live item preview on page 1.
- Hover the demonstrations for explanations and synchronized values.

The page-selector and sample items remain yours; the showcase never consumes
them. It uses a 256×232 canvas and continues ticking while open. Changes to its
Lua definitions require a Minecraft restart.

The commented script demonstrates every container-GUI element type. Its
`BACKGROUND` setting can be `panel`, `custom`, or `builtin`. The custom panel
has the same dimensions as the generated one. Built-in backgrounds scale to
the gallery canvas, with slot frames drawn at the container's actual positions.

`state_on.png`, `state_off.png`, and `state_unknown.png` reuse BetaMoon's GUI
symbols. The other PNGs are small, flat pixel textures for progress fills,
empty meters, and the optional custom background. All PNG paths in the script
are relative to `lua_scripts`, not to the script file or the game JAR.

## License

All PNG image assets in this folder are licensed under the [MIT License](LICENSE.md).
Include the copyright and license notice when redistributing the images or substantial portions of them.
