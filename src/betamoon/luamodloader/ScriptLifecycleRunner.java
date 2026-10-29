package betamoon.luamodloader;

import betamoon.BetaMoonCommon;
import betamoon.atmosphere.AtmosphereRegistry;
import betamoon.dimension.DimensionRegistry;
import betamoon.loot.LootTableRegistry;
import betamoon.luaapi.audio.SoundEvents;
import betamoon.luaapi.module.ModuleRegistry;
import betamoon.multiblock.MultiblockRegistry;
import betamoon.particles.ParticleRegistry;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.terrain.TerrainRegistry;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Executes script lifecycle callbacks with consistent owner and failure
 * handling.
 */
final class ScriptLifecycleRunner {
    private static final String SCRIPT_ERROR_PREFIX = "Lua mod failed to load: ";
    private static final String SEPARATOR = "-------------------------------------------------------------\n";

    private final RetainedScriptCatalog retainedScripts;
    private final ErrorReporter errorReporter;

    ScriptLifecycleRunner(RetainedScriptCatalog retainedScripts) {
        this(retainedScripts, null);
    }

    ScriptLifecycleRunner(RetainedScriptCatalog retainedScripts, ErrorReporter errorReporter) {
        this.retainedScripts = retainedScripts;
        this.errorReporter = errorReporter;
    }

    void run(List<ScriptMod> ordered, List<String> failedMods, boolean hotReload) {
        run(ordered, failedMods, hotReload, false);
    }

    void run(List<ScriptMod> ordered, List<String> failedMods, boolean hotReload, boolean logTimings) {
        for (int i = 0; i < ordered.size(); i++) {
            ScriptMod mod = ordered.get(i);
            if (hotReload && NonReloadableScriptRegistry.contains(mod.sourceFileName)) {
                continue;
            }
            String unavailableDependency = unavailableDependency(mod);
            if (unavailableDependency != null) {
                recordLoadFailure(mod, failedMods, "Dependency did not initialize: " + unavailableDependency,
                        new IllegalStateException("Dependency did not initialize: " + unavailableDependency));
                continue;
            }

            long startedNanos = System.nanoTime();
            mod.generationCodeSignature = null;
            try (ScriptExecutionScope ignored = ScriptExecutionScope.open(mod.sourceFileName);
                    ScriptAssetScope assets = ScriptAssetScope.open(mod.sourceFileName);
                    ScriptEntityScope entities = ScriptEntityScope.open(mod.sourceFileName);
                    ParticleRegistry.Batch particles = ParticleRegistry.begin(mod.sourceFileName);
                    MultiblockRegistry.Batch multiblocks = MultiblockRegistry.begin(mod.sourceFileName);
                    AtmosphereRegistry.Batch atmospheres = AtmosphereRegistry.begin(mod.sourceFileName);
                    SpatialRegistry.Batch spatial = SpatialRegistry.begin(mod.sourceFileName);
                    TerrainRegistry.Batch terrain = TerrainRegistry.begin(mod.sourceFileName);
                    DimensionRegistry.Batch dimensions = DimensionRegistry.begin(mod.sourceFileName);
                    LootTableRegistry.PublicationBatch lootTables = LootTableRegistry
                            .beginPublication(mod.sourceFileName, mod.getDisplayName());
                    WorldGenRegistry.PublicationBatch worldgen = WorldGenRegistry.beginPublication(mod.sourceFileName,
                            mod.getDisplayName());
                    BiomeGenRegistry.PublicationBatch biomes = BiomeGenRegistry.beginPublication(mod.sourceFileName,
                            mod.getDisplayName())) {
                ScriptExecution.invoke(mod.sourceFileName, "modInit", mod.modInit, LuaValue.NONE);
                if (hotReload && mod.modReload != null && mod.modReload.isfunction()) {
                    ScriptExecution.invoke(mod.sourceFileName, "modReload", mod.modReload, LuaValue.NONE);
                }
                multiblocks.validate();
                spatial.validate();
                atmospheres.validate();
                lootTables.validate();
                worldgen.validate();
                biomes.validate();
                terrain.validate();
                dimensions.validate();
                ModuleRegistry.publish(mod.sourceFileName);
                assets.publish();
                entities.publish();
                lootTables.publish();
                worldgen.publish();
                biomes.publish();
                SoundEvents.publish(mod.sourceFileName);
                particles.publish();
                multiblocks.publish();
                spatial.publish();
                terrain.publish();
                dimensions.publish();
                atmospheres.publish();
                if (mod.hooks != null) {
                    mod.hooks.publish();
                }
                LuaScriptRegistry.markLoadedByFile(mod.sourceFileName);
                retainedScripts.rememberIfStructural(mod);
            } catch (LuaError error) {
                recordLoadFailure(mod, failedMods, error.getMessage(), error);
            } catch (Throwable error) {
                recordLoadFailure(mod, failedMods, error.toString(), error);
            } finally {
                if (logTimings) {
                    BetaMoonCommon.LOGGER.info("Lua mod init " + mod.getDisplayName() + " took "
                            + ((System.nanoTime() - startedNanos) / 1000000L) + " ms.");
                }
            }
        }
    }

    void unloadReloadableScripts() {
        List<ScriptMod> entries = LuaScriptRegistry.getEntries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            ScriptMod mod = entries.get(i);
            if (NonReloadableScriptRegistry.contains(mod.sourceFileName) || !mod.isLoaded() || mod.modUnload == null
                    || !mod.modUnload.isfunction()) {
                continue;
            }

            try (ScriptExecutionScope ignored = ScriptExecutionScope.open(mod.sourceFileName)) {
                ScriptExecution.invoke(mod.sourceFileName, "modUnload", mod.modUnload, LuaValue.NONE);
            } catch (Throwable error) {
                String message = "Unload hook failed: " + error.getMessage();
                LuaScriptErrors.add(mod.getDisplayName(), message, LuaScriptErrors.Outcome.UNKNOWN, error);
                if (errorReporter != null) {
                    errorReporter.report(
                            "Lua mod unload hook failed: " + mod.getDisplayName() + " (" + error.getMessage() + ")");
                }
            }
        }
    }

    private String unavailableDependency(ScriptMod mod) {
        for (String dependency : mod.dependencies) {
            for (ScriptMod candidate : LuaScriptRegistry.getEntries()) {
                if (dependency.equals(candidate.name) && !candidate.isLoaded()) {
                    return dependency;
                }
            }
        }
        return null;
    }

    private void recordLoadFailure(ScriptMod mod, List<String> failedMods, String message, Throwable error) {
        ModuleRegistry.discardPending(mod.sourceFileName);
        TileEntityRegistry.removeOwned(mod.sourceFileName);
        ScriptResourceTracker.unload(mod.sourceFileName);
        failedMods.add(mod.name);
        LuaScriptErrors.add(mod.name, message, LuaScriptErrors.Outcome.LOAD_FAILED, error);
        LuaScriptRegistry.markFailedByFile(mod.sourceFileName, message);
        if (errorReporter != null) {
            errorReporter.report(SCRIPT_ERROR_PREFIX + mod.name + "\n" + SEPARATOR + message + "\n" + SEPARATOR);
        }
    }

    @FunctionalInterface
    interface ErrorReporter {
        void report(String message);
    }
}
