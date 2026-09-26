package betamoon.debug;

/** Groups recipe schemas and recipe instances into one catalog category. */
final class DebugRecipesExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        DebugRecipeTypeExporter.export(session);
        DebugRecipeExporter.export(session);
    }
}
