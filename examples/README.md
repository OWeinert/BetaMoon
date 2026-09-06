# BetaMoon Examples

The files in this folder are examples for different functions that BetaMoon provides for mod development.

Lua files are hot-reloaded during gameplay after a save settles, or manually
with the **Reload** button on BetaMoon's Scripts screen. The examples use the
declarative registry API exclusively. See `docs/LUA_API.md` for the complete
contract and `docs/LUA_HOT_RELOAD.md` for reload guarantees.

You can use most of the examples by placing them in your `.minecraft/lua_scripts/` folder.

`22_custom_furnace.lua` demonstrates persistent tile data, named inventory
slots, a standalone container, and a synchronized furnace-style GUI. Restart
Minecraft after editing this startup-only example.

The texture examples also need their PNG files from this folder. This includes
`example_block.png` for `04_custom_textures.lua` and the six `example_armor_*.png`
files for `08_custom_armor.lua`.
