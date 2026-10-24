# BetaMoon

Extensive Lua scripting for **Minecraft Beta 1.7.3**.

[Wiki](https://github.com/OWeinert/BetaMoon/wiki) · [Lua API](https://github.com/OWeinert/BetaMoon/wiki/API-Documentation) · [Example scripts](examples/README.md) · [Changelog](CHANGELOG.md) · [Downloads](https://github.com/OWeinert/BetaMoon/releases)

[![Modrinth](https://img.shields.io/badge/dynamic/json?labelColor=black&color=grey&label=&suffix=%20downloads&query=downloads&url=https://api.modrinth.com/v2/project/BwTdIvv1&style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAMAAABEpIrGAAAAIGNIUk0AAHomAACAhAAA+gAAAIDoAAB1MAAA6mAAADqYAAAXcJy6UTwAAAJPUExURQAAABvZahWnUha1WAYzGQlHIxvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZahvZav///9ScwmYAAADDdFJOUwAAAAAAAA8zW3uOYwIBK3rB6Pn+ml18KiGL5HEDquOIH07R/UzKz2zu+uLHIibtafWkVCMNBRqg7/RQuT8EQbvT+5ETDBSU/NAgCSdZlcQKii7mtxJY5fF/7D1SRkB+EcWh4UilOOtPMdTCR1PqN969vmGDCw7G4DSSsAcGHrSPr3bds5CEwDKoXumcZdwcG4KmjayX32A79pjOqRjIFoXynVYVgPi6qxDnL78p2obVJFquNbGZCPdyvHPZ1yhuh8s+iRzcsrEAAAABYktHRMQUDBvhAAAAB3RJTUUH5wQXDwgZWDUtiQAAAqRJREFUOMttU/k7lFEU/k4L4hsxtNAnhRgiJZOiSfbI2oJpmRFCi2kV0aaNVLTIEpVISmnf97r/WO+534yZnqfzw73vOe+559xz7rmKIoVYvLx95vj6qUSqVBW3sGrwnxsQaAwSwcDz5i9g0wxPfmFIqJCyiEgLE4vDl7iD8PGlETotIqOQahlAdJTLA5sWY5JsbNzysHiihEBWViRqRDOlgyFmJVtMq5JWJ5vhvyZlLevrEmUILKl8PihtvYWcYt6QLmNwFjjEb4SSkZnlZLO5yJzcPBg35bODwZfPZxYwWbC5sKjYzwBUkssxSg1wKNsCtHUb89vTy3GwopBxDmcJ9YdDJYB1B9t2WmUtcbtkqt18Mxsp9irsezS2VEu+uka/mqUWSrFd2VsnhLEetoZ9TFv3HwCexdUdjBWi0aH4YTuEvtoPM3/EO1nnuTpEjD2qHIM54DhR0wmA5hZySzIntyknsbZyi8IB0tp07tRpBDKfgeWsdDjHDlFGRIjnl3O0t573gcMFUBeVS1gvlxB1dOZxv0pIu9IF01UDZRdjr1SuZQhxvbvnRrmsMePmrdsrGPRaKP8OLtmn9KM7A4N3xb8yNExUb5RlZt2btpZbXajzPoYkBaDKrtADp3HkYdloo56ndgx37UcHxSP0tIWBGH9cg1nx6TJFTNhS7eCfcA1PJ+GgDbLDs2GuX3V05Ohj0xYSBGumyq/yfApw4EWTRxPJqxQvIF6+ks9Gr0d4nN+8Nbhoi+NdJEwVSc6hVNvHOUvg+5qeDxoP3GQz6x8/qXLuZ6Op7SPy+gNTnydSELabk1Z8Mbs/hlr4dbobfUgxJMS3UdXza9H33jqnA3/OH41FCZ7/l7HW8vOXiWc9GvPf06D953/T2O/EP8HBNtcH0Zm/lqFNUgTAex4AAAAldEVYdGRhdGU6Y3JlYXRlADIwMjMtMDQtMjNUMTU6MDg6MjQrMDA6MDAE5dOaAAAAJXRFWHRkYXRlOm1vZGlmeQAyMDIzLTA0LTIzVDE1OjA4OjI0KzAwOjAwdbhrJgAAACh0RVh0ZGF0ZTp0aW1lc3RhbXAAMjAyMy0wNC0yM1QxNTowODoyNSswMDowMITaQU0AAAAASUVORK5CYII=)](https://modrinth.com/mod/betamoon)
[![CurseForge](https://cf.way2muchnoise.eu/full_1750754_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/betamoon)

## What you can build

- Custom blocks, items, food, tools and armor with textures and interaction callbacks.
- Custom flowing fluids and animated textures for blocks, items, models and GUI images.
- Block state, placement rules, collision shapes, scheduled updates and redstone behavior.
- Crafting and smelting recipes, plus custom recipe types for processing machines.
- Persistent tile inventories, containers and screens with synchronized data.
- [Reactive GUI presentation](https://github.com/OWeinert/BetaMoon/wiki/API-GUI-Presentation), independent transitions,
  resizable panels/visual slots, artwork tinting and screen-local state.
- Multiblock structures with controller-based or controllerless discovery and activation.
- Custom particles, animated sprites and reusable bursts or emitters.
- Deterministic ores, trees, structures, biomes, surfaces and placement rules.
- Save-bound terrain generators, shared spatial fields, material rules, carvers and population controls.
- Persistent custom dimensions with independent generation/environment and queued teleportation with safe return history.
- Scoped player and entity access for checked movement, position, rotation and velocity changes.
- Owned living-entity movement rules, native/manual motion and additive forces, including Lua-authored player flight.
- Scoped fog, sky and light tints, plus ambient and weather sounds.
- Input maps, hotkeys, event-driven behavior, and overrides for registered content.

Scripts support hot reload, dependency ordering and shared modules. The in-game **Scripts** screen shows loading status and errors.

Read the release notes when updating and keep persistent IDs and schemas stable for saved worlds. See [reload and compatibility](https://github.com/OWeinert/BetaMoon/wiki/Hot-Reload-and-Compatibility).

## Requirements

| Component | Version / setup |
| --- | --- |
| Minecraft | Beta 1.7.3 |
| [Java](https://adoptium.net/temurin/releases/?version=8) | Java 8, selected for this Minecraft instance |
| [Risugami's ModLoader](https://mcarchive.net/mods/modloader?gvsn=b1.7.3) | Beta 1.7.3 build |
| [MinecraftForge](https://mcarchive.net/mods/minecraftforge?gvsn=b1.7.3) | Beta 1.7.3 build; use Forge 1.0.6 for this setup |
| BetaMoon | Matching mod JAR, also enabled as a Java agent |

The packaged BetaMoon 0.10.0 JAR bundles LuaJ 3.0.1 and the agent's runtime dependencies.

## MultiMC installation

[MultiMC](https://multimc.org/) keeps the mod setup and Java configuration within a Minecraft instance.

1. **Create the instance.** Select Minecraft Beta 1.7.3.
2. **Select Java 8.** Open **Edit Instance → Settings → Java**, enable the **Java installation** override and select your Java 8 executable. On Windows, use `bin/javaw.exe` inside the Java installation.
3. **Install the JAR mods.** Open **Version → Add to Minecraft.jar**. Add ModLoader first, then Forge.
4. **Install BetaMoon.** Open **Loader mods**, add `betamoon-0.10.0.jar` and make sure it is enabled.
5. **Enable the Java agent.** Return to **Settings → Java**, enable the **Java arguments** override and append the argument below to the instance's existing arguments. Replace the example with the absolute path to the installed BetaMoon JAR.

   ```text
   -javaagent:"C:/your/path/to/MultiMC/instances/your_instance_name/.minecraft/mods/betamoon-0.10.0.jar"
   ```

6. **Launch Minecraft.** Inspect the console for initialization errors, then open BetaMoon's **Scripts** screen.

MultiMC documents these controls under [instance settings](https://github.com/MultiMC/Launcher/wiki/Instance-settings). See [agent setup notes](#java-agent-setup-notes) for path handling and upgrades.

## Manual installation

Use these steps with a launcher that supports a modified `minecraft.jar` and custom JVM arguments.

1. **Prepare the instance.** Install Minecraft Beta 1.7.3 and configure the launcher to use Java 8 for that instance.
2. **Back up `minecraft.jar`.** Locate the instance's game JAR, conventionally `.minecraft/bin/minecraft.jar`, and make a backup before editing it.
3. **Install ModLoader and Forge.** Open the JAR with an archive editor. Add ModLoader's contents, remove the original `META-INF` signature directory, then add Forge's contents.
4. **Install BetaMoon.** Put `betamoon-0.10.0.jar` in the instance's `.minecraft/mods` folder.
5. **Enable the Java agent.** Append the following to the launcher's JVM arguments for this instance, using the actual absolute path to the installed JAR:

   ```text
   -javaagent:"C:/Users/YourName/AppData/Roaming/.minecraft/mods/betamoon-0.10.0.jar"
   ```

   If a script launches Java directly, place this JVM option before the main class or `-jar` argument in the existing launch command.

6. **Launch Minecraft.** Check the console for initialization errors and open the **Scripts** screen.

## Java agent setup notes

The BetaMoon JAR serves as both the mod and its Java agent. The agent is required for callbacks on existing content,
global block/item interaction events, custom dimensions and teleportation. Java loads it through the
[`-javaagent` JVM option](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/package-summary.html).

Use an absolute path to the same JAR installed in the instance. On Windows, use forward slashes and quote the path as shown above, especially when it contains spaces. On macOS or Linux, substitute the corresponding absolute path, for example:

```text
-javaagent:"/your/path/to/MultiMC/instances/your_instance_name/.minecraft/mods/betamoon-0.10.0.jar"
```

Update the argument whenever you move or rename the JAR, including after a version upgrade, and restart Minecraft. If BetaMoon shows an agent warning, click its displayed argument to copy the detected JAR path, paste it into the instance's Java arguments and restart.

## VS Code completion

Install [BetaMoon API from the Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=OWeinert.betamoon-api). Select **Install** on the listing to open
VS Code, then confirm installation. Open your scripts folder and run **BetaMoon: Enable API for Workspace Folder**
for completion, hover documentation and type checking. See the [setup guide](https://github.com/OWeinert/BetaMoon/wiki/VS-Code-and-LuaLS).

## Installing scripts

Place loose scripts, script mod folders, or packaged mod ZIPs directly inside
the active instance's `.minecraft/betamoon/scripts` folder. BetaMoon creates this
folder when it starts.
Use the lowercase `.lua` extension and copy complete
folders and required assets without changing their directory structure.

For the examples: Keep dependency scripts together and copy manifested example folders as complete
units. The [example index](examples/README.md) explains which files belong to the
same lesson, required assets and how to try the content. Check numeric content
IDs when combining scripts with other mods.

Set `scripts.hotReloadOnFileChange=true` to reload saved script changes automatically after they settle.
You can also use **Reload** on the Scripts screen. Changes to persistent structural definitions, including tile
entities, containers and GUIs, require a Minecraft restart. See [installing scripts](https://github.com/OWeinert/BetaMoon/wiki/Install-Scripts) and [reload and compatibility](https://github.com/OWeinert/BetaMoon/wiki/Hot-Reload-and-Compatibility).

Startup and reload notifications show script errors by default. To include warnings in the popup and reload chat messages,
set `notifications.showScriptWarningsOnLoad=true` in `.minecraft/config/betamoon.cfg` and restart Minecraft. Warnings remain
available in the Scripts screen and launcher log when notifications are disabled.

Click an error or warning in **Script Issues** or a script's details to copy its complete diagnostic.
**Copy all** copies the displayed errors and warnings as a formatted list. **Save report** creates a full diagnostic
report with all recorded issues, occurrence times and callback context, script versions and dependencies, Java and
system details, memory usage, relevant settings, and available graphics, dimension and mod information. Reports are
saved in `betamoon/reports/` inside the Minecraft instance; a popup shows the clickable file path after saving.
Reports are also available from the pinned **Full script report** section in **Scripts → Debug**, even with no issues.
**Export All** in Debug Tools includes the report as `script_report.txt` in its export directory.
Script names are red when errors are recorded and yellow when only warnings are recorded.

Runtime output is written to `betamoon/logs/latest.log`, with automatic rotation and five retained runtime archives.
`logging.fileMinLevel` and `logging.consoleMinLevel` in `config/betamoon.cfg` both default to `info` and can be set
independently to `trace`, `debug`, `info`, `warn`, `error`, or `off`. Restart after changing them.
Use `betamoon.log("info", "Message")` or `betamoon.log:info("Message")` in scripts; `print(...)` logs at `info`.
Full diagnostic reports include recent log entries. See [Logging](https://github.com/OWeinert/BetaMoon/wiki/API-Logging).

## Writing scripts

Start with [your first BetaMoon script](https://github.com/OWeinert/BetaMoon/wiki/Getting-Started), then use the [Lua API reference](https://github.com/OWeinert/BetaMoon/wiki/API-Documentation) for function signatures, declaration fields and focused snippets. The [examples](examples/README.md) range from simple registrations to complete processing machines.

A curated selection of community scripts is collected in [AwesomeBetaMoon](https://github.com/OWeinert/AwesomeBetaMoon).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Startup failure | Java 8, matching Beta 1.7.3 dependencies, and ModLoader before Forge. Read the first console error. |
| Java agent warning | The instance's Java arguments and the absolute path to its installed BetaMoon JAR. Restart after correcting the argument. |
| Agent JAR cannot be opened | File existence, filename, path quoting and read permissions. |
| Script fails to load | Errors on the Scripts screen, script metadata and required dependencies. |
| Content ID already occupied | IDs used by other installed scripts or mods. |
| Texture missing | PNG paths relative to `betamoon/scripts`, or to `assets/` inside a script mod directory or ZIP package. |
| Changes require restart | Retained structural definitions; follow BetaMoon's restart indication. |

See the wiki's [troubleshooting guide](https://github.com/OWeinert/BetaMoon/wiki/Troubleshooting) for more detail. When [reporting an issue](https://github.com/OWeinert/BetaMoon/issues), include the BetaMoon version, relevant console error and steps to reproduce it.

## License

BetaMoon is available under the [MIT license](LICENSE).

## AI Disclaimer

BetaMoon is developed with assistance of AI Agents to increase development and research speed.
