package betamoon.gui.inventory;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsStateGuard;
import forge.IArmorTextureProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderHelper;
import net.minecraft.src.RenderManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Native player rendering with temporary pose state and a bounded inventory
 * depth region.
 */
public final class GuiInventoryPreview {
    private static final String[] ARMOR_TEXTURES = {"cloth", "chain", "iron", "diamond", "gold"};

    public static void prepare(Minecraft client) {
        EntityPlayer player = client.thePlayer;
        if (player == null) {
            return;
        }
        try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
            client.renderEngine.getTextureForDownloadableImage(player.skinUrl, player.getEntityTexture());
            client.renderEngine.getTextureForDownloadableImage(player.playerCloakUrl, null);
            for (int index = 0; index < 4; index++) {
                ItemStack stack = player.inventory.armorItemInSlot(index);
                if (stack == null) {
                    continue;
                }
                Item item = stack.getItem();
                if (item instanceof IArmorTextureProvider) {
                    client.renderEngine.getTexture(((IArmorTextureProvider) item).getArmorTextureFile());
                } else if (item instanceof ItemArmor) {
                    int material = ((ItemArmor) item).renderIndex;
                    if (material >= 0 && material < ARMOR_TEXTURES.length) {
                        client.renderEngine.getTexture("/armor/" + ARMOR_TEXTURES[material] + "_"
                                + (((ItemArmor) item).armorType == 2 ? 2 : 1) + ".png");
                    }
                }
            }
        }
    }

    public static void draw(Minecraft client, Rect bounds, int mouseX, int mouseY, int width, int height) {
        if (bounds.isEmpty() || client.thePlayer == null) {
            return;
        }
        EntityPlayer player = client.thePlayer;
        float yawOffset = player.renderYawOffset;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        float brightness = player.entityBrightness;
        float cameraYaw = RenderManager.instance.playerViewY;
        GraphicsClip.Pixels clip = new GraphicsClip(bounds.getLeft(), bounds.getTop(), bounds.getRight(),
                bounds.getBottom()).pixels(width, height, client.displayWidth, client.displayHeight);
        try {
            try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(clip.x, clip.y, clip.width, clip.height);
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                GL11.glEnable(GL11.GL_COLOR_MATERIAL);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                float x = bounds.getLeft() + bounds.getWidth() / 2.0F;
                float y = bounds.getBottom();
                float scale = Math.min(bounds.getWidth(), bounds.getHeight()) / 2.0F;
                float horizontal = x - mouseX;
                float vertical = y - bounds.getHeight() * 5.0F / 6.0F - mouseY;
                GL11.glTranslatef(x, y, 50);
                GL11.glScalef(-scale, scale, scale);
                GL11.glRotatef(180, 0, 0, 1);
                GL11.glRotatef(135, 0, 1, 0);
                RenderHelper.enableStandardItemLighting();
                GL11.glRotatef(-135, 0, 1, 0);
                GL11.glRotatef(-(float) Math.atan(vertical / 40.0F) * 20, 1, 0, 0);
                int matrixDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                try {
                    player.renderYawOffset = (float) Math.atan(horizontal / 40.0F) * 20;
                    player.rotationYaw = (float) Math.atan(horizontal / 40.0F) * 40;
                    player.rotationPitch = -(float) Math.atan(vertical / 40.0F) * 20;
                    player.entityBrightness = 1;
                    GL11.glTranslatef(0, player.yOffset, 0);
                    RenderManager.instance.playerViewY = 180;
                    RenderManager.instance.renderEntityWithPosYaw(player, 0, 0, 0, 0, 1);
                } finally {
                    player.renderYawOffset = yawOffset;
                    player.rotationYaw = yaw;
                    player.rotationPitch = pitch;
                    player.entityBrightness = brightness;
                    RenderManager.instance.playerViewY = cameraYaw;
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    int remaining = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                    while (remaining > matrixDepth) {
                        GL11.glPopMatrix();
                        remaining--;
                    }
                }
            }
        } finally {
            // Native slots are painted afterwards and must not inherit the preview's depth
            // pixels.
            GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
            try {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(clip.x, clip.y, clip.width, clip.height);
                GL11.glDepthMask(true);
                GL11.glClearDepth(1);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    private GuiInventoryPreview() {
    }
}
