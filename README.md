# BetaMoon

BetaMoon is a Mod for Minecraft Beta 1.7.3 that adds Lua-based mod loading.<br>
The Lua API is designed to be easy-to-use and is fully documented in the Wiki.<br>
<br>

### W.I.P Notice
BetaMoon is very W.I.P. That means it doesn't have a lot of features currently!<br>
BUT the plan is to provide full backwards compatibility, so you can already start to create your Lua Mods without the worry of redoing everything in the future!

## Requirements
- Minecraft Beta 1.7.3 (Recommended to be installed via MultiMC or similar client)
- [Risugami's ModLoader](https://mcarchive.net/mods/modloader?gvsn=b1.7.3)
- [MinecraftForge (1.0.6 is recommended)](https://mcarchive.net/mods/minecraftforge?gvsn=b1.7.3)
  - Note: 1.0.7 may crash when used with ModLoaderMP, so it is not recommended if you need ModloaderMP. 
    (1.0.6 can be found when you click the "7 more" button below the 1.0.7 downloads on MCArchive)
- [LuaJ (luaj-jse-3.0.1.jar)](https://central.sonatype.com/artifact/org.luaj/luaj-jse/3.0.1/versions)

## Recommended: MultiMC installation
[MultiMC](https://multimc.org/) is the easiest and safest way to install legacy mods because it keeps everything in one instance without the need to modify the minecraft.jar yourself.
Of course you can use other similar clients like [Prism](https://prismlauncher.org/), but the setup process might be different there.
For Prism specifically, the setup may be the same since it's a direct fork of MultiMC.

1) Create a new instance
   - Instance version: Minecraft Beta 1.7.3

2) Install ModLoader and MinecraftForge
   - Download Risugami's ModLoader and MinecraftForge from the links in ["Requirements"](#requirements).
   - Right-click the instance -> Edit Instance -> Version tab
   - Click "Add to Minecraft.jar"
   - Add ModLoader first, then add MinecraftForge 1.0.6 second
   - Order matters: ModLoader must be applied before Forge

3) Add BetaMoon and LuaJ
   - In the same Edit Instance window, open the "Loader mods" section
   - Add `betamoon-X.Y.Z.jar` (X.Y.Z is the version, e.g. "0.1.0") and `luaj-jse-3.0.1.jar` using the "Add" button on the top right.
   - If MultiMC lists them, make sure both are enabled

4) Run the instance
   - Start the instance and verify no ModLoader/MinecraftForge or BetaMoon errors appear on launch in the console.

5) Install BetaMoon mods or start modding yourself!

## Manual installation (not recommended, but possible)
1) Prepare `minecraft.jar`
   - Download Risugami's ModLoader and MinecraftForge from the links in ["Requirements"](#requirements).
   - Backup `minecraft.jar` (from your `.minecraft/bin` folder).
   - Open `minecraft.jar` with a zip tool (7-Zip/WinRAR).
   - Add ModLoader contents and delete `META-INF`.
   - Add MinecraftForge contents to the same jar (do not remove ModLoader).

2) Install BetaMoon and LuaJ
   - Put `betamoon-X.Y.Z.jar` (X.Y.Z is the version, e.g. "0.1.0") and `luaj-jse-3.0.1.jar` into `.minecraft/mods`.

3) Run the game
   - Launch Minecraft Beta 1.7.3.

4) Install BetaMoon mods or start modding yourself!

## Where to put Lua mods
BetaMoon looks for scripts in:
- `.minecraft/luamods/`

Create that folder if it does not exist and place your `.lua` files there.
You can also start Minecraft first and BetaMoon will create the folder automatically.

Lua mods will only be loaded on game start!
That means if you change anything in a lua script you need to restart your game.

## Lua Mod Developement
How to create your own BetaMoon Lua Mod is fully documented in the Wiki.

## Troubleshooting
- If the game crashes on startup, verify the ModLoader -> Forge install order.
- BetaMoon logs any Lua errors in the console. It is highly recommended to use MultiMC or similar clients that provide a console so you can debug your Lua mod.
- If BetaMoon logs Lua errors, check the `.minecraft/luamods/` folder and script syntax.
- Make sure that you have the correct LuaJ version because otherwise BetaMoon might crash.
