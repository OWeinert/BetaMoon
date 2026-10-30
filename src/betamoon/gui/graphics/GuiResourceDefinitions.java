package betamoon.gui.graphics;

import betamoon.client.assets.AssetLocation;
import betamoon.gui.style.GuiBitmapFont;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded immutable preparation metadata. No GPU object is allocated by a
 * definition.
 */
public final class GuiResourceDefinitions {
    public static final GuiResourceDefinitions EMPTY = new GuiResourceDefinitions(Collections.emptyMap(), 0);
    public enum Kind {
        IMAGE, MESH, BUFFER, PROGRAM, TARGET, DRAWABLE, FONT
    }

    public abstract static class Resource {
        public final Kind kind;
        protected Resource(Kind kind) {
            this.kind = kind;
        }
    }
    public static final class Image extends Resource {
        public final AssetLocation source;
        public final boolean nearest;
        public Image(AssetLocation source, boolean nearest) {
            super(Kind.IMAGE);
            this.source = source;
            this.nearest = nearest;
        }
    }
    public static final class Mesh extends Resource {
        public final GraphicsMesh geometry;
        public Mesh(GraphicsMesh geometry) {
            super(Kind.MESH);
            this.geometry = geometry;
        }
    }
    public static final class Buffer extends Resource {
        public final int mode;
        public final int stride;
        private final float[] values;
        public Buffer(int mode, int stride, float[] values) {
            super(Kind.BUFFER);
            this.mode = mode;
            this.stride = stride;
            this.values = values.clone();
        }

        public float[] values() {
            return values.clone();
        }

        public int bytes() {
            return values.length * 4;
        }
    }
    public static final class Program extends Resource {
        public final String vertex;
        public final String fragment;
        public final Map<String, Integer> uniforms;
        public Program(String vertex, String fragment, Map<String, Integer> uniforms) {
            super(Kind.PROGRAM);
            this.vertex = vertex;
            this.fragment = fragment;
            this.uniforms = Collections.unmodifiableMap(new LinkedHashMap<>(uniforms));
        }
    }
    public static final class Target extends Resource {
        public final int width;
        public final int height;
        public final boolean depth;
        public Target(int width, int height, boolean depth) {
            super(Kind.TARGET);
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        public long bytes() {
            return (long) width * height * (depth ? 8 : 4);
        }
    }
    public static final class Drawable extends Resource {
        public final String image;
        public final int color;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final int border;
        public Drawable(String image, int color, int x, int y, int width, int height, int border) {
            super(Kind.DRAWABLE);
            this.image = image;
            this.color = color;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.border = border;
        }
    }
    public static final class Font extends Resource {
        public final String image;
        public final int lineHeight;
        public final int fallback;
        public final Map<Integer, GuiBitmapFont.Glyph> glyphs;
        public Font(String image, int lineHeight, int fallback, Map<Integer, GuiBitmapFont.Glyph> glyphs) {
            super(Kind.FONT);
            this.image = image;
            this.lineHeight = lineHeight;
            this.fallback = fallback;
            this.glyphs = Collections.unmodifiableMap(new LinkedHashMap<>(glyphs));
        }
    }

    public final Map<String, Resource> entries;
    public final long metadataBytes;
    public GuiResourceDefinitions(Map<String, Resource> entries, long metadataBytes) {
        if (entries.size() > 128 || metadataBytes < 0 || metadataBytes > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("UI resources exceed 128 names/4 MiB metadata");
        }
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.metadataBytes = metadataBytes;
        for (Resource resource : entries.values()) {
            String image = resource instanceof Drawable
                    ? ((Drawable) resource).image
                    : resource instanceof Font ? ((Font) resource).image : null;
            if (image != null) {
                Resource target = entries.get(image);
                if (!(target instanceof Image) && !(target instanceof Target)) {
                    throw new IllegalArgumentException("Resource references an unknown image/target: " + image);
                }
            }
        }
    }
}
