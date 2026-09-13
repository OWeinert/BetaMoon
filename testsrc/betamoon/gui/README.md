# Off-screen GUI regression check

`GuiShowcaseRenderTest` uses Minecraft's actual font and GUI renderer with an
LWJGL 2 pbuffer. It opens no game window and uses only build-directory assets.
It needs the matching LWJGL 2 native libraries for your operating system and
the existing RetroMCP Minecraft JAR/resources and dependencies.

With the local Gradle setup, run:

```text
gradlew guiShowcaseRenderTest -PlwjglNativesDir=/path/to/extracted/lwjgl2/natives
```

The default native directory is `build/gui-natives`. This optional test is kept
separate from `check` because it requires a working OpenGL driver and natives.

It executes the showcase's real tile/entity/container GUI registration, advances
the animation through all four pages, and checks item-count page selection.
Block/recipe registration is captured without loading ModLoader. It then checks
actual white tooltip glyph pixels with lighting/depth enabled, confirms GL state
restoration, and reproduces the previous missing-text rendering path.

Screenshots of all four pages and tooltips are written to
`build/gui-showcase-render/` for visual inspection. These render the GUI layers
and sample item previews; they do not simulate a player opening a world.
