# BetaMoon (Minecraft Beta 1.7.3)

BetaMoon adds Lua-based mod loading to Minecraft Beta 1.7.3. This README explains how to install the mod for play (recommended via MultiMC), including ModLoader, MinecraftForge 1.0.6, and the LuaJ runtime dependency.

## Requirements
- Minecraft Beta 1.7.3 (Recommended to be installed via MultiMC or similar client)
- [Risugami's ModLoader](https://mcarchive.net/mods/modloader?gvsn=b1.7.3)
- [MinecraftForge (1.0.6 is recommended)](https://mcarchive.net/mods/minecraftforge?gvsn=b1.7.3)
  - Note: 1.0.7 may crash when used with ModLoaderMP, so it is not recommended if you need ModloaderMP. 
    (1.0.6 can be found when you click the "7 more" button below the 1.0.7 downloads on MCArchive)
- [LuaJ (luaj-jse-3.0.1.jar)](https://central.sonatype.com/artifact/org.luaj/luaj-jse/3.0.1/versions)

## Recommended: MultiMC installation
MultiMC is the easiest and safest way to install legacy mods because it keeps everything in one instance.

1) Create a new instance
   - Instance version: Minecraft Beta 1.7.3

2) Install ModLoader and MinecraftForge
   - Download ModLoader and MinecraftForge from the provided links in "Requirements".
   - Right-click the instance -> Edit Instance -> Version tab
   - Click "Add to Minecraft.jar"
   - Add ModLoader first, then add MinecraftForge 1.0.6 second
   - Order matters: ModLoader must be applied before Forge

3) Add BetaMoon and LuaJ
   - In the same Edit Instance window, open the "Loader Mods" or "Mods" section
   - Add the BetaMoon mod jar
   - Add `luaj-jse-3.0.1.jar` (this is required at runtime)
   - If MultiMC lists them, make sure both are enabled

4) Run the instance
   - Start the instance and verify no ModLoader/Forge errors appear on launch

## Manual installation (not recommended, but possible)
1) Download ModLoader and MinecraftForge from the provided links in "Requirements".
2) Backup `minecraft.jar` (from your .minecraft/bin folder).
3) Open `minecraft.jar` with a zip tool (7-Zip/WinRAR).
4) Add ModLoader contents to `minecraft.jar` and delete `META-INF`.
5) Add MinecraftForge 1.0.6 contents to the same `minecraft.jar` (do not remove ModLoader).
6) Put `BetaMoon.jar` and `luaj-jse-3.0.1.jar` into `.minecraft/mods`.
7) Launch Minecraft Beta 1.7.3.

## LuaJ placement
LuaJ is required for BetaMoon to run Lua mods!
- Place `luaj-jse-3.0.1.jar` in the same mods folder as BetaMoon.
- In MultiMC, add it in the Mods list alongside the BetaMoon jar.

## Where to put Lua mods
BetaMoon looks for scripts in:
- `.minecraft/luamods/`

Create that folder if it does not exist and place your `.lua` files there.
You can also start Minecraft first and BetaMoon will create the folder automatically.

Lua mods will only be loaded on game start!
That means if you change anything in a lua script you need to restart your game.

## Troubleshooting
- If the game crashes on startup, verify the ModLoader -> Forge install order.
- BetaMoon logs any Lua errors in the console. It is highly recommended to use MultiMC or similar clients that provide a console so you can debug your Lua mod.
- If BetaMoon logs Lua errors, check the `.minecraft/luamods/` folder and script syntax.
- Make sure that you have the correct LuaJ version because otherwise BetaMoon might crash.
