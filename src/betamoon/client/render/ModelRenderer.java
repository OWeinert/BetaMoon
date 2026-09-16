package betamoon.client.render;

import betamoon.assets.model.ModelGeometry;
import betamoon.assets.model.ModelPose;
import betamoon.assets.model.ModelVector;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.src.ModLoader;
import org.lwjgl.opengl.GL11;

/**
 * Legacy renderer with owned GL state; never touches Minecraft's active
 * tessellator.
 */
public final class ModelRenderer {
    interface TextureBinder {
        void bind(String resource);
    }

    private ModelRenderer() {
    }

    public static void render(ModelAppearance appearance, String context, double ageTicks, int metadata, double x,
            double y, double z, float brightness) {
        render(appearance, context, ageTicks, metadata, x, y, z, brightness, resource -> GL11
                .glBindTexture(GL11.GL_TEXTURE_2D, ModLoader.getMinecraftInstance().renderEngine.getTexture(resource)));
    }

    static void render(ModelAppearance appearance, String context, double ageTicks, int metadata, double x, double y,
            double z, float brightness, TextureBinder binder) {
        if (appearance.isFailed()) {
            return;
        }
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_NORMALIZE);
            List<ModelAppearance> layers = new ArrayList<>();
            layers.add(appearance);
            layers.addAll(appearance.layers);
            // Finish solid/cutout faces across every layer before translucent faces.
            List<ModelPose> poses = new ArrayList<>();
            for (ModelAppearance layer : layers) {
                poses.add(layer.evaluate(context, ageTicks, metadata, x, y, z));
            }
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < layers.size(); i++) {
                    renderLayer(layers.get(i), poses.get(i), context, brightness, pass, binder);
                }
            }
        } catch (RuntimeException error) {
            appearance.fail(error);
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
            GL11.glPopAttrib();
        }
    }

    private static void renderLayer(ModelAppearance appearance, ModelPose pose, String context, float brightness,
            int pass, TextureBinder binder) {
        for (Map.Entry<String, ModelAppearanceDeclaration.Material> entry : appearance.definition.materials
                .entrySet()) {
            ModelAppearanceDeclaration.Material material = entry.getValue();
            boolean translucent = material.blend.equals("alpha") || material.blend.equals("additive");
            if (translucent != (pass == 1)) {
                continue;
            }
            binder.bind(appearance.textures.get(entry.getKey()));
            for (int sided = 0; sided < 2; sided++) {
                state(material, brightness);
                if (sided == 1) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                }
                GL11.glBegin(GL11.GL_QUADS);
                try {
                    ModelGeometry geometry = pose.getGeometry();
                    for (int bone = 0; bone < geometry.bones.size(); bone++) {
                        for (ModelGeometry.Quad face : geometry.bones.get(bone).faces) {
                            if (!face.material.equals(entry.getKey()) || face.plane != (sided == 1)) {
                                continue;
                            }
                            ModelVector[] vertices = new ModelVector[4];
                            for (int i = 0; i < 4; i++) {
                                vertices[i] = appearance.transform(pose.transform(bone, face.vertices.get(i)), context)
                                        .times(1.0 / 16);
                            }
                            ModelVector normal = normal(vertices);
                            GL11.glNormal3d(normal.x, normal.y, normal.z);
                            for (int i = 0; i < 4; i++) {
                                ModelVector uv = face.uv.get(i);
                                GL11.glTexCoord2d(uv.x * material.scaleU + material.u,
                                        uv.y * material.scaleV + material.v);
                                GL11.glVertex3d(vertices[i].x, vertices[i].y, vertices[i].z);
                            }
                        }
                    }
                } finally {
                    GL11.glEnd();
                }
            }
        }
    }

    private static void state(ModelAppearanceDeclaration.Material material, float brightness) {
        if (material.cull) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        float light = material.unlit ? 1 : brightness;
        GL11.glColor4f(((material.tint >> 16) & 255) / 255f * light, ((material.tint >> 8) & 255) / 255f * light,
                (material.tint & 255) / 255f * light, material.opacity);
        boolean translucent = material.blend.equals("alpha") || material.blend.equals("additive");
        GL11.glDepthMask(!translucent);
        if (translucent) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                    material.blend.equals("additive") ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        if (material.blend.equals("cutout")) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
    }

    public static ModelVector normal(ModelVector[] vertices) {
        ModelVector a = vertices[1].add(vertices[0].times(-1));
        ModelVector b = vertices[2].add(vertices[0].times(-1));
        ModelVector cross = new ModelVector(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
        double length = Math.sqrt(cross.x * cross.x + cross.y * cross.y + cross.z * cross.z);
        return length == 0 ? new ModelVector(0, 1, 0) : cross.times(1 / length);
    }
}
