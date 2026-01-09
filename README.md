# Industrio (RetroMCP + Forge 1.0.6, Minecraft Beta 1.7.3)

Starter workspace that uses [RetroMCP-Java](https://github.com/MCPHackers/RetroMCP-Java) instead of the legacy MCP 2.12, and layers MinecraftForge 1.0.6 on top for Beta 1.7.3 modding. The repo only tracks your mod sources and helper scripts; toolchains and generated artifacts live in `tools/` and stay out of git.

## Prerequisites
- JDK 8 (RetroMCP CLI requires a JDK, not just a JRE)
- VS Code
- Internet access for RetroMCP to download the game and libraries on first setup

## One-time setup (RetroMCP CLI)
From the repo root:
1) `bash scripts/setup_rmcp.sh`  
   - Downloads RetroMCP CLI jar into `tools/` if missing.
   - Creates `tools/rmcp/`, runs `setup` for `b1.7.3`, then `decompile` to generate sources/projects.
2) VS Code project files will be generated under `tools/rmcp/minecraft/.vscode/` by RetroMCP; you can open that folder in VS Code for IntelliSense/debugging.

## Adding MinecraftForge 1.0.6
After RetroMCP is decompiled:
1) Run `bash scripts/install_forge.sh`  
   - Downloads `minecraftforge-src-1.0.6.zip` into `tools/forge/`.
   - Unpacks it and overlays Forge sources/patches into the RetroMCP client workspace at `tools/rmcp/minecraft/src`.
   - Does **not** touch your mod sources in this repo.
2) Re-run RetroMCP CLI build steps from `tools/rmcp/minecraft/` when needed:
   - `java -jar ../RetroMCP-Java-CLI.jar recompile`
   - `java -jar ../RetroMCP-Java-CLI.jar reobfuscate`
   - `java -jar ../RetroMCP-Java-CLI.jar build`

Forge mods are packaged separately (jar/zip) by the `build` task; only Forge itself patches the game jars.

## Syncing your mod sources
Keep editing your mod under `src/...` (e.g., `src/piggo/industrio/...`) in this repo. When you’re ready to build/test:
```
bash scripts/sync_to_rmcp.sh     # copies your mod sources into tools/rmcp/minecraft/src
```
Then run RetroMCP CLI tasks (recompile/reobfuscate/build) from `tools/rmcp/minecraft/`.

## Repo layout
- `src/minecraft/net/minecraft/src/mod_Industrio.java` — starter ModLoader/Forge entry point.
- `scripts/setup_rmcp.sh` — sets up RetroMCP (download CLI, setup b1.7.3, decompile).
- `scripts/install_forge.sh` — downloads and overlays Forge 1.0.6 sources.
- `scripts/sync_to_rmcp.sh` / `.bat` — sync mod sources into the RetroMCP workspace.
- `tools/` — ignored; holds RetroMCP CLI jar, generated workspaces, Forge download.

## Typical loop
1) Edit code in `src/minecraft/...`.
2) `bash scripts/sync_to_rmcp.sh`
3) From `tools/rmcp/minecraft`: `java -jar ../RetroMCP-Java-CLI.jar recompile && java -jar ../RetroMCP-Java-CLI.jar reobfuscate && java -jar ../RetroMCP-Java-CLI.jar build`
4) Grab built mod artifacts from `tools/rmcp/minecraft/build/` (jar/zip) and drop into your client.

## VS Code tasks
- `RetroMCP: Sync sources` — runs `scripts/sync_to_rmcp.sh`.
- `RetroMCP: Build (recompile + reobf + build)` — runs the three RetroMCP CLI steps inside `tools/rmcp/minecraft/`.
- `RetroMCP: Full pipeline` — chains the two tasks above.
- `Mod: Sync to MultiMC` — packages your mod classes (from `bin/piggo/industrio`) plus `resources/mcmod.info` into `tools/rmcp/minecraft/build/Industrio.zip` and copies it to your MultiMC mods folder (configurable via `MULTIMC_MODS_PATH`, default `/mnt/e/Program Files/MultiMC/instances/b1.7.3_modding/.minecraft/mods`).

Use the VS Code task runner (`Ctrl/Cmd+Shift+P` → “Run Task…”) to automate the loop.
If you see “path not found,” confirm VS Code is opened at the repo root and `tools/rmcp/minecraft` exists (tasks now use an absolute workspace path).
