# BetaMoon Examples

The files in this folder are examples for different functions that BetaMoon provides for mod development.

Lua files are hot-reloaded during gameplay after a save settles, or manually
with the **Reload** button on BetaMoon's Scripts screen. The examples use the
declarative registry API exclusively. See `docs/LUA_API.md` for the complete
contract and `docs/LUA_HOT_RELOAD.md` for reload guarantees.

You can use any of most of the examples by placing them in your `.minecraft/lua_scripts/` folder.<br>
**The `04_custom_textures.lua` script relies on a texture file in the `.minecraft/lua_scripts/` (`example_block.png`). Make sure to include it.**
