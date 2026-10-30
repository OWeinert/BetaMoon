package betamoon.gui.interop;

import betamoon.gui.graphics.GraphicsFramebuffer;
import betamoon.gui.graphics.GraphicsMesh;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.graphics.GraphicsShaderProgram;
import betamoon.gui.graphics.GraphicsVertexBuffer;
import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.gui.graphics.GuiResourceDefinitions.Buffer;
import betamoon.gui.graphics.GuiResourceDefinitions.Drawable;
import betamoon.gui.graphics.GuiResourceDefinitions.Font;
import betamoon.gui.graphics.GuiResourceDefinitions.Image;
import betamoon.gui.graphics.GuiResourceDefinitions.Mesh;
import betamoon.gui.graphics.GuiResourceDefinitions.Program;
import betamoon.gui.graphics.GuiResourceDefinitions.Resource;
import betamoon.gui.graphics.GuiResourceDefinitions.Target;
import betamoon.gui.style.GuiAtlasLease;
import betamoon.gui.style.GuiBitmapFont;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import betamoon.gui.style.GuiNamedFontFace;
import betamoon.interop.client.NativeClientResource;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luamodloader.ScriptExecution;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;

/**
 * One actual NativeClientSession per consuming owner; graphics methods reuse
 * its existing handles.
 */
public final class GuiOwnedResources implements GraphicsResources, AutoCloseable {
    private final NativeClientScreenHost host;
    private final String owner;
    private final Map<String, NativeClientResource<GraphicsMesh>> meshes = new LinkedHashMap<>();
    private final Map<String, NativeClientResource<GraphicsVertexBuffer>> buffers = new LinkedHashMap<>();
    private final Map<String, NativeClientResource<GraphicsShaderProgram>> programs = new LinkedHashMap<>();
    private final Map<String, NativeClientResource<GraphicsFramebuffer>> targets = new LinkedHashMap<>();
    private final Map<String, NativeClientResource<GuiAssetImage>> images = new LinkedHashMap<>();
    private final Map<String, GuiAssetImage> preparedImages = new LinkedHashMap<>();
    private final Map<String, GuiAtlasLease> atlases = new LinkedHashMap<>();
    private final Map<String, GuiDrawable> drawables = new LinkedHashMap<>();
    private final Map<String, GuiFontFace> fonts = new LinkedHashMap<>();

    public GuiOwnedResources(Minecraft client, GuiScreen screen, String owner, GuiResourceDefinitions definitions) {
        this.owner = owner;
        host = ScriptExecution.withOwner(owner, () -> NativeClientScreenHost.prepareDetached(client, screen, owner));
        boolean complete = false;
        try {
            ScriptExecution.withOwner(owner, () -> {
                host.initialize();
                host.session().prepare(() -> prepare(client, definitions));
                return null;
            });
            complete = true;
        } finally {
            if (!complete) {
                close();
            }
        }
    }

    public NativeClientScreenHost host() {
        return host;
    }

    private void prepare(Minecraft client, GuiResourceDefinitions definitions) {
        for (Map.Entry<String, Resource> entry : definitions.entries.entrySet()) {
            String name = entry.getKey();
            Resource value = entry.getValue();
            switch (value.kind) {
                case IMAGE: {
                    NativeClientResource<GuiAssetImage> resource = host.session()
                            .resource(() -> new GuiAssetImage(client, owner, (Image) value), GuiAssetImage::close);
                    GuiAssetImage image = resource.use();
                    image.liveness(resource::requireActive);
                    images.put(name, resource);
                    preparedImages.put(name, image);
                    atlases.put(name, image);
                    break;
                }
                case MESH:
                    meshes.put(name, host.session().resource(() -> ((Mesh) value).geometry, ignored -> {
                    }));
                    break;
                case BUFFER: {
                    Buffer buffer = (Buffer) value;
                    buffers.put(name,
                            own(buffer.bytes(),
                                    () -> new GraphicsVertexBuffer(buffer.mode, buffer.stride, buffer.values()),
                                    GraphicsVertexBuffer::close));
                    break;
                }
                case PROGRAM: {
                    Program program = (Program) value;
                    programs.put(name,
                            own((program.vertex.length() + program.fragment.length()) * 2L + 8192,
                                    () -> new GraphicsShaderProgram(program.vertex, program.fragment, program.uniforms),
                                    GraphicsShaderProgram::close));
                    break;
                }
                case TARGET: {
                    Target target = (Target) value;
                    NativeClientResource<GraphicsFramebuffer> resource = own(target.bytes(),
                            () -> new GraphicsFramebuffer(target.width, target.height, target.depth),
                            GraphicsFramebuffer::close);
                    targets.put(name, resource);
                    atlases.put(name, PreparedAtlasLease.target(resource));
                    break;
                }
                default:
                    break;
            }
        }
        for (Map.Entry<String, Resource> entry : definitions.entries.entrySet()) {
            Resource value = entry.getValue();
            if (value instanceof Drawable) {
                Drawable drawable = (Drawable) value;
                if (drawable.image == null) {
                    drawables.put(entry.getKey(), GuiDrawable.solid(drawable.color));
                } else {
                    GuiAtlasLease atlas = image(drawable.image);
                    drawables.put(entry.getKey(), GuiDrawable.image(atlas, drawable.x, drawable.y,
                            drawable.width == 0 ? atlas.width() - drawable.x : drawable.width,
                            drawable.height == 0 ? atlas.height() - drawable.y : drawable.height, drawable.border));
                }
            } else if (value instanceof Font) {
                Font font = (Font) value;
                fonts.put(entry.getKey(), new GuiNamedFontFace(entry.getKey(),
                        new GuiBitmapFont(image(font.image), font.lineHeight, font.fallback, font.glyphs)));
            }
        }
    }

    private <T> NativeClientResource<T> own(long bytes, Supplier<T> factory, Consumer<T> disposer) {
        GuiResourceBudget.Reservation[] allocation = {null};
        return host.session().resource(() -> {
            allocation[0] = GuiResourceBudget.reserve(owner, bytes);
            try {
                T value = factory.get();
                if (value == null) {
                    throw new IllegalStateException("UI resource factory returned no value");
                }
                return value;
            } catch (RuntimeException | Error failure) {
                allocation[0].close();
                throw failure;
            }
        }, value -> {
            try {
                disposer.accept(value);
            } finally {
                allocation[0].close();
            }
        });
    }

    public GraphicsMesh mesh(String name) {
        return required(meshes, name, "mesh").use();
    }

    public GraphicsVertexBuffer buffer(String name) {
        return required(buffers, name, "buffer").use();
    }

    public GraphicsShaderProgram program(String name) {
        return required(programs, name, "program").use();
    }

    public GraphicsFramebuffer target(String name) {
        return required(targets, name, "target").use();
    }

    public GuiAtlasLease image(String name) {
        GuiAtlasLease image = atlases.get(name);
        if (image == null) {
            throw GraphicsResources.missing("image", name);
        }
        image.requireActive();
        return image;
    }

    public GuiFontFace font(String name) {
        if (name.equals("native")) {
            return GuiFontFace.NATIVE;
        }
        GuiFontFace font = fonts.get(name);
        if (font == null) {
            throw GraphicsResources.missing("font", name);
        }
        font.requireActive();
        return font;
    }

    public GuiDrawable drawable(String name) {
        GuiDrawable drawable = drawables.get(name);
        if (drawable != null) {
            drawable.requireActive();
            return drawable;
        }
        GuiAtlasLease image = image(name);
        return GuiDrawable.image(image, 0, 0, image.width(), image.height(), 0);
    }

    public GuiPaintCompiler.Resources painting() {
        return new GuiPaintCompiler.Resources() {
            public GuiFontFace font(String name) {
                return GuiOwnedResources.this.font(name);
            }

            public GuiDrawable image(String name) {
                return drawable(name);
            }
        };
    }

    public void update() {
        boolean dirty = false;
        for (GuiAssetImage image : preparedImages.values()) {
            image.observe();
            dirty |= image.needsUpload();
        }
        if (dirty) {
            ScriptExecution.withOwner(owner, () -> {
                host.session().prepare(() -> {
                    for (GuiAssetImage image : preparedImages.values()) {
                        image.update();
                    }
                });
                return null;
            });
        }
    }

    public boolean needsRevision() {
        for (GuiAssetImage image : preparedImages.values()) {
            if (image.needsRevision()) {
                return true;
            }
        }
        return false;
    }

    public long imageRevision() {
        long value = 1;
        for (GuiAssetImage image : preparedImages.values()) {
            value = 31 * value + image.revision();
        }
        return value;
    }

    private static <T> NativeClientResource<T> required(Map<String, NativeClientResource<T>> values, String name,
            String kind) {
        NativeClientResource<T> value = values.get(name);
        if (value == null) {
            throw GraphicsResources.missing(kind, name);
        }
        return value;
    }

    @Override
    public void close() {
        host.close();
        meshes.clear();
        buffers.clear();
        programs.clear();
        targets.clear();
        images.clear();
        preparedImages.clear();
        atlases.clear();
        drawables.clear();
        fonts.clear();
    }
}
