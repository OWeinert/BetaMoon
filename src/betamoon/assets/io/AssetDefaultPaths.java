package betamoon.assets.io;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import java.io.IOException;

/** Conventional default locations for key-only script declarations. */
public final class AssetDefaultPaths {
    private AssetDefaultPaths() {
    }

    public static AssetDefinition resolve(AssetId id, AssetResolver resolver) throws IOException {
        String base = id.getKey().getNamespace() + "/" + id.getKind().getDirectory() + "/"
                + id.getKey().getPath();
        if (id.getKind() == AssetKind.SOUND) {
            rejectFileSuffix(id, ".ogg", ".wav");
            AssetPath ogg = AssetPath.parse(base + ".ogg");
            AssetPath wav = AssetPath.parse(base + ".wav");
            boolean hasOgg = resolver.defaultExists(ogg);
            boolean hasWav = resolver.defaultExists(wav);
            if (hasOgg && hasWav) {
                throw new IllegalArgumentException("Both sound defaults exist for " + id.getKey() + ": " + ogg
                        + " and " + wav + "; keep one or specify path");
            }
            if (!hasOgg && !hasWav) {
                throw new IOException("No sound default for " + id.getKey() + "; expected " + ogg + " or " + wav);
            }
            return new AssetDefinition(id, hasOgg ? ogg : wav, hasOgg ? "ogg" : "wav");
        }

        String extension;
        if (id.getKind() == AssetKind.TEXTURE) {
            extension = "png";
        } else if (id.getKind() == AssetKind.MODEL) {
            extension = "json";
        } else if (id.getKind() == AssetKind.ANIMATION) {
            extension = "animation.json";
        } else {
            throw new IllegalArgumentException("No default path convention for " + id.getKind());
        }
        rejectFileSuffix(id, "." + extension);
        return new AssetDefinition(id, AssetPath.parse(base + "." + extension), extension);
    }

    private static void rejectFileSuffix(AssetId id, String... suffixes) {
        for (String suffix : suffixes) {
            if (id.getKey().getPath().endsWith(suffix)) {
                throw new IllegalArgumentException("Asset key " + id.getKey() + " includes " + suffix
                        + "; omit the file suffix or specify path");
            }
        }
    }
}
