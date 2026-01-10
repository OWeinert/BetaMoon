# BetaMoon

BetaMoon is a Mod for Minecraft Beta 1.7.3 that adds Lua-based mod loading.<br>
The Lua API is designed to be easy-to-use and is fully documented in the Wiki.<br>
<br>

### W.I.P Notice
BetaMoon is very W.I.P. That means it doesn't have a lot of features currently!<br>
BUT the plan is to provide full backwards compatibility, so you can already start to create your Lua Mods without the worry of redoing everything in the future!

## Requirements
- Minecraft Beta 1.7.3 (Recommended to be installed via MultiMC or similar client)
- [Java 8 (Adoptium is recommended since it's open source)](https://adoptium.net/de/temurin/releases?version=8&os=any&arch=any)
  (Select JDK 8 on the website and then download either JDK or JRE for your OS. You'll probably only need JRE)
- [Risugami's ModLoader](https://mcarchive.net/mods/modloader?gvsn=b1.7.3)
- [MinecraftForge (1.0.6 is recommended)](https://mcarchive.net/mods/minecraftforge?gvsn=b1.7.3)
  - Note: 1.0.7 may crash when used with ModLoaderMP, so it is not recommended if you need ModloaderMP. 
    (1.0.6 can be found when you click the "7 more" button below the 1.0.7 downloads on MCArchive)
- [LuaJ (luaj-jse-3.0.1.jar)](https://central.sonatype.com/artifact/org.luaj/luaj-jse/3.0.1/versions)

## Recommended: MultiMC installation
[MultiMC](https://multimc.org/) is the easiest and safest way to install legacy mods because it keeps everything in one instance without the need to modify the minecraft.jar yourself.
Of course you can use other similar clients like [Prism](https://prismlauncher.org/), but the setup process might be different there.
For Prism specifically, the setup may be the same since it's a direct fork of MultiMC.

1) Download and install Java 8
   - Download and install Java 8 using the link in [Requirements](#requirements).

2) Create a new instance in MultiMC
   - Instance version: Minecraft Beta 1.7.3
   - Right-click the instance -> Edit Instance -> Settings
   - Click on "Java installation" and "Browse..."
   - Select the javaw.exe from your Java 8 installation (for Adoptium it's defaulted to "C:/Program Files/Eclipse Adoptium/jre-W.X.YYY.Z-hotspot/bin/javaw.exe" on Windows, where W/X/Y/Z are numbers for the version you downloaded) <br> 
   For other OS' it will vary.<br>
   <br>
     
3) Install ModLoader and MinecraftForge
   - Download Risugami's ModLoader and MinecraftForge from the links in [Requirements](#requirements).
   - Right-click the instance -> Edit Instance -> Version tab
   - Click "Add to Minecraft.jar"
   - Add ModLoader first, then add MinecraftForge 1.0.6 second
   - Order matters: ModLoader must be applied before Forge

4) Add BetaMoon and LuaJ
   - In the same Edit Instance window, open the "Loader mods" section
   - Add `betamoon-X.Y.Z.jar` (X.Y.Z is the version, e.g. "0.1.0") and `luaj-jse-3.0.1.jar` using the "Add" button on the top right.
   - If MultiMC lists them, make sure both are enabled

5) Run the instance
   - Start the instance and verify no ModLoader/MinecraftForge or BetaMoon errors appear on launch in the console.

6) Install BetaMoon mods or start modding yourself!

## Manual installation (not recommended, but possible)

1) Download and install Java 8
   - Download and install Java 8 using the link in ["Requirements"](#requirements).
   - Set Java 8 as the active version:
     - Windows: set `JAVA_HOME` to your Java 8 install and add `%JAVA_HOME%\\bin` to `PATH`.
     (for Adoptium it's defaulted to "C:/Program Files/Eclipse Adoptium/jre-W.X.YYY.Z-hotspot/bin/javaw.exe", where W/X/Y/Z are numbers for the version you downloaded)
     - macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)` and ensure `$JAVA_HOME/bin` is first in `PATH`.
     - Linux: use `update-alternatives --config java` (or your distro's equivalent) to select Java 8.
   (You probably have to change back your Java version to whatever you need for other Java applications everytime you don't play Minecraft, that's mainly why this installation method is not recommended!)

2) Prepare `minecraft.jar`
   - Download Minecraft b1.7.3 through official methods.
   - Download Risugami's ModLoader and MinecraftForge from the links in ["Requirements"](#requirements).
   - Backup `minecraft.jar` (from your `.minecraft/bin` folder).
   - Open `minecraft.jar` with a zip tool (7-Zip/WinRAR).
   - Add ModLoader contents and delete `META-INF`.
   - Add MinecraftForge contents to the same jar (do not remove ModLoader).

3) Install BetaMoon and LuaJ
   - Put `betamoon-X.Y.Z.jar` (X.Y.Z is the version, e.g. "0.1.0") and `luaj-jse-3.0.1.jar` into `.minecraft/mods`.

4) Run the game
   - Launch Minecraft Beta 1.7.3.

5) Install BetaMoon mods or start modding yourself!

## Where to put Lua mods
BetaMoon looks for scripts in:
- `.minecraft/luamods/`

Create that folder if it does not exist and place your `.lua` files there. <br>
You can also start Minecraft first and BetaMoon will create the folder automatically.<br>
<br>
Lua mods will only be loaded on game start!<br>
That means if you change anything in a Lua script or add/remove a Lua mod you need to restart your game.

## Lua Mod Developement
How to create your own BetaMoon Lua Mod is fully documented in the [Wiki](https://github.com/OWeinert/BetaMoon/wiki).

## Troubleshooting
- If the game crashes on startup, verify the ModLoader -> Forge install order.
- BetaMoon logs any Lua errors in the console. It is highly recommended to use MultiMC or similar clients that provide a console so you can debug your Lua mod.
- If BetaMoon logs Lua errors, check the `.minecraft/luamods/` folder and script syntax.
- Make sure that you have the correct LuaJ version because otherwise BetaMoon might crash.