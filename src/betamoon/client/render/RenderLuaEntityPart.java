package betamoon.client.render;

import net.minecraft.src.Entity;
import net.minecraft.src.Render;

/** Collision proxies are never drawn; the parent model owns their visuals. */
public final class RenderLuaEntityPart extends Render {
    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTick) {
    }
}
