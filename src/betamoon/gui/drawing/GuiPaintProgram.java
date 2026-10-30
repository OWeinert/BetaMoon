package betamoon.gui.drawing;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.graphics.GraphicsAffine;
import betamoon.gui.graphics.GraphicsExpression;
import betamoon.gui.graphics.GraphicsHitRegion;
import betamoon.gui.graphics.GraphicsMesh;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Validated update-built paint/hit program. A complete Java frame sample is
 * resolved before any draw executes.
 */
public final class GuiPaintProgram {
    private static final ThreadLocal<FloatBuffer> MATRIX = ThreadLocal
            .withInitial(() -> BufferUtils.createFloatBuffer(16));
    private final GraphicsExpression[] expressions;
    private final Transform[] transforms;
    private final Clip[] clips;
    private final Draw[] draws;
    private final Hit[] hits;
    private final int vertices;
    private final int hitVertices;
    private final int textUnits;

    private GuiPaintProgram(Builder builder) {
        expressions = builder.expressions.toArray(new GraphicsExpression[0]);
        transforms = builder.transforms.toArray(new Transform[0]);
        clips = builder.clips.toArray(new Clip[0]);
        draws = builder.draws.toArray(new Draw[0]);
        hits = builder.hits.toArray(new Hit[0]);
        vertices = builder.vertices;
        hitVertices = builder.hitVertices;
        textUnits = builder.textUnits;
    }

    public int commands() {
        return draws.length;
    }

    public int vertices() {
        return vertices;
    }

    public int hitVertices() {
        return hitVertices;
    }

    public int expressions() {
        return expressions.length;
    }

    public int textUnits() {
        return textUnits;
    }

    public Frame sample(int width, int height, Map<String, Object> properties) {
        return sample(width, height, properties, false);
    }

    public Frame sample(int width, int height, Map<String, Object> properties, boolean retainProperties) {
        if (width < 0 || height < 0 || width > 16384 || height > 16384) {
            throw new IllegalArgumentException("Prepared paint bounds must be within 0..16384");
        }
        double[] values = new double[expressions.length];
        for (int index = 0; index < expressions.length; index++) {
            values[index] = expressions[index].sample(width, height, properties);
        }
        GraphicsAffine[] matrices = new GraphicsAffine[transforms.length];
        matrices[0] = GraphicsAffine.IDENTITY;
        for (int index = 1; index < matrices.length; index++) {
            Transform transform = transforms[index];
            matrices[index] = matrices[transform.parent]
                    .compose(GraphicsAffine.transform(values[transform.x], values[transform.y],
                            values[transform.scaleX], values[transform.scaleY], values[transform.rotation]));
        }
        Rect[] rectangles = new Rect[clips.length];
        rectangles[0] = Rect.fromPositionAndSize(0, 0, width, height);
        for (int index = 1; index < rectangles.length; index++) {
            Clip clip = clips[index];
            GraphicsAffine matrix = matrices[clip.transform];
            if (!matrix.axisAligned()) {
                throw new IllegalArgumentException("Prepared rectangular clips cannot be rotated");
            }
            double x = matrix.mapX(values[clip.x], values[clip.y]);
            double y = matrix.mapY(values[clip.x], values[clip.y]);
            double right = matrix.mapX(values[clip.x] + values[clip.width], values[clip.y]);
            double bottom = matrix.mapY(values[clip.x], values[clip.y] + values[clip.height]);
            if (values[clip.width] < 0 || values[clip.height] < 0) {
                throw new IllegalArgumentException("Prepared clip dimensions cannot be negative");
            }
            if (values[clip.width] == 0 || values[clip.height] == 0) {
                rectangles[index] = Rect.EMPTY;
                continue;
            }
            Rect requested = new Rect((int) Math.floor(Math.min(x, right)), (int) Math.floor(Math.min(y, bottom)),
                    (int) Math.ceil(Math.max(x, right)), (int) Math.ceil(Math.max(y, bottom)));
            rectangles[index] = rectangles[clip.parent].intersect(requested);
        }
        List<Region> regions = new ArrayList<>();
        for (Hit hit : hits) {
            GraphicsHitRegion region = hit.shape.sample(values).transformed(matrices[hit.transform]);
            regions.add(new Region(hit.name, region, rectangles[hit.clip]));
        }
        Frame frame = new Frame(this, values, matrices, rectangles, regions, width, height,
                retainProperties ? properties : Collections.emptyMap());
        frame.requireActive();
        return frame;
    }

    public static final class Frame implements GuiFrameContent {
        private final GuiPaintProgram program;
        private final double[] values;
        private final GraphicsAffine[] matrices;
        private final Rect[] clips;
        private final List<Region> regions;
        public final int width;
        public final int height;
        public final Map<String, Object> properties;

        private Frame(GuiPaintProgram program, double[] values, GraphicsAffine[] matrices, Rect[] clips,
                List<Region> regions, int width, int height, Map<String, Object> properties) {
            this.program = program;
            this.width = width;
            this.height = height;
            this.properties = properties.isEmpty()
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
            this.values = values;
            this.matrices = matrices;
            this.clips = clips;
            this.regions = Collections.unmodifiableList(regions);
        }

        public void requireActive() {
            for (Draw draw : program.draws) {
                draw.painter.validate(this);
            }
        }

        public String hit(double x, double y) {
            for (int index = regions.size() - 1; index >= 0; index--) {
                Region region = regions.get(index);
                if (x >= region.clip.getLeft() && x < region.clip.getRight() && y >= region.clip.getTop()
                        && y < region.clip.getBottom() && region.shape.contains(x, y)) {
                    return region.name;
                }
            }
            return null;
        }

        @Override
        public boolean contains(double x, double y) {
            return hit(x, y) != null;
        }

        public void paint(GuiContext context, int left, int top) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT
                    | GL11.GL_TEXTURE_BIT | GL11.GL_TRANSFORM_BIT);
            try {
                for (Draw draw : program.draws) {
                    Rect clip = clips[draw.clip];
                    if (clip.isEmpty()) {
                        continue;
                    }
                    context.getRenderer().pushClip(new Rect(left + clip.getLeft(), top + clip.getTop(),
                            left + clip.getRight(), top + clip.getBottom()));
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(left, top, 0);
                        multiply(matrices[draw.transform]);
                        draw.painter.paint(context, this);
                    } finally {
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glPopMatrix();
                        context.getRenderer().popClip();
                    }
                }
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    public static final class Builder {
        private final List<GraphicsExpression> expressions = new ArrayList<>();
        private final Map<GraphicsExpression, Integer> indices = new IdentityHashMap<>();
        private final List<Transform> transforms = new ArrayList<>();
        private final List<Clip> clips = new ArrayList<>();
        private final List<Draw> draws = new ArrayList<>();
        private final List<Hit> hits = new ArrayList<>();
        private final Set<String> names = new LinkedHashSet<>();
        private final Deque<Integer> transformStack = new ArrayDeque<>();
        private final Deque<Integer> clipStack = new ArrayDeque<>();
        private int transform;
        private int clip;
        private int vertices;
        private int textUnits;
        private int hitVertices;
        private boolean built;

        public Builder() {
            transforms.add(null);
            clips.add(null);
        }

        public Builder pushTransform(GraphicsExpression x, GraphicsExpression y, GraphicsExpression scaleX,
                GraphicsExpression scaleY, GraphicsExpression degrees) {
            requireBuilding();
            if (transformStack.size() >= 32 || transforms.size() >= 1024) {
                throw new IllegalArgumentException("Paint transform budget exceeded");
            }
            transformStack.push(transform);
            transforms.add(new Transform(transform, expression(x), expression(y), expression(scaleX),
                    expression(scaleY), expression(degrees)));
            transform = transforms.size() - 1;
            return this;
        }

        public Builder popTransform() {
            requireBuilding();
            if (transformStack.isEmpty()) {
                throw new IllegalArgumentException("Paint transform stack underflow");
            }
            transform = transformStack.pop();
            return this;
        }

        public Builder pushClip(GraphicsExpression x, GraphicsExpression y, GraphicsExpression width,
                GraphicsExpression height) {
            requireBuilding();
            if (clipStack.size() >= 32 || clips.size() >= 1024) {
                throw new IllegalArgumentException("Paint clip budget exceeded");
            }
            clipStack.push(clip);
            clips.add(new Clip(clip, transform, expression(x), expression(y), expression(width), expression(height)));
            clip = clips.size() - 1;
            return this;
        }

        public Builder popClip() {
            requireBuilding();
            if (clipStack.isEmpty()) {
                throw new IllegalArgumentException("Paint clip stack underflow");
            }
            clip = clipStack.pop();
            return this;
        }

        public Builder rectangle(GraphicsExpression x, GraphicsExpression y, GraphicsExpression width,
                GraphicsExpression height, int color) {
            int px = expression(x);
            int py = expression(y);
            int w = expression(width);
            int h = expression(height);
            add(new Painter() {
                public void validate(Frame frame) {
                    dimensions(frame, w, h);
                }

                public void paint(GuiContext context, Frame frame) {
                    GraphicsMesh.color(color, context.getOpacity());
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    Tessellator batch = Tessellator.instance;
                    batch.startDrawingQuads();
                    double left = frame.values[px];
                    double top = frame.values[py];
                    batch.addVertex(left, top, 0);
                    batch.addVertex(left + frame.values[w], top, 0);
                    batch.addVertex(left + frame.values[w], top + frame.values[h], 0);
                    batch.addVertex(left, top + frame.values[h], 0);
                    batch.draw();
                }
            }, 4);
            return this;
        }

        public Builder mesh(GraphicsMesh mesh, int color) {
            if (mesh == null) {
                throw new IllegalArgumentException("Prepared mesh is required");
            }
            add((context, frame) -> mesh.draw(color, context.getOpacity()), mesh.vertices());
            return this;
        }

        public Builder line(GraphicsExpression x1, GraphicsExpression y1, GraphicsExpression x2, GraphicsExpression y2,
                GraphicsExpression width, int color) {
            int ax = expression(x1);
            int ay = expression(y1);
            int bx = expression(x2);
            int by = expression(y2);
            int thickness = expression(width);
            add(new Painter() {
                public void validate(Frame frame) {
                    if (frame.values[thickness] <= 0 || frame.values[thickness] > 256) {
                        throw new IllegalArgumentException("Line width requires 0..256, exclusive at zero");
                    }
                }

                public void paint(GuiContext context, Frame frame) {
                    GraphicsMesh.color(color, context.getOpacity());
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glLineWidth((float) frame.values[thickness]);
                    Tessellator batch = Tessellator.instance;
                    batch.startDrawing(GL11.GL_LINES);
                    batch.addVertex(frame.values[ax], frame.values[ay], 0);
                    batch.addVertex(frame.values[bx], frame.values[by], 0);
                    batch.draw();
                }
            }, 2);
            return this;
        }

        public Builder ring(GraphicsExpression x, GraphicsExpression y, GraphicsExpression inner,
                GraphicsExpression outer, GraphicsExpression start, GraphicsExpression sweep, int segments, int color) {
            if (segments < 3 || segments > 512) {
                throw new IllegalArgumentException("Ring segments require 3..512");
            }
            int cx = expression(x);
            int cy = expression(y);
            int low = expression(inner);
            int high = expression(outer);
            int first = expression(start);
            int span = expression(sweep);
            add(new Painter() {
                public void validate(Frame frame) {
                    if (frame.values[low] < 0 || frame.values[high] < frame.values[low]
                            || Math.abs(frame.values[span]) > 360) {
                        throw new IllegalArgumentException(
                                "Ring requires ordered radii and a sweep within +/-360 degrees");
                    }
                }

                public void paint(GuiContext context, Frame frame) {
                    GraphicsMesh.color(color, context.getOpacity());
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    Tessellator batch = Tessellator.instance;
                    batch.startDrawingQuads();
                    for (int index = 0; index < segments; index++) {
                        double a = Math.toRadians(frame.values[first] + frame.values[span] * index / segments);
                        double b = Math.toRadians(frame.values[first] + frame.values[span] * (index + 1) / segments);
                        vertex(batch, frame.values[cx], frame.values[cy], frame.values[low], a);
                        vertex(batch, frame.values[cx], frame.values[cy], frame.values[high], a);
                        vertex(batch, frame.values[cx], frame.values[cy], frame.values[high], b);
                        vertex(batch, frame.values[cx], frame.values[cy], frame.values[low], b);
                    }
                    batch.draw();
                }
            }, segments * 4);
            return this;
        }

        public Builder text(String text, GuiFontFace font, GraphicsExpression x, GraphicsExpression y, int color,
                boolean shadow) {
            if (text == null || font == null || text.length() > 65536 - textUnits) {
                throw new IllegalArgumentException("Paint text/font is invalid or exceeds its text budget");
            }
            textUnits += text.length();
            font.requireActive();
            int px = expression(x);
            int py = expression(y);
            add(new Painter() {
                public void validate(Frame frame) {
                    font.requireActive();
                }

                public void paint(GuiContext context, Frame frame) {
                    int faded = color & 0xFFFFFF | (int) Math.round((color >>> 24) * context.getOpacity()) << 24;
                    font.draw(context, text, (int) Math.round(frame.values[px]), (int) Math.round(frame.values[py]),
                            faded, shadow);
                }
            }, text.length() * (shadow ? 8 : 4));
            return this;
        }

        public Builder image(GuiDrawable drawable, GraphicsExpression x, GraphicsExpression y, GraphicsExpression width,
                GraphicsExpression height) {
            if (drawable == null) {
                throw new IllegalArgumentException("Prepared image drawable is required");
            }
            drawable.requireActive();
            int px = expression(x);
            int py = expression(y);
            int w = expression(width);
            int h = expression(height);
            add(new Painter() {
                public void validate(Frame frame) {
                    dimensions(frame, w, h);
                    drawable.requireActive();
                }

                public void paint(GuiContext context, Frame frame) {
                    drawable.draw(context,
                            Rect.fromPositionAndSize((int) Math.round(frame.values[px]),
                                    (int) Math.round(frame.values[py]), (int) Math.round(frame.values[w]),
                                    (int) Math.round(frame.values[h])));
                }
            }, 36);
            return this;
        }

        public Builder hitRectangle(String name, GraphicsExpression x, GraphicsExpression y, GraphicsExpression width,
                GraphicsExpression height) {
            int px = expression(x);
            int py = expression(y);
            int w = expression(width);
            int h = expression(height);
            hit(name, values -> GraphicsHitRegion.rectangle(values[px], values[py], values[w], values[h]), 4);
            return this;
        }

        public Builder hitCircle(String name, GraphicsExpression x, GraphicsExpression y, GraphicsExpression radius) {
            int px = expression(x);
            int py = expression(y);
            int r = expression(radius);
            hit(name, values -> GraphicsHitRegion.circle(values[px], values[py], values[r]), 4);
            return this;
        }

        public Builder hitPolygon(String name, double[] vertices) {
            GraphicsHitRegion shape = GraphicsHitRegion.polygon(vertices);
            hit(name, values -> shape, vertices.length / 2);
            return this;
        }

        public GuiPaintProgram build() {
            requireBuilding();
            if (!transformStack.isEmpty() || !clipStack.isEmpty()) {
                throw new IllegalArgumentException("Paint transform/clip stacks must balance");
            }
            built = true;
            return new GuiPaintProgram(this);
        }

        private void add(Painter painter, int cost) {
            requireBuilding();
            if (draws.size() >= 1024 || cost < 0 || cost > 65536 - vertices) {
                throw new IllegalArgumentException("Prepared paint exceeds 1024 commands/65536 vertices");
            }
            vertices += cost;
            draws.add(new Draw(transform, clip, painter));
        }

        private void hit(String name, Shape shape, int cost) {
            requireBuilding();
            if (name == null || name.isEmpty() || name.length() > 128 || !names.add(name) || hits.size() >= 256) {
                throw new IllegalArgumentException("Hit regions require unique names and at most 256 regions");
            }
            if (cost > 8192 - hitVertices) {
                throw new IllegalArgumentException("Prepared hit vertex budget exceeded");
            }
            hitVertices += cost;
            hits.add(new Hit(name, transform, clip, shape));
        }

        private int expression(GraphicsExpression expression) {
            requireBuilding();
            if (expression == null) {
                throw new IllegalArgumentException("Prepared numeric expression is required");
            }
            Integer existing = indices.get(expression);
            if (existing != null) {
                return existing;
            }
            if (expressions.size() >= 8192) {
                throw new IllegalArgumentException("Prepared expression budget exceeded");
            }
            int index = expressions.size();
            expressions.add(expression);
            indices.put(expression, index);
            return index;
        }

        private void requireBuilding() {
            if (built) {
                throw new IllegalStateException("Prepared paint builder has already finished");
            }
        }
    }

    @FunctionalInterface
    private interface Painter {
        void paint(GuiContext context, Frame frame);

        default void validate(Frame frame) {
        }
    }

    @FunctionalInterface
    private interface Shape {
        GraphicsHitRegion sample(double[] values);
    }

    private static final class Transform {
        final int parent;
        final int x;
        final int y;
        final int scaleX;
        final int scaleY;
        final int rotation;
        Transform(int parent, int x, int y, int scaleX, int scaleY, int rotation) {
            this.parent = parent;
            this.x = x;
            this.y = y;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.rotation = rotation;
        }
    }

    private static final class Clip {
        final int parent;
        final int transform;
        final int x;
        final int y;
        final int width;
        final int height;
        Clip(int parent, int transform, int x, int y, int width, int height) {
            this.parent = parent;
            this.transform = transform;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Draw {
        final int transform;
        final int clip;
        final Painter painter;
        Draw(int transform, int clip, Painter painter) {
            this.transform = transform;
            this.clip = clip;
            this.painter = painter;
        }
    }

    private static final class Hit {
        final String name;
        final int transform;
        final int clip;
        final Shape shape;
        Hit(String name, int transform, int clip, Shape shape) {
            this.name = name;
            this.transform = transform;
            this.clip = clip;
            this.shape = shape;
        }
    }

    private static final class Region {
        final String name;
        final GraphicsHitRegion shape;
        final Rect clip;
        Region(String name, GraphicsHitRegion shape, Rect clip) {
            this.name = name;
            this.shape = shape;
            this.clip = clip;
        }
    }

    private static void dimensions(Frame frame, int width, int height) {
        if (frame.values[width] < 0 || frame.values[height] < 0 || frame.values[width] > 16384
                || frame.values[height] > 16384) {
            throw new IllegalArgumentException("Paint dimensions require 0..16384");
        }
    }

    private static void vertex(Tessellator batch, double x, double y, double radius, double angle) {
        batch.addVertex(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, 0);
    }

    private static void multiply(GraphicsAffine transform) {
        FloatBuffer matrix = MATRIX.get();
        matrix.clear();
        matrix.put((float) transform.m00).put((float) transform.m10).put(0).put(0);
        matrix.put((float) transform.m01).put((float) transform.m11).put(0).put(0);
        matrix.put(0).put(0).put(1).put(0);
        matrix.put((float) transform.x).put((float) transform.y).put(0).put(1);
        matrix.flip();
        GL11.glMultMatrix(matrix);
    }
}
