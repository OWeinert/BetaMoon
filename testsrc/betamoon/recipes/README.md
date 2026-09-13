# Recipe integration tests

Run `gradlew.bat customRecipeTest` (also included in the local `check` task).
`CustomRecipeTest` loads `custom_recipes.lua` through the real BetaMoon module,
then executes advanced examples 02a-02c, 03, 04, 06a-06c, 07a-07c,
08a-08c, and 09a-09b. It uses Java 8, LuaJ, and the deobfuscated Minecraft
classes already configured in the local Gradle build.

The in-memory World owns real LuaTileEntity inventories and NBT. ModLoader's
tile registration hook is initialized as it is during client startup. Recipe
registration, schemas, matching, overrides, inventory commits, contexts,
container/GUI parsing, persistence, and owner cleanup use production code.

Only the examples' block-registration call is substituted with a vanilla
reference, avoiding a live client/texture engine. Block/chunk registration has
separate coverage in `BlockTileEntityTest`. These tests do not launch gameplay.

Assertions cover quantities, alternatives, metadata, optional/retained inputs,
conditions, multiple outputs, remainders, rollback on failure, notification
coalescing/reentrancy, stale plans, priorities, query/reference identity,
overrides and cleanup, native/opaque recipes, out-of-order native smelting
owner cleanup, NBT, pinned callbacks after recipe reload, and both example
machines' complete timed operations.
