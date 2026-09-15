# Asset foundation checks

`AssetFoundationTest` is a plain Java 8 entrypoint. It deliberately uses no Minecraft,
Lua, graphics, audio, or test-framework classes. Compile the files in
`src/betamoon/assets/` and `testsrc/betamoon/assets/AssetFoundationTest.java` together,
then run `betamoon.assets.AssetFoundationTest` with only that output directory on the
classpath. This verifies the headless metadata boundary as well as the contracts.

`betamoon.luamodloader.ScriptAssetLifecycleTest` uses the normal BetaMoon development
test classpath to exercise the actual loader and script resource tracker.

In the local Gradle workspace, run:

```text
gradlew assetFoundationTest scriptAssetLifecycleTest loaderCollectionsTest
```

The Gradle configuration is local/ignored in this repository. Both tests also support
direct Java invocation in other development workspaces; no JUnit runner is required.


Step 2 adds these checks:

- `assetResourceTest`: real PNG/WAV/OGG codecs, ZIP/default fallback, malformed data,
  source provenance, cache refresh, and detached playback streams. It puts the
  original Minecraft JAR first to verify Beta's actual sound-library versions.
- `assetLuaApiTest`: user-facing declarations, references, block/item binding, sound
  events, recoverable errors and script cleanup through the real loader.
- `atlasTexturesTest`: stable slot allocation and no uploads for unchanged static pixels.
- `clientAudioTest`: shared buffers, independent gain/range, voice limits and cleanup.

The atlas/playback tests use narrow backend adapters and do not require a GPU or audio
output. A passing test suite does not establish live rendering or audible output.
