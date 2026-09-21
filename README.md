# BetaMoon

Extensive Lua scripting for **Minecraft Beta 1.7.3**.

[Wiki](https://github.com/OWeinert/BetaMoon/wiki) · [Lua API](https://github.com/OWeinert/BetaMoon/wiki/API-Documentation) · [Example scripts](examples/README.md) · [Changelog](CHANGELOG.md) · [Downloads](https://github.com/OWeinert/BetaMoon/releases)

## What you can build

- Custom blocks, items, food, tools and armor with textures and interaction callbacks.
- Block state, placement rules, collision shapes, scheduled updates and redstone behavior.
- Crafting and smelting recipes, plus custom recipe types for processing machines.
- Persistent tile inventories, containers and screens with synchronized data.
- Ore and biome generation, event-driven behavior, and overrides for registered content.

Scripts support hot reload, dependency ordering and shared modules. The in-game **Scripts** screen shows loading status and errors.

BetaMoon is under active development. Read the release notes when updating and keep persistent IDs and schemas stable for saved worlds. See [reload and compatibility](https://github.com/OWeinert/BetaMoon/wiki/Hot-Reload-and-Compatibility).

## Requirements

| Component | Version / setup |
| --- | --- |
| Minecraft | Beta 1.7.3 |
| [Java](https://adoptium.net/temurin/releases/?version=8) | Java 8, selected for this Minecraft instance |
| [Risugami's ModLoader](https://mcarchive.net/mods/modloader?gvsn=b1.7.3) | Beta 1.7.3 build |
| [MinecraftForge](https://mcarchive.net/mods/minecraftforge?gvsn=b1.7.3) | Beta 1.7.3 build; use Forge 1.0.6 for this setup |
| BetaMoon | Matching mod JAR, also enabled as a Java agent |

The packaged BetaMoon 0.7.0 JAR bundles LuaJ 3.0.1 and the agent's runtime dependencies.

## MultiMC installation

[MultiMC](https://multimc.org/) keeps the mod setup and Java configuration within a Minecraft instance.

1. **Create the instance.** Select Minecraft Beta 1.7.3.
2. **Select Java 8.** Open **Edit Instance → Settings → Java**, enable the **Java installation** override and select your Java 8 executable. On Windows, use `bin/javaw.exe` inside the Java installation.
3. **Install the JAR mods.** Open **Version → Add to Minecraft.jar**. Add ModLoader first, then Forge.
4. **Install BetaMoon.** Open **Loader mods**, add `betamoon-0.7.0.jar` and make sure it is enabled.
5. **Enable the Java agent.** Return to **Settings → Java**, enable the **Java arguments** override and append the argument below to the instance's existing arguments. Replace the example with the absolute path to the installed BetaMoon JAR.

   ```text
   -javaagent:"C:/your/path/to/MultiMC/instances/your_instance_name/.minecraft/mods/betamoon-0.7.0.jar"
   ```

6. **Launch Minecraft.** Inspect the console for initialization errors, then open BetaMoon's **Scripts** screen.

MultiMC documents these controls under [instance settings](https://github.com/MultiMC/Launcher/wiki/Instance-settings). See [agent setup notes](#java-agent-setup-notes) for path handling and upgrades.

## Manual installation

Use these steps with a launcher that supports a modified `minecraft.jar` and custom JVM arguments.

1. **Prepare the instance.** Install Minecraft Beta 1.7.3 and configure the launcher to use Java 8 for that instance.
2. **Back up `minecraft.jar`.** Locate the instance's game JAR, conventionally `.minecraft/bin/minecraft.jar`, and make a backup before editing it.
3. **Install ModLoader and Forge.** Open the JAR with an archive editor. Add ModLoader's contents, remove the original `META-INF` signature directory, then add Forge's contents.
4. **Install BetaMoon.** Put `betamoon-0.7.0.jar` in the instance's `.minecraft/mods` folder.
5. **Enable the Java agent.** Append the following to the launcher's JVM arguments for this instance, using the actual absolute path to the installed JAR:

   ```text
   -javaagent:"C:/Users/YourName/AppData/Roaming/.minecraft/mods/betamoon-0.7.0.jar"
   ```

   If a script launches Java directly, place this JVM option before the main class or `-jar` argument in the existing launch command.

6. **Launch Minecraft.** Check the console for initialization errors and open the **Scripts** screen.

## Java agent setup notes

The BetaMoon JAR serves as both the mod and its Java agent. The agent enables engine hooks used by callbacks on existing content and global block/item interaction events. Java loads it through the [`-javaagent` JVM option](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/package-summary.html).

Use an absolute path to the same JAR installed in the instance. On Windows, use forward slashes and quote the path as shown above, especially when it contains spaces. On macOS or Linux, substitute the corresponding absolute path, for example:

```text
-javaagent:"/your/path/to/MultiMC/instances/your_instance_name/.minecraft/mods/betamoon-0.7.0.jar"
```

Update the argument whenever you move or rename the JAR, including after a version upgrade, and restart Minecraft. If BetaMoon shows an agent warning, click its displayed argument to copy the detected JAR path, paste it into the instance's Java arguments and restart.

## Installing scripts

Place scripts directly inside the active instance's `.minecraft/lua_scripts` folder. BetaMoon creates this folder when it starts. Use the lowercase `.lua` extension and copy required assets while preserving their directory structure. Texture paths resolve from `lua_scripts`.

Keep dependency scripts together. The [example index](examples/README.md) explains which example files belong to the same lesson, required assets and how to try the content. Check numeric content IDs when combining scripts with other mods.

Saved script changes trigger hot reload after the save settles. You can also use **Reload** on the Scripts screen. Changes to persistent structural definitions, including tile entities, containers and GUIs, require a Minecraft restart. See [installing scripts](https://github.com/OWeinert/BetaMoon/wiki/Install-Scripts) and [reload and compatibility](https://github.com/OWeinert/BetaMoon/wiki/Hot-Reload-and-Compatibility).

## Writing scripts

Start with [your first BetaMoon script](https://github.com/OWeinert/BetaMoon/wiki/Getting-Started), then use the [Lua API reference](https://github.com/OWeinert/BetaMoon/wiki/API-Documentation) for function signatures, declaration fields and focused snippets. The [examples](examples/README.md) range from simple registrations to complete processing machines.

Community scripts are collected in [AwesomeBetaMoon](https://github.com/OWeinert/AwesomeBetaMoon).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Startup failure | Java 8, matching Beta 1.7.3 dependencies, and ModLoader before Forge. Read the first console error. |
| Java agent warning | The instance's Java arguments and the absolute path to its installed BetaMoon JAR. Restart after correcting the argument. |
| Agent JAR cannot be opened | File existence, filename, path quoting and read permissions. |
| Script fails to load | Errors on the Scripts screen, script metadata and required dependencies. |
| Content ID already occupied | IDs used by other installed scripts or mods. |
| Texture missing | PNG paths relative to `lua_scripts` and the package's asset directories. |
| Changes require restart | Retained structural definitions; follow BetaMoon's restart indication. |

See the wiki's [troubleshooting guide](https://github.com/OWeinert/BetaMoon/wiki/Troubleshooting) for more detail. When [reporting an issue](https://github.com/OWeinert/BetaMoon/issues), include the BetaMoon version, relevant console error and steps to reproduce it.

## License

BetaMoon is available under the [MIT license](LICENSE).

## AI Disclaimer

BetaMoon is developed with assistance of AI Agents to increase development and research speed.
