package betamoon.client.render;

import betamoon.entity.EntityPresentationState;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.TypedEntity;
import net.minecraft.src.Entity;
import net.minecraft.src.Render;
import org.lwjgl.opengl.GL11;

/** Draws model-backed entities with the same backend used by blocks and items. */
public class RenderLuaProp extends Render {
    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTick) {
        EntityTypeDefinition definition = ((TypedEntity) entity).entityState().definition();
        if (definition == null) {
            return;
        }
        EntityPresentationState state = ((TypedEntity) entity).entityState().presentation();
        if (!state.visible(definition.render)) {
            return;
        }
        ModelAppearance appearance = EntityVisuals.get(definition);
        if (appearance == null) {
            return;
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            applyKindTransform(entity, definition, partialTick);
            GL11.glRotatef(-yaw, 0, 1, 0);
            GL11.glRotatef(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTick,
                    1, 0, 0);
            GL11.glTranslated(state.offsetX(), state.offsetY(), state.offsetZ());
            GL11.glRotatef(state.rotationYaw(), 0, 1, 0);
            GL11.glRotatef(state.rotationPitch(), 1, 0, 0);
            GL11.glRotatef(state.rotationRoll(), 0, 0, 1);
            GL11.glScaled(state.scaleX(), state.scaleY(), state.scaleZ());
            ModelRenderer.render(appearance, "entity", entity.ticksExisted + partialTick, 0, entity.posX,
                    entity.posY, entity.posZ, entity.getEntityBrightness(partialTick), state.animation());
        } finally {
            GL11.glPopMatrix();
        }
    }

    protected void applyKindTransform(Entity entity, EntityTypeDefinition definition, float partialTick) {
    }

    @Override
    public void doRenderShadowAndFire(Entity entity, double x, double y, double z, float yaw, float partialTick) {
        EntityTypeDefinition definition = ((TypedEntity) entity).entityState().definition();
        if (definition == null) {
            return;
        }
        EntityPresentationState state = ((TypedEntity) entity).entityState().presentation();
        if (!state.visible(definition.render)) {
            return;
        }
        float previousShadow = shadowSize;
        int previousFire = entity.fire;
        shadowSize = definition.render.shadowRadius;
        if (!definition.render.fireOverlay) {
            entity.fire = 0;
        }
        try {
            super.doRenderShadowAndFire(entity, x, y, z, yaw, partialTick);
        } finally {
            shadowSize = previousShadow;
            entity.fire = previousFire;
        }
    }
}
