package betamoon.debug;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetRegistration;
import betamoon.assets.BuiltinAssets;
import betamoon.luaapi.audio.SoundEvents;
import betamoon.luamodloader.ScriptAssetScope;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Exports asset lookup paths, sound events, and the built-in model catalog. */
final class DebugAssetExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        exportAssets(session);
        exportSoundEvents(session);
        exportBuiltInModels(session);
    }

    private static void exportAssets(DebugExportSession session) throws Exception {
        final List<AssetRow> assets = new ArrayList<AssetRow>();
        for (Map.Entry<AssetId, AssetDefinition> entry : BuiltinAssets.models().entrySet()) {
            assets.add(new AssetRow(entry.getKey(), entry.getValue(), "minecraft", true));
        }
        for (AssetRegistration registration : ScriptAssetScope.snapshot()) {
            AssetDefinition definition = registration.getDefinition();
            assets.add(new AssetRow(definition.getId(), definition, registration.getOwner(), false));
        }
        Collections.sort(assets, AssetRow.ORDER);
        session.writeTextFile("assets.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < assets.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    AssetRow row = assets.get(index);
                    writer.write("kind: " + row.id.getKind().name().toLowerCase());
                    writer.newLine();
                    writer.write("key: " + row.id.getKey());
                    writer.newLine();
                    writer.write("owner: " + safe(row.owner));
                    writer.newLine();
                    writer.write("path source: " + (row.builtIn ? "built-in"
                            : row.definition.isPathDerived() ? "derived from key" : "explicit"));
                    writer.newLine();
                    writer.write("default path: " + row.definition.getFallbackPath());
                    writer.newLine();
                    writer.write("texture-pack override: " + row.definition.getOverridePath());
                    writer.newLine();
                    writer.write("extension: " + row.definition.getExtension());
                    writer.newLine();
                    writer.write("built-in: " + row.builtIn);
                    writer.newLine();
                    if (!row.id.equals(row.definition.getId())) {
                        writer.write("alias of: " + row.definition.getId().getKey());
                        writer.newLine();
                    }
                }
                return assets.size();
            }
        });
    }

    private static void exportSoundEvents(DebugExportSession session) throws Exception {
        final List<SoundEvents.Description> events = SoundEvents.snapshot();
        session.writeTextFile("sound_events.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < events.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    SoundEvents.Description event = events.get(index);
                    writer.write("key: " + event.key);
                    writer.newLine();
                    writer.write("owner: " + safe(event.owner));
                    writer.newLine();
                    writer.write("volume: " + event.volume + " | pitch: " + event.pitchMin + ".." + event.pitchMax
                            + " | range: " + event.range);
                    writer.newLine();
                    for (int clipIndex = 0; clipIndex < event.clips.size(); clipIndex++) {
                        SoundEvents.ClipDescription clip = event.clips.get(clipIndex);
                        writer.write("clip " + (clipIndex + 1) + ": kind=" + clip.kind + ", weight=" + clip.weight
                                + ", default=" + clip.fallbackPath + ", texture-pack override=" + clip.overridePath
                                + ", built-in=" + clip.builtIn);
                        writer.newLine();
                    }
                }
                return events.size();
            }
        });
    }

    private static void exportBuiltInModels(DebugExportSession session) throws Exception {
        final List<Map.Entry<AssetId, AssetDefinition>> models = new ArrayList<Map.Entry<AssetId, AssetDefinition>>(
                BuiltinAssets.models().entrySet());
        Collections.sort(models, new Comparator<Map.Entry<AssetId, AssetDefinition>>() {
            @Override
            public int compare(Map.Entry<AssetId, AssetDefinition> left,
                    Map.Entry<AssetId, AssetDefinition> right) {
                return left.getKey().getKey().toString().compareTo(right.getKey().getKey().toString());
            }
        });
        session.writeTextFile("builtin_models.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (Map.Entry<AssetId, AssetDefinition> entry : models) {
                    AssetId lookup = entry.getKey();
                    AssetDefinition model = entry.getValue();
                    writer.write("key: " + lookup.getKey() + " | intended content: "
                            + intendedContent(lookup.getKey().getPath()) + " | override: " + model.getOverridePath());
                    if (!lookup.equals(model.getId())) {
                        writer.write(" | alias of: " + model.getId().getKey());
                    }
                    writer.newLine();
                }
                return models.size();
            }
        });
    }

    private static String intendedContent(String path) {
        int separator = path.indexOf('/');
        return separator < 0 ? "general" : path.substring(0, separator);
    }

    private static String safe(String value) {
        return DebugExportNames.safeString(value == null ? "unavailable" : value);
    }

    private static final class AssetRow {
        private static final Comparator<AssetRow> ORDER = new Comparator<AssetRow>() {
            @Override
            public int compare(AssetRow left, AssetRow right) {
                int kind = left.id.getKind().compareTo(right.id.getKind());
                return kind != 0 ? kind : left.id.getKey().toString().compareTo(right.id.getKey().toString());
            }
        };

        private final AssetId id;
        private final AssetDefinition definition;
        private final String owner;
        private final boolean builtIn;

        private AssetRow(AssetId id, AssetDefinition definition, String owner, boolean builtIn) {
            this.id = id;
            this.definition = definition;
            this.owner = owner;
            this.builtIn = builtIn;
        }
    }
}
