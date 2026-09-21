package betamoon.client.render;

import betamoon.entity.EntityPresentationState;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.LuaPickupEntity;
import betamoon.entity.TypedEntity;
import net.minecraft.src.Entity;
import net.minecraft.src.RenderItem;
import net.minecraft.src.RenderManager;
import org.lwjgl.opengl.GL11;

/** Keeps native item rendering as the default and enables an optional model appearance. */
public final class RenderLuaPickup extends RenderLuaProp {
    private final RenderItem nativeRenderer = new RenderItem();

    @Override
    public void setRenderManager(RenderManager manager) {
        super.setRenderManager(manager);
        nativeRenderer.setRenderManager(manager);
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTick) {
        EntityTypeDefinition definition = ((TypedEntity) entity).entityState().definition();
        EntityPresentationState state = ((TypedEntity) entity).entityState().presentation();
        if (definition != null && !state.visible(definition.render)) {
            return;
        }
        if (definition != null && definition.appearance == null) {
            nativeRenderer.doRender(entity, x, y, z, yaw, partialTick);
            return;
        }
        super.doRender(entity, x, y, z, yaw, partialTick);
    }

    @Override
    protected void applyKindTransform(Entity entity, EntityTypeDefinition definition, float partialTick) {
        LuaPickupEntity pickup = (LuaPickupEntity) entity;
        if (definition.render.pickupBobbing) {
            float bob = (float) Math.sin((pickup.age + partialTick) / 10.0f + pickup.field_804_d) * 0.1f + 0.1f;
            GL11.glTranslatef(0, bob, 0);
        }
        if (definition.render.pickupSpin) {
            float spin = ((pickup.age + partialTick) / 20.0f + pickup.field_804_d) * 57.29578f;
            GL11.glRotatef(spin, 0, 1, 0);
        }
    }
}
